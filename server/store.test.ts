import test from "node:test";
import assert from "node:assert/strict";
import { createStore } from "./store.ts";
import {
  buildTranslationDraft,
  calculateReadingStats,
  diffArticleBlocks,
  evaluateArticleQuality,
  normalizeThemePreference,
  parseBrowserInfo,
  rankKnowledgeRequests,
  resolveThemeMode,
  scanSensitiveContent,
  slugifyTitle,
  tiptapToBlocks,
  toggleThemeMode,
  transformWriting,
  validateProductFeedbackInput
} from "./domain-helpers.ts";
import { parseMultipartBuffer } from "./multipart.ts";

test("seeded demo login succeeds and rejects invalid credentials", () => {
  const store = createStore();
  const ok = store.login("demo", "admin@demo.ricozknow.local", "Password123!");
  assert.ok("response" in ok);
  assert.equal(ok.response.email, "admin@demo.ricozknow.local");
  assert.ok(ok.response.roles.includes("ADMIN"));

  const auth = store.authenticateToken(ok.response.accessToken);
  assert.ok(auth);
  assert.equal(auth.tenant.slug, "demo");

  const bad = store.login("demo", "admin@demo.ricozknow.local", "wrong-password");
  assert.ok("error" in bad);
  assert.equal(bad.status, 401);
});

test("register creates new tenant and allows immediate sign-in", () => {
  const store = createStore();
  const reg = store.register({
    organizationName: "Acme Corp",
    tenantSlug: "acme",
    subdomain: "acme",
    email: "owner@acme.io",
    password: "SuperPassword123!",
    name: "Acme Owner"
  });
  assert.equal(reg.status, 201);

  const login = store.login("acme", "owner@acme.io", "SuperPassword123!");
  assert.ok("response" in login);
  assert.equal(login.response.email, "owner@acme.io");
});

test("article workflow transitions from DRAFT to IN_REVIEW to PUBLISHED and ARCHIVED", () => {
  const store = createStore();
  const tenantId = store.defaultTenantId;
  const created = store.createArticle(tenantId, "user-admin-1", {
    title: "Troubleshooting API Rate Limits",
    slug: "troubleshooting-api-rate-limits",
    categoryId: "cat-api",
    content: { blocks: [{ type: "paragraph", text: "Back off exponentially on 429 responses." }] }
  });

  assert.equal(created.status, "DRAFT");
  const versions = store.getVersions(tenantId, created.id);
  assert.ok(versions && versions.length === 1);

  const submitted = store.submitForReview(tenantId, created.id, versions[0].id);
  assert.equal(submitted?.status, "IN_REVIEW");
  assert.equal(store.getArticle(tenantId, created.id)?.status, "IN_REVIEW");

  const published = store.reviewVersion(tenantId, created.id, versions[0].id, "user-reviewer-1", true, "Looks good");
  assert.equal(published?.status, "PUBLISHED");
  assert.equal(store.getArticle(tenantId, created.id)?.status, "PUBLISHED");
  assert.equal(store.getArticle(tenantId, created.id)?.activeVersionNumber, 1);

  const portalMatch = store.portalArticleBySlug(tenantId, "troubleshooting-api-rate-limits");
  assert.ok(portalMatch);
  assert.equal(portalMatch.title, "Troubleshooting API Rate Limits");

  assert.equal(store.archiveArticle(tenantId, created.id), true);
  assert.equal(store.getArticle(tenantId, created.id)?.status, "ARCHIVED");
});

test("categories CRUD and public search with synonym expansion work", () => {
  const store = createStore();
  const tenantId = store.defaultTenantId;

  const initialCount = store.listCategories(tenantId).length;
  const created = store.createCategory(tenantId, {
    name: "Release Notes",
    slug: "release-notes",
    parentId: null,
    sortOrder: 5
  });
  assert.equal(store.listCategories(tenantId).length, initialCount + 1);

  const updated = store.updateCategory(tenantId, created.id, {
    name: "Changelog & Releases",
    slug: "changelog",
    parentId: null,
    sortOrder: 6
  });
  assert.equal(updated?.name, "Changelog & Releases");

  assert.equal(store.deleteCategory(tenantId, created.id), true);
  assert.equal(store.listCategories(tenantId).length, initialCount);

  const search = store.searchArticles(tenantId, "password", { publicOnly: true });
  assert.ok(search.total >= 1);
  assert.equal(search.results[0].slug, "password-reset-procedure");

  const nlSearch = store.searchArticles(tenantId, "How do I reset my password?", { publicOnly: true });
  assert.ok(nlSearch.total >= 1);
  assert.equal(nlSearch.results[0].slug, "password-reset-procedure");
});

