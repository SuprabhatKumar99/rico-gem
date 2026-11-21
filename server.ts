import express from "express";
import type { Request, Response, NextFunction } from "express";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { execSync } from "node:child_process";
import { createStore, paginate } from "./server/store.ts";
import { diffArticleBlocks, evaluateArticleQuality, tiptapToBlocks } from "./server/domain-helpers.ts";
import { readMultipartRequest } from "./server/multipart.ts";
import { registerAiAndSearchRoutes } from "./server/routes/ai-and-search.ts";
import { registerOperationsAndEnterpriseRoutes } from "./server/routes/operations-and-enterprise.ts";

const PORT = Number(process.env.PORT || 3000);
const HOST = "0.0.0.0";
const DIST_DIR = path.resolve(process.cwd(), "frontend/dist/ricozknow-frontend/browser");
const INDEX_HTML = path.join(DIST_DIR, "index.html");

if (!fs.existsSync(INDEX_HTML)) {
  console.log("[ricozknow] Frontend build not found, building Angular app...");
  execSync("npm run build:frontend", { stdio: "inherit" });
}

const store = createStore();
const app = express();

app.use(express.json({ limit: "10mb" }));
app.use(express.urlencoded({ extended: true }));

function parseCookies(header?: string): Record<string, string> {
  const out: Record<string, string> = {};
  if (!header) return out;
  for (const part of header.split(";")) {
    const idx = part.indexOf("=");
    if (idx === -1) continue;
    const key = part.slice(0, idx).trim();
    const val = decodeURIComponent(part.slice(idx + 1).trim());
    if (key) out[key] = val;
  }
  return out;
}

function getBearerToken(req: Request): string | null {
  const auth = req.header("authorization") || "";
  if (auth.toLowerCase().startsWith("bearer ")) {
    return auth.slice(7).trim();
  }
  return null;
}

function getAuth(req: Request) {
  return store.authenticateToken(getBearerToken(req));
}

function resolveRequestTenant(req: Request) {
  const auth = getAuth(req);
  if (auth) return auth.tenant;
  const queryTenant = typeof req.query.tenant === "string" ? req.query.tenant : null;
  const sub = typeof req.query.subdomain === "string" ? req.query.subdomain : null;
  return store.resolveTenantBySlugOrSubdomain(queryTenant || sub);
}

function requireAuth(req: Request, res: Response, next: NextFunction): void {
  const auth = getAuth(req);
  if (!auth) {
    res.status(401).json({ message: "Authentication required." });
    return;
  }
  (req as Request & { auth: typeof auth }).auth = auth;
  next();
}

function getReqAuth(req: Request) {
  return (req as Request & { auth: NonNullable<ReturnType<typeof getAuth>> }).auth;
}

function generateRecoveryCodes(): string[] {
  return Array.from({ length: 6 }, () => {
    const a = crypto.randomInt(1000, 9999);
    const b = crypto.randomInt(1000, 9999);
    return `RK-${a}-${b}`;
  });
}

// --- Health & Status ---
app.get("/api/v1/status", (_req, res) => {
  res.json({
    status: "UP",
    version: "2.0.0",
    mode: "in-memory",
    timestamp: new Date().toISOString()
  });
});

// --- Auth & Security ---
app.post("/api/v1/auth/login", (req, res) => {
  const { tenantSlug = "", email = "", password = "" } = req.body ?? {};
  const result = store.login(String(tenantSlug), String(email), String(password));
  if ("error" in result) {
    res.status(result.status).json({ message: result.error });
    return;
  }
  res.setHeader("Set-Cookie", `rk_refresh=${encodeURIComponent(result.refreshToken)}; Path=/; HttpOnly; SameSite=Lax`);
  res.json(result.response);
});

app.post("/api/v1/auth/register", (req, res) => {
  const result = store.register(req.body ?? {});
  res.status(result.status).json(result.body);
});

app.post("/api/v1/auth/refresh", (req, res) => {
  const cookies = parseCookies(req.header("cookie"));
  const refreshed = store.refreshSession(cookies.rk_refresh);
  if (!refreshed) {
    res.status(401).json({ message: "Session expired." });
    return;
  }
  res.setHeader("Set-Cookie", `rk_refresh=${encodeURIComponent(refreshed.refreshToken)}; Path=/; HttpOnly; SameSite=Lax`);
  res.json(refreshed.response);
});

