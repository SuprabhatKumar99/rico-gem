package com.ricozknow.ingestion;

public enum KnowledgeProcessingPhase {
    PENDING,
    VALIDATING,
    UPLOADING,
    EXTRACTING,
    INDEXING,
    READY,
    FAILED,
    DELETING
}
