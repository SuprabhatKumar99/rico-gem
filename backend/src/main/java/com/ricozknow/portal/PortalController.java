package com.ricozknow.portal;

import com.ricozknow.analytics.AnalyticsEventService;
import com.ricozknow.analytics.AnalyticsEventType;
import com.ricozknow.common.RateLimiter;
import com.ricozknow.common.TooManyRequestsException;
import com.ricozknow.portal.dto.PortalArticleDetail;
import com.ricozknow.portal.dto.PortalArticleSummary;
import com.ricozknow.search.SearchService;
import com.ricozknow.search.dto.SearchResultResponse;
import com.ricozknow.article.Visibility;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Everything here is anonymous-accessible (spec section 2's "Customer Portal").
 * Tenant is resolved from the request hostname by TenantResolverFilter same as
 * any other request — no separate tenant param needed on these endpoints.
 */
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
public class PortalController {

    private static final String SESSION_COOKIE = "rk_session";
    private static final int SEARCH_PAGE_SIZE = 10;

    private final PortalArticleService portalArticleService;
    private final SearchService searchService;
    private final AnalyticsEventService analyticsEventService;
    private final RateLimiter rateLimiter;
    private final com.ricozknow.customer.CustomerAuthService customerAuthService;

    @Value("${ricozknow.security.rate-limits.search.max-requests}")
    private int searchMaxRequests;
    @Value("${ricozknow.security.rate-limits.search.window-seconds}")
    private long searchWindowSeconds;

    @GetMapping("/articles")
    public Page<PortalArticleSummary> browse(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "false") boolean pinned,
            @RequestParam(defaultValue = "false") boolean featured,
            Pageable pageable,
            HttpServletRequest req) {
        if (pinned && featured) {
            throw new IllegalArgumentException("Choose either pinned or featured, not both");
        }
        if (pinned) return portalArticleService.browsePinned(categoryId, pageable, customer(req));
        if (featured) return portalArticleService.browseFeatured(categoryId, pageable, customer(req));
        return portalArticleService.browse(categoryId, pageable, customer(req));
    }

    @GetMapping("/articles/{slug}")
    public PortalArticleDetail get(@PathVariable String slug, HttpServletRequest request) {
        return portalArticleService.getBySlug(slug, sessionId(request), customer(request));
    }

    /** Real OpenSearch-backed search (Phase 4), replacing the Phase 3 naive DB stub entirely. */
    @GetMapping("/search")
    public SearchResultResponse search(@RequestParam String q,
                                        @RequestParam(required = false) UUID categoryId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(required = false) String language,
                                        @RequestParam(required = false) String tag,
                                        @RequestParam(required = false) String articleType,
                                        @RequestParam(required = false) Instant dateFrom,
                                        @RequestParam(required = false) Instant dateTo,
                                        @RequestParam(required = false) String sourceType,
                                        HttpServletRequest request) {
        String session = sessionId(request);
        // Anonymous, unauthenticated, and hits OpenSearch on every call — the
        // one portal endpoint most worth throttling per visitor (spec section
        // 22: "Search rate limiting").
        if (!rateLimiter.allow("search:" + session, searchMaxRequests, Duration.ofSeconds(searchWindowSeconds))) {
            throw new TooManyRequestsException("Too many searches. Please slow down.");
        }
        String role = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() == null ? null :
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().findFirst().map(a -> a.getAuthority().replace("ROLE_","")).orElse(null);
        return searchService.search(q, categoryId, page, SEARCH_PAGE_SIZE, session,
                customer(request) ? java.util.EnumSet.of(Visibility.PUBLIC, Visibility.AUTHENTICATED) : java.util.EnumSet.of(Visibility.PUBLIC),
                language, tag, articleType, dateFrom, dateTo, sourceType, role);
    }

    /**
     * Records that a visitor clicked a search result (Phase 8). This is the
     * precise "did they find it" signal spec section 16's SEARCH_RESULT_CLICKED
     * event type always intended — AnalyticsAggregationService now uses this
     * instead of the earlier "viewed something within 10 minutes" proxy to
     * decide whether a search was successful.
     *
     * Fire-and-forget from the frontend: called just before navigating to the
     * clicked article, so it deliberately doesn't block on a response body.
     */
    @PostMapping("/search/click")
    public void recordClick(@RequestParam(name="q", required=false) String query, @RequestParam UUID articleId, @RequestParam(required=false) Integer position, HttpServletRequest request) {
        String metadata = "{\"query\":\"" + (query == null ? "" : query.strip().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")) + "\"}";
        analyticsEventService.record(AnalyticsEventType.SEARCH_RESULT_CLICKED, sessionId(request), null,
                articleId, null, metadata);
    }

    @PostMapping("/articles/{id}/feedback")
    public void feedback(@PathVariable UUID id, @RequestParam boolean helpful, @RequestParam(required = false) String reason, HttpServletRequest request) {
        portalArticleService.recordFeedback(id, helpful, sessionId(request), reason);
    }

    private boolean customer(HttpServletRequest request) { String token=null; if(request.getCookies()!=null) for(var c:request.getCookies()) if("rk_customer_session".equals(c.getName())) token=c.getValue(); return customerAuthService.current(token)!=null; }

    private String sessionId(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (SESSION_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        // Anonymous session tracking cookie is set by the SPA on first load
        // (see frontend PortalSessionService); absence just means we can't
        // correlate this visitor's events, not that the request is rejected.
        return request.getSession(true).getId();
    }
}