app.post("/api/v1/auth/logout", (req, res) => {
  const cookies = parseCookies(req.header("cookie"));
  store.logout(getBearerToken(req), cookies.rk_refresh);
  res.setHeader("Set-Cookie", "rk_refresh=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
  res.status(204).end();
});

app.post("/api/v1/auth/verify-email", (_req, res) => res.status(204).end());
app.post("/api/v1/auth/resend-verification", (_req, res) => res.status(204).end());
app.post("/api/v1/auth/forgot-password", (_req, res) => res.status(204).end());
app.post("/api/v1/auth/reset-password", (_req, res) => res.status(204).end());
app.post("/api/v1/auth/accept-invitation", (_req, res) => res.status(204).end());

app.get("/api/v1/auth/sessions", requireAuth, (req, res) => {
  const { user } = getReqAuth(req);
  const sessions = Array.from(store.sessions.values())
    .filter((s) => s.userId === user.id)
    .map((s) => ({
      id: s.token.slice(0, 14),
      deviceId: "browser",
      deviceName: "Web Session",
      userAgent: req.header("user-agent") || "Browser",
      ipAddress: req.ip || "127.0.0.1",
      lastUsedAt: s.createdAt,
      expiresAt: s.expiresAt
    }));
  res.json(sessions);
});

app.delete("/api/v1/auth/sessions/:id", requireAuth, (req, res) => {
  const { user } = getReqAuth(req);
  for (const [tok, session] of Array.from(store.sessions.entries())) {
    if (session.userId === user.id && tok.startsWith(req.params.id)) {
      store.logout(tok, session.refreshToken);
    }
  }
  res.status(204).end();
});

app.get("/api/v1/auth/mfa/status", requireAuth, (req, res) => {
  res.json({ enabled: getReqAuth(req).user.mfaEnabled });
});
app.post("/api/v1/auth/mfa/enroll", requireAuth, (req, res) => {
  const { user } = getReqAuth(req);
  const secret = "JBSWY3DPEHPK3PXP";
  res.json({ secret, otpauthUri: `otpauth://totp/RicozKnow:${encodeURIComponent(user.email)}?secret=${secret}&issuer=RicozKnow` });
});
app.post("/api/v1/auth/mfa/enable", requireAuth, (req, res) => {
  const { user } = getReqAuth(req);
  user.mfaEnabled = true;
  store.recordAudit(user.id, "USER", user.id, "MFA_ENABLE");
  res.json({ recoveryCodes: generateRecoveryCodes() });
});
app.post("/api/v1/auth/mfa/recovery-codes/regenerate", requireAuth, (req, res) => {
  const { user } = getReqAuth(req);
  store.recordAudit(user.id, "USER", user.id, "MFA_ROTATE_RECOVERY_CODES");
  res.json({ recoveryCodes: generateRecoveryCodes() });
});
app.post("/api/v1/auth/mfa/disable", requireAuth, (req, res) => {
  const { user } = getReqAuth(req);
  user.mfaEnabled = false;
  store.recordAudit(user.id, "USER", user.id, "MFA_DISABLE");
  res.status(204).end();
});

// --- Tenants & Branding ---
app.get("/api/v1/tenants/lookup", (req, res) => {
  const tenant = store.resolveTenantBySlugOrSubdomain(String(req.query.subdomain || ""));
  res.json(tenant);
});

app.get("/api/v1/tenants/me", requireAuth, (req, res) => {
  res.json(getReqAuth(req).tenant);
});

app.put("/api/v1/admin/branding", requireAuth, (req, res) => {
  const { tenant, user } = getReqAuth(req);
  const body = req.body ?? {};
  if (typeof body.portalTitle === "string") tenant.portalTitle = body.portalTitle;
  if (typeof body.logoUrl === "string") tenant.logoUrl = body.logoUrl;
  if (typeof body.primaryColor === "string") tenant.primaryColor = body.primaryColor;
  if (body.customDomain !== undefined) tenant.customDomain = body.customDomain || null;
  if (body.supportEmail !== undefined) tenant.supportEmail = body.supportEmail || null;
  if (typeof body.supportedLanguages === "string") tenant.supportedLanguages = body.supportedLanguages;
  store.recordAudit(user.id, "TENANT", tenant.tenantId, "UPDATE_BRANDING");
  res.json(tenant);
});

// --- Categories ---
app.get("/api/v1/categories", (req, res) => {
  const tenant = resolveRequestTenant(req);
  res.json(store.listCategories(tenant.tenantId));
});

app.get("/api/v1/categories/:id", (req, res) => {
  const tenant = resolveRequestTenant(req);
  const cat = store.listCategories(tenant.tenantId).find((c) => c.id === req.params.id);
  if (!cat) {
    res.status(404).json({ message: "Category not found." });
    return;
  }
  res.json(cat);
});

app.post("/api/v1/categories", requireAuth, (req, res) => {
  const { tenant, user } = getReqAuth(req);
  const created = store.createCategory(tenant.tenantId, req.body ?? {});
  store.recordAudit(user.id, "CATEGORY", created.id, "CREATE");
  res.status(201).json(created);
});

app.put("/api/v1/categories/:id", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  const updated = store.updateCategory(tenant.tenantId, req.params.id, req.body ?? {});
  if (!updated) {
    res.status(404).json({ message: "Category not found." });
    return;
  }
  res.json(updated);
});

app.delete("/api/v1/categories/:id", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  store.deleteCategory(tenant.tenantId, req.params.id);
  res.status(204).end();
});

// --- Assets ---
app.post("/api/v1/assets", requireAuth, async (req, res) => {
  try {
    const { tenant } = getReqAuth(req);
    const parsed = await readMultipartRequest(req);
    const file = parsed.files[0];
    if (!file) {
      res.status(400).json({ message: "No file uploaded." });
      return;
    }
    const id = `ast-${crypto.randomUUID().slice(0, 8)}`;
    const record = {
      id,
      tenantId: tenant.tenantId,
      articleId: parsed.fields.articleId || null,
      fileName: file.fileName,
      mimeType: file.mimeType,
      sizeBytes: file.data.length,
      data: file.data,
      createdAt: new Date().toISOString()
    };
    store.assets.set(id, record);
    res.status(201).json({
      id: record.id,
      articleId: record.articleId,
      fileName: record.fileName,
      mimeType: record.mimeType,
      sizeBytes: record.sizeBytes,
      downloadUrl: `/api/v1/assets/${record.id}/download`,
      createdAt: record.createdAt
    });
  } catch (err) {
    res.status(400).json({ message: err instanceof Error ? err.message : "Asset upload failed." });
  }
});

app.get("/api/v1/assets/:id", requireAuth, (req, res) => {
  const record = store.assets.get(req.params.id);
  if (!record) {
    res.status(404).json({ message: "Asset not found." });
    return;
  }
  res.json({
    id: record.id,
    articleId: record.articleId,
    fileName: record.fileName,
    mimeType: record.mimeType,
    sizeBytes: record.sizeBytes,
    downloadUrl: `/api/v1/assets/${record.id}/download`,
    createdAt: record.createdAt
  });
});

app.get("/api/v1/assets/:id/download", (req, res) => {
  const record = store.assets.get(req.params.id);
  if (!record) {
    res.status(404).json({ message: "Asset not found." });
    return;
  }
  res.setHeader("Content-Type", record.mimeType);
  res.setHeader("Content-Disposition", `inline; filename="${record.fileName.replace(/"/g, "")}"`);
  res.send(record.data);
});

// --- Articles, Templates, Governance, Collaboration & Bulk Ops ---
app.get("/api/v1/articles/templates", requireAuth, (_req, res) => {
  res.json([
    {
      key: "how-to",
      name: "How-To Guide",
      description: "Step-by-step instructions for accomplishing a task.",
      blocks: [
        { type: "heading", level: 2, text: "Overview", items: null },
        { type: "paragraph", level: null, text: "Explain what this guide helps the reader achieve.", items: null },
        { type: "list", level: null, text: null, items: ["Step 1", "Step 2", "Step 3"] }
      ]
    },
    {
      key: "faq",
      name: "FAQ",
      description: "Common questions and concise answers.",
      blocks: [
        { type: "heading", level: 2, text: "Frequently Asked Questions", items: null },
        { type: "paragraph", level: null, text: "Add clear answers for recurring user questions.", items: null }
      ]
    },
    {
      key: "runbook",
      name: "Incident Runbook",
      description: "Structured triage, mitigation, and post-incident checklist.",
      blocks: [
        { type: "heading", level: 2, text: "Detection & Triage", items: null },
        { type: "paragraph", level: null, text: "List alerts, dashboards, and initial diagnostic commands.", items: null },
        { type: "heading", level: 2, text: "Mitigation Steps", items: null },
        { type: "list", level: null, text: null, items: ["Isolate affected component", "Apply rollback or failover", "Notify status page"] }
      ]
    }
  ]);
});

app.get("/api/v1/articles/governance/health", requireAuth, (req, res) => {
  res.json(store.getKnowledgeHealth(getReqAuth(req).tenant.tenantId));
});

app.get("/api/v1/articles/governance/:id", requireAuth, (req, res) => {
  const article = store.getArticle(getReqAuth(req).tenant.tenantId, req.params.id);
  if (!article) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  res.json({
    articleId: article.id,
    ownerId: article.ownerId,
    reviewIntervalDays: article.reviewIntervalDays,
    nextReviewAt: article.nextReviewAt,
    verifiedAt: article.verifiedAt,
    verifiedBy: article.verifiedBy,
    reviewDue: !article.verifiedAt
  });
});

