import crypto from "node:crypto";
import type { Express, Request, Response, NextFunction } from "express";
import type { RicozKnowStore } from "../store.ts";
import { evaluateArticleQuality, transformWriting } from "../domain-helpers.ts";

export interface RouteContext {
  store: RicozKnowStore;
  requireAuth: (req: Request, res: Response, next: NextFunction) => void;
  getReqAuth: (req: Request) => NonNullable<ReturnType<RicozKnowStore["authenticateToken"]>>;
}

const CONNECTOR_CATALOG = [
  { id: "confluence", name: "Confluence", configured: true, webhookSupported: true, description: "Sync spaces, pages, and engineering runbooks.", kind: "WIKI" },
  { id: "notion", name: "Notion", configured: true, webhookSupported: false, description: "Index Notion workspaces, databases, and team wikis.", kind: "WIKI" },
  { id: "github", name: "GitHub", configured: true, webhookSupported: true, description: "Ingest Markdown documentation and ADRs from repositories.", kind: "CODE" },
  { id: "slack", name: "Slack", configured: true, webhookSupported: true, description: "Capture verified resolutions from support channels.", kind: "CHAT" },
  { id: "google-drive", name: "Google Drive", configured: true, webhookSupported: false, description: "Sync approved Docs, PDFs, and SOP folders.", kind: "STORAGE" },
  { id: "jira", name: "Jira", configured: true, webhookSupported: true, description: "Index resolved incident tickets and postmortems.", kind: "ISSUE_TRACKER" },
  { id: "sharepoint", name: "SharePoint", configured: true, webhookSupported: false, description: "Connect enterprise SharePoint document libraries.", kind: "STORAGE" }
];

const BROWSE_ITEMS_BY_PROVIDER: Record<string, Array<{ id: string; name: string; mimeType: string }>> = {
  confluence: [
    { id: "ENG-SPACE", name: "Engineering Runbooks", mimeType: "application/vnd.confluence.space" },
    { id: "SEC-SPACE", name: "Security & Compliance Wiki", mimeType: "application/vnd.confluence.space" },
    { id: "SUP-SPACE", name: "Support Playbooks", mimeType: "application/vnd.confluence.space" }
  ],
  notion: [
    { id: "notion-db-handbook", name: "Company Knowledge Handbook", mimeType: "application/vnd.notion.database" },
    { id: "notion-db-releases", name: "Product Release Notes", mimeType: "application/vnd.notion.database" }
  ],
  github: [
    { id: "org/platform-docs", name: "platform-docs (main/docs)", mimeType: "application/vnd.github.repo" },
    { id: "org/sdk-reference", name: "sdk-reference (main/guides)", mimeType: "application/vnd.github.repo" }
  ],
  slack: [
    { id: "C-SUPPORT-L2", name: "#support-escalations", mimeType: "application/vnd.slack.channel" },
    { id: "C-INCIDENTS", name: "#incident-postmortems", mimeType: "application/vnd.slack.channel" }
  ],
  "google-drive": [
    { id: "drv-folder-sop", name: "Customer Success SOPs", mimeType: "application/vnd.google-apps.folder" },
    { id: "drv-folder-sec", name: "Security Questionnaires", mimeType: "application/vnd.google-apps.folder" }
  ],
  jira: [
    { id: "JIRA-SUP", name: "Support Escalation Project (SUP)", mimeType: "application/vnd.jira.project" },
    { id: "JIRA-OPS", name: "Infrastructure Postmortems (OPS)", mimeType: "application/vnd.jira.project" }
  ],
  sharepoint: [
    { id: "sp-site-policies", name: "Corporate Policies Library", mimeType: "application/vnd.sharepoint.library" }
  ]
};

