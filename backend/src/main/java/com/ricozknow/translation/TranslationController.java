package com.ricozknow.translation;
import com.ricozknow.common.TenantContext; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/articles") @RequiredArgsConstructor public class TranslationController{
 private final ArticleTranslationLinkRepository repo;
 private final com.ricozknow.article.ArticleRepository articles;
 @GetMapping("/{id}/translations") public List<ArticleTranslationLink> list(@PathVariable UUID id){return repo.findByTenantIdAndSourceArticleId(TenantContext.get(),id);}
 @PostMapping("/{id}/translations") @PreAuthorize("hasAnyRole('ADMIN','EDITOR')") public ArticleTranslationLink link(@PathVariable UUID id,@Valid @RequestBody Request r){
  UUID tenant=TenantContext.get();
  var source=articles.findByTenantIdAndId(tenant,id).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
  var target=articles.findByTenantIdAndId(tenant,r.translatedArticleId()).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
  if(id.equals(r.translatedArticleId())||!target.getLanguage().equalsIgnoreCase(r.language()))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Translation target language mismatch");
  ArticleTranslationLink x=new ArticleTranslationLink();x.setTenantId(TenantContext.get());x.setSourceArticleId(id);x.setTranslatedArticleId(r.translatedArticleId());x.setLanguage(r.language().toLowerCase());return repo.save(x);}
 public record Request(@NotNull UUID translatedArticleId,@NotBlank @Size(max=16) String language){}
}