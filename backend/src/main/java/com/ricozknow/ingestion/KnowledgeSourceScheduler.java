package com.ricozknow.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component @RequiredArgsConstructor @Slf4j
public class KnowledgeSourceScheduler {
    private final KnowledgeSourceRepository repository;
    private final KnowledgeIngestionService service;
    @Scheduled(fixedDelayString="${ricozknow.knowledge.sync-check-ms:60000}")
    public void syncDueSources(){
        for(var source: repository.findByStatusAndNextSyncAtBefore(KnowledgeSourceStatus.INDEXED, Instant.now())) {
            try { service.sync(source); repository.save(source); } catch(Exception ex) { log.error("Scheduled source sync failed {}", source.getId(), ex); }
        }
    }
}