app.put("/api/v1/articles/governance/:id", requireAuth, (req, res) => {
  const article = store.getArticle(getReqAuth(req).tenant.tenantId, req.params.id);
  if (!article) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  if (req.body?.ownerId !== undefined) article.ownerId = req.body.ownerId || null;
  if (typeof req.body?.reviewIntervalDays === "number" && req.body.reviewIntervalDays > 0) {
    article.reviewIntervalDays = req.body.reviewIntervalDays;
    article.nextReviewAt = new Date(Date.now() + req.body.reviewIntervalDays * 86400000).toISOString();
  }
  res.json({
    articleId: article.id,
    ownerId: article.ownerId,
    reviewIntervalDays: article.reviewIntervalDays,
    nextReviewAt: article.nextReviewAt,
    verifiedAt: article.verifiedAt,
    verifiedBy: article.verifiedBy,
    reviewDue: !article.verifiedAt
  });
});

app.post("/api/v1/articles/governance/:id/verify", requireAuth, (req, res) => {
  const { tenant, user } = getReqAuth(req);
  const article = store.getArticle(tenant.tenantId, req.params.id);
  if (!article) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  const now = new Date().toISOString();
  article.verifiedAt = now;
  article.verifiedBy = user.id;
  article.nextReviewAt = new Date(Date.now() + article.reviewIntervalDays * 86400000).toISOString();
  const events = store.verificationHistory.get(article.id) ?? [];
  events.unshift({ action: "VERIFIED", actorId: user.id, details: "Verified via governance check.", at: now });
  store.verificationHistory.set(article.id, events);
  store.recordAudit(user.id, "ARTICLE", article.id, "VERIFY");
  res.json({
    articleId: article.id,
    ownerId: article.ownerId,
    reviewIntervalDays: article.reviewIntervalDays,
    nextReviewAt: article.nextReviewAt,
    verifiedAt: article.verifiedAt,
    verifiedBy: article.verifiedBy,
    reviewDue: false
  });
});

app.get("/api/v1/articles/governance/:id/history", requireAuth, (req, res) => {
  res.json(store.verificationHistory.get(req.params.id) ?? []);
});

app.get("/api/v1/articles/governance/:id/quality", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  const article = store.getArticle(tenant.tenantId, req.params.id);
  if (!article) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  const versions = store.getVersions(tenant.tenantId, article.id) ?? [];
  const latest = versions[versions.length - 1] ?? null;
  res.json(evaluateArticleQuality(article, latest));
});

app.get("/api/v1/articles/library/blocks", requireAuth, (_req, res) => res.json(store.reusableBlocks));
app.post("/api/v1/articles/library/blocks", requireAuth, (req, res) => {
  const item = { id: `blk-${crypto.randomUUID().slice(0, 8)}`, name: req.body?.name || "Block", block: req.body?.block || {} };
  store.reusableBlocks.push(item);
  res.status(201).json(item);
});
app.get("/api/v1/articles/library/templates", requireAuth, (_req, res) => res.json(store.customTemplates));
app.post("/api/v1/articles/library/templates", requireAuth, (req, res) => {
  const item = {
    id: `tpl-${crypto.randomUUID().slice(0, 8)}`,
    key: req.body?.key || "custom",
    name: req.body?.name || "Custom Template",
    description: null,
    content: req.body?.content || { blocks: [] }
  };
  store.customTemplates.push(item);
  res.status(201).json(item);
});

app.get("/api/v1/articles/bulk/export", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  const format = String(req.query.format || "csv").toLowerCase();
  const items = store.listArticles(tenant.tenantId, { page: 0, size: 500 }).content;
  if (format === "markdown") {
    const md = items
      .map((a) => {
        const latest = (store.getVersions(tenant.tenantId, a.id) ?? []).slice(-1)[0];
        const body = (latest?.content?.blocks ?? []).map((b) => b.text || b.code || (b.items || []).join("\n- ")).join("\n\n");
        return `# ${a.title}\n\nStatus: ${a.status} | Slug: ${a.slug}\n\n${body}`;
      })
      .join("\n\n---\n\n");
    res.setHeader("Content-Type", "text/markdown; charset=utf-8");
    res.send(md);
    return;
  }
  if (format === "html") {
    const html = `<!doctype html><html><body>${items
      .map((a) => `<article><h1>${a.title}</h1><p>${a.status} · ${a.slug}</p></article>`)
      .join("")}</body></html>`;
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.send(html);
    return;
  }
  const csv = ["id,title,slug,status,visibility", ...items.map((a) => `"${a.id}","${a.title.replace(/"/g, '""')}","${a.slug}","${a.status}","${a.visibility}"`)].join("\n");
  res.setHeader("Content-Type", "text/csv; charset=utf-8");
  res.send(csv);
});

app.post("/api/v1/articles/bulk/import", requireAuth, async (req, res) => {
  try {
    const { tenant, user } = getReqAuth(req);
    const parsed = await readMultipartRequest(req);
    const createdIds: string[] = [];
    for (const file of parsed.files) {
      const text = file.data.toString("utf8").trim();
      const baseTitle = file.fileName.replace(/\.[^.]+$/, "").replace(/[-_]+/g, " ") || "Imported Article";
      const created = store.createArticle(tenant.tenantId, user.id, {
        title: baseTitle,
        slug: `${baseTitle.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-${crypto.randomUUID().slice(0, 4)}`,
        categoryId: "cat-getting-started",
        content: {
          blocks: [
            { id: "b1", type: "heading", level: 2, text: baseTitle },
            { id: "b2", type: "paragraph", text: text.slice(0, 4000) || "Imported content." }
          ]
        }
      });
      createdIds.push(created.id);
    }
    res.json({ imported: createdIds.length, articles: createdIds });
  } catch (err) {
    res.status(400).json({ message: err instanceof Error ? err.message : "Bulk import failed." });
  }
});

// Mount AI Platform, AI Studio, Innovation, Enterprise Search, Connectors BEFORE /articles/:id wildcard
registerAiAndSearchRoutes(app, { store, requireAuth, getReqAuth });
registerOperationsAndEnterpriseRoutes(app, { store, requireAuth, getReqAuth, resolveRequestTenant });

app.get("/api/v1/articles", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  const status = typeof req.query.status === "string" ? req.query.status : undefined;
  const page = Number(req.query.page ?? 0);
  const size = Number(req.query.size ?? 10);
  res.json(store.listArticles(tenant.tenantId, { status, page, size }));
});

app.post("/api/v1/articles", requireAuth, (req, res) => {
  const { tenant, user } = getReqAuth(req);
  const created = store.createArticle(tenant.tenantId, user.id, req.body ?? {});
  store.recordAudit(user.id, "ARTICLE", created.id, "CREATE");
  res.status(201).json(created);
});