test("portal feedback increments article performance metrics", () => {
  const store = createStore();
  const tenantId = store.defaultTenantId;
  const before = store.getArticlePerformance(tenantId).find((p) => p.articleId === "art-getting-started");
  assert.ok(before);

  store.recordFeedback(tenantId, "art-getting-started", true);
  const after = store.getArticlePerformance(tenantId).find((p) => p.articleId === "art-getting-started");
  assert.equal(after?.helpfulCount, before.helpfulCount + 1);
});

test("rollback, schedule publish, and executive analytics nested DTO work", () => {
  const store = createStore();
  const tid = store.defaultTenantId;
  const v2 = store.createDraftVersion(tid, "art-getting-started", "user-admin-1");
  assert.ok(v2);
  store.updateDraft(tid, "art-getting-started", v2.id, { blocks: [{ id: "b1", type: "paragraph", text: "Updated draft text." }] }, "v2 update");
  const scheduled = store.schedulePublish(tid, "art-getting-started", v2.id, "2026-12-01T10:00:00Z", "user-admin-1");
  assert.equal(scheduled?.status, "SCHEDULED");

  const versions = store.getVersions(tid, "art-getting-started")!;
  const rolled = store.rollbackArticle(tid, "art-getting-started", versions[0].id, "user-admin-1");
  assert.ok(rolled);
  assert.equal(rolled.status, "PUBLISHED");
  assert.equal(rolled.versionNumber, 3);

  const exec = store.getExecutiveAnalytics(tid);
  assert.ok(exec.knowledge.articleViews > 0);
  assert.ok(Array.isArray(exec.subscription) && exec.subscription[0].plan_code === "GROWTH");
  assert.ok(Array.isArray(exec.providerCosts) && exec.providerCosts.length > 0);
});

test("sensitive content scanner detects credentials, SSNs, and emails", () => {
  const clean = scanSensitiveContent("Follow the setup guide to configure workspace permissions.");
  assert.equal(clean.length, 0);

  const risky = scanSensitiveContent("Contact ops@example.com with SSN 123-45-6789 and key AKIAIOSFODNN7EXAMPLE");
  assert.ok(risky.some((f) => f.severity === "HIGH" && f.detail.includes("AWS")));
  assert.ok(risky.some((f) => f.severity === "HIGH" && f.detail.includes("Social Security")));
  assert.ok(risky.some((f) => f.severity === "MEDIUM" && f.detail.includes("Email")));
});

test("block diffing, quality evaluation, TipTap conversion, writing assistant, and translation draft work", () => {
  const diffs = diffArticleBlocks(
    [
      { id: "b1", type: "heading", level: 2, text: "Intro" },
      { id: "b2", type: "paragraph", text: "Old text" }
    ],
    [
      { id: "b1", type: "heading", level: 2, text: "Intro" },
      { id: "b2", type: "paragraph", text: "New text" },
      { id: "b3", type: "paragraph", text: "Added block" }
    ]
  );
  assert.equal(diffs.length, 2);
  assert.equal(diffs.find((d) => d.blockId === "b2")?.change, "MODIFIED");
  assert.equal(diffs.find((d) => d.blockId === "b3")?.change, "ADDED");

  const store = createStore();
  const art = store.getArticle(store.defaultTenantId, "art-getting-started")!;
  const ver = store.getVersions(store.defaultTenantId, "art-getting-started")![0];
  const quality = evaluateArticleQuality(art, ver);
  assert.equal(quality.readyForVerification, true);
  assert.ok(quality.score >= 80);

  const converted = tiptapToBlocks({
    type: "doc",
    content: [
      { type: "heading", attrs: { level: 2, blockId: "h1" }, content: [{ type: "text", text: "Title" }] },
      { type: "paragraph", attrs: { blockId: "p1" }, content: [{ type: "text", text: "Body paragraph" }] }
    ]
  });
  assert.equal(converted.blocks.length, 2);
  assert.equal(converted.blocks[0].type, "heading");
  assert.equal(converted.blocks[1].text, "Body paragraph");

  const summary = transformWriting("SUMMARIZE", "First sentence. Second sentence. Third sentence.", "en", "Keep concise");
  assert.ok(summary.includes("Summary (en):"));

  const tr = buildTranslationDraft(art, ver.content.blocks, "fr");
  assert.ok(tr.translatedTitle.startsWith("[FR]"));
});

