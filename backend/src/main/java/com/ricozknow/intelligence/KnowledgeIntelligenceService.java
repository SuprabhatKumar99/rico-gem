package com.ricozknow.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ricozknow.analytics.DailyArticleStat;
import com.ricozknow.analytics.DailyArticleStatRepository;
import com.ricozknow.article.*;
import com.ricozknow.common.TenantContext;
import com.ricozknow.intelligence.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class KnowledgeIntelligenceService {
    private final ArticleRepository articleRepository;
    private final ArticleVersionRepository versionRepository;
    private final ArticleIntelligenceRepository intelligenceRepository;
    private final ArticleDependencyRepository dependencyRepository;
    private final KnowledgeGapTicketRepository gapRepository;
    private final DailyArticleStatRepository articleStatRepository;
    private final AiKnowledgeService ai;
    private final ObjectMapper mapper;

    @Transactional
    public ArticleIntelligenceResponse analyze(UUID articleId) {
        UUID tenant=TenantContext.get(); Article a=articleRepository.findByTenantIdAndId(tenant,articleId).orElseThrow();
        ArticleVersion v=a.getActiveVersion(); if(v==null) v=versionRepository.findTopByTenantIdAndArticleIdOrderByVersionNumberDesc(tenant,articleId).orElseThrow();
        List<String> keywords=ai.keywords(v.getContent(),a.getTitle()); List<String> topics=ai.topics(v.getContent(),a.getTitle());
        Health health=health(a, v, tenant);
        ArticleIntelligence i=intelligenceRepository.findByTenantIdAndArticleId(tenant,articleId).orElseGet(ArticleIntelligence::new);
        i.setTenantId(tenant); i.setArticleId(articleId); i.setVersionId(v.getId()); i.setKeywords(mapper.valueToTree(keywords).toString()); i.setTopics(mapper.valueToTree(topics).toString()); i.setHealthScore(health.score); i.setHealthReasons(mapper.valueToTree(health.reasons).toString()); i.setAnalyzedAt(Instant.now()); intelligenceRepository.save(i);
        detectDependencies(a, v);
        return response(i);
    }

    @Transactional
    public ArticleIntelligenceResponse get(UUID articleId){ return intelligenceRepository.findByTenantIdAndArticleId(TenantContext.get(),articleId).map(this::response).orElseGet(()->analyze(articleId)); }

    @Transactional
    public List<ArticleDependencyResponse> detectDependencies(Article article, ArticleVersion version){
        UUID tenant=TenantContext.get(); dependencyRepository.deleteByTenantIdAndArticleId(tenant,article.getId());
        String text=article.getTitle()+" "+flatten(version.getContent());
        List<Article> all=articleRepository.findAllByTenantId(tenant); List<ArticleDependency> deps=new ArrayList<>();
        for(Article target:all){ if(target.getId().equals(article.getId())) continue; double confidence=0; String evidence=null;
            String slug=target.getSlug(); String title=target.getTitle();
            if(text.toLowerCase(Locale.ROOT).contains(slug.toLowerCase(Locale.ROOT))) {confidence=.98; evidence="Referenced by slug: "+slug;}
            else if(title!=null && title.length()>6 && text.toLowerCase(Locale.ROOT).contains(title.toLowerCase(Locale.ROOT))) {confidence=.85; evidence="Referenced by article title: "+title;}
            Matcher m=Pattern.compile("(?i)(?:article|knowledge|doc(?:ument)?)\\s*[:#/-]?\\s*"+Pattern.quote(target.getId().toString())).matcher(text);
            if(m.find()){confidence=1.0; evidence=m.group();}
            if(confidence>0){ArticleDependency d=new ArticleDependency(); d.setTenantId(tenant);d.setArticleId(article.getId());d.setDependsOnArticleId(target.getId());d.setConfidence(confidence);d.setEvidence(evidence);deps.add(d);}
        }
        dependencyRepository.saveAll(deps); return deps.stream().map(this::dep).toList();
    }

    @Transactional(readOnly=true)
    public List<ArticleDependencyResponse> dependencies(UUID articleId){return dependencyRepository.findByTenantIdAndArticleId(TenantContext.get(),articleId).stream().map(this::dep).toList();}
    @Transactional(readOnly=true)
    public List<ArticleDependencyResponse> dependents(UUID articleId){return dependencyRepository.findByTenantIdAndDependsOnArticleId(TenantContext.get(),articleId).stream().map(this::dep).toList();}

    @Transactional
    public List<KnowledgeGapTicketResponse> refreshKnowledgeGaps(int days,int limit){
        UUID tenant=TenantContext.get();
        var stats=articleStatRepository.findByTenantIdAndStatDateBetween(tenant,LocalDate.now(ZoneOffset.UTC).minusDays(Math.max(1,days)),LocalDate.now(ZoneOffset.UTC));
        // Gap aggregation comes from daily search stats; this method delegates through AnalyticsDashboardService in controller/service composition.
        return gapRepository.findTop100ByTenantIdAndStatusOrderByGapScoreDesc(tenant,"OPEN").stream().limit(limit).map(this::gap).toList();
    }

    @Transactional
    public KnowledgeGapTicket upsertGap(String query,int volume,int zero,int unsuccessful,int score){
        UUID tenant=TenantContext.get(); KnowledgeGapTicket t=gapRepository.findByTenantIdAndQueryText(tenant,query).orElseGet(KnowledgeGapTicket::new);
        t.setTenantId(tenant);t.setQueryText(query);t.setVolume(volume);t.setZeroResultCount(zero);t.setUnsuccessfulCount(unsuccessful);t.setGapScore(score);if(t.getStatus()==null)t.setStatus("OPEN");
        return gapRepository.save(t);
    }

    public KnowledgeGapTicketResponse gap(KnowledgeGapTicket t){ return new KnowledgeGapTicketResponse(t.getId(),t.getQueryText(),t.getVolume(),t.getZeroResultCount(),t.getUnsuccessfulCount(),t.getGapScore(),t.getStatus(),t.getSuggestedTitle(),readStrings(t.getSuggestedOutline()),readStrings(t.getSuggestedKeywords()),t.getArticleId()); }
    public ArticleIntelligenceResponse response(ArticleIntelligence i){return new ArticleIntelligenceResponse(i.getArticleId(),i.getVersionId(),readStrings(i.getKeywords()),readStrings(i.getTopics()),i.getHealthScore(),readStrings(i.getHealthReasons()),i.getAnalyzedAt());}
    private ArticleDependencyResponse dep(ArticleDependency d){return new ArticleDependencyResponse(d.getId(),d.getArticleId(),d.getDependsOnArticleId(),d.getDependencyType(),d.getEvidence(),d.getConfidence());}
    private List<String> readStrings(String json){try{return Arrays.asList(mapper.readValue(json,String[].class));}catch(Exception e){return List.of();}}
    private String flatten(com.fasterxml.jackson.databind.JsonNode n){if(n==null)return "";if(n.isTextual())return n.asText()+" ";StringBuilder b=new StringBuilder();n.elements().forEachRemaining(x->b.append(flatten(x)));return b.toString();}
    private Health health(Article a,ArticleVersion v,UUID tenant){
        double score=100;List<String> reasons=new ArrayList<>(); Instant now=Instant.now();
        if(v.getPublishedAt()!=null){long age=Duration.between(v.getPublishedAt(),now).toDays(); if(age>365){score-=30;reasons.add("published more than a year ago");}else if(age>180){score-=15;reasons.add("published more than six months ago");}}
        if(v.getExpiresAt()!=null){long days=Duration.between(now,v.getExpiresAt()).toDays();if(days<0){score-=35;reasons.add("expired");}else if(days<30){score-=20;reasons.add("expires within 30 days");}}
        var stats=articleStatRepository.findByTenantIdAndStatDateBetween(tenant,LocalDate.now(ZoneOffset.UTC).minusDays(30),LocalDate.now(ZoneOffset.UTC)).stream().filter(s->s.getArticleId().equals(a.getId())).toList();
        int views=stats.stream().mapToInt(DailyArticleStat::getViews).sum(), bad=stats.stream().mapToInt(DailyArticleStat::getUnhelpfulCount).sum(), good=stats.stream().mapToInt(DailyArticleStat::getHelpfulCount).sum();
        if(bad>0 && bad>=Math.max(3,good)){score-=25;reasons.add("negative feedback is high relative to helpful feedback");}else if(bad>0){score-=Math.min(15,bad*2);reasons.add("negative feedback detected");}
        if(views==0 && a.getStatus()==ArticleStatus.PUBLISHED){score-=10;reasons.add("published article has low recent usage");}
        return new Health(Math.max(0,Math.min(100,score)),reasons);
    }
    private record Health(double score,List<String> reasons){}
}