app.get("/api/v1/articles/:id", requireAuth, (req, res) => {
  const article = store.getArticle(getReqAuth(req).tenant.tenantId, req.params.id);
  if (!article) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  res.json(article);
});

app.get("/api/v1/articles/:id/versions/diff", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  const versions = store.getVersions(tenant.tenantId, req.params.id) ?? [];
  const fromId = String(req.query.fromVersionId || "");
  const toId = String(req.query.toVersionId || "");
  const fromVer = versions.find((v) => v.id === fromId);
  const toVer = versions.find((v) => v.id === toId);
  const blocks = diffArticleBlocks(fromVer?.content?.blocks ?? [], toVer?.content?.blocks ?? []);
  res.json({ fromVersionId: fromId, toVersionId: toId, blocks });
});

app.get("/api/v1/articles/:id/versions", requireAuth, (req, res) => {
  const versions = store.getVersions(getReqAuth(req).tenant.tenantId, req.params.id);
  if (!versions) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  res.json(versions);
});

app.post("/api/v1/articles/:id/versions", requireAuth, (req, res) => {
  const { tenant, user } = getReqAuth(req);
  const created = store.createDraftVersion(tenant.tenantId, req.params.id, user.id);
  if (!created) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  res.status(201).json(created);
});

app.put("/api/v1/articles/:id/versions/:versionId", requireAuth, (req, res) => {
  const updated = store.updateDraft(
    getReqAuth(req).tenant.tenantId,
    req.params.id,
    req.params.versionId,
    req.body?.content,
    req.body?.changeSummary
  );
  if (!updated) {
    res.status(404).json({ message: "Version not found." });
    return;
  }
  res.json(updated);
});

app.post("/api/v1/articles/:id/versions/:versionId/submit", requireAuth, (req, res) => {
  const updated = store.submitForReview(getReqAuth(req).tenant.tenantId, req.params.id, req.params.versionId);
  if (!updated) {
    res.status(404).json({ message: "Version not found." });
    return;
  }
  res.json(updated);
});

app.post("/api/v1/articles/:id/versions/:versionId/review", requireAuth, (req, res) => {
  const { tenant, user } = getReqAuth(req);
  const updated = store.reviewVersion(
    tenant.tenantId,
    req.params.id,
    req.params.versionId,
    user.id,
    Boolean(req.body?.approve),
    req.body?.comment
  );
  if (!updated) {
    res.status(404).json({ message: "Version not found." });
    return;
  }
  store.recordAudit(user.id, "ARTICLE", req.params.id, req.body?.approve ? "PUBLISH" : "REJECT");
  res.json(updated);
});

app.post("/api/v1/articles/:id/versions/:versionId/approve-for-schedule", requireAuth, (req, res) => {
  const { tenant, user } = getReqAuth(req);
  const updated = store.approveForSchedule(tenant.tenantId, req.params.id, req.params.versionId, user.id, req.body?.comment);
  if (!updated) {
    res.status(404).json({ message: "Version not found." });
    return;
  }
  res.json(updated);
});

app.post("/api/v1/articles/:id/versions/:versionId/schedule-publish", requireAuth, (req, res) => {
  const { tenant, user } = getReqAuth(req);
  const at = String(req.body?.at || "");
  const updated = store.schedulePublish(tenant.tenantId, req.params.id, req.params.versionId, at, user.id);
  if (!updated) {
    res.status(404).json({ message: "Version not found." });
    return;
  }
  res.json(updated);
});

app.post("/api/v1/articles/:id/schedule-unpublish", requireAuth, (req, res) => {
  const { tenant, user } = getReqAuth(req);
  const article = store.getArticle(tenant.tenantId, req.params.id);
  if (!article) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  article.scheduledUnpublishAt = String(req.body?.at || "");
  store.recordAudit(user.id, "ARTICLE", article.id, "SCHEDULE_UNPUBLISH", null, article.scheduledUnpublishAt);
  res.json(article);
});

app.delete("/api/v1/articles/:id/schedule", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  const article = store.getArticle(tenant.tenantId, req.params.id);
  if (!article) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  article.scheduledPublishAt = null;
  article.scheduledUnpublishAt = null;
  res.json(article);
});

app.post("/api/v1/articles/:id/rollback", requireAuth, (req, res) => {
  const { tenant, user } = getReqAuth(req);
  const rolled = store.rollbackArticle(
    tenant.tenantId,
    req.params.id,
    String(req.body?.versionId || ""),
    user.id,
    req.body?.changeSummary
  );
  if (!rolled) {
    res.status(404).json({ message: "Version not found." });
    return;
  }
  res.status(201).json(rolled);
});

// --- Collaboration Gateway (HTTP-synced rich text + presence) ---
app.post("/api/v1/articles/:id/collaboration/:versionId/ticket", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  const versions = store.getVersions(tenant.tenantId, req.params.id);
  const ver = versions?.find((v) => v.id === req.params.versionId);
  if (!ver) {
    res.status(404).json({ message: "Version not found." });
    return;
  }
  res.json({
    token: `collab_${crypto.randomUUID().slice(0, 16)}`,
    room: `${tenant.tenantId}:${req.params.id}:${req.params.versionId}`,
    websocketUrl: "",
    expiresInSeconds: 3600
  });
});

app.get("/api/v1/articles/:id/collaboration/:versionId", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  const versions = store.getVersions(tenant.tenantId, req.params.id);
  const ver = versions?.find((v) => v.id === req.params.versionId);
  if (!ver) {
    res.status(404).json({ message: "Version not found." });
    return;
  }
  const key = `${req.params.id}:${req.params.versionId}`;
  const saved = store.collaborationDocs.get(key);
  res.json({
    articleId: req.params.id,
    versionId: req.params.versionId,
    content: ver.content,
    tiptapDoc: saved?.tiptapDoc ?? null,
    updatedAt: saved?.updatedAt ?? new Date().toISOString()
  });
});

app.put("/api/v1/articles/:id/collaboration/:versionId", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  const versions = store.getVersions(tenant.tenantId, req.params.id);
  const ver = versions?.find((v) => v.id === req.params.versionId);
  if (!ver) {
    res.status(404).json({ message: "Version not found." });
    return;
  }
  const key = `${req.params.id}:${req.params.versionId}`;
  const now = new Date().toISOString();
  if (req.body?.tiptapDoc) {
    store.collaborationDocs.set(key, { tiptapDoc: req.body.tiptapDoc, updatedAt: now });
    const converted = tiptapToBlocks(req.body.tiptapDoc);
    if (converted.blocks.length) {
      ver.content = converted;
    }
  } else if (req.body?.content) {
    store.updateDraft(tenant.tenantId, req.params.id, req.params.versionId, req.body.content);
  }
  res.json({ saved: true, updatedAt: now });
});

