package com.ricozknow.enterprise.webhook; 
import com.ricozknow.common.TenantOwnedEntity; 
import jakarta.persistence.*;
import lombok.*; 
import java.time.Instant; 
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes; 
@Entity 
@Table(name="webhook_dead_letters") 
@Getter 
@Setter 
@NoArgsConstructor 
public class WebhookDeadLetter extends TenantOwnedEntity {
    private UUID deliveryId; 
    private UUID subscriptionId; 
    private String eventType; 
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition="jsonb") 
    private String payload; 
    
    @Column(columnDefinition="text") 
    private String reason; 
    
    private int attemptCount; 
    
    private Instant replayedAt;
}