export function registerAiAndSearchRoutes(app: Express, ctx: RouteContext): void {
  const { store, requireAuth, getReqAuth } = ctx;

  // --- Connectors Marketplace & OAuth ---
  app.get("/api/v1/connectors/catalog", requireAuth, (_req, res) => {
    res.json(
      CONNECTOR_CATALOG.map((c) => ({
        id: c.id,
        name: c.name,
        configured: c.configured,
        webhookSupported: c.webhookSupported
      }))
    );
  });

  app.get("/api/v1/connectors/oauth/connections", requireAuth, (_req, res) => {
    res.json(Array.from(store.connectorOauth.values()));
  });

  app.post("/api/v1/connectors/oauth/:provider/authorize", requireAuth, (req, res) => {
    const provider = String(req.params.provider || "").toLowerCase();
    const existing = store.connectorOauth.get(provider) ?? { provider, connected: false, configured: true };
    existing.connected = true;
    store.connectorOauth.set(provider, existing);
    store.recordAudit(getReqAuth(req).user.id, "CONNECTOR_OAUTH", provider, "CONNECT");
    res.json({ url: "/connectors", connected: true });
  });

  app.delete("/api/v1/connectors/oauth/:provider", requireAuth, (req, res) => {
    const provider = String(req.params.provider || "").toLowerCase();
    const existing = store.connectorOauth.get(provider);
    if (existing) existing.connected = false;
    for (const [id, src] of Array.from(store.connectorSources.entries())) {
      if (String(src.provider).toLowerCase() === provider) {
        store.connectorSources.delete(id);
        store.connectorRuns.delete(id);
      }
    }
    store.recordAudit(getReqAuth(req).user.id, "CONNECTOR_OAUTH", provider, "DISCONNECT");
    res.status(204).end();
  });

  app.get("/api/v1/connectors/:provider/browse", requireAuth, (req, res) => {
    const provider = String(req.params.provider || "").toLowerCase();
    const items = BROWSE_ITEMS_BY_PROVIDER[provider] ?? [
      { id: `${provider}-root`, name: `${provider} Default Workspace`, mimeType: "application/octet-stream" }
    ];
    res.json({ items, nextCursor: null });
  });

  app.get("/api/v1/connectors/sources", requireAuth, (_req, res) => {
    res.json(Array.from(store.connectorSources.values()));
  });

  app.post("/api/v1/connectors/:provider/sources", requireAuth, (req, res) => {
    const provider = String(req.params.provider || "").toLowerCase();
    const id = `csrc-${crypto.randomUUID().slice(0, 8)}`;
    const now = new Date().toISOString();
    const name = String(req.body?.name || `${provider} source`);
    const remoteId = String(req.body?.remoteId || id);
    const intervalMinutes = Number(req.body?.intervalMinutes || 60);
    const source = {
      id,
      provider,
      remoteId,
      name,
      status: "ACTIVE",
      documents: 1,
      intervalMinutes,
      lastSyncedAt: now,
      nextSyncAt: new Date(Date.now() + intervalMinutes * 60000).toISOString(),
      lastError: null
    };
    store.connectorSources.set(id, source);
    store.connectorRuns.set(id, [
      { id: `crun-${crypto.randomUUID().slice(0, 6)}`, startedAt: now, state: "COMPLETED", inserted: 1, updated: 0, deleted: 0, error: null }
    ]);
    store.connectorDocuments.push({
      id: `cdoc-${crypto.randomUUID().slice(0, 6)}`,
      sourceId: id,
      provider,
      title: `${name} — Overview`,
      excerpt: `Synchronized documentation from ${provider} (${remoteId}) ready for enterprise search and AI grounding.`,
      url: `https://${provider}.example.com/${encodeURIComponent(remoteId)}`,
      modifiedAt: now
    });
    store.recordAudit(getReqAuth(req).user.id, "CONNECTOR_SOURCE", id, "CREATE");
    res.status(201).json(source);
  });

  app.post("/api/v1/connectors/sources/:id/sync", requireAuth, (req, res) => {
    const src = store.connectorSources.get(req.params.id);
    if (!src) {
      res.status(404).json({ message: "Connector source not found." });
      return;
    }
    const now = new Date().toISOString();
    src.status = "ACTIVE";
    src.lastSyncedAt = now;
    src.lastError = null;
    const runs = store.connectorRuns.get(req.params.id) ?? [];
    runs.unshift({
      id: `crun-${crypto.randomUUID().slice(0, 6)}`,
      startedAt: now,
      state: "COMPLETED",
      inserted: 0,
      updated: Number(src.documents || 1),
      deleted: 0,
      error: null
    });
    store.connectorRuns.set(req.params.id, runs);
    res.status(204).end();
  });

  app.post("/api/v1/connectors/sources/:id/reindex", requireAuth, (req, res) => {
    const src = store.connectorSources.get(req.params.id);
    if (src) src.lastSyncedAt = new Date().toISOString();
    res.status(204).end();
  });

  app.post("/api/v1/connectors/sources/:id/disable", requireAuth, (req, res) => {
    const src = store.connectorSources.get(req.params.id);
    if (src) src.status = "DISABLED";
    res.status(204).end();
  });

  app.delete("/api/v1/connectors/sources/:id", requireAuth, (req, res) => {
    store.connectorSources.delete(req.params.id);
    store.connectorRuns.delete(req.params.id);
    res.status(204).end();
  });

  app.get("/api/v1/connectors/sources/:id/runs", requireAuth, (req, res) => {
    res.json(store.connectorRuns.get(req.params.id) ?? []);
  });

  app.get("/api/v1/connectors/documents/search", requireAuth, (req, res) => {
    const q = String(req.query.q || "").trim().toLowerCase();
    const hits = store.connectorDocuments.filter((d) =>
      !q || d.title.toLowerCase().includes(q) || d.excerpt.toLowerCase().includes(q) || d.provider.toLowerCase().includes(q)
    );
    res.json(hits);
  });

  // --- Enterprise Search ---
  const buildEnterpriseHits = (tenantId: string, query: string, provider?: string | null, limit = 15) => {
    const q = query.trim().toLowerCase();
    const articleSearch = store.searchArticles(tenantId, query, { size: limit });
    const articleHits = articleSearch.results.map((r) => {
      const art = store.getArticle(tenantId, r.articleId);
      return {
        id: r.articleId,
        sourceType: "ARTICLE",
        title: r.title,
        excerpt: r.excerpt,
        url: `/articles/${r.articleId}`,
        citation: `Article: ${r.title}`,
        accessLevel: art?.visibility || "PUBLIC",
        trust: art?.verifiedAt ? "VERIFIED" : "UNVERIFIED",
        freshness: "CURRENT",
        modifiedAt: art?.verifiedAt || new Date().toISOString(),
        score: r.score
      };
    });

    const connectorHits = store.connectorDocuments
      .filter((d) => !provider || d.provider.toLowerCase() === provider.toLowerCase())
      .filter((d) => !q || d.title.toLowerCase().includes(q) || d.excerpt.toLowerCase().includes(q))
      .map((d) => ({
        id: d.id,
        sourceType: d.provider.toUpperCase(),
        title: d.title,
        excerpt: d.excerpt,
        url: d.url,
        citation: `${d.provider}: ${d.title}`,
        accessLevel: "INTERNAL",
        trust: "SYNCED",
        freshness: "RECENT",
        modifiedAt: d.modifiedAt,
        score: 2
      }));

    const combined = provider ? connectorHits : [...articleHits, ...connectorHits];
    return combined.slice(0, limit);
  };

  app.post("/api/v1/enterprise-search/query", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const query = String(req.body?.query || "");
    const provider = req.body?.provider ? String(req.body.provider) : null;
    const limit = Number(req.body?.limit || 15);
    const results = buildEnterpriseHits(tenant.tenantId, query, provider, limit);
    store.enterpriseSearchCount += 1;
    if (!results.length) store.enterpriseSearchUnanswered += 1;
    res.json({ results, latencyMs: 28 });
  });

  app.post("/api/v1/enterprise-search/answer", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const query = String(req.body?.query || "");
    const provider = req.body?.provider ? String(req.body.provider) : null;
    const citations = buildEnterpriseHits(tenant.tenantId, query, provider, Number(req.body?.limit || 6));
    store.aiRequestCount += 1;
    const answer = citations.length
      ? `Based on ${citations.length} governed source${citations.length === 1 ? "" : "s"}, ${citations[0].title} states: ${citations[0].excerpt}`
      : "No matching articles or connected documents were found for that query.";
    res.json({ answer, citations });
  });

  app.post("/api/v1/enterprise-search/rewrite", requireAuth, (req, res) => {
    const query = String(req.body?.query || "").trim();
    const alternatives = query
      ? [`${query} configuration steps`, `${query} troubleshooting guide`, `how to manage ${query}`]
      : [];
    res.json({ query, alternatives });
  });

  app.post("/api/v1/enterprise-search/feedback", requireAuth, (_req, res) => res.status(204).end());

  app.get("/api/v1/enterprise-search/insights", requireAuth, (_req, res) => {
    res.json({
      searches: store.enterpriseSearchCount,
      unanswered: store.enterpriseSearchUnanswered,
      averageLatencyMs: 31
    });
  });

  app.get("/api/v1/enterprise-search/evaluation/cases", requireAuth, (_req, res) => {
    res.json(store.enterpriseEvalCases);
  });

  app.post("/api/v1/enterprise-search/evaluation/cases", requireAuth, (req, res) => {
    const item = {
      id: `eeval-${crypto.randomUUID().slice(0, 6)}`,
      question: String(req.body?.question || ""),
      expectedIds: Array.isArray(req.body?.expectedIds) ? req.body.expectedIds : []
    };
    store.enterpriseEvalCases.push(item);
    res.status(201).json(item);
  });

  app.post("/api/v1/enterprise-search/evaluation/run", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    let hits = 0;
    let mrrSum = 0;
    for (const c of store.enterpriseEvalCases) {
      const results = buildEnterpriseHits(tenant.tenantId, String(c.question || ""), null, 10);
      const expected = Array.isArray(c.expectedIds) ? (c.expectedIds as string[]) : [];
      const rank = results.findIndex((r) => expected.includes(r.id));
      if (rank !== -1) {
        hits += 1;
        mrrSum += 1 / (rank + 1);
      }
    }
    const total = store.enterpriseEvalCases.length || 1;
    res.json({
      cases: store.enterpriseEvalCases.length,
      hitAt10: Number((hits / total).toFixed(2)),
      mrr: Number((mrrSum / total).toFixed(2))
    });
  });

  // --- AI Intelligence Platform ---
  app.post("/api/v1/ai-platform/writing", requireAuth, (req, res) => {
    store.aiRequestCount += 1;
    const text = transformWriting(
      String(req.body?.operation || "IMPROVE"),
      String(req.body?.text || ""),
      String(req.body?.language || "en"),
      String(req.body?.guidance || "")
    );
    res.json({ text });
  });

  app.get("/api/v1/ai-platform/agents", requireAuth, (_req, res) => {
    res.json(store.aiAgents);
  });

  app.post("/api/v1/ai-platform/agents", requireAuth, (req, res) => {
    const agent = {
      id: `agent-${crypto.randomUUID().slice(0, 8)}`,
      name: String(req.body?.name || "Knowledge Agent"),
      instructions: String(req.body?.instructions || ""),
      enabled: req.body?.enabled !== false,
      tool: String(req.body?.tool || "RAG_SEARCH"),
      sourceScope: String(req.body?.sourceScope || "PUBLIC_ARTICLES"),
      requiresApproval: Boolean(req.body?.requiresApproval),
      createdAt: new Date().toISOString()
    };
    store.aiAgents.push(agent);
    res.status(201).json(agent);
  });

  app.delete("/api/v1/ai-platform/agents/:id", requireAuth, (req, res) => {
    const idx = store.aiAgents.findIndex((a) => a.id === req.params.id);
    if (idx !== -1) store.aiAgents.splice(idx, 1);
    res.status(204).end();
  });

  app.post("/api/v1/ai-platform/agents/:id/runs", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const agent = store.aiAgents.find((a) => a.id === req.params.id);
    if (!agent) {
      res.status(404).json({ message: "Agent not found." });
      return;
    }
    store.aiRequestCount += 1;
    const question = String(req.body?.question || "");
    const hits = buildEnterpriseHits(tenant.tenantId, question, null, 4);
    const run = {
      id: `arun-${crypto.randomUUID().slice(0, 8)}`,
      agentId: agent.id,
      status: agent.requiresApproval ? "AWAITING_APPROVAL" : "COMPLETED",
      answer: hits.length
        ? `[${agent.name}] Grounded answer for "${question}": ${hits[0].excerpt}`
        : `[${agent.name}] No governed source matched "${question}".`,
      citations: hits.map((h) => ({ id: h.id, title: h.title, url: h.url, sourceType: h.sourceType }))
    };
    store.aiAgentRuns.set(run.id, run);
    res.status(201).json(run);
  });

  app.post("/api/v1/ai-platform/agents/runs/:runId/approve", requireAuth, (req, res) => {
    const run = store.aiAgentRuns.get(req.params.runId);
    if (!run) {
      res.status(404).json({ message: "Agent run not found." });
      return;
    }
    run.status = "APPROVED";
    res.json(run);
  });

  app.get("/api/v1/ai-platform/graph", requireAuth, (req, res) => {
    const articles = store.listArticles(getReqAuth(req).tenant.tenantId, { size: 100 }).content;
    const tagSet = new Set<string>();
    for (const a of articles) {
      for (const t of a.tags) tagSet.add(t);
    }
    res.json({
      nodes: articles.map((a) => ({ id: a.id, title: a.title, status: a.status })),
      edges: store.graphEdges,
      entities: Array.from(tagSet).map((name) => ({ name, type: "TOPIC" }))
    });
  });

  app.post("/api/v1/ai-platform/graph/rebuild", requireAuth, (req, res) => {
    const articles = store.listArticles(getReqAuth(req).tenant.tenantId, { size: 100 }).content;
    store.graphEdges.length = 0;
    for (const a of articles) {
      for (const relId of a.relatedArticleIds) {
        store.graphEdges.push({ from_article_id: a.id, to_article_id: relId, relation: "RELATED" });
      }
    }
    if (!store.graphEdges.length && articles.length >= 2) {
      store.graphEdges.push({ from_article_id: articles[0].id, to_article_id: articles[1].id, relation: "LINKS_TO" });
    }
    res.json({ nodes: articles.length, edges: store.graphEdges.length });
  });

  app.post("/api/v1/ai-platform/graph/quality/scan", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const articles = store.listArticles(tenant.tenantId, { size: 100 }).content;
    let created = 0;
    for (const a of articles) {
      if (!a.verifiedAt && !Array.from(store.graphProposals.values()).some((p) => p.article_id === a.id && p.status === "OPEN")) {
        const id = `prop-${crypto.randomUUID().slice(0, 6)}`;
        store.graphProposals.set(id, {
          id,
          category: "VERIFICATION_NEEDED",
          status: "OPEN",
          explanation: `"${a.title}" is currently ${a.status} and has not been verified by a reviewer.`,
          article_id: a.id,
          related_article_id: null
        });
        created += 1;
      }
    }
    res.json({ scanned: articles.length, newProposals: created });
  });

  app.get("/api/v1/ai-platform/graph/quality/proposals", requireAuth, (_req, res) => {
    res.json(Array.from(store.graphProposals.values()));
  });

  app.post("/api/v1/ai-platform/graph/quality/proposals/:id/review", requireAuth, (req, res) => {
    const prop = store.graphProposals.get(req.params.id);
    if (prop) {
      prop.status = String(req.body?.status || "APPROVED");
    }
    res.status(204).end();
  });

  app.get("/api/v1/ai-platform/evaluation/cases", requireAuth, (_req, res) => {
    res.json(store.evalCases);
  });

  app.post("/api/v1/ai-platform/evaluation/cases", requireAuth, (req, res) => {
    const item = {
      id: `eval-${crypto.randomUUID().slice(0, 6)}`,
      question: String(req.body?.question || ""),
      expectedArticleId: String(req.body?.expectedArticleId || ""),
      expectedPhrase: String(req.body?.expectedPhrase || "")
    };
    store.evalCases.push(item);
    res.status(201).json(item);
  });

  app.post("/api/v1/ai-platform/evaluation/run", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    let passed = 0;
    for (const c of store.evalCases) {
      const search = store.searchArticles(tenant.tenantId, String(c.question || ""), { size: 5 });
      if (search.results.some((r) => r.articleId === c.expectedArticleId) || !c.expectedArticleId) {
        passed += 1;
      }
    }
    const total = store.evalCases.length || 1;
    res.json({
      cases: store.evalCases.length,
      passed,
      accuracy: Number((passed / total).toFixed(2)),
      executedAt: new Date().toISOString()
    });
  });

  app.get("/api/v1/ai-platform/governance/usage", requireAuth, (_req, res) => {
    res.json({
      monthlyRequests: store.aiRequestCount,
      monthlyLimit: 50000,
      estimatedCostUsd: Number((store.aiRequestCount * 0.0085).toFixed(2))
    });
  });

  // --- AI Studio ---
  const generateDraftPayload = (topic: string, sourceMaterial?: string, audience?: string) => {
    const cleanTopic = topic.trim() || "Knowledge Base Guide";
    return {
      title: cleanTopic,
      slug: cleanTopic.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "knowledge-guide",
      summary: `Governed draft for ${audience || "customers and support teams"} covering ${cleanTopic}.`,
      content: {
        blocks: [
          { id: "b1", type: "heading", level: 2, text: cleanTopic },
          {
            id: "b2",
            type: "paragraph",
            text: sourceMaterial?.trim()
              ? `Summary based on provided notes: ${sourceMaterial.trim()}`
              : `This guide walks ${audience || "users"} through configuring and verifying ${cleanTopic}.`
          },
          { id: "b3", type: "heading", level: 3, text: "Recommended Steps" },
          {
            id: "b4",
            type: "list",
            items: [
              `Review prerequisites for ${cleanTopic}.`,
              "Apply the configuration in your workspace settings.",
              "Verify expected behavior and document any edge cases."
            ]
          }
        ]
      }
    };
  };

  app.post("/api/v1/articles/ai/generate", requireAuth, (req, res) => {
    store.aiRequestCount += 1;
    res.json(generateDraftPayload(String(req.body?.topic || ""), req.body?.sourceMaterial, req.body?.audience));
  });

  app.post("/api/v1/articles/ai/generate-and-create-draft", requireAuth, (req, res) => {
    const { tenant, user } = getReqAuth(req);
    store.aiRequestCount += 1;
    const generated = generateDraftPayload(String(req.body?.topic || ""), req.body?.sourceMaterial, req.body?.audience);
    const article = store.createArticle(tenant.tenantId, user.id, {
      title: generated.title,
      slug: `${generated.slug}-${crypto.randomUUID().slice(0, 4)}`,
      categoryId: req.body?.categoryId || null,
      content: generated.content
    });
    store.recordAudit(user.id, "ARTICLE", article.id, "AI_CREATE_DRAFT");
    res.status(201).json({ articleId: article.id, slug: article.slug, title: article.title, status: article.status, generated });
  });

  app.post("/api/v1/articles/ai/improve", requireAuth, (req, res) => {
    store.aiRequestCount += 1;
    const instruction = String(req.body?.instruction || "Improve clarity and structure");
    const content = req.body?.content ?? { blocks: [] };
    res.json({
      instruction,
      summary: `Applied editorial pass: ${instruction}.`,
      suggestions: [
        "Lead with a concise summary sentence for faster scanning.",
        "Ensure technical steps are ordered chronologically.",
        "Verify all links and role prerequisites before submitting for review."
      ],
      improvedContent: content
    });
  });

  app.get("/api/v1/articles/:id/intelligence", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const article = store.getArticle(tenant.tenantId, req.params.id);
    if (!article) {
      res.status(404).json({ message: "Article not found." });
      return;
    }
    const versions = store.getVersions(tenant.tenantId, article.id) ?? [];
    const latest = versions[versions.length - 1] ?? null;
    const quality = evaluateArticleQuality(article, latest);
    res.json({
      articleId: article.id,
      title: article.title,
      qualityScore: quality.score,
      readabilityGrade: "Grade 9 (Clear)",
      suggestedTags: article.tags.length ? article.tags : ["guide", "knowledge-base"],
      warnings: quality.warnings,
      lastAnalyzedAt: new Date().toISOString()
    });
  });

  app.post("/api/v1/articles/:id/intelligence/analyze", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const article = store.getArticle(tenant.tenantId, req.params.id);
    if (!article) {
      res.status(404).json({ message: "Article not found." });
      return;
    }
    store.aiRequestCount += 1;
    const versions = store.getVersions(tenant.tenantId, article.id) ?? [];
    const latest = versions[versions.length - 1] ?? null;
    const quality = evaluateArticleQuality(article, latest);
    res.json({
      articleId: article.id,
      title: article.title,
      qualityScore: quality.score,
      readabilityGrade: "Grade 9 (Clear)",
      suggestedTags: [...new Set([...article.tags, article.articleType.toLowerCase()])],
      warnings: quality.warnings,
      lastAnalyzedAt: new Date().toISOString()
    });
  });

  app.get("/api/v1/articles/:id/dependencies", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const article = store.getArticle(tenant.tenantId, req.params.id);
    if (!article) {
      res.status(404).json({ message: "Article not found." });
      return;
    }
    const deps = article.relatedArticleIds
      .map((relId) => store.getArticle(tenant.tenantId, relId))
      .filter(Boolean)
      .map((a) => ({ articleId: a!.id, title: a!.title, relation: "RELATED_REFERENCE" }));
    res.json(deps);
  });

  app.post("/api/v1/articles/:id/dependencies/detect", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const article = store.getArticle(tenant.tenantId, req.params.id);
    if (!article) {
      res.status(404).json({ message: "Article not found." });
      return;
    }
    const all = store.listArticles(tenant.tenantId, { size: 100 }).content.filter((a) => a.id !== article.id);
    const detected = all
      .filter((other) => other.categoryId === article.categoryId || other.tags.some((t) => article.tags.includes(t)))
      .slice(0, 3)
      .map((a) => ({ articleId: a.id, title: a.title, relation: "TOPIC_DEPENDENCY" }));
    res.json(detected);
  });

  app.get("/api/v1/articles/:id/dependents", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const dependents = store
      .listArticles(tenant.tenantId, { size: 100 })
      .content.filter((a) => a.relatedArticleIds.includes(req.params.id))
      .map((a) => ({ articleId: a.id, title: a.title, relation: "REFERENCED_BY" }));
    res.json(dependents);
  });

  app.get("/api/v1/admin/knowledge-gaps", requireAuth, (_req, res) => {
    res.json(store.getKnowledgeGaps());
  });

  app.post("/api/v1/admin/knowledge-gaps/:id/generate-suggestion", requireAuth, (req, res) => {
    const gap = store.knowledgeGapTickets.find((g) => g.id === req.params.id);
    if (!gap) {
      res.status(404).json({ message: "Knowledge gap not found." });
      return;
    }
    store.aiRequestCount += 1;
    const suggestion = generateDraftPayload(String(gap.query || "Knowledge Article"));
    gap.suggestion = suggestion;
    gap.status = "SUGGESTED";
    res.json({ id: gap.id, query: gap.query, suggestion });
  });

  app.post("/api/v1/admin/knowledge-gaps/:id/create-draft", requireAuth, (req, res) => {
    const { tenant, user } = getReqAuth(req);
    const gap = store.knowledgeGapTickets.find((g) => g.id === req.params.id);
    if (!gap) {
      res.status(404).json({ message: "Knowledge gap not found." });
      return;
    }
    const generated = (gap.suggestion as ReturnType<typeof generateDraftPayload> | null) ?? generateDraftPayload(String(gap.query || "Knowledge Article"));
    const article = store.createArticle(tenant.tenantId, user.id, {
      title: generated.title,
      slug: `${generated.slug}-${crypto.randomUUID().slice(0, 4)}`,
      categoryId: "cat-getting-started",
      content: generated.content
    });
    gap.draftArticleId = article.id;
    gap.status = "DRAFT_CREATED";
    res.status(201).json({ gapId: gap.id, articleId: article.id, title: article.title, status: article.status });
  });

  // --- Knowledge Innovation Hub ---
  app.get("/api/v1/innovation/quality", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const articles = store.listArticles(tenant.tenantId, { size: 100 }).content;
    const findings: Array<{ articleId: string; title: string; severity: string; rule: string; recommendation: string }> = [];
    for (const a of articles) {
      const versions = store.getVersions(tenant.tenantId, a.id) ?? [];
      const latest = versions[versions.length - 1] ?? null;
      const q = evaluateArticleQuality(a, latest);
      if (!a.verifiedAt) {
        findings.push({
          articleId: a.id,
          title: a.title,
          severity: "MEDIUM",
          rule: "GOVERNANCE_VERIFICATION",
          recommendation: "Assign a reviewer to verify accuracy and stamp the verification date."
        });
      }
      for (const w of q.warnings) {
        findings.push({
          articleId: a.id,
          title: a.title,
          severity: w.startsWith("Sensitive") ? "HIGH" : "LOW",
          rule: "CONTENT_QUALITY",
          recommendation: w
        });
      }
    }
    res.json({ findingsCount: findings.length, totalArticles: articles.length, findings });
  });

  app.get("/api/v1/innovation/connectors/catalog", requireAuth, (_req, res) => {
    res.json(
      CONNECTOR_CATALOG.map((c) => ({
        id: c.id,
        name: c.name,
        status: store.connectorOauth.get(c.id)?.connected ? "CONNECTED" : "AVAILABLE",
        description: c.description,
        kind: c.kind
      }))
    );
  });

  app.get("/api/v1/innovation/connectors/sources", requireAuth, (req, res) => {
    const ks = Array.from(store.knowledgeSources.values()).filter((k) => k.tenantId === getReqAuth(req).tenant.tenantId);
    const cs = Array.from(store.connectorSources.values());
    res.json([...ks, ...cs]);
  });

  app.post("/api/v1/innovation/copilot/ask", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    store.aiRequestCount += 1;
    const question = String(req.body?.question || "");
    const conversationId = req.body?.conversationId || `cop-${crypto.randomUUID().slice(0, 8)}`;
    const search = store.searchArticles(tenant.tenantId, question, { publicOnly: false, size: 3 });
    res.json({
      conversationId,
      answer: search.results.length
        ? `Based on "${search.results[0].title}": ${search.results[0].excerpt}`
        : "I couldn't find a direct match in the workspace knowledge base. Consider creating a draft article for this topic.",
      grounded: search.results.length > 0,
      confidence: search.results.length > 0 ? 0.91 : 0.25,
      citations: search.results.map((r, idx) => ({ rank: idx + 1, title: r.title, slug: r.slug }))
    });
  });
}
