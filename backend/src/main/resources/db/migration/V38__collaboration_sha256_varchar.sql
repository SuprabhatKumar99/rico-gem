-- Fix Hibernate schema validation after the Phase 4 collaboration migration.
-- V33 created CHAR(64); ArticleCollaborationState maps mirrored_sha256 as VARCHAR(64).
-- Preserve existing Yjs states and hash values; drop only trailing CHAR padding.
ALTER TABLE public.article_collaboration_state
    ALTER COLUMN mirrored_sha256 TYPE varchar(64)
    USING rtrim(mirrored_sha256::text);