app.post("/api/v1/articles/:id/collaboration/:versionId/presence", requireAuth, (req, res) => {
  const { user } = getReqAuth(req);
  const key = `${req.params.id}:${req.params.versionId}`;
  const now = Date.now();
  const room = store.collaborationPresence.get(key) ?? new Map();
  room.set(user.id, { userId: user.id, email: user.email, name: user.name, lastSeenAt: now });
  for (const [uid, entry] of Array.from(room.entries())) {
    if (now - entry.lastSeenAt > 45000) room.delete(uid);
  }
  store.collaborationPresence.set(key, room);
  res.json(Array.from(room.values()));
});

app.delete("/api/v1/articles/:id/collaboration/:versionId/presence", requireAuth, (req, res) => {
  const { user } = getReqAuth(req);
  const key = `${req.params.id}:${req.params.versionId}`;
  store.collaborationPresence.get(key)?.delete(user.id);
  res.status(204).end();
});

app.get("/api/v1/articles/:id/review-history", requireAuth, (req, res) => {
  res.json(store.reviewHistory.get(req.params.id) ?? []);
});

app.get("/api/v1/articles/:id/metadata", requireAuth, (req, res) => {
  const article = store.getArticle(getReqAuth(req).tenant.tenantId, req.params.id);
  if (!article) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  res.json({
    tags: article.tags,
    relatedArticleIds: article.relatedArticleIds,
    pinned: article.pinned,
    featured: article.featured
  });
});

app.put("/api/v1/articles/:id/metadata", requireAuth, (req, res) => {
  const article = store.getArticle(getReqAuth(req).tenant.tenantId, req.params.id);
  if (!article) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  if (Array.isArray(req.body?.tags)) article.tags = req.body.tags;
  if (Array.isArray(req.body?.relatedArticleIds)) article.relatedArticleIds = req.body.relatedArticleIds;
  if (typeof req.body?.pinned === "boolean") article.pinned = req.body.pinned;
  if (typeof req.body?.featured === "boolean") article.featured = req.body.featured;
  res.json({
    tags: article.tags,
    relatedArticleIds: article.relatedArticleIds,
    pinned: article.pinned,
    featured: article.featured
  });
});

app.post("/api/v1/articles/:id/archive", requireAuth, (req, res) => {
  store.archiveArticle(getReqAuth(req).tenant.tenantId, req.params.id);
  res.status(204).end();
});

app.put("/api/v1/articles/:id/visibility", requireAuth, (req, res) => {
  const article = store.getArticle(getReqAuth(req).tenant.tenantId, req.params.id);
  if (article && typeof req.query.visibility === "string") {
    article.visibility = req.query.visibility as "PUBLIC" | "AUTHENTICATED" | "PRIVATE";
  }
  res.status(204).end();
});

app.get("/api/v1/articles/:id/versions/:versionId/comments", requireAuth, (req, res) => {
  res.json(store.comments.get(`${req.params.id}:${req.params.versionId}`) ?? []);
});

app.post("/api/v1/articles/:id/versions/:versionId/comments", requireAuth, (req, res) => {
  const { user } = getReqAuth(req);
  const key = `${req.params.id}:${req.params.versionId}`;
  const list = store.comments.get(key) ?? [];
  const item = {
    id: `cmt-${crypto.randomUUID().slice(0, 8)}`,
    articleId: req.params.id,
    versionId: req.params.versionId,
    blockId: String(req.body?.blockId || "b1"),
    authorId: user.id,
    comment: String(req.body?.comment || ""),
    parentId: req.body?.parentId || null,
    resolved: false,
    createdAt: new Date().toISOString(),
    resolvedAt: null
  };
  list.push(item);
  store.comments.set(key, list);
  res.status(201).json(item);
});

app.post("/api/v1/articles/:id/comments/:commentId/resolve", requireAuth, (req, res) => {
  for (const list of store.comments.values()) {
    const found = list.find((c) => c.id === req.params.commentId);
    if (found) {
      found.resolved = true;
      found.resolvedAt = new Date().toISOString();
    }
  }
  res.status(204).end();
});

// --- Portal (Public & Customer Auth) ---
app.get("/api/v1/portal/articles", (req, res) => {
  const tenant = resolveRequestTenant(req);
  res.json(
    store.portalArticles(tenant.tenantId, {
      categoryId: typeof req.query.categoryId === "string" ? req.query.categoryId : undefined,
      language: typeof req.query.language === "string" ? req.query.language : undefined,
      page: Number(req.query.page ?? 0),
      size: Number(req.query.size ?? 10),
      pinned: req.query.pinned === "true",
      featured: req.query.featured === "true"
    })
  );
});

app.get("/api/v1/portal/articles/:slug", (req, res) => {
  const tenant = resolveRequestTenant(req);
  const detail = store.portalArticleBySlug(tenant.tenantId, req.params.slug);
  if (!detail) {
    res.status(404).json({ message: "Article not found." });
    return;
  }
  res.json(detail);
});

app.post("/api/v1/portal/articles/:articleId/feedback", (req, res) => {
  const tenant = resolveRequestTenant(req);
  store.recordFeedback(tenant.tenantId, req.params.articleId, req.query.helpful !== "false");
  res.status(204).end();
});

app.get("/api/v1/portal/search", (req, res) => {
  const tenant = resolveRequestTenant(req);
  res.json(
    store.searchArticles(tenant.tenantId, String(req.query.q || ""), {
      publicOnly: true,
      categoryId: typeof req.query.categoryId === "string" ? req.query.categoryId : undefined,
      tag: typeof req.query.tag === "string" ? req.query.tag : undefined,
      language: typeof req.query.language === "string" ? req.query.language : undefined,
      page: Number(req.query.page ?? 0)
    })
  );
});

app.get("/api/v1/portal/search/suggestions", (_req, res) => {
  res.json(["password reset", "SSO setup", "RAG API", "getting started", "billing FAQ"]);
});

app.post("/api/v1/portal/search/click", (_req, res) => res.status(204).end());

app.get("/api/v1/portal/search/saved", (_req, res) => res.json(Array.from(store.savedSearches.values())));
app.post("/api/v1/portal/search/saved", (req, res) => {
  const id = `ss-${crypto.randomUUID().slice(0, 8)}`;
  const item = { id, ...req.body, queryText: req.body?.query || "", active: true };
  store.savedSearches.set(id, item);
  res.status(201).json(item);
});
app.delete("/api/v1/portal/search/saved/:id", (req, res) => {
  store.savedSearches.delete(req.params.id);
  res.status(204).end();
});

app.post("/api/v1/portal/support/tickets", (req, res) => {
  const tenant = resolveRequestTenant(req);
  const now = new Date().toISOString();
  const integration = store.supportIntegrationByTenant.get(tenant.tenantId);
  const provider = String(integration?.provider || "EMAIL");
  const ticket = {
    id: `tkt-${crypto.randomUUID().slice(0, 8)}`,
    tenantId: tenant.tenantId,
    status: "OPEN",
    provider,
    email: String(req.body?.email || ""),
    subject: String(req.body?.subject || "Support request"),
    description: String(req.body?.description || ""),
    conversationId: req.body?.conversationId || null,
    created_at: now,
    createdAt: now
  };
  store.supportTickets.unshift(ticket);
  res.status(201).json(ticket);
});

