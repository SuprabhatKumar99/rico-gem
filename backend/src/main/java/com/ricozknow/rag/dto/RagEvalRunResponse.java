package com.ricozknow.rag.dto; import java.util.UUID; public record RagEvalRunResponse(UUID id,int caseCount,double hitAtK,double mrr,double avgLatencyMs){}
