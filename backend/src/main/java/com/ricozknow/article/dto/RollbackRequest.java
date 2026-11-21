package com.ricozknow.article.dto;
import java.util.UUID;
public record RollbackRequest(UUID versionId, String changeSummary) {}
