import crypto from "node:crypto";
import type { Express, Request, Response, NextFunction } from "express";
import type { RicozKnowStore } from "../store.ts";
import { paginate } from "../store.ts";
import {
  buildTranslationDraft,
  parseBrowserInfo,
  rankKnowledgeRequests,
  scanSensitiveContent,
  slugifyTitle,
  validateProductFeedbackInput
} from "../domain-helpers.ts";
import { readMultipartRequest } from "../multipart.ts";

export interface OpsRouteContext {
  store: RicozKnowStore;
  requireAuth: (req: Request, res: Response, next: NextFunction) => void;
  getReqAuth: (req: Request) => NonNullable<ReturnType<RicozKnowStore["authenticateToken"]>>;
  getAuth: (req: Request) => ReturnType<RicozKnowStore["authenticateToken"]>;
  resolveRequestTenant: (req: Request) => ReturnType<RicozKnowStore["resolveTenantBySlugOrSubdomain"]>;
}

export function registerOperationsAndEnterpriseRoutes(app: Express, ctx: OpsRouteContext): void {
  const { store, requireAuth, getReqAuth, getAuth, resolveRequestTenant } = ctx;

  // --- Executive & Standard Analytics ---
  app.get("/api/v1/analytics/articles/performance", requireAuth, (req, res) => {
    res.json(store.getArticlePerformance(getReqAuth(req).tenant.tenantId));
  });
  app.get("/api/v1/analytics/knowledge-gaps", requireAuth, (_req, res) => res.json(store.getKnowledgeGaps()));
  app.get("/api/v1/analytics/deflection", requireAuth, (_req, res) => res.json(store.getDeflectionSummary()));
  app.get("/api/v1/analytics/stale-content", requireAuth, (req, res) => {
    res.json(store.getStaleContent(getReqAuth(req).tenant.tenantId));
  });
  app.post("/api/v1/analytics/aggregate", requireAuth, (req, res) => {
    res.json({ status: "OK", date: String(req.query.date || new Date().toISOString().slice(0, 10)) });
  });
  app.get("/api/v1/analytics/executive", requireAuth, (req, res) => {
    res.json(store.getExecutiveAnalytics(getReqAuth(req).tenant.tenantId));
  });
  app.get("/api/v1/analytics/executive/daily", requireAuth, (req, res) => {
    const days = Math.max(1, Math.min(90, Number(req.query.days || 14)));
    const count = Math.min(days, 14);
    const today = Date.now();
    const rows = Array.from({ length: count }, (_, i) => ({
      day: new Date(today - (count - 1 - i) * 86400000).toISOString().slice(0, 10),
      article_views: 95 + ((i * 23) % 75) + i * 6
    }));
    res.json(rows);
  });

  // --- Billing & Platform Admin ---
  app.get("/api/v1/billing/overview", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const sub = store.billingSubscriptions.get(tenant.tenantId) ?? {
      plan: "GROWTH",
      status: "ACTIVE",
      periodEnd: new Date(Date.now() + 24 * 86400000).toISOString(),
      trialEnd: null,
      cancelAtPeriodEnd: false
    };
    const planObj = store.billingPlans.find((p) => p.code === sub.plan) ?? store.billingPlans[1];
    const seats = Array.from(store.users.values()).filter((u) => u.tenantId === tenant.tenantId).length;
    const articles = Array.from(store.articles.values()).filter((a) => a.tenantId === tenant.tenantId).length;
    res.json({
      subscription: sub,
      effectivePlan: sub.plan,
      limits: {
        max_seats: planObj?.maxSeats ?? 50,
        max_articles: planObj?.maxArticles ?? 2500,
        max_connectors: planObj?.maxConnectors ?? 8,
        max_ai_requests: planObj?.maxAiRequests ?? 50000
      },
      usage: {
        seats,
        articles,
        connectors: store.connectorSources.size,
        aiRequestsThisMonth: store.aiRequestCount
      }
    });
  });

  app.get("/api/v1/billing/plans", requireAuth, (_req, res) => {
    res.json(store.billingPlans);
  });

  app.get("/api/v1/billing/invoices", requireAuth, (_req, res) => {
    res.json(store.billingInvoices);
  });

  app.post("/api/v1/billing/cancel", requireAuth, (req, res) => {
    const { tenant, user } = getReqAuth(req);
    const sub = store.billingSubscriptions.get(tenant.tenantId);
    if (sub) {
      sub.cancelAtPeriodEnd = req.body?.atPeriodEnd !== false;
    }
    store.recordAudit(user.id, "BILLING", tenant.tenantId, "SCHEDULE_CANCEL");
    res.status(204).end();
  });

  app.post("/api/v1/billing/checkout", requireAuth, (req, res) => {
    const { tenant, user } = getReqAuth(req);
    const planCode = String(req.body?.planCode || "GROWTH").toUpperCase();
    const plan = store.billingPlans.find((p) => p.code === planCode);
    if (!plan) {
      res.status(404).json({ message: "Plan not found." });
      return;
    }
    const sub = store.billingSubscriptions.get(tenant.tenantId);
    if (sub) {
      sub.plan = planCode;
      sub.cancelAtPeriodEnd = false;
    }
    store.recordAudit(user.id, "BILLING", tenant.tenantId, "CHANGE_PLAN", null, planCode);
    res.status(400).json({ message: `Plan updated to ${plan.name} locally. Configure a live Stripe API key to launch Stripe Checkout.` });
  });

  app.post("/api/v1/billing/portal", requireAuth, (_req, res) => {
    res.status(400).json({ message: "Configure a live Stripe customer portal key to redirect to Stripe Billing." });
  });

  app.get("/api/v1/platform/overview", requireAuth, (_req, res) => {
    const tenants = Array.from(store.tenants.values()).map((t) => {
      const sub = store.billingSubscriptions.get(t.tenantId) ?? { plan: "GROWTH", status: "ACTIVE" };
      const seats = Array.from(store.users.values()).filter((u) => u.tenantId === t.tenantId).length;
      return {
        name: t.name,
        slug: t.slug,
        plan_code: sub.plan,
        billing_status: sub.status,
        seats
      };
    });
    const receivedMinor = store.billingInvoices.reduce((acc, inv) => acc + Number(inv.paid || 0), 0);
    res.json({
      tenants,
      plans: store.billingPlans.map((p) => ({ code: p.code, name: p.name, stripe_configured: Boolean(p.stripe_configured) })),
      paidInvoiceTotals: [{ currency: "USD", received_minor: receivedMinor, paid_invoices: store.billingInvoices.length }]
    });
  });

  app.put("/api/v1/platform/plans/:code/stripe-price", requireAuth, (req, res) => {
    const code = String(req.params.code || "").toUpperCase();
    const plan = store.billingPlans.find((p) => String(p.code).toUpperCase() === code);
    if (!plan) {
      res.status(404).json({ message: "Plan not found." });
      return;
    }
    plan.stripePriceId = String(req.body?.stripePriceId || "");
    plan.stripe_configured = Boolean(plan.stripePriceId);
    res.json(plan);
  });

  // --- Automation Rules & Sensitive Content Scan ---
  app.post("/api/v1/knowledge-security/scan", requireAuth, (req, res) => {
    res.json(scanSensitiveContent(String(req.body?.content || "")));
  });

  app.get("/api/v1/automation/rules", requireAuth, (_req, res) => {
    res.json(Array.from(store.automationRules.values()));
  });

  app.post("/api/v1/automation/rules", requireAuth, (req, res) => {
    const id = `rule-${crypto.randomUUID().slice(0, 8)}`;
    const rule = {
      id,
      name: String(req.body?.name || "Automation Rule").trim(),
      triggerType: String(req.body?.triggerType || "ARTICLE_UPDATED"),
      actionType: String(req.body?.actionType || "FLAG_FOR_REVIEW"),
      enabled: Boolean(req.body?.enabled),
      articleIdFilter: req.body?.articleIdFilter || null,
      cooldownMinutes: Number(req.body?.cooldownMinutes || 0),
      maxAttempts: Number(req.body?.maxAttempts || 5)
    };
    store.automationRules.set(id, rule);
    store.automationRuns.set(id, []);
    store.recordAudit(getReqAuth(req).user.id, "AUTOMATION_RULE", id, "CREATE");
    res.status(201).json(rule);
  });

  app.put("/api/v1/automation/rules/:id", requireAuth, (req, res) => {
    const existing = store.automationRules.get(req.params.id);
    if (!existing) {
      res.status(404).json({ message: "Automation rule not found." });
      return;
    }
    const updated = {
      ...existing,
      name: req.body?.name !== undefined ? String(req.body.name) : existing.name,
      triggerType: req.body?.triggerType !== undefined ? String(req.body.triggerType) : existing.triggerType,
      actionType: req.body?.actionType !== undefined ? String(req.body.actionType) : existing.actionType,
      enabled: req.body?.enabled !== undefined ? Boolean(req.body.enabled) : existing.enabled,
      articleIdFilter: req.body?.articleIdFilter !== undefined ? req.body.articleIdFilter : existing.articleIdFilter,
      cooldownMinutes: req.body?.cooldownMinutes !== undefined ? Number(req.body.cooldownMinutes) : existing.cooldownMinutes,
      maxAttempts: req.body?.maxAttempts !== undefined ? Number(req.body.maxAttempts) : existing.maxAttempts
    };
    store.automationRules.set(req.params.id, updated);
    res.json(updated);
  });

  app.delete("/api/v1/automation/rules/:id", requireAuth, (req, res) => {
    store.automationRules.delete(req.params.id);
    store.automationRuns.delete(req.params.id);
    res.status(204).end();
  });

  app.post("/api/v1/automation/rules/:id/preview", requireAuth, (req, res) => {
    const rule = store.automationRules.get(req.params.id);
    if (!rule) {
      res.status(404).json({ message: "Automation rule not found." });
      return;
    }
    const scope = rule.articleIdFilter ? `article ${rule.articleIdFilter}` : "all tenant articles";
    res.json({
      wouldExecute: Boolean(rule.enabled),
      explanation: `Dry run for "${rule.name}": When ${rule.triggerType} fires on ${scope}, action ${rule.actionType} will ${rule.enabled ? "execute" : "stay idle (rule is disabled)"} with ${rule.cooldownMinutes}m cooldown.`
    });
  });

  app.get("/api/v1/automation/rules/:id/executions", requireAuth, (req, res) => {
    res.json(store.automationRuns.get(req.params.id) ?? []);
  });

  app.post("/api/v1/automation/rules/:id/executions/:runId/retry", requireAuth, (req, res) => {
    const runs = store.automationRuns.get(req.params.id) ?? [];
    const run = runs.find((r) => r.id === req.params.runId);
    if (!run) {
      res.status(404).json({ message: "Execution not found." });
      return;
    }
    run.state = "COMPLETED";
    run.attempts = Number(run.attempts || 0) + 1;
    run.last_error = null;
    res.json(run);
  });

  // --- Support Operations ---
  app.get("/api/v1/admin/support/tickets", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const items = store.supportTickets.filter((t) => !t.tenantId || t.tenantId === tenant.tenantId);
    res.json(items);
  });

  app.patch("/api/v1/admin/support/tickets/:id", requireAuth, (req, res) => {
    const ticket = store.supportTickets.find((t) => t.id === req.params.id);
    if (!ticket) {
      res.status(404).json({ message: "Ticket not found." });
      return;
    }
    if (req.body?.status) {
      ticket.status = String(req.body.status);
    }
    store.recordAudit(getReqAuth(req).user.id, "SUPPORT_TICKET", req.params.id, "UPDATE_STATUS", null, String(ticket.status));
    res.json(ticket);
  });

  app.get("/api/v1/admin/support/insights", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const tickets = store.supportTickets.filter((t) => !t.tenantId || t.tenantId === tenant.tenantId);
    const resolvedTickets = tickets.filter((t) => t.status === "RESOLVED" || t.status === "CLOSED").length;
    const conversations = Array.from(store.portalConversations.values()).filter((c) => c.tenantId === tenant.tenantId).length;
    const totalInteractions = Math.max(1, conversations + tickets.length);
    const estimatedDeflectionRate = Number(Math.min(0.95, Math.max(0.25, (conversations + resolvedTickets) / (totalInteractions + 1))).toFixed(3));
    res.json({
      tickets: tickets.length,
      resolvedTickets,
      conversations,
      estimatedDeflectionRate
    });
  });

  // --- Translation Proposals ---
  app.get("/api/v1/translation-proposals", requireAuth, (_req, res) => {
    res.json(Array.from(store.translationProposals.values()));
  });

  app.post("/api/v1/translation-proposals", requireAuth, (req, res) => {
    const { tenant, user } = getReqAuth(req);
    const articleId = String(req.body?.articleId || "").trim();
    const language = String(req.body?.language || "fr").trim().toLowerCase();
    const article = store.getArticle(tenant.tenantId, articleId);
    if (!article) {
      res.status(404).json({ message: "Source article not found." });
      return;
    }
    const versions = store.getVersions(tenant.tenantId, article.id) ?? [];
    const latest = versions[versions.length - 1];
    const draft = buildTranslationDraft(article, latest?.content?.blocks ?? [], language);
    const id = `tr-${crypto.randomUUID().slice(0, 8)}`;
    const proposal = {
      id,
      source_article_id: article.id,
      target_language: language,
      translated_title: draft.translatedTitle,
      translated_content: draft.translatedContent,
      state: "PENDING",
      created_by: user.id,
      created_at: new Date().toISOString()
    };
    store.translationProposals.set(id, proposal);
    res.status(201).json(proposal);
  });

  app.post("/api/v1/translation-proposals/:id/review", requireAuth, (req, res) => {
    const { tenant, user } = getReqAuth(req);
    const proposal = store.translationProposals.get(req.params.id);
    if (!proposal) {
      res.status(404).json({ message: "Proposal not found." });
      return;
    }
    const decision = String(req.body?.decision || "REJECTED").toUpperCase();
    proposal.state = decision === "APPROVED" ? "APPROVED" : "REJECTED";
    let draftArticleId: string | null = null;
    if (proposal.state === "APPROVED") {
      const source = store.getArticle(tenant.tenantId, String(proposal.source_article_id));
      const lang = String(proposal.target_language || "fr");
      const created = store.createArticle(tenant.tenantId, user.id, {
        title: String(proposal.translated_title),
        slug: `${source?.slug || "translated"}-${lang}-${crypto.randomUUID().slice(0, 4)}`,
        categoryId: source?.categoryId ?? null,
        content: {
          blocks: [
            { id: "b1", type: "heading", level: 2, text: String(proposal.translated_title) },
            { id: "b2", type: "paragraph", text: String(proposal.translated_content) }
          ]
        }
      });
      created.language = lang;
      created.visibility = "PRIVATE";
      draftArticleId = created.id;
    }
    res.json({ draftArticleId });
  });

  // --- Developer Portal & RAG Extended ---
  app.get("/api/v1/admin/developer/usage", requireAuth, (_req, res) => {
    res.json(store.developerUsage);
  });

  app.get("/api/v1/admin/developer/review-requests", requireAuth, (_req, res) => {
    res.json(Array.from(store.developerReviewRequests.values()));
  });

  app.post("/api/v1/admin/developer/review-requests/:id/resolve", requireAuth, (req, res) => {
    const item = store.developerReviewRequests.get(req.params.id);
    if (item) item.status = "RESOLVED";
    res.status(204).end();
  });

  app.post("/api/v1/admin/rag-api-keys/:id/rotate", requireAuth, (req, res) => {
    const rec = store.ragKeys.get(req.params.id);
    if (!rec) {
      res.status(404).json({ message: "API key not found." });
      return;
    }
    const newKey = `rk_live_${crypto.randomUUID().replace(/-/g, "").slice(0, 24)}`;
    rec.key = newKey;
    rec.prefix = newKey.slice(0, 12);
    rec.status = "ACTIVE";
    res.json({ id: rec.id, name: rec.name, key: rec.key, prefix: rec.prefix, expiresAt: rec.expiresAt });
  });

  app.post("/api/v1/admin/rag-api-keys/:id/ip-allowlist", requireAuth, (req, res) => {
    const rec = store.ragKeys.get(req.params.id);
    if (rec) rec.ipAllowlist = String(req.body?.allowlist || "") || null;
    res.status(204).end();
  });

  app.get("/api/v1/admin/api-keys/:id/scopes", requireAuth, (req, res) => {
    const rec = store.ragKeys.get(req.params.id);
    res.json(rec?.scopes ?? ["rag:search", "rag:answer"]);
  });

  app.put("/api/v1/admin/api-keys/:id/scopes", requireAuth, (req, res) => {
    const rec = store.ragKeys.get(req.params.id);
    if (!rec) {
      res.status(404).json({ message: "API key not found." });
      return;
    }
    rec.scopes = Array.isArray(req.body) ? req.body.map(String) : [];
    res.json(rec.scopes);
  });

  app.get("/api/v1/rag/conversations", (_req, res) => {
    res.json(Array.from(store.ragConversations.values()));
  });

  app.post("/api/v1/rag/conversations", (_req, res) => {
    const id = `rconv-${crypto.randomUUID().slice(0, 8)}`;
    const conv = { id, name: "New RAG Conversation", createdAt: new Date().toISOString(), turns: [] as Array<Record<string, unknown>> };
    store.ragConversations.set(id, conv);
    res.status(201).json(conv);
  });

  app.get("/api/v1/rag/conversations/:id", (req, res) => {
    const conv = store.ragConversations.get(req.params.id);
    if (!conv) {
      res.status(404).json({ message: "Conversation not found." });
      return;
    }
    res.json(conv);
  });

  app.patch("/api/v1/rag/conversations/:id", (req, res) => {
    const conv = store.ragConversations.get(req.params.id);
    if (!conv) {
      res.status(404).json({ message: "Conversation not found." });
      return;
    }
    if (typeof req.body?.name === "string" && req.body.name.trim()) {
      conv.name = req.body.name.trim();
    }
    res.json(conv);
  });

  app.delete("/api/v1/rag/conversations/:id", (req, res) => {
    store.ragConversations.delete(req.params.id);
    res.status(204).end();
  });

  app.post("/api/v1/rag/feedback", (req, res) => {
    store.ragFeedbacks.push({ ...req.body, createdAt: new Date().toISOString() });
    res.status(204).end();
  });

  app.post("/api/v1/admin/rag/evaluation/cases", requireAuth, (req, res) => {
    const item = {
      id: `reval-${crypto.randomUUID().slice(0, 6)}`,
      question: String(req.body?.question || ""),
      expectedArticleIds: Array.isArray(req.body?.expectedArticleIds) ? req.body.expectedArticleIds : [],
      expectedAnswer: req.body?.expectedAnswer || null,
      enabled: req.body?.enabled !== false
    };
    store.ragEvalCases.push(item);
    res.status(201).json(item);
  });

  app.get("/api/v1/admin/rag/evaluation/runs", requireAuth, (_req, res) => {
    res.json(store.ragEvalRuns);
  });

  app.post("/api/v1/admin/rag/evaluation/runs", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    let passed = 0;
    for (const c of store.ragEvalCases) {
      const search = store.searchArticles(tenant.tenantId, String(c.question || ""), { publicOnly: true, size: 5 });
      const expected = Array.isArray(c.expectedArticleIds) ? (c.expectedArticleIds as string[]) : [];
      if (!expected.length || search.results.some((r) => expected.includes(r.articleId))) {
        passed += 1;
      }
    }
    const total = store.ragEvalCases.length || 1;
    const run = {
      id: `rrun-${crypto.randomUUID().slice(0, 6)}`,
      status: "COMPLETED",
      totalCases: store.ragEvalCases.length,
      passedCases: passed,
      hitRate: Number((passed / total).toFixed(2)),
      averageLatencyMs: 36,
      createdAt: new Date().toISOString()
    };
    store.ragEvalRuns.unshift(run);
    res.status(201).json(run);
  });

  // --- Enterprise Admin & Operations Mutations ---
  app.put("/api/v1/admin/sso", requireAuth, (req, res) => {
    const payload = { id: "sso-1", ...(req.body ?? {}), updatedAt: new Date().toISOString() };
    store.ssoConnections.length = 0;
    store.ssoConnections.push(payload);
    store.recordAudit(getReqAuth(req).user.id, "SSO", "sso-1", "UPDATE");
    res.json(payload);
  });

  app.post("/api/v1/admin/groups", requireAuth, (req, res) => {
    const group = {
      id: `grp-${crypto.randomUUID().slice(0, 6)}`,
      name: String(req.body?.name || "New Group"),
      externalId: req.body?.externalId || null,
      members: [] as Array<Record<string, unknown>>
    };
    store.groups.push(group);
    res.status(201).json(group);
  });

  app.post("/api/v1/admin/groups/:groupId/members", requireAuth, (req, res) => {
    const group = store.groups.find((g) => g.id === req.params.groupId);
    const user = store.users.get(String(req.body?.userId || ""));
    if (!group || !user) {
      res.status(404).json({ message: "Group or user not found." });
      return;
    }
    const members = (group.members as Array<Record<string, unknown>>) || [];
    if (!members.some((m) => m.id === user.id)) {
      const { password: _p, ...safe } = user;
      members.push(safe);
    }
    group.members = members;
    res.json(group);
  });

  app.delete("/api/v1/admin/groups/:groupId/members/:userId", requireAuth, (req, res) => {
    const group = store.groups.find((g) => g.id === req.params.groupId);
    if (group && Array.isArray(group.members)) {
      group.members = (group.members as Array<Record<string, unknown>>).filter((m) => m.id !== req.params.userId);
    }
    res.status(204).end();
  });

  app.post("/api/v1/admin/roles", requireAuth, (req, res) => {
    const perms = Array.isArray(req.body?.permissions) ? req.body.permissions.join(",") : String(req.body?.permissions || "");
    const role = {
      id: `role-${crypto.randomUUID().slice(0, 6)}`,
      name: String(req.body?.name || "CUSTOM_ROLE"),
      custom: true,
      permissions: perms
    };
    store.customRoles.push(role);
    res.status(201).json(role);
  });

  app.post("/api/v1/admin/experiments", requireAuth, (req, res) => {
    const exp = {
      id: `exp-${crypto.randomUUID().slice(0, 6)}`,
      key: String(req.body?.key || "experiment"),
      name: String(req.body?.name || "Experiment"),
      target: String(req.body?.target || "ARTICLE_TITLE"),
      enabled: Boolean(req.body?.enabled),
      trafficPercent: Number(req.body?.trafficPercent || 100),
      variantsJson: String(req.body?.variantsJson || '[{"key":"control"}]')
    };
    const idx = store.experiments.findIndex((e) => e.key === exp.key);
    if (idx !== -1) store.experiments[idx] = exp;
    else store.experiments.push(exp);
    res.status(201).json(exp);
  });

  app.post("/api/v1/admin/scim/tokens", requireAuth, (req, res) => {
    const raw = `scim_live_${crypto.randomUUID().replace(/-/g, "").slice(0, 24)}`;
    const tokenObj = {
      id: `scim-${crypto.randomUUID().slice(0, 6)}`,
      name: String(req.body?.name || "SCIM Connector"),
      tokenPrefix: raw.slice(0, 12),
      createdAt: new Date().toISOString()
    };
    store.scimTokens.push(tokenObj);
    res.status(201).json({ ...tokenObj, token: raw });
  });

  app.post("/api/v1/admin/scim/tokens/:id/revoke", requireAuth, (req, res) => {
    const idx = store.scimTokens.findIndex((t) => t.id === req.params.id);
    if (idx !== -1) store.scimTokens.splice(idx, 1);
    res.status(204).end();
  });

  app.get("/api/v1/admin/mfa-recovery", requireAuth, (_req, res) => {
    res.json(paginate(store.mfaRecoveryRequests, 0, 50));
  });

  app.post("/api/v1/admin/mfa-recovery", requireAuth, (req, res) => {
    const item = {
      id: `mfar-${crypto.randomUUID().slice(0, 6)}`,
      targetUserId: String(req.body?.targetUserId || ""),
      externalTicket: String(req.body?.externalTicket || ""),
      reason: String(req.body?.reason || ""),
      status: "PENDING",
      expiresAt: new Date(Date.now() + 30 * 60000).toISOString()
    };
    store.mfaRecoveryRequests.unshift(item);
    res.status(201).json(item);
  });

  app.post("/api/v1/admin/mfa-recovery/:id/approve", requireAuth, (req, res) => {
    const item = store.mfaRecoveryRequests.find((r) => r.id === req.params.id);
    if (!item) {
      res.status(404).json({ message: "Recovery request not found." });
      return;
    }
    item.status = "APPROVED";
    const target = store.users.get(String(item.targetUserId));
    if (target) target.mfaEnabled = false;
    res.json(item);
  });

  app.get("/api/v1/admin/privacy/users/:id/export", requireAuth, (req, res) => {
    const u = store.users.get(req.params.id);
    if (!u) {
      res.status(404).json({ message: "User not found." });
      return;
    }
    const { password: _p, ...safe } = u;
    res.json({
      exportedAt: new Date().toISOString(),
      user: safe,
      auditEvents: store.auditLogs.filter((a) => a.actorUserId === u.id)
    });
  });

  app.delete("/api/v1/admin/privacy/users/:id", requireAuth, (req, res) => {
    const u = store.users.get(req.params.id);
    if (u) {
      u.name = "Anonymized User";
      u.email = `anonymized-${u.id}@deleted.local`;
      u.status = "DISABLED";
    }
    res.status(204).end();
  });

  app.get("/api/v1/admin/audit-logs/export", requireAuth, (_req, res) => {
    const header = "id,createdAt,actorUserId,action,entityType,entityId,source";
    const rows = store.auditLogs.map(
      (a) => `"${a.id}","${a.createdAt}","${a.actorUserId}","${a.action}","${a.entityType}","${a.entityId}","${a.source}"`
    );
    res.setHeader("Content-Type", "text/csv; charset=utf-8");
    res.send([header, ...rows].join("\n"));
  });

  app.get("/api/v1/admin/usage/ai-costs", requireAuth, (_req, res) => {
    res.json(store.aiCostEvents);
  });

  app.get("/api/v1/admin/webhooks/:id/deliveries", requireAuth, (req, res) => {
    res.json(store.webhookDeliveries.get(req.params.id) ?? []);
  });

  app.get("/api/v1/admin/webhooks/dlq", requireAuth, (_req, res) => {
    res.json(store.webhookDlq);
  });

  app.post("/api/v1/admin/webhooks/dlq/:id/replay", requireAuth, (req, res) => {
    const idx = store.webhookDlq.findIndex((d) => d.id === req.params.id);
    if (idx === -1) {
      res.status(404).json({ message: "Dead letter not found." });
      return;
    }
    const [replayed] = store.webhookDlq.splice(idx, 1);
    replayed.replayedAt = new Date().toISOString();
    res.json(replayed);
  });

  // --- Custom Domain Verification ---
  app.post("/api/v1/admin/custom-domain/challenge", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const domain = tenant.customDomain || "help.example.com";
    const token = crypto.randomBytes(12).toString("hex");
    const challenge = {
      domain,
      recordName: `_ricozknow-challenge.${domain}`,
      recordValue: `ricozknow-verification=${token}`,
      token
    };
    store.customDomainChallenges.set(tenant.tenantId, challenge);
    res.json({ domain: challenge.domain, recordName: challenge.recordName, recordValue: challenge.recordValue });
  });

  app.post("/api/v1/admin/custom-domain/verify", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const challenge = store.customDomainChallenges.get(tenant.tenantId);
    const supplied = String(req.body?.token || "").trim();
    if (!supplied || (challenge && supplied !== challenge.token && supplied !== challenge.recordValue)) {
      res.status(400).json({ message: "Verification token did not match the generated TXT challenge." });
      return;
    }
    tenant.customDomainVerified = true;
    tenant.customDomainSslStatus = "VERIFIED_AWAITING_TLS";
    res.json({ verified: true, domain: tenant.customDomain });
  });

  // --- Knowledge Sources (Upload, Website, Sync, Reindex, Retry) ---
  app.post("/api/v1/knowledge-sources/website", requireAuth, (req, res) => {
    const id = `ks-${crypto.randomUUID().slice(0, 8)}`;
    const now = new Date().toISOString();
    const item = {
      id,
      tenantId: getReqAuth(req).tenant.tenantId,
      type: "WEBSITE",
      name: String(req.query.name || req.query.url || "Crawled Website"),
      url: String(req.query.url || ""),
      mimeType: "text/html",
      language: String(req.query.language || "en"),
      status: "COMPLETED",
      syncIntervalMinutes: Number(req.query.syncIntervalMinutes || 1440),
      nextSyncAt: new Date(Date.now() + 86400000).toISOString(),
      lastSyncedAt: now,
      discoveredPages: Math.min(Number(req.query.maxPages || 12), 25),
      lastError: null,
      processingPhase: "INDEXED",
      retryCount: 0,
      maxRetries: 3,
      lastAttemptAt: now
    };
    store.knowledgeSources.set(id, item);
    res.status(201).json(item);
  });

  app.post("/api/v1/knowledge-sources/documents", requireAuth, async (req, res) => {
    try {
      const parsed = await readMultipartRequest(req);
      const file = parsed.files[0];
      const id = `ks-${crypto.randomUUID().slice(0, 8)}`;
      const now = new Date().toISOString();
      const item = {
        id,
        tenantId: getReqAuth(req).tenant.tenantId,
        type: "DOCUMENT",
        name: file?.fileName || "Uploaded Document",
        url: null,
        mimeType: file?.mimeType || "application/pdf",
        language: String(req.query.language || "en"),
        status: "COMPLETED",
        syncIntervalMinutes: req.query.syncIntervalMinutes ? Number(req.query.syncIntervalMinutes) : null,
        nextSyncAt: null,
        lastSyncedAt: now,
        discoveredPages: 1,
        lastError: null,
        processingPhase: "INDEXED",
        retryCount: 0,
        maxRetries: 3,
        lastAttemptAt: now
      };
      store.knowledgeSources.set(id, item);
      res.status(201).json(item);
    } catch (err) {
      res.status(400).json({ message: err instanceof Error ? err.message : "Upload failed." });
    }
  });

  app.post("/api/v1/knowledge-sources/documents/bulk", requireAuth, async (req, res) => {
    try {
      const parsed = await readMultipartRequest(req);
      const created = [];
      const now = new Date().toISOString();
      for (const file of parsed.files) {
        const id = `ks-${crypto.randomUUID().slice(0, 8)}`;
        const item = {
          id,
          tenantId: getReqAuth(req).tenant.tenantId,
          type: "DOCUMENT",
          name: file.fileName,
          url: null,
          mimeType: file.mimeType,
          language: String(req.query.language || "en"),
          status: "COMPLETED",
          syncIntervalMinutes: req.query.syncIntervalMinutes ? Number(req.query.syncIntervalMinutes) : null,
          nextSyncAt: null,
          lastSyncedAt: now,
          discoveredPages: 1,
          lastError: null,
          processingPhase: "INDEXED",
          retryCount: 0,
          maxRetries: 3,
          lastAttemptAt: now
        };
        store.knowledgeSources.set(id, item);
        created.push(item);
      }
      res.status(201).json(created);
    } catch (err) {
      res.status(400).json({ message: err instanceof Error ? err.message : "Bulk upload failed." });
    }
  });

  for (const action of ["sync", "reindex", "retry"] as const) {
    app.post(`/api/v1/knowledge-sources/:id/${action}`, requireAuth, (req, res) => {
      const ks = store.knowledgeSources.get(req.params.id);
      if (!ks) {
        res.status(404).json({ message: "Knowledge source not found." });
        return;
      }
      const now = new Date().toISOString();
      ks.status = "COMPLETED";
      ks.processingPhase = "INDEXED";
      ks.lastSyncedAt = now;
      ks.lastAttemptAt = now;
      ks.lastError = null;
      res.json(ks);
    });
  }

  // --- Portal History (Customer Conversations & My Tickets) ---
  app.get("/api/v1/portal/conversations", (req, res) => {
    const tenant = resolveRequestTenant(req);
    const cookieHeader = req.header("cookie") || "";
    const match = /rk_customer=([^;]+)/.exec(cookieHeader);
    const session = match ? store.customerSessions.get(decodeURIComponent(match[1])) : null;
    if (!session) {
      res.status(401).json({ message: "Sign in to view your conversations." });
      return;
    }
    const items = Array.from(store.portalConversations.values())
      .filter((c) => c.tenantId === tenant.tenantId && c.customerEmail === session.email)
      .map(({ messages: _m, ...summary }) => summary);
    res.json(items);
  });

  app.get("/api/v1/portal/conversations/:id/messages", (req, res) => {
    const cookieHeader = req.header("cookie") || "";
    const match = /rk_customer=([^;]+)/.exec(cookieHeader);
    const session = match ? store.customerSessions.get(decodeURIComponent(match[1])) : null;
    if (!session) {
      res.status(401).json({ message: "Sign in to view conversation messages." });
      return;
    }
    const conv = store.portalConversations.get(req.params.id);
    if (!conv || conv.customerEmail !== session.email) {
      res.status(404).json({ message: "Conversation not found." });
      return;
    }
    res.json(conv.messages);
  });

  app.get("/api/v1/portal/support/my-tickets", (req, res) => {
    const tenant = resolveRequestTenant(req);
    const cookieHeader = req.header("cookie") || "";
    const match = /rk_customer=([^;]+)/.exec(cookieHeader);
    const session = match ? store.customerSessions.get(decodeURIComponent(match[1])) : null;
    if (!session) {
      res.status(401).json({ message: "Sign in to view support tickets." });
      return;
    }
    const tickets = store.supportTickets.filter(
      (t) => (!t.tenantId || t.tenantId === tenant.tenantId) && String(t.email || "").toLowerCase() === session.email.toLowerCase()
    );
    res.json(tickets);
  });

  // =========================================================================
  // KNOWLEDGE REQUEST INTAKE & TRIAGE BOARD
  // =========================================================================
  app.get("/api/v1/knowledge-requests", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const status = typeof req.query.status === "string" && req.query.status ? req.query.status : undefined;
    const priority = typeof req.query.priority === "string" && req.query.priority ? req.query.priority : undefined;
    const items = rankKnowledgeRequests(store.listKnowledgeRequests(tenant.tenantId, { status, priority }));
    res.json(items);
  });

  app.post("/api/v1/knowledge-requests", requireAuth, (req, res) => {
    const { tenant, user } = getReqAuth(req);
    const title = String(req.body?.title || "").trim();
    const description = String(req.body?.description || "").trim();
    if (!title) {
      res.status(400).json({ message: "Request title is required." });
      return;
    }
    const priority = ["LOW", "MEDIUM", "HIGH", "URGENT"].includes(String(req.body?.priority))
      ? (String(req.body.priority) as "LOW" | "MEDIUM" | "HIGH" | "URGENT")
      : "MEDIUM";
    const source = ["INTERNAL", "SUPPORT", "CUSTOMER"].includes(String(req.body?.source))
      ? (String(req.body.source) as "INTERNAL" | "SUPPORT" | "CUSTOMER")
      : "INTERNAL";
    const created = store.createKnowledgeRequest(tenant.tenantId, {
      title,
      description,
      categoryId: req.body?.categoryId ? String(req.body.categoryId) : null,
      priority,
      source,
      requesterEmail: String(req.body?.requesterEmail || user.email)
    });
    store.recordAudit(user.id, "KNOWLEDGE_REQUEST", created.id, "CREATE", null, created.title);
    res.status(201).json(created);
  });

  app.post("/api/v1/knowledge-requests/:id/upvote", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const updated = store.upvoteKnowledgeRequest(tenant.tenantId, req.params.id);
    if (!updated) {
      res.status(404).json({ message: "Knowledge request not found." });
      return;
    }
    res.json(updated);
  });

  app.patch("/api/v1/knowledge-requests/:id", requireAuth, (req, res) => {
    const { tenant, user } = getReqAuth(req);
    const updated = store.updateKnowledgeRequest(tenant.tenantId, req.params.id, {
      status: req.body?.status,
      priority: req.body?.priority,
      assigneeEmail: req.body?.assigneeEmail
    });
    if (!updated) {
      res.status(404).json({ message: "Knowledge request not found." });
      return;
    }
    store.recordAudit(user.id, "KNOWLEDGE_REQUEST", updated.id, "UPDATE", null, updated.status);
    res.json(updated);
  });

  app.post("/api/v1/knowledge-requests/:id/convert-to-draft", requireAuth, (req, res) => {
    const { tenant, user } = getReqAuth(req);
    const existing = store.knowledgeRequests.get(req.params.id);
    if (!existing || existing.tenantId !== tenant.tenantId) {
      res.status(404).json({ message: "Knowledge request not found." });
      return;
    }
    const result = store.convertKnowledgeRequestToDraft(tenant.tenantId, req.params.id, user.id, slugifyTitle(existing.title));
    if (!result) {
      res.status(404).json({ message: "Knowledge request not found." });
      return;
    }
    res.status(201).json(result);
  });

  // =========================================================================
  // GUIDED TROUBLESHOOTING PLAYBOOKS
  // =========================================================================
  app.get("/api/v1/playbooks", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    res.json(store.listPlaybooks(tenant.tenantId, false));
  });

  app.get("/api/v1/portal/playbooks", (req, res) => {
    const tenant = resolveRequestTenant(req);
    res.json(store.listPlaybooks(tenant.tenantId, true));
  });

  app.post("/api/v1/playbooks", requireAuth, (req, res) => {
    const { tenant, user } = getReqAuth(req);
    const title = String(req.body?.title || "").trim();
    const summary = String(req.body?.summary || "").trim();
    const category = String(req.body?.category || "General").trim();
    const steps = Array.isArray(req.body?.steps) ? req.body.steps : [];
    if (!title || steps.length === 0) {
      res.status(400).json({ message: "Title and at least one step are required." });
      return;
    }
    const created = store.createPlaybook(tenant.tenantId, {
      title,
      summary,
      category,
      published: req.body?.published !== false,
      steps
    });
    store.recordAudit(user.id, "PLAYBOOK", created.id, "CREATE", null, created.title);
    res.status(201).json(created);
  });

  app.delete("/api/v1/playbooks/:id", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    if (!store.deletePlaybook(tenant.tenantId, req.params.id)) {
      res.status(404).json({ message: "Playbook not found." });
      return;
    }
    res.status(204).end();
  });

  app.post("/api/v1/playbooks/:id/run", (req, res) => {
    const tenant = resolveRequestTenant(req);
    const outcome = req.body?.outcome === "ESCALATED" ? "ESCALATED" : "RESOLVED";
    const updated = store.recordPlaybookRun(tenant.tenantId, req.params.id, outcome);
    if (!updated) {
      res.status(404).json({ message: "Playbook not found." });
      return;
    }
    res.json(updated);
  });

  // =========================================================================
  // PRODUCT FEEDBACK (BUG REPORTS & FEATURE REQUESTS TO DEV TEAM)
  // =========================================================================
  app.post("/api/v1/product-feedback", (req, res) => {
    const auth = getAuth(req);
    const tenant = auth ? auth.tenant : resolveRequestTenant(req);
    const validated = validateProductFeedbackInput(req.body, req.header("user-agent") || "");
    if (!validated.ok) {
      res.status(400).json({ message: validated.error });
      return;
    }
    const input = validated.value;
    const browserInfo = input.includeDiagnostics && input.userAgent ? parseBrowserInfo(input.userAgent) : null;
    const created = store.createProductFeedback(tenant.tenantId, {
      type: input.type,
      severity: input.severity,
      title: input.title,
      description: input.description,
      reporterEmail: input.reporterEmail || auth?.user.email || null,
      reporterUserId: auth?.user.id || null,
      includeDiagnostics: input.includeDiagnostics,
      pageUrl: input.pageUrl,
      routePath: input.routePath,
      browser: browserInfo?.browser ?? null,
      os: browserInfo?.os ?? null,
      userAgent: input.userAgent,
      viewport: input.viewport,
      theme: input.theme,
      language: input.language
    });
    if (auth) {
      store.recordAudit(auth.user.id, "PRODUCT_FEEDBACK", created.id, "SUBMIT", null, `${created.type}:${created.title}`);
    }
    res.status(201).json(created);
  });

  app.get("/api/v1/product-feedback", requireAuth, (req, res) => {
    const { tenant } = getReqAuth(req);
    const type = typeof req.query.type === "string" && req.query.type ? req.query.type.toUpperCase() : undefined;
    const status = typeof req.query.status === "string" && req.query.status ? req.query.status.toUpperCase() : undefined;
    res.json(store.listProductFeedback(tenant.tenantId, { type, status }));
  });

  app.patch("/api/v1/product-feedback/:id", requireAuth, (req, res) => {
    const { tenant, user } = getReqAuth(req);
    const rawStatus = String(req.body?.status || "").trim().toUpperCase();
    const allowed = ["NEW", "TRIAGED", "IN_PROGRESS", "RESOLVED", "WONT_FIX"] as const;
    if (!allowed.includes(rawStatus as (typeof allowed)[number])) {
      res.status(400).json({ message: "Invalid feedback status." });
      return;
    }
    const updated = store.updateProductFeedbackStatus(
      tenant.tenantId,
      req.params.id,
      rawStatus as (typeof allowed)[number]
    );
    if (!updated) {
      res.status(404).json({ message: "Feedback report not found." });
      return;
    }
    store.recordAudit(user.id, "PRODUCT_FEEDBACK", updated.id, "UPDATE_STATUS", null, updated.status);
    res.json(updated);
  });
}
