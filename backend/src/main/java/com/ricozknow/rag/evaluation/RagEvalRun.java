package com.ricozknow.rag.evaluation;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.*;
@Entity @Table(name="rag_eval_runs",indexes={@Index(name="idx_rag_eval_runs_tenant_created",columnList="tenant_id,created_at")}) @Getter @Setter @NoArgsConstructor
public class RagEvalRun { @Id private UUID id; @Column(name="tenant_id",nullable=false) private UUID tenantId; @Column(name="started_at",nullable=false) private Instant startedAt; @Column(name="completed_at") private Instant completedAt; @Column(name="case_count",nullable=false) private int caseCount; @Column(name="hit_at_k",nullable=false) private double hitAtK; @Column(name="mrr",nullable=false) private double mrr; @Column(name="avg_latency_ms",nullable=false) private double avgLatencyMs; }