// --- Customer Portal Auth ---
app.get("/api/v1/customer/auth/me", (req, res) => {
  const cookies = parseCookies(req.header("cookie"));
  const session = cookies.rk_customer ? store.customerSessions.get(cookies.rk_customer) : null;
  res.json(session ? { id: session.id, email: session.email, name: session.name } : null);
});

app.post("/api/v1/customer/auth/magic-link", (req, res) => {
  const tenant = resolveRequestTenant(req);
  const email = String(req.body?.email || "").trim().toLowerCase();
  if (!email) {
    res.status(400).json({ message: "Email is required." });
    return;
  }
  const token = `cust_${crypto.randomUUID()}`;
  const session = { id: `cu-${crypto.randomUUID().slice(0, 8)}`, tenantId: tenant.tenantId, email, name: email.split("@")[0] };
  store.customerMagicTokens.set(token, { tenantId: tenant.tenantId, email });
  store.customerSessions.set(token, session);
  res.setHeader("Set-Cookie", `rk_customer=${encodeURIComponent(token)}; Path=/; HttpOnly; SameSite=Lax`);
  res.status(204).end();
});

app.post("/api/v1/customer/auth/consume", (req, res) => {
  const token = String(req.body?.token || "").trim();
  const magic = store.customerMagicTokens.get(token);
  if (!magic) {
    res.status(401).json({ message: "Invalid or expired magic link." });
    return;
  }
  const session = { id: `cu-${crypto.randomUUID().slice(0, 8)}`, tenantId: magic.tenantId, email: magic.email, name: magic.email.split("@")[0] };
  store.customerSessions.set(token, session);
  res.setHeader("Set-Cookie", `rk_customer=${encodeURIComponent(token)}; Path=/; HttpOnly; SameSite=Lax`);
  res.status(204).end();
});

app.post("/api/v1/customer/auth/logout", (req, res) => {
  const cookies = parseCookies(req.header("cookie"));
  if (cookies.rk_customer) store.customerSessions.delete(cookies.rk_customer);
  res.setHeader("Set-Cookie", "rk_customer=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
  res.status(204).end();
});

// --- Widget ---
app.post("/api/v1/widget/answer", (req, res) => {
  const tenant = resolveRequestTenant(req);
  const cookies = parseCookies(req.header("cookie"));
  const customer = cookies.rk_customer ? store.customerSessions.get(cookies.rk_customer) : null;
  const q = String(req.body?.query || "");
  const language = String(req.body?.language || "en");
  const search = store.searchArticles(tenant.tenantId, q, { publicOnly: true, size: 3 });
  const now = new Date().toISOString();
  const conversationId = String(req.body?.conversationId || `conv-${crypto.randomUUID().slice(0, 8)}`);
  const answer = search.results.length
    ? `Based on "${search.results[0].title}": ${search.results[0].excerpt}`
    : "I couldn't find an exact published article for that question. Would you like to contact support?";

  const existing = store.portalConversations.get(conversationId) ?? {
    id: conversationId,
    tenantId: tenant.tenantId,
    customerEmail: customer?.email ?? null,
    language,
    created_at: now,
    messages: []
  };
  if (customer?.email && !existing.customerEmail) existing.customerEmail = customer.email;
  existing.messages.push(
    { id: `msg-${crypto.randomUUID().slice(0, 6)}`, role: "user", content: q, created_at: now },
    { id: `msg-${crypto.randomUUID().slice(0, 6)}`, role: "assistant", content: answer, created_at: now }
  );
  store.portalConversations.set(conversationId, existing);

  res.json({
    answer,
    articleSlugs: search.results.map((r) => r.slug),
    needsHandoff: !search.results.length,
    conversationId
  });
});

// --- Staff Search & Agent Access ---
app.get("/api/v1/search", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  res.json(
    store.searchArticles(tenant.tenantId, String(req.query.q || ""), {
      categoryId: typeof req.query.category === "string" ? req.query.category : undefined,
      page: Number(req.query.page ?? 0)
    })
  );
});

app.get("/api/v1/agent/search", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  res.json(
    store.searchArticles(tenant.tenantId, String(req.query.q || ""), {
      publicOnly: true,
      categoryId: typeof req.query.category === "string" ? req.query.category : undefined,
      page: Number(req.query.page ?? 0)
    })
  );
});

app.get("/api/v1/agent/articles/:id", requireAuth, (req, res) => {
  const detail = store.agentArticleById(getReqAuth(req).tenant.tenantId, req.params.id);
  if (!detail) {
    res.status(404).json({ message: "Published article not found." });
    return;
  }
  res.json(detail);
});

// --- Notifications ---
app.get("/api/v1/notifications", requireAuth, (req, res) => {
  const items = Array.from(store.notifications.values()).filter((n) => n.tenantId === getReqAuth(req).tenant.tenantId);
  res.json({ items, unreadCount: items.filter((i) => !i.read).length });
});
app.post("/api/v1/notifications/:id/read", requireAuth, (req, res) => {
  const n = store.notifications.get(req.params.id);
  if (n) n.read = true;
  res.status(204).end();
});
app.post("/api/v1/notifications/read-all", requireAuth, (_req, res) => {
  for (const n of store.notifications.values()) n.read = true;
  res.status(204).end();
});

// --- Users ---
app.get("/api/v1/users", requireAuth, (req, res) => {
  const { tenant } = getReqAuth(req);
  const users = Array.from(store.users.values())
    .filter((u) => u.tenantId === tenant.tenantId)
    .map(({ password: _p, ...safe }) => safe);
  res.json(users);
});
app.post("/api/v1/users", requireAuth, (req, res) => {
  const { tenant, user: actor } = getReqAuth(req);
  const id = `user-${crypto.randomUUID().slice(0, 8)}`;
  const user = {
    id,
    tenantId: tenant.tenantId,
    email: String(req.body?.email || "").toLowerCase(),
    name: String(req.body?.name || "Team Member"),
    password: String(req.body?.password || "Password123!"),
    status: "ACTIVE" as const,
    roles: Array.isArray(req.body?.roles) ? req.body.roles : ["EDITOR"],
    mfaEnabled: false
  };
  store.users.set(id, user);
  store.recordAudit(actor.id, "USER", id, "CREATE");
  const { password: _p, ...safe } = user;
  res.status(201).json(safe);
});
app.put("/api/v1/users/:id", requireAuth, (req, res) => {
  const u = store.users.get(req.params.id);
  if (!u) {
    res.status(404).json({ message: "User not found." });
    return;
  }
  if (req.body?.name) u.name = req.body.name;
  if (req.body?.email) u.email = req.body.email;
  if (Array.isArray(req.body?.roles)) u.roles = req.body.roles;
  const { password: _p, ...safe } = u;
  res.json(safe);
});
app.post("/api/v1/users/:id/disable", requireAuth, (req, res) => {
  const u = store.users.get(req.params.id);
  if (u) u.status = "DISABLED";
  res.status(204).end();
});
app.post("/api/v1/users/:id/enable", requireAuth, (req, res) => {
  const u = store.users.get(req.params.id);
  if (u) u.status = "ACTIVE";
  res.status(204).end();
});
app.post("/api/v1/users/:id/revoke-sessions", requireAuth, (req, res) => {
  for (const [tok, session] of Array.from(store.sessions.entries())) {
    if (session.userId === req.params.id) {
      store.logout(tok, session.refreshToken);
    }
  }
  res.status(204).end();
});
app.post("/api/v1/users/invite", requireAuth, (req, res) => {
  const { tenant, user: actor } = getReqAuth(req);
  const email = String(req.body?.email || "").toLowerCase();
  if (email && !Array.from(store.users.values()).some((u) => u.email === email && u.tenantId === tenant.tenantId)) {
    const id = `user-${crypto.randomUUID().slice(0, 8)}`;
    store.users.set(id, {
      id,
      tenantId: tenant.tenantId,
      email,
      name: String(req.body?.name || email.split("@")[0]),
      password: "Password123!",
      status: "ACTIVE",
      roles: Array.isArray(req.body?.roles) ? req.body.roles : ["EDITOR"],
      mfaEnabled: false
    });
    store.recordAudit(actor.id, "USER", id, "INVITE");
  }
  res.status(204).end();
});

