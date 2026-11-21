package com.ricozknow.search;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, UUID> {
    List<SavedSearch> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<SavedSearch> findByTenantIdAndEmailIgnoreCaseOrderByCreatedAtDesc(UUID tenantId, String email);
    List<SavedSearch> findByActiveTrue();
}