test("multipart parser extracts fields and files cleanly", () => {
  const boundary = "----TestBoundary123";
  const body = [
    `--${boundary}`,
    'Content-Disposition: form-data; name="articleId"',
    "",
    "art-getting-started",
    `--${boundary}`,
    'Content-Disposition: form-data; name="file"; filename="notes.md"',
    "Content-Type: text/markdown",
    "",
    "# Hello World",
    `--${boundary}--`,
    ""
  ].join("\r\n");

  const parsed = parseMultipartBuffer(Buffer.from(body, "utf8"), `multipart/form-data; boundary=${boundary}`);
  assert.equal(parsed.fields.articleId, "art-getting-started");
  assert.equal(parsed.files.length, 1);
  assert.equal(parsed.files[0].fileName, "notes.md");
  assert.equal(parsed.files[0].data.toString("utf8"), "# Hello World");
});

test("knowledge requests ranking, upvoting, and convert-to-draft work", () => {
  const store = createStore();
  const tid = store.defaultTenantId;

  const created = store.createKnowledgeRequest(tid, {
    title: "Custom OAuth Scopes Guide",
    description: "Explain how to restrict API scopes per integration.",
    priority: "HIGH",
    source: "SUPPORT",
    requesterEmail: "agent@demo.ricozknow.local"
  });
  assert.equal(created.status, "OPEN");
  assert.equal(created.upvotes, 1);

  const upvoted = store.upvoteKnowledgeRequest(tid, created.id);
  assert.equal(upvoted?.upvotes, 2);

  const ranked = rankKnowledgeRequests(store.listKnowledgeRequests(tid));
  assert.equal(ranked[0].priority, "URGENT");
  assert.equal(ranked[ranked.length - 1].status, "COMPLETED");

  const slug = slugifyTitle(created.title);
  assert.equal(slug, "custom-oauth-scopes-guide");

  const converted = store.convertKnowledgeRequestToDraft(tid, created.id, "user-admin-1", slug);
  assert.ok(converted);
  assert.equal(converted.request.status, "DRAFT_CREATED");
  assert.equal(converted.request.linkedArticleId, converted.article.id);
  assert.equal(store.getArticle(tid, converted.article.id)?.status, "DRAFT");
});

test("guided playbooks CRUD, run tracking, and reading stats work", () => {
  const store = createStore();
  const tid = store.defaultTenantId;

  const initial = store.listPlaybooks(tid);
  assert.ok(initial.length >= 2);

  const created = store.createPlaybook(tid, {
    title: "API Rate Limit Check",
    summary: "Diagnose 429 responses quickly.",
    category: "API & Integrations",
    steps: [
      { id: "start", prompt: "Are you seeing HTTP 429?", options: [{ label: "Yes", nextStepId: "done" }] },
      { id: "done", prompt: "Resolution", resolution: { title: "Back off requests", summary: "Retry with exponential backoff." } }
    ]
  });
  assert.equal(store.listPlaybooks(tid).length, initial.length + 1);

  const ran = store.recordPlaybookRun(tid, created.id, "RESOLVED");
  assert.equal(ran?.runsCount, 1);
  assert.equal(ran?.resolvedCount, 1);

  assert.equal(store.deletePlaybook(tid, created.id), true);

  const stats = calculateReadingStats([
    { type: "heading", level: 2, text: "Intro" },
    { type: "paragraph", text: Array.from({ length: 240 }, () => "word").join(" ") }
  ]);
  assert.equal(stats.wordCount, 241);
  assert.equal(stats.readingMinutes, 2);
});

