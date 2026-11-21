package com.ricozknow.analytics;

import org.springframework.data.jpa.repository.JpaRepository; 
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.*;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent,UUID>{
 
    @Query(value="select coalesce(metadata->>'query','') as query, count(*) as volume from analytics_events where tenant_id=:tenant and event_type='SEARCH_PERFORMED' and occurred_at >= now() - interval '90 days' and coalesce(metadata->>'query','') <> '' group by metadata->>'query' order by count(*) desc limit 8",nativeQuery=true)
    List<Object[]> popularQueries(UUID tenant);

    @Query(value="select distinct article_id from analytics_events where tenant_id=:tenant and event_type='SEARCH_RESULT_CLICKED' and anonymous_session_id=:session and article_id is not null and occurred_at >= now() - interval '90 days' limit 20", nativeQuery=true)
    List<UUID> clickedArticles(UUID tenant, String session);

    List<AnalyticsEvent> findByTenantIdAndEventTypeAndOccurredAtBetween(
            UUID tenantId,
            AnalyticsEventType eventType,
            Instant from,
            Instant to
    );
}