package com.ricozknow.notification;
import java.time.Instant; import java.util.UUID;
public record NotificationResponse(UUID id,String type,String title,String message,UUID articleId,UUID versionId,boolean read,Instant createdAt){}