// --- Synonyms, Webhooks, RAG Keys, Knowledge Sources, Admin Operations ---
app.get("/api/v1/admin/synonyms", requireAuth, (req, res) => {
  res.json(Array.from(store.synonyms.values()).filter((s) => s.tenantId === getReqAuth(req).tenant.tenantId));
});
app.post("/api/v1/admin/synonyms", requireAuth, (req, res) => {
  const id = `syn-${crypto.randomUUID().slice(0, 8)}`;
  const item = {
    id,
    tenantId: getReqAuth(req).tenant.tenantId,
    term: String(req.body?.term || ""),
    synonyms: Array.isArray(req.body?.synonyms) ? req.body.synonyms : []
  };
  store.synonyms.set(id, item);
  res.status(201).json(item);
});
app.delete("/api/v1/admin/synonyms/:id", requireAuth, (req, res) => {
  store.synonyms.delete(req.params.id);
  res.status(204).end();
});

app.get("/api/v1/admin/webhooks", requireAuth, (req, res) => {
  res.json(Array.from(store.webhooks.values()).filter((w) => w.tenantId === getReqAuth(req).tenant.tenantId));
});
app.post("/api/v1/admin/webhooks", requireAuth, (req, res) => {
  const id = `wh-${crypto.randomUUID().slice(0, 8)}`;
  const item = {
    id,
    tenantId: getReqAuth(req).tenant.tenantId,
    url: String(req.body?.url || ""),
    eventTypes: Array.isArray(req.body?.eventTypes) ? req.body.eventTypes : ["ARTICLE_PUBLISHED"],
    active: true,
    secret: `whsec_${crypto.randomUUID().slice(0, 12)}`
  };
  store.webhooks.set(id, item);
  store.webhookDeliveries.set(id, []);
  res.status(201).json(item);
});
app.post("/api/v1/admin/webhooks/:id/deactivate", requireAuth, (req, res) => {
  const w = store.webhooks.get(req.params.id);
  if (w) w.active = false;
  res.status(204).end();
});
app.delete("/api/v1/admin/webhooks/:id", requireAuth, (req, res) => {
  store.webhooks.delete(req.params.id);
  res.status(204).end();
});

app.get("/api/v1/admin/rag-api-keys", requireAuth, (req, res) => {
  const keys = Array.from(store.ragKeys.values())
    .filter((k) => k.tenantId === getReqAuth(req).tenant.tenantId)
    .map(({ key: _k, ...safe }) => safe);
  res.json(keys);
});
app.post("/api/v1/admin/rag-api-keys", requireAuth, (req, res) => {
  const id = `ragkey-${crypto.randomUUID().slice(0, 8)}`;
  const rawKey = `rk_live_${crypto.randomUUID().replace(/-/g, "").slice(0, 24)}`;
  const rec = {
    id,
    tenantId: getReqAuth(req).tenant.tenantId,
    name: String(req.body?.name || "API Key"),
    key: rawKey,
    prefix: rawKey.slice(0, 12),
    status: "ACTIVE" as const,
    scopes: Array.isArray(req.body?.scopes) ? req.body.scopes : ["rag:search", "rag:answer"],
    createdAt: new Date().toISOString(),
    expiresAt: req.body?.expiresAt || null,
    lastUsedAt: null,
    ipAllowlist: null
  };
  store.ragKeys.set(id, rec);
  res.status(201).json({ id: rec.id, name: rec.name, key: rec.key, prefix: rec.prefix, expiresAt: rec.expiresAt, scopes: rec.scopes });
});
app.post("/api/v1/admin/rag-api-keys/:id/revoke", requireAuth, (req, res) => {
  const rec = store.ragKeys.get(req.params.id);
  if (rec) rec.status = "REVOKED";
  res.status(204).end();
});

app.post("/api/v1/rag/search", (req, res) => {
  const tenant = resolveRequestTenant(req);
  const search = store.searchArticles(tenant.tenantId, String(req.body?.query || ""), { publicOnly: true, size: Number(req.body?.topK || 5) });
  res.json({
    query: search.query,
    results: search.results.map((r, idx) => ({
      chunkId: `chunk-${r.articleId}-${idx}`,
      articleId: r.articleId,
      versionId: `ver-${r.articleId}-1`,
      slug: r.slug,
      title: r.title,
      content: r.excerpt,
      score: r.score,
      categoryId: r.categoryId,
      categoryName: null,
      visibility: "PUBLIC",
      publishedAt: new Date().toISOString()
    }))
  });
});

app.post("/api/v1/rag/answer", (req, res) => {
  const tenant = resolveRequestTenant(req);
  store.aiRequestCount += 1;
  const query = String(req.body?.query || "");
  const search = store.searchArticles(tenant.tenantId, query, { publicOnly: true, size: Number(req.body?.topK || 3) });
  const answerId = `ans-${crypto.randomUUID().slice(0, 8)}`;
  const answer = search.results.length
    ? `According to ${search.results[0].title}: ${search.results[0].excerpt}`
    : "No published knowledge matched this question.";
  const citations = search.results.map((r) => ({ articleId: r.articleId, slug: r.slug, title: r.title, score: r.score }));
  const convId = req.body?.conversationId ? String(req.body.conversationId) : null;
  if (convId) {
    const conv = store.ragConversations.get(convId);
    if (conv && Array.isArray(conv.turns)) {
      (conv.turns as Array<Record<string, unknown>>).push({
        answerId,
        query,
        answer,
        citations,
        createdAt: new Date().toISOString()
      });
    }
  }
  res.json({ answerId, conversationId: convId, answer, citations });
});

