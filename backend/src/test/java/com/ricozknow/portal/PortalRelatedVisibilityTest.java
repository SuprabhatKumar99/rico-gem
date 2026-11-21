package com.ricozknow.portal;

import com.ricozknow.analytics.AnalyticsEventService;
import com.ricozknow.article.*;
import com.ricozknow.common.TenantContext;
import com.ricozknow.customer.CustomerAuthService;
import com.ricozknow.enterprise.group.GroupService;
import com.ricozknow.intelligence.FeedbackIntelligenceService;
import com.ricozknow.translation.ArticleTranslationLink;
import com.ricozknow.translation.ArticleTranslationLinkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalRelatedVisibilityTest {
    private final ArticleRepository articles = mock(ArticleRepository.class);
    private final ArticleTranslationLinkRepository translations = mock(ArticleTranslationLinkRepository.class);
    private final ArticleTagRepository tags = mock(ArticleTagRepository.class);
    private final ArticleRelatedRepository related = mock(ArticleRelatedRepository.class);
    private final PortalArticleService service = new PortalArticleService(
            articles, mock(AnalyticsEventService.class), mock(FeedbackIntelligenceService.class),
            mock(GroupService.class), new com.ricozknow.security.DocumentAccessPolicy(mock(GroupService.class)),
            mock(CustomerAuthService.class), translations, tags, related);

    @AfterEach void cleanup() { TenantContext.clear(); }

    @Test void anonymousArticleDetailsDoNotLeakPrivateOrDraftRelatedMetadata() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        Article publicArticle = article(tenant, Visibility.PUBLIC, ArticleStatus.PUBLISHED);
        Article privateArticle = article(tenant, Visibility.PRIVATE, ArticleStatus.PUBLISHED);
        Article draftArticle = article(tenant, Visibility.PUBLIC, ArticleStatus.DRAFT);
        Article allowed = article(tenant, Visibility.PUBLIC, ArticleStatus.PUBLISHED);

        when(articles.findByTenantIdAndSlugAndStatus(tenant, "public", ArticleStatus.PUBLISHED))
                .thenReturn(Optional.of(publicArticle));
        when(articles.findByTenantIdAndId(tenant, privateArticle.getId())).thenReturn(Optional.of(privateArticle));
        when(articles.findByTenantIdAndId(tenant, draftArticle.getId())).thenReturn(Optional.of(draftArticle));
        when(articles.findByTenantIdAndId(tenant, allowed.getId())).thenReturn(Optional.of(allowed));

        ArticleRelated privateLink = new ArticleRelated(); privateLink.setRelatedArticle(privateArticle);
        ArticleRelated draftLink = new ArticleRelated(); draftLink.setRelatedArticle(draftArticle);
        ArticleRelated publicLink = new ArticleRelated(); publicLink.setRelatedArticle(allowed);
        when(related.findByTenantIdAndArticleId(tenant, publicArticle.getId()))
                .thenReturn(List.of(privateLink, draftLink, publicLink));

        ArticleTranslationLink privateTranslation = new ArticleTranslationLink();
        privateTranslation.setTranslatedArticleId(privateArticle.getId());
        ArticleTranslationLink publicTranslation = new ArticleTranslationLink();
        publicTranslation.setTranslatedArticleId(allowed.getId());
        when(translations.findByTenantIdAndSourceArticleId(tenant, publicArticle.getId()))
                .thenReturn(List.of(privateTranslation, publicTranslation));

        var result = service.getBySlug("public", "visitor", false);
        assertEquals(List.of(allowed.getId()), result.relatedArticleIds());
        assertEquals(List.of(allowed.getId()), result.translations().stream().map(t -> t.articleId()).toList());
    }

    private static Article article(UUID tenant, Visibility visibility, ArticleStatus status) {
        Article article = new Article();
        article.setId(UUID.randomUUID());
        article.setTenantId(tenant);
        article.setSlug("public");
        article.setTitle("Article");
        article.setVisibility(visibility);
        article.setStatus(status);
        ArticleVersion version = new ArticleVersion();
        version.setId(UUID.randomUUID());
        version.setVersionNumber(1);
        article.setActiveVersion(version);
        return article;
    }
}
