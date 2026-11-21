package com.ricozknow.notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InAppNotificationRepository extends JpaRepository<InAppNotification,UUID>{
 List<InAppNotification> findTop50ByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId,UUID userId);
 long countByTenantIdAndUserIdAndReadFalse(UUID tenantId,UUID userId);
 Optional<InAppNotification> findByTenantIdAndId(UUID tenantId,UUID id);
}
