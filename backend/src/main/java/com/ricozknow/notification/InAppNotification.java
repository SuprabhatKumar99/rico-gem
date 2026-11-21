package com.ricozknow.notification;
import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="in_app_notifications")
@Getter @Setter @NoArgsConstructor
public class InAppNotification extends TenantOwnedEntity {
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(nullable=false,length=120) private String type;
 @Column(nullable=false,length=300) private String title;
 @Column(nullable=false,columnDefinition="text") private String message;
 @Column(name="article_id") private UUID articleId;
 @Column(name="version_id") private UUID versionId;
 @Column(nullable=false) private boolean read=false;
 @Column(name="read_at") private Instant readAt;
}
