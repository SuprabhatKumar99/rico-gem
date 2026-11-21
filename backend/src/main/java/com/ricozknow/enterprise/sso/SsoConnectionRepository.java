package com.ricozknow.enterprise.sso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SsoConnectionRepository extends JpaRepository<SsoConnection, UUID> {
    Optional<SsoConnection> findByTenantIdAndProvider(UUID tenantId, SsoConnection.Provider provider);
    @Query("select s from SsoConnection s join com.ricozknow.tenant.Tenant t on t.id = s.tenantId where lower(t.slug) = lower(:slug) and s.provider = :provider")
    Optional<SsoConnection> findByTenantSlugAndProvider(String slug, SsoConnection.Provider provider);
    List<SsoConnection> findByTenantId(UUID tenantId);
}