test("theme preference normalization, resolution, and toggling handle light, dark, and system modes", () => {
  assert.equal(normalizeThemePreference("dark"), "dark");
  assert.equal(normalizeThemePreference("LIGHT"), "light");
  assert.equal(normalizeThemePreference("invalid"), "system");
  assert.equal(normalizeThemePreference(null), "system");

  assert.equal(resolveThemeMode("dark", false), "dark");
  assert.equal(resolveThemeMode("light", true), "light");
  assert.equal(resolveThemeMode("system", true), "dark");
  assert.equal(resolveThemeMode("system", false), "light");
  assert.equal(resolveThemeMode(undefined, true), "dark");

  assert.equal(toggleThemeMode("light"), "dark");
  assert.equal(toggleThemeMode("dark"), "light");
});

test("parseBrowserInfo extracts readable browser and OS labels across user agents", () => {
  assert.deepEqual(
    parseBrowserInfo("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"),
    { browser: "Chrome 131.0", os: "macOS" }
  );
  assert.deepEqual(
    parseBrowserInfo("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0"),
    { browser: "Firefox 133.0", os: "Windows" }
  );
  assert.deepEqual(
    parseBrowserInfo("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"),
    { browser: "Edge 131.0", os: "Windows" }
  );
  assert.deepEqual(
    parseBrowserInfo("Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1"),
    { browser: "Safari 18.1", os: "iOS" }
  );
  assert.deepEqual(parseBrowserInfo(""), { browser: "Unknown browser", os: "Unknown OS" });
});

test("validateProductFeedbackInput and store product feedback CRUD work cleanly", () => {
  const rejectedShort = validateProductFeedbackInput({ type: "BUG", title: "Hi", description: "Too short" });
  assert.equal(rejectedShort.ok, false);

  const rejectedEmail = validateProductFeedbackInput({
    type: "FEATURE",
    title: "Valid feature title",
    description: "Detailed feature request description.",
    reporterEmail: "not-an-email"
  });
  assert.equal(rejectedEmail.ok, false);

  const valid = validateProductFeedbackInput({
    type: "BUG",
    severity: "HIGH",
    title: "Filter dropdown resets on pagination",
    description: "Switching from page 1 to page 2 clears the selected status filter.",
    reporterEmail: "QA@Demo.io",
    includeDiagnostics: true,
    pageUrl: "http://localhost:3000/articles?status=DRAFT",
    routePath: "/articles?status=DRAFT",
    userAgent: "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36",
    viewport: "1440x900",
    theme: "dark",
    language: "en-US"
  });
  assert.equal(valid.ok, true);
  if (!valid.ok) return;
  assert.equal(valid.value.reporterEmail, "qa@demo.io");
  assert.equal(valid.value.pageUrl, "http://localhost:3000/articles?status=DRAFT");

  const unsafeUrl = validateProductFeedbackInput({
    type: "BUG",
    title: "Unsafe URL test",
    description: "Verify javascript scheme is stripped.",
    pageUrl: "javascript:alert(1)"
  });
  assert.equal(unsafeUrl.ok, true);
  if (unsafeUrl.ok) {
    assert.equal(unsafeUrl.value.pageUrl, null);
  }

  const optedOut = validateProductFeedbackInput({
    type: "FEATURE",
    title: "No diagnostics attached",
    description: "User unchecked diagnostics sharing.",
    includeDiagnostics: false,
    pageUrl: "http://localhost:3000/dashboard",
    userAgent: "Mozilla/5.0 Chrome/131.0"
  });
  assert.equal(optedOut.ok, true);
  if (optedOut.ok) {
    assert.equal(optedOut.value.pageUrl, null);
    assert.equal(optedOut.value.userAgent, null);
  }

  const store = createStore();
  const tid = store.defaultTenantId;
  const initialCount = store.listProductFeedback(tid).length;
  assert.ok(initialCount >= 3);

  const info = parseBrowserInfo(valid.value.userAgent);
  const created = store.createProductFeedback(tid, {
    ...valid.value,
    reporterUserId: "user-admin-1",
    browser: info.browser,
    os: info.os
  });
  assert.equal(created.status, "NEW");
  assert.equal(created.browser, "Chrome 131.0");
  assert.equal(created.os, "Linux");
  assert.equal(store.listProductFeedback(tid).length, initialCount + 1);
  assert.equal(store.listProductFeedback(tid)[0].id, created.id);

  const triaged = store.updateProductFeedbackStatus(tid, created.id, "TRIAGED");
  assert.equal(triaged?.status, "TRIAGED");
});




