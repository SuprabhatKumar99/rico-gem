package com.ricozknow.ingestion;

import com.ricozknow.common.TenantContext;
import com.ricozknow.rag.EmbeddingService;
import com.ricozknow.rag.RagChunkDocument;
import com.ricozknow.rag.RagIndexInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class KnowledgeIngestionService {
    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    private static final int MAX_BULK_FILES = 20;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream",
            "application/zip"
    );

    private final KnowledgeSourceRepository repository;
    private final DocumentTextExtractor extractor;
    private final WebsiteCrawler crawler;
    private final OpenSearchClient openSearch;
    private final EmbeddingService embeddingService;
    private final S3Client s3Client;
    private final com.ricozknow.common.SsrfGuard ssrfGuard;
    @Value("${storage.s3.bucket}") private String bucket;
    @Value("${ricozknow.security.rag.chunk.max-chars:1800}") private int maxChars;
    @Value("${ricozknow.security.rag.enabled:false}") private boolean ragEnabled;
    @Value("${ricozknow.knowledge.max-file-size-bytes:26214400}") private long configuredMaxFileSize;
    @Value("${ricozknow.knowledge.max-bulk-files:20}") private int configuredMaxBulkFiles;

    @Transactional
    public KnowledgeSource upload(MultipartFile file, String language, Integer syncIntervalMinutes) {
        validateFile(file);
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("document");
        KnowledgeSourceType type = detectType(name);
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (Exception ex) { throw new IllegalArgumentException("Unable to read uploaded file", ex); }
        validateContent(type, file.getContentType(), bytes, name);

        KnowledgeSource source = new KnowledgeSource();
        source.setId(UUID.randomUUID()); source.setTenantId(TenantContext.get()); source.setType(type); source.setName(name);
        source.setMimeType(normalizeMime(type, file.getContentType())); source.setLanguage(normalizeLanguage(language));
        source.setCreatedBy(currentUserId()); source.setSyncIntervalMinutes(syncIntervalMinutes);
        source.setStatus(KnowledgeSourceStatus.PROCESSING); source.setProcessingPhase(KnowledgeProcessingPhase.UPLOADING);
        source.setCreatedAt(Instant.now()); source.setUpdatedAt(Instant.now()); source.setRetryCount(0); source.setMaxRetries(3);
        source.setStorageKey(source.getTenantId()+"/knowledge-sources/"+source.getId()+"-"+sanitize(name));
        source.setChecksum(sha256(bytes));
        source = repository.save(source);
        try {
            s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(source.getStorageKey()).contentType(source.getMimeType()).build(), RequestBody.fromBytes(bytes));
            source.setProcessingPhase(KnowledgeProcessingPhase.EXTRACTING); source.setUpdatedAt(Instant.now()); repository.save(source);
            ingest(source, extractor.extract(bytes, name));
            return repository.save(source);
        } catch (Exception ex) {
            markFailed(source, ex);
            return repository.save(source);
        }
    }

    @Transactional
    public List<KnowledgeSource> bulkUpload(List<MultipartFile> files, String language, Integer syncIntervalMinutes) {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("At least one document is required");
        int max = Math.min(MAX_BULK_FILES, Math.max(1, configuredMaxBulkFiles));
        if (files.size() > max) throw new IllegalArgumentException("Bulk upload is limited to " + max + " files");
        List<KnowledgeSource> results = new ArrayList<>();
        for (MultipartFile file : files) {
            try { results.add(upload(file, language, syncIntervalMinutes)); }
            catch (RuntimeException ex) {
                KnowledgeSource failed = failedPlaceholder(file, language, ex);
                results.add(failed);
            }
        }
        return results;
    }

    @Transactional
    public KnowledgeSource createUrl(String url, String name, String language, Integer syncIntervalMinutes, Integer maxPages, boolean website) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("URL is required");
        URIValidator.validateHttpUrl(url); ssrfGuard.assertSafeTarget(url);
        var source = new KnowledgeSource(); source.setId(UUID.randomUUID()); source.setTenantId(TenantContext.get());
        source.setType(website ? KnowledgeSourceType.WEBSITE : KnowledgeSourceType.URL); source.setName(name == null || name.isBlank() ? url : name);
        source.setUrl(url); source.setLanguage(normalizeLanguage(language)); source.setCreatedBy(currentUserId()); source.setSyncIntervalMinutes(syncIntervalMinutes);
        source.setMaxPages(maxPages == null ? 25 : Math.min(100, Math.max(1, maxPages))); source.setStatus(KnowledgeSourceStatus.PROCESSING);
        source.setProcessingPhase(KnowledgeProcessingPhase.VALIDATING); source.setCreatedAt(Instant.now()); source.setUpdatedAt(Instant.now());
        source = repository.save(source);
        sync(source);
        return repository.save(source);
    }

    @Transactional
    public KnowledgeSource sync(UUID sourceId) {
        KnowledgeSource source = repository.findByTenantIdAndId(TenantContext.get(), sourceId).orElseThrow(() -> new IllegalArgumentException("Knowledge source not found"));
        sync(source); return repository.save(source);
    }

    @Transactional
    public KnowledgeSource reindex(UUID sourceId) {
        KnowledgeSource source = repository.findByTenantIdAndId(TenantContext.get(), sourceId).orElseThrow(() -> new IllegalArgumentException("Knowledge source not found"));
        source.setRetryCount(0); source.setLastError(null); source.setStatus(KnowledgeSourceStatus.PROCESSING); source.setProcessingPhase(KnowledgeProcessingPhase.PENDING); source.setUpdatedAt(Instant.now());
        sync(source); return repository.save(source);
    }

    @Transactional
    public KnowledgeSource retry(UUID sourceId) {
        KnowledgeSource source = repository.findByTenantIdAndId(TenantContext.get(), sourceId).orElseThrow(() -> new IllegalArgumentException("Knowledge source not found"));
        if (source.getStatus() != KnowledgeSourceStatus.FAILED) throw new IllegalStateException("Only failed sources can be retried");
        if (source.getRetryCount() >= source.getMaxRetries()) throw new IllegalStateException("Retry limit reached; use reindex to start a new retry cycle");
        sync(source); return repository.save(source);
    }

    @Transactional
    public void delete(UUID sourceId) {
        KnowledgeSource source = repository.findByTenantIdAndId(TenantContext.get(), sourceId).orElseThrow(() -> new IllegalArgumentException("Knowledge source not found"));
        source.setProcessingPhase(KnowledgeProcessingPhase.DELETING); source.setStatus(KnowledgeSourceStatus.DISABLED); source.setUpdatedAt(Instant.now()); repository.save(source);
        try {
            deleteSourceChunks(source.getTenantId(), source.getId());
            if (source.getStorageKey() != null) s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(source.getStorageKey()).build());
            repository.delete(source);
        } catch (Exception ex) {
            source.setStatus(KnowledgeSourceStatus.FAILED); source.setProcessingPhase(KnowledgeProcessingPhase.FAILED); source.setLastError("Delete failed: " + safeMessage(ex)); source.setUpdatedAt(Instant.now()); repository.save(source);
            throw new IllegalStateException("Failed to delete knowledge source", ex);
        }
    }

    @Transactional
    public void sync(KnowledgeSource source) {
        source.setStatus(KnowledgeSourceStatus.PROCESSING); source.setProcessingPhase(KnowledgeProcessingPhase.EXTRACTING); source.setLastError(null);
        source.setLastAttemptAt(Instant.now()); source.setUpdatedAt(Instant.now()); source.setRetryCount(source.getRetryCount() + 1); repository.save(source);
        try {
            String text; int pages = 1;
            if (source.getType() == KnowledgeSourceType.URL) {
                text = crawler.crawl(source.getUrl(), 1).text();
            } else if (source.getType() == KnowledgeSourceType.WEBSITE) {
                var result = crawler.crawl(source.getUrl(), source.getMaxPages()); text = result.text(); pages = result.pages();
            } else {
                if (source.getStorageKey() == null) throw new IllegalStateException("Binary source has no storage object");
                byte[] bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(source.getStorageKey()).build()).asByteArray();
                source.setProcessingPhase(KnowledgeProcessingPhase.EXTRACTING); repository.save(source);
                text = extractor.extract(bytes, source.getName());
            }
            if (text == null || text.isBlank()) throw new IllegalArgumentException("No readable content was found");
            source.setProcessingPhase(KnowledgeProcessingPhase.INDEXING); repository.save(source);
            ingest(source, text); source.setDiscoveredPages(pages);
        } catch (Exception ex) {
            markFailed(source, ex); log.error("Knowledge source sync failed {}", source.getId(), ex);
        }
    }

    private void ingest(KnowledgeSource source, String text) throws Exception {
        if (!ragEnabled) throw new IllegalStateException("RAG must be enabled before knowledge sources can be indexed");
        source.setProcessingPhase(KnowledgeProcessingPhase.INDEXING); repository.save(source);
        deleteSourceChunks(source.getTenantId(), source.getId());
        List<String> chunks = chunk(text);
        if (chunks.isEmpty()) throw new IllegalArgumentException("No indexable content was found");
        for (int i=0;i<chunks.size();i++) {
            String content=chunks.get(i); if(content.isBlank()) continue;
            List<Float> vector=embeddingService.embed(content);
            String chunkId=source.getId()+":"+i;
            RagChunkDocument doc=new RagChunkDocument(source.getTenantId().toString(), null, null, chunkId, null, source.getName(), content, null, null, "PUBLIC", null, i, vector, source.getId().toString(), source.getType().name(), source.getUrl(), source.getLanguage());
            openSearch.index(IndexRequest.of(b->b.index(RagIndexInitializer.INDEX_NAME).id(source.getTenantId()+":source:"+chunkId).document(doc)));
        }
        source.setStatus(KnowledgeSourceStatus.INDEXED); source.setProcessingPhase(KnowledgeProcessingPhase.READY); source.setLastSyncedAt(Instant.now()); source.setLastError(null); source.setUpdatedAt(Instant.now());
        if(source.getSyncIntervalMinutes()!=null && source.getSyncIntervalMinutes()>0) source.setNextSyncAt(Instant.now().plusSeconds(source.getSyncIntervalMinutes()*60L));
    }

    private void deleteSourceChunks(UUID tenantId, UUID sourceId) throws Exception {
        Query query = Query.of(q -> q.bool(b -> b
            .filter(f -> f.term(t -> t.field("tenantId").value(FieldValue.of(tenantId.toString()))))
            .filter(f -> f.term(t -> t.field("sourceId").value(FieldValue.of(sourceId.toString()))))));
        openSearch.deleteByQuery(DeleteByQueryRequest.of(d -> d.index(RagIndexInitializer.INDEX_NAME).query(query)));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty");
        long max = configuredMaxFileSize > 0 ? Math.min(configuredMaxFileSize, MAX_FILE_SIZE) : MAX_FILE_SIZE;
        if (file.getSize() > max) throw new IllegalArgumentException("Document exceeds maximum size of " + (max / (1024*1024)) + "MB");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").trim();
        if (name.isBlank() || name.length() > 255) throw new IllegalArgumentException("Invalid filename");
        if (name.contains("..") || name.contains("/") || name.contains("\\")) throw new IllegalArgumentException("Invalid filename");
    }

    private KnowledgeSourceType detectType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return KnowledgeSourceType.PDF;
        if (lower.endsWith(".docx")) return KnowledgeSourceType.DOCX;
        throw new IllegalArgumentException("Only PDF and DOCX files are supported");
    }

    private void validateContent(KnowledgeSourceType type, String contentType, byte[] bytes, String name) {
        String detected = new org.apache.tika.Tika().detect(bytes, name);
        if (type == KnowledgeSourceType.PDF && !(detected.equals("application/pdf") || looksLikePdf(bytes))) throw new IllegalArgumentException("File content is not a valid PDF");
        if (type == KnowledgeSourceType.DOCX && !looksLikeZip(bytes)) throw new IllegalArgumentException("File content is not a valid DOCX archive");
        if (contentType != null && !contentType.isBlank() && !ALLOWED_MIME_TYPES.contains(contentType) && !(type == KnowledgeSourceType.DOCX && contentType.equals("application/zip"))) {
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }

    private boolean looksLikePdf(byte[] b) { return b.length >= 5 && b[0]=='%' && b[1]=='P' && b[2]=='D' && b[3]=='F' && b[4]=='-'; }
    private boolean looksLikeZip(byte[] b) { return b.length >= 4 && b[0]=='P' && b[1]=='K' && (b[2]==3 || b[2]==5 || b[2]==7) && (b[3]==4 || b[3]==6 || b[3]==8); }
    private String normalizeMime(KnowledgeSourceType type, String mime) { return type == KnowledgeSourceType.PDF ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; }
    private void markFailed(KnowledgeSource source, Exception ex) { source.setStatus(KnowledgeSourceStatus.FAILED); source.setProcessingPhase(KnowledgeProcessingPhase.FAILED); source.setLastError(safeMessage(ex)); source.setLastAttemptAt(Instant.now()); source.setUpdatedAt(Instant.now()); }
    private KnowledgeSource failedPlaceholder(MultipartFile file, String language, RuntimeException ex) { KnowledgeSource s=new KnowledgeSource(); s.setId(UUID.randomUUID()); s.setTenantId(TenantContext.get()); String failedName=Optional.ofNullable(file.getOriginalFilename()).orElse("document.pdf"); s.setType(safeDetectType(failedName)); s.setName(Optional.ofNullable(file.getOriginalFilename()).orElse("document")); s.setLanguage(normalizeLanguage(language)); s.setStatus(KnowledgeSourceStatus.FAILED); s.setProcessingPhase(KnowledgeProcessingPhase.FAILED); s.setLastError(safeMessage(ex)); s.setCreatedBy(currentUserId()); s.setCreatedAt(Instant.now()); s.setUpdatedAt(Instant.now()); return repository.save(s); }
    private KnowledgeSourceType safeDetectType(String name) { try { return detectType(name); } catch (RuntimeException ignored) { return KnowledgeSourceType.PDF; } }
    private String safeMessage(Throwable ex) { String m=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage(); return m.length()>4000?m.substring(0,4000):m; }
    private List<String> chunk(String text){ List<String> out=new ArrayList<>(); if(text==null)return out; for(int s=0;s<text.length();s+=maxChars) out.add(text.substring(s,Math.min(text.length(),s+maxChars)).trim()); return out; }
    private String normalizeLanguage(String language){ if(language==null||language.isBlank())return "en"; String v=language.trim().toLowerCase(Locale.ROOT); if(!v.matches("[a-z]{2,8}(-[a-z]{2,8})?")) throw new IllegalArgumentException("Invalid language code"); return v; }
    private String sanitize(String s){return s.replaceAll("[^a-zA-Z0-9._-]","_");}
    private String sha256(byte[] b){try{var md=MessageDigest.getInstance("SHA-256");return HexFormat.of().formatHex(md.digest(b));}catch(Exception e){throw new IllegalStateException(e);}}
    private UUID currentUserId(){var a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); if(a!=null && a.getPrincipal() instanceof com.ricozknow.user.AuthenticatedUser u)return u.userId(); throw new IllegalStateException("No authenticated user in context");}
    public static final class URIValidator { static void validateHttpUrl(String url){ var u=java.net.URI.create(url); if(!"http".equalsIgnoreCase(u.getScheme())&& !"https".equalsIgnoreCase(u.getScheme())) throw new IllegalArgumentException("Only HTTP(S) URLs are allowed"); if(u.getHost()==null) throw new IllegalArgumentException("URL host is required"); } }
}
