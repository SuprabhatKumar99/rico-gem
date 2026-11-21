package com.ricozknow.search;
import com.ricozknow.common.TenantContext;
import com.ricozknow.notification.EmailService;
import com.ricozknow.search.dto.SearchResultResponse;
import com.ricozknow.article.Visibility;
import com.ricozknow.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
@Component @RequiredArgsConstructor
public class SavedSearchAlertScheduler {
 private final SavedSearchRepository repo; private final SearchService search; private final EmailService email; private final TenantRepository tenants;
 @Scheduled(fixedDelayString="${ricozknow.search.saved-alert-interval-ms:3600000}")
 public void check(){
  for(SavedSearch saved: repo.findByActiveTrue()){
   try {
    TenantContext.set(saved.getTenantId());
    java.time.Instant effectiveFrom = saved.getLastCheckedAt() != null && (saved.getDateFrom() == null || saved.getLastCheckedAt().isAfter(saved.getDateFrom()))
      ? saved.getLastCheckedAt() : saved.getDateFrom();
    SearchResultResponse r=search.search(saved.getQueryText(),saved.getCategoryId(),0,5,"saved-alert:"+saved.getId(),
      Set.of(Visibility.PUBLIC,Visibility.AUTHENTICATED),saved.getLanguage(),saved.getTag(),saved.getArticleType(),effectiveFrom,saved.getDateTo(),null,null);
    SearchResultResponse.SearchHit newestHit=r.results().stream().findFirst().orElse(null);
    UUID newest=newestHit == null ? null : newestHit.articleId();
    String newestSource=newestHit == null ? null : newestHit.sourceId();
    boolean changed = newestHit != null && (newest != null ? !newest.equals(saved.getLastNotifiedArticleId()) : !Objects.equals(newestSource, saved.getLastNotifiedSourceId()));
    if(changed){
      String tenantName=tenants.findById(saved.getTenantId()).map(t->t.getName()).orElse("Knowledge base");
      email.send(saved.getEmail(),"New knowledge matches your saved search",
        "New content matched your saved search \"" + saved.getQueryText() + "\" in " + tenantName + ".\n\n"+r.results().stream().limit(3).map(SearchResultResponse.SearchHit::title).toList());
      saved.setLastNotifiedArticleId(newest); saved.setLastNotifiedSourceId(newestSource);
    }
    saved.setLastCheckedAt(Instant.now()); saved.setUpdatedAt(Instant.now()); repo.save(saved);
   } catch(Exception ignored) { } finally { TenantContext.clear(); }
  }
 }
}
