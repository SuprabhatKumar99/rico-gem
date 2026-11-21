package com.ricozknow.article.dto;
import java.util.List;
import java.util.UUID;
public record VersionDiffResponse(UUID fromVersionId, UUID toVersionId, List<BlockDiff> blocks) {
    public record BlockDiff(String blockId, String change, Object before, Object after) {}
}
