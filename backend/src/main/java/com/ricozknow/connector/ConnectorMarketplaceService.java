package com.ricozknow.connector;

import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Durable, tenant-qualified source selection and ingestion, independent of article publishing. */
@Service @RequiredArgsConstructor
public class ConnectorMarketplaceService {
    private final com.ricozknow.billing.BillingService billing;
    private final JdbcTemplate db;
    private final ConnectorCredentialService credentials;
    private final ConnectorRemoteClient remote;
    public record Source(UUID id,String provider,String remoteId,String name,String status,Instant lastSyncedAt,Instant nextSyncAt,int retryCount,String lastError,boolean disabled,long documents) {}
    public record SyncRun(UUID id,Instant startedAt,Instant finishedAt,String state,int inserted,int updated,int deleted,String error) {}
    public record SearchHit(UUID id,UUID sourceId,String provider,String title,String url,String excerpt,Instant modifiedAt) {}
    private record Claimed(UUID id,UUID tenant,String provider,String remoteId) {}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static String message(Throwable ex){String s=ex instanceof ConnectorRemoteClient.RemoteException?ex.getMessage():ex.getClass().getSimpleName()+": "+ex.getMessage();return s==null?"Unknown connector error":s.substring(0,Math.min(500,s.length()));}
    public List<Source> sources(UUID tenant){return db.query("select s.id,s.provider,s.remote_id,s.display_name,s.status,s.last_synced_at,s.next_sync_at,s.retry_count,s.last_error,s.disabled,(select count(*) from connector_document d where d.source_id=s.id and d.tenant_id=s.tenant_id and d.deleted_at is null) from connector_source s where s.tenant_id=? order by s.created_at desc",
        (rs,i)->new Source((UUID)rs.getObject(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toInstant(),rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toInstant(),rs.getInt(8),rs.getString(9),rs.getBoolean(10),rs.getLong(11)),tenant);}
    public ConnectorRemoteClient.Page browse(UUID tenant,String provider,String cursor){return remote.browse(provider,credentials.accessToken(tenant,provider),cursor);}
    @org.springframework.transaction.annotation.Transactional
    public Source select(UUID tenant,String provider,String remoteId,String title,Integer interval){
        ConnectorCatalog.require(provider);
        if(remoteId==null||remoteId.isBlank()||remoteId.length()>800||!remoteId.matches("[a-zA-Z0-9_./:!~-]+")||remoteId.contains(".."))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid remote document ID");
        int minutes=interval==null?60:interval;if(minutes<15||minutes>10080)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sync interval must be between 15 minutes and 7 days");
        credentials.accessToken(tenant,provider); // only a configured connection may add sources
        // Selecting an already-registered source does not consume a new slot.
        Boolean wasDisabled=db.query("select disabled from connector_source where tenant_id=? and provider=? and remote_id=?",rs->rs.next()?rs.getBoolean(1):null,tenant,provider,remoteId);
        if(wasDisabled==null || wasDisabled) billing.checkCapacity(tenant,"max_connectors");
        UUID id=UUID.randomUUID();String name=title==null||title.isBlank()?remoteId:title.substring(0,Math.min(title.length(),400));
        db.update("insert into connector_source(id,tenant_id,provider,remote_id,display_name,sync_interval_minutes,next_sync_at) values(?,?,?,?,?,?,now()) on conflict(tenant_id,provider,remote_id) do update set disabled=false,display_name=excluded.display_name,sync_interval_minutes=excluded.sync_interval_minutes,next_sync_at=now(),status='PENDING'",id,tenant,provider,remoteId,name,minutes);
        return sources(tenant).stream().filter(s->s.provider().equals(provider)&&s.remoteId().equals(remoteId)).findFirst().orElseThrow();
    }
    public List<SyncRun> runs(UUID tenant,UUID source){
        if(sources(tenant).stream().noneMatch(s->s.id().equals(source)))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return db.query("select id,started_at,finished_at,state,inserted_count,updated_count,deleted_count,error_message from connector_sync_run where tenant_id=? and source_id=? order by started_at desc limit 30",
          (rs,i)->new SyncRun((UUID)rs.getObject(1),rs.getTimestamp(2).toInstant(),rs.getTimestamp(3)==null?null:rs.getTimestamp(3).toInstant(),rs.getString(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),rs.getString(8)),tenant,source);
    }
    public void disable(UUID tenant,UUID source){if(db.update("update connector_source set disabled=true,status='DISABLED',updated_at=now() where id=? and tenant_id=?",source,tenant)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    public void remove(UUID tenant,UUID source){if(db.update("delete from connector_source where id=? and tenant_id=?",source,tenant)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    @org.springframework.transaction.annotation.Transactional
    public void enqueue(UUID tenant,UUID source){
        Boolean wasDisabled=db.query("select disabled from connector_source where tenant_id=? and id=?",rs->rs.next()?rs.getBoolean(1):null,tenant,source);
        if(wasDisabled==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(wasDisabled) billing.checkCapacity(tenant,"max_connectors");
        if(db.update("update connector_source set next_sync_at=now(),status='PENDING',retry_count=0,last_error=null,disabled=false,updated_at=now() where tenant_id=? and id=? and status<>'PROCESSING'",tenant,source)==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Source not found or already synchronizing");
    }
    /** Atomic SKIP LOCKED job claims let multiple worker instances coexist without double ingestion. */
    @Scheduled(fixedDelayString="${ricozknow.connectors.scheduler-ms:60000}")
    public void scheduled(){
        List<Claimed> due=db.query("update connector_source set status='PROCESSING',next_sync_at=now()+interval '20 minutes',updated_at=now() where id in (select id from connector_source where disabled=false and next_sync_at<=now() order by next_sync_at limit 10 for update skip locked) returning id,tenant_id,provider,remote_id",
            (rs,i)->new Claimed((UUID)rs.getObject(1),(UUID)rs.getObject(2),rs.getString(3),rs.getString(4)));
        for(Claimed s:due){try{TenantContext.set(s.tenant());synchronize(s);}catch(Exception ignored){/* synchronize records failures */}finally{TenantContext.clear();}}
    }
    private void synchronize(Claimed s){
        UUID run=UUID.randomUUID();db.update("insert into connector_sync_run(id,tenant_id,source_id) values(?,?,?)",run,s.tenant(),s.id());
        try{
            String token=credentials.accessToken(s.tenant(),s.provider());
            ConnectorRemoteClient.Content result=remote.fetch(s.provider(),token,s.remoteId());
            String title=result.item().name();String content=result.text();String checksum=sha256(content);
            List<String> previous=db.query("select checksum from connector_document where tenant_id=? and source_id=? and remote_id=?",(rs,i)->rs.getString(1),s.tenant(),s.id(),s.remoteId());
            boolean changed=previous.isEmpty()||!previous.get(0).equals(checksum);
            db.update("insert into connector_document(id,tenant_id,source_id,remote_id,title,source_url,mime_type,remote_modified_at,checksum,content_text,acl_mode,last_seen_at) values (?,?,?,?,?,?,?,?,?,?,'ADMIN_ONLY',now()) on conflict(tenant_id,source_id,remote_id) do update set title=excluded.title,source_url=excluded.source_url,mime_type=excluded.mime_type,remote_modified_at=excluded.remote_modified_at,checksum=excluded.checksum,content_text=excluded.content_text,deleted_at=null,last_seen_at=now(),updated_at=now()",
               UUID.randomUUID(),s.tenant(),s.id(),s.remoteId(),title, result.item().url(),result.item().mimeType(),result.item().modifiedAt()==null?null:java.sql.Timestamp.from(result.item().modifiedAt()),checksum,content);
            db.update("update connector_source set status='READY',retry_count=0,last_error=null,last_synced_at=now(),next_sync_at=now()+(sync_interval_minutes*interval '1 minute'),updated_at=now() where id=? and tenant_id=?",s.id(),s.tenant());
            db.update("update connector_sync_run set state='SUCCEEDED',finished_at=now(),inserted_count=?,updated_count=? where id=? and tenant_id=?",previous.isEmpty()?1:0,!previous.isEmpty()&&changed?1:0,run,s.tenant());
        }catch(Exception ex){
            boolean gone=ex instanceof ConnectorRemoteClient.RemoteException remoteError && remoteError.deleted();
            if(gone){
                db.update("update connector_document set deleted_at=now(),content_text='',updated_at=now() where tenant_id=? and source_id=? and deleted_at is null",s.tenant(),s.id());
                db.update("update connector_source set status='REMOTE_DELETED',disabled=true,last_error='Remote item deleted',updated_at=now() where tenant_id=? and id=?",s.tenant(),s.id());
                db.update("update connector_sync_run set state='REMOTE_DELETED',finished_at=now(),deleted_count=1 where id=? and tenant_id=?",run,s.tenant());
            }else{
                String safe=message(ex);
                // Capped exponential delay, no runaway retry loop.
                db.update("update connector_source set status='ERROR',retry_count=retry_count+1,last_error=?,next_sync_at=now()+(least(3600,60*power(2,least(retry_count,6)))*interval '1 second'),disabled=(retry_count+1>=max_retries),updated_at=now() where id=? and tenant_id=?",safe,s.id(),s.tenant());
                db.update("update connector_sync_run set state='FAILED',finished_at=now(),error_message=? where id=? and tenant_id=?",safe,run,s.tenant());
            }
        }
    }
    /** Restricted by role at the controller: no external content enters unrestricted search/RAG. */
    public List<SearchHit> search(UUID tenant,String question){
        if(question==null||question.isBlank())return List.of();
        return db.query("select d.id,d.source_id,s.provider,d.title,d.source_url,left(d.content_text,300),d.remote_modified_at from connector_document d join connector_source s on s.id=d.source_id and s.tenant_id=d.tenant_id where d.tenant_id=? and d.deleted_at is null and s.disabled=false and d.acl_mode='ADMIN_ONLY' and d.search_vector @@ plainto_tsquery('simple',?) order by ts_rank(d.search_vector,plainto_tsquery('simple',?)) desc limit 30",
          (rs,i)->new SearchHit((UUID)rs.getObject(1),(UUID)rs.getObject(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toInstant()),tenant,question.substring(0,Math.min(200,question.length())),question.substring(0,Math.min(200,question.length())));
    }
}