app.get("/api/v1/knowledge-sources", requireAuth, (req, res) => {
  res.json(Array.from(store.knowledgeSources.values()).filter((k) => k.tenantId === getReqAuth(req).tenant.tenantId));
});
app.post("/api/v1/knowledge-sources/url", requireAuth, (req, res) => {
  const id = `ks-${crypto.randomUUID().slice(0, 8)}`;
  const item = {
    id,
    tenantId: getReqAuth(req).tenant.tenantId,
    type: "URL",
    name: String(req.query.name || req.query.url || "Web Source"),
    url: String(req.query.url || ""),
    mimeType: "text/html",
    language: String(req.query.language || "en"),
    status: "COMPLETED",
    syncIntervalMinutes: Number(req.query.syncIntervalMinutes || 1440),
    nextSyncAt: null,
    lastSyncedAt: new Date().toISOString(),
    discoveredPages: 1,
    lastError: null,
    processingPhase: "INDEXED",
    retryCount: 0,
    maxRetries: 3,
    lastAttemptAt: new Date().toISOString()
  };
  store.knowledgeSources.set(id, item);
  res.status(201).json(item);
});
app.delete("/api/v1/knowledge-sources/:id", requireAuth, (req, res) => {
  store.knowledgeSources.delete(req.params.id);
  res.status(204).end();
});

app.get("/api/v1/knowledge-health", requireAuth, (req, res) => {
  const health = store.getKnowledgeHealth(getReqAuth(req).tenant.tenantId);
  const all = store.listArticles(getReqAuth(req).tenant.tenantId, { size: 500 }).content;
  const issues = all
    .filter((a) => a.status !== "PUBLISHED" || !a.verifiedAt)
    .map((a) => ({
      articleId: a.id,
      title: a.title,
      code: a.status !== "PUBLISHED" ? "UNPUBLISHED" : "UNVERIFIED",
      severity: a.status === "IN_REVIEW" ? "HIGH" : "MEDIUM",
      detail: a.status !== "PUBLISHED" ? `Article is currently ${a.status}.` : "Article has not yet been verified by a reviewer."
    }));
  res.json({
    total: health.total,
    published: health.published,
    drafts: all.filter((a) => a.status === "DRAFT").length,
    inReview: all.filter((a) => a.status === "IN_REVIEW").length,
    searchFailures: 0,
    ragFailures: 0,
    issueCount: issues.length,
    generatedAt: new Date().toISOString(),
    issues
  });
});

app.get("/api/v1/admin/tenant-settings", requireAuth, (req, res) => {
  res.json(store.tenantSettingsByTenant.get(getReqAuth(req).tenant.tenantId) ?? {});
});
app.put("/api/v1/admin/tenant-settings", requireAuth, (req, res) => {
  const tid = getReqAuth(req).tenant.tenantId;
  const updated = { ...(store.tenantSettingsByTenant.get(tid) ?? {}), ...(req.body ?? {}), updatedAt: new Date().toISOString() };
  store.tenantSettingsByTenant.set(tid, updated);
  res.json(updated);
});
app.get("/api/v1/admin/health", requireAuth, (_req, res) => {
  res.json({ status: "UP", database: "IN_MEMORY", search: "IN_MEMORY", queue: "INLINE", uptimeSeconds: Math.round(process.uptime()) });
});
app.get("/api/v1/admin/usage", requireAuth, (_req, res) => {
  const periodStart = new Date().toISOString().slice(0, 7) + "-01";
  res.json([
    { metric: "API_REQUESTS", periodStart, quantity: 14820 },
    { metric: "RAG_QUERIES", periodStart, quantity: 2340 + store.aiRequestCount },
    { metric: "STORAGE_BYTES", periodStart, quantity: 48204100 }
  ]);
});
app.get("/api/v1/admin/audit-logs", requireAuth, (req, res) => {
  res.json(paginate(store.auditLogs, Number(req.query.page ?? 0), Number(req.query.size ?? 50)));
});
app.post("/api/v1/admin/search/reindex", requireAuth, (req, res) => {
  const count = store.listArticles(getReqAuth(req).tenant.tenantId, { size: 500 }).totalElements;
  store.recordAudit(getReqAuth(req).user.id, "SEARCH", "index", "REINDEX");
  res.json({ indexed: count });
});
app.post("/api/v1/admin/search/reconcile", requireAuth, (_req, res) => res.json({ status: "OK", reconciled: store.articles.size }));
app.get("/api/v1/admin/search/index-status", requireAuth, (req, res) => {
  const items = store.listArticles(getReqAuth(req).tenant.tenantId, { size: 100 }).content.map((a) => ({
    articleId: a.id,
    activeVersionId: a.activeVersionId,
    searchStatus: "INDEXED",
    ragStatus: "INDEXED",
    searchIndexedAt: new Date().toISOString(),
    ragIndexedAt: new Date().toISOString(),
    searchError: null,
    ragError: null
  }));
  res.json(items);
});
app.get("/api/v1/admin/search/jobs/pending", requireAuth, (_req, res) => res.json({ pending: 0 }));
app.get("/api/v1/admin/search/jobs", requireAuth, (_req, res) => res.json([]));
app.get("/api/v1/admin/sso", requireAuth, (_req, res) => res.json(store.ssoConnections));
app.get("/api/v1/admin/groups", requireAuth, (_req, res) => res.json(store.groups));
app.get("/api/v1/admin/roles", requireAuth, (_req, res) => res.json(store.customRoles));
app.get("/api/v1/admin/experiments", requireAuth, (_req, res) => res.json(store.experiments));
app.get("/api/v1/admin/scim/tokens", requireAuth, (_req, res) => res.json(store.scimTokens));
app.get("/api/v1/admin/support", requireAuth, (req, res) => {
  res.json(store.supportIntegrationByTenant.get(getReqAuth(req).tenant.tenantId) ?? { provider: "", enabled: false });
});
app.put("/api/v1/admin/support", requireAuth, (req, res) => {
  const tid = getReqAuth(req).tenant.tenantId;
  store.supportIntegrationByTenant.set(tid, req.body ?? {});
  res.json(req.body ?? {});
});

// Graceful JSON fallback for any remaining /api/* endpoints
app.use("/api", (req, res) => {
  if (req.method === "GET") {
    const plural = /s\/?$|cases|runs|logs|items|sources|connections|deliveries|proposals|tickets|history|requests$/i.test(req.path);
    res.json(plural ? [] : {});
    return;
  }
  res.json({ ok: true });
});

// Serve Angular SPA static assets + client-side route fallback
app.use(express.static(DIST_DIR, { maxAge: "1h", index: "index.html" }));
app.get("*", (_req, res) => {
  res.sendFile(INDEX_HTML);
});

app.listen(PORT, HOST, () => {
  console.log(`[ricozknow] Server listening on http://${HOST}:${PORT}`);
});
