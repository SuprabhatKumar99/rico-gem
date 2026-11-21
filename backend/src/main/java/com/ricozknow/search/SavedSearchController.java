package com.ricozknow.search;

import com.ricozknow.common.TenantContext;
import com.ricozknow.customer.CustomerAuthService;
import com.ricozknow.customer.CustomerUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Portal subscriptions are private. A tenant slug alone never grants access to subscribers. */
@RestController
@RequestMapping("/api/v1/portal/search/saved")
@RequiredArgsConstructor
public class SavedSearchController {
    private final SavedSearchRepository repo;
    private final CustomerAuthService customerAuth;

    public record Request(String email, String query, UUID categoryId, String language, String tag,
                          String articleType, java.time.Instant dateFrom, java.time.Instant dateTo) {}

    private CustomerUser requireCustomer(String rawSession) {
        CustomerUser user = customerAuth.current(rawSession);
        if (user == null || !TenantContext.get().equals(user.getTenantId()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Customer login required");
        return user;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<SavedSearch> list(@CookieValue(name = "rk_customer_session", required = false) String session) {
        var current = requireCustomer(session);
        return repo.findByTenantIdAndEmailIgnoreCaseOrderByCreatedAtDesc(TenantContext.get(), current.getEmail());
    }

    @PostMapping
    @Transactional
    public SavedSearch create(@CookieValue(name = "rk_customer_session", required = false) String session,
                              @RequestBody Request request) {
        var customer = requireCustomer(session);
        if (request.query() == null || request.query().isBlank() || request.query().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query required (max 500 characters)");
        if (request.email() != null && !request.email().isBlank()
                && !request.email().trim().equalsIgnoreCase(customer.getEmail()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot subscribe another email");
        SavedSearch saved = new SavedSearch();
        saved.setTenantId(TenantContext.get());
        saved.setEmail(customer.getEmail());
        saved.setQueryText(request.query().trim());
        saved.setCategoryId(request.categoryId());
        saved.setLanguage(request.language());
        saved.setTag(request.tag());
        saved.setArticleType(request.articleType());
        saved.setDateFrom(request.dateFrom());
        saved.setDateTo(request.dateTo());
        saved.setLastCheckedAt(java.time.Instant.now());
        return repo.save(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@CookieValue(name = "rk_customer_session", required = false) String session,
                       @PathVariable UUID id) {
        var customer = requireCustomer(session);
        SavedSearch saved = repo.findById(id)
                .filter(s -> TenantContext.get().equals(s.getTenantId())
                        && customer.getEmail().equalsIgnoreCase(s.getEmail()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found"));
        repo.delete(saved);
    }
}
