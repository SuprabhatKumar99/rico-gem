package com.ricozknow.rag.evaluation;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.*;
@Entity @Table(name="rag_eval_cases",indexes={@Index(name="idx_rag_eval_cases_tenant",columnList="tenant_id")}) @Getter @Setter @NoArgsConstructor
public class RagEvalCase { @Id private UUID id; @Column(name="tenant_id",nullable=false) private UUID tenantId; @Column(nullable=false,columnDefinition="text") private String question; @Column(name="expected_article_ids",nullable=false,columnDefinition="text") private String expectedArticleIds; @Column(name="expected_answer",columnDefinition="text") private String expectedAnswer; @Column(nullable=false) private boolean enabled=true; @Column(name="created_at",nullable=false) private Instant createdAt; }
