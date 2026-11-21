import crypto from "node:crypto";

export type Role = "ADMIN" | "EDITOR" | "REVIEWER" | "AGENT_VIEWER";
export type ArticleStatus = "DRAFT" | "IN_REVIEW" | "PUBLISHED" | "ARCHIVED";
export type VersionStatus = "DRAFT" | "IN_REVIEW" | "REJECTED" | "APPROVED" | "SCHEDULED" | "PUBLISHED";
export type Visibility = "PUBLIC" | "AUTHENTICATED" | "PRIVATE";

export interface RichTextBlock {
  id?: string;
  type: "heading" | "paragraph" | "list" | "code" | "image";
  text?: string;
  level?: number;
  items?: string[];
  code?: string;
  url?: string;
  alt?: string;
  caption?: string;
}

export interface RichTextContent {
  blocks: RichTextBlock[];
}

export interface TenantRecord {
  tenantId: string;
  slug: string;
  subdomain: string;
  name: string;
  portalTitle: string;
  logoUrl: string;
  primaryColor: string;
  customDomain: string | null;
  customDomainVerified: boolean;
  customDomainSslStatus: string;
  supportEmail: string | null;
  supportedLanguages: string;
}

export interface UserRecord {
  id: string;
  tenantId: string;
  email: string;
  name: string;
  password: string;
  status: "ACTIVE" | "DISABLED";
  roles: Role[];
  mfaEnabled: boolean;
}

export interface CategoryRecord {
  id: string;
  tenantId: string;
  name: string;
  slug: string;
  parentId: string | null;
  sortOrder: number;
}

export interface ArticleVersionRecord {
  id: string;
  articleId: string;
  versionNumber: number;
  content: RichTextContent;
  changeSummary: string | null;
  status: VersionStatus;
  createdBy: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  publishedAt: string | null;
  expiresAt: string | null;
}

export interface ArticleRecord {
  id: string;
  tenantId: string;
  slug: string;
  title: string;
  status: ArticleStatus;
  visibility: Visibility;
  categoryId: string | null;
  activeVersionId: string | null;
  activeVersionNumber: number | null;
  language: string;
  articleType: string;
  pinned: boolean;
  featured: boolean;
  tags: string[];
  relatedArticleIds: string[];
  scheduledPublishAt: string | null;
  scheduledUnpublishAt: string | null;
  ownerId: string | null;
  reviewIntervalDays: number;
  nextReviewAt: string | null;
  verifiedAt: string | null;
  verifiedBy: string | null;
  views: number;
  helpfulCount: number;
  unhelpfulCount: number;
}

export interface SynonymRecord {
  id: string;
  tenantId: string;
  term: string;
  synonyms: string[];
}

export interface WebhookSubscriptionRecord {
  id: string;
  tenantId: string;
  url: string;
  eventTypes: string[];
  active: boolean;
  secret: string;
}

export interface RagApiKeyRecord {
  id: string;
  tenantId: string;
  name: string;
  key: string;
  prefix: string;
  status: "ACTIVE" | "REVOKED";
  scopes: string[];
  createdAt: string;
  expiresAt: string | null;
  lastUsedAt: string | null;
  ipAllowlist: string | null;
}

export interface NotificationRecord {
  id: string;
  tenantId: string;
  type: string;
  title: string;
  message: string;
  articleId: string | null;
  versionId: string | null;
  read: boolean;
  createdAt: string;
}

export interface KnowledgeSourceRecord {
  id: string;
  tenantId: string;
  type: string;
  name: string;
  url: string | null;
  mimeType: string | null;
  language: string;
  status: string;
  syncIntervalMinutes: number | null;
  nextSyncAt: string | null;
  lastSyncedAt: string | null;
  discoveredPages: number;
  lastError: string | null;
  processingPhase: string | null;
  retryCount: number;
  maxRetries: number;
  lastAttemptAt: string | null;
}

export interface SessionRecord {
  token: string;
  refreshToken: string;
  userId: string;
  tenantId: string;
  createdAt: string;
  expiresAt: string;
}

export interface BlockCommentRecord {
  id: string;
  articleId: string;
  versionId: string;
  blockId: string;
  authorId: string;
  comment: string;
  parentId: string | null;
  resolved: boolean;
  createdAt: string;
  resolvedAt: string | null;
}

export interface ReviewHistoryRecord {
  id: string;
  articleId: string;
  versionId: string;
  reviewerId: string;
  decision: string;
  comment: string | null;
  createdAt: string;
}

export interface ProductFeedbackRecord {
  id: string;
  tenantId: string;
  type: "BUG" | "FEATURE";
  severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  status: "NEW" | "TRIAGED" | "IN_PROGRESS" | "RESOLVED" | "WONT_FIX";
  title: string;
  description: string;
  reporterEmail: string | null;
  reporterUserId: string | null;
  includeDiagnostics: boolean;
  pageUrl: string | null;
  routePath: string | null;
  browser: string | null;
  os: string | null;
  userAgent: string | null;
  viewport: string | null;
  theme: "light" | "dark" | null;
  language: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface LoginResponseDto {
  accessToken: string;
  userId: string;
  tenantId: string;
  email: string;
  roles: string[];
}

export interface PageDto<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

function uid(prefix: string): string {
  return `${prefix}-${crypto.randomUUID().slice(0, 8)}`;
}

function normalizeBlocks(content: unknown): RichTextContent {
  if (content && typeof content === "object" && Array.isArray((content as { blocks?: unknown }).blocks)) {
    return { blocks: (content as { blocks: RichTextBlock[] }).blocks };
  }
  if (Array.isArray(content)) {
    return { blocks: content as RichTextBlock[] };
  }
  if (typeof content === "string" && content.trim()) {
    return { blocks: [{ type: "paragraph", text: content.trim() }] };
  }
  return { blocks: [] };
}

function extractPlainText(content: RichTextContent): string {
  return content.blocks
    .map((block) => {
      if (block.text) return block.text;
      if (block.code) return block.code;
      if (Array.isArray(block.items)) return block.items.join(" ");
      return "";
    })
    .filter(Boolean)
    .join(" ");
}

export function paginate<T>(items: T[], page = 0, size = 10): PageDto<T> {
  const safeSize = Math.max(1, Number.isFinite(size) ? size : 10);
  const safePage = Math.max(0, Number.isFinite(page) ? page : 0);
  const totalElements = items.length;
  const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / safeSize);
  const start = safePage * safeSize;
  const content = items.slice(start, start + safeSize);
  return {
    content,
    totalElements,
    totalPages,
    number: safePage,
    size: safeSize,
    first: safePage === 0,
    last: totalPages === 0 || safePage >= totalPages - 1
  };
}

export class RicozKnowStore {
  readonly tenants = new Map<string, TenantRecord>();
  readonly users = new Map<string, UserRecord>();
  readonly sessions = new Map<string, SessionRecord>();
  readonly refreshIndex = new Map<string, SessionRecord>();
  readonly categories = new Map<string, CategoryRecord>();
  readonly articles = new Map<string, ArticleRecord>();
  readonly versions = new Map<string, ArticleVersionRecord[]>();
  readonly comments = new Map<string, BlockCommentRecord[]>();
  readonly reviewHistory = new Map<string, ReviewHistoryRecord[]>();
  readonly synonyms = new Map<string, SynonymRecord>();
  readonly webhooks = new Map<string, WebhookSubscriptionRecord>();
  readonly ragKeys = new Map<string, RagApiKeyRecord>();
  readonly notifications = new Map<string, NotificationRecord>();
  readonly knowledgeSources = new Map<string, KnowledgeSourceRecord>();
  readonly savedSearches = new Map<string, Record<string, unknown>>();
  readonly supportTickets: Array<Record<string, unknown>> = [];
  readonly reusableBlocks: Array<{ id: string; name: string; block: Record<string, unknown> }> = [];
  readonly customTemplates: Array<{ id: string; key: string; name: string; description: string | null; content: { blocks: Record<string, unknown>[] } }> = [];
  readonly auditLogs: Array<Record<string, unknown>> = [];
  readonly groups: Array<Record<string, unknown>> = [];
  readonly customRoles: Array<Record<string, unknown>> = [];
  readonly experiments: Array<Record<string, unknown>> = [];
  readonly ssoConnections: Array<Record<string, unknown>> = [];
  readonly scimTokens: Array<Record<string, unknown>> = [];
  readonly mfaRecoveryRequests: Array<Record<string, unknown>> = [];
  readonly aiAgents: Array<Record<string, unknown>> = [];
  readonly aiAgentRuns = new Map<string, Record<string, unknown>>();
  readonly evalCases: Array<Record<string, unknown>> = [];
  readonly evalRuns: Array<Record<string, unknown>> = [];
  readonly enterpriseEvalCases: Array<Record<string, unknown>> = [];
  readonly ragEvalCases: Array<Record<string, unknown>> = [];
  readonly ragEvalRuns: Array<Record<string, unknown>> = [];
  readonly ragConversations = new Map<string, Record<string, unknown>>();
  readonly ragFeedbacks: Array<Record<string, unknown>> = [];
  readonly customerSessions = new Map<string, { id: string; tenantId: string; email: string; name: string | null }>();
  readonly customerMagicTokens = new Map<string, { tenantId: string; email: string }>();
  readonly portalConversations = new Map<string, { id: string; tenantId: string; customerEmail: string | null; language: string; created_at: string; messages: Array<{ id: string; role: string; content: string; created_at: string }> }>();
  readonly automationRules = new Map<string, Record<string, unknown>>();
  readonly automationRuns = new Map<string, Array<Record<string, unknown>>>();
  readonly translationProposals = new Map<string, Record<string, unknown>>();
  readonly connectorOauth = new Map<string, { provider: string; connected: boolean; configured: boolean }>();
  readonly connectorSources = new Map<string, Record<string, unknown>>();
  readonly connectorRuns = new Map<string, Array<Record<string, unknown>>>();
  readonly connectorDocuments: Array<{ id: string; sourceId: string; provider: string; title: string; excerpt: string; url: string; modifiedAt: string }> = [];
  readonly graphProposals = new Map<string, Record<string, unknown>>();
  readonly graphEdges: Array<{ from_article_id: string; to_article_id: string; relation: string }> = [];
  readonly developerReviewRequests = new Map<string, Record<string, unknown>>();
  readonly developerUsage: Array<Record<string, unknown>> = [];
  readonly knowledgeGapTickets: Array<Record<string, unknown>> = [];
  readonly verificationHistory = new Map<string, Array<{ action: string; actorId: string; details: string; at: string }>>();
  readonly assets = new Map<string, { id: string; tenantId: string; articleId: string | null; fileName: string; mimeType: string; sizeBytes: number; data: Buffer; createdAt: string }>();
  readonly webhookDeliveries = new Map<string, Array<Record<string, unknown>>>();
  readonly webhookDlq: Array<Record<string, unknown>> = [];
  readonly customDomainChallenges = new Map<string, { domain: string; recordName: string; recordValue: string; token: string }>();
  readonly collaborationDocs = new Map<string, { tiptapDoc: unknown; updatedAt: string }>();
  readonly collaborationPresence = new Map<string, Map<string, { userId: string; email: string; name: string; lastSeenAt: number }>>();
  readonly billingPlans: Array<Record<string, unknown>> = [];
  readonly billingInvoices: Array<Record<string, unknown>> = [];
  readonly billingSubscriptions = new Map<string, Record<string, unknown>>();
  readonly aiCostEvents: Array<Record<string, unknown>> = [];
  readonly knowledgeRequests = new Map<string, {
    id: string;
    tenantId: string;
    title: string;
    description: string;
    categoryId: string | null;
    priority: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
    status: "OPEN" | "IN_PROGRESS" | "DRAFT_CREATED" | "COMPLETED" | "DECLINED";
    source: "INTERNAL" | "SUPPORT" | "CUSTOMER";
    requesterEmail: string;
    assigneeEmail: string | null;
    upvotes: number;
    linkedArticleId: string | null;
    createdAt: string;
    updatedAt: string;
  }>();
  readonly playbooks = new Map<string, {
    id: string;
    tenantId: string;
    title: string;
    summary: string;
    category: string;
    published: boolean;
    runsCount: number;
    resolvedCount: number;
    updatedAt: string;
    steps: Array<{
      id: string;
      prompt: string;
      guidance?: string;
      options?: Array<{ label: string; nextStepId: string }>;
      resolution?: {
        title: string;
        summary: string;
        articleSlug?: string;
        actionLabel?: string;
      };
    }>;
  }>();
  readonly productFeedback = new Map<string, ProductFeedbackRecord>();
  enterpriseSearchCount = 42;
  enterpriseSearchUnanswered = 3;
  aiRequestCount = 1420;
  tenantSettingsByTenant = new Map<string, Record<string, unknown>>();
  supportIntegrationByTenant = new Map<string, Record<string, unknown>>();

  defaultTenantId = "tenant-demo";

  constructor() {
    this.seed();
  }

  private seed(): void {
    const now = new Date();
    const isoNow = now.toISOString();
    const daysFromNow = (days: number) => new Date(now.getTime() + days * 86400000).toISOString();

    const demoTenant: TenantRecord = {
      tenantId: this.defaultTenantId,
      slug: "demo",
      subdomain: "demo",
      name: "RicozKnow Demo",
      portalTitle: "RicozKnow Help Center",
      logoUrl: "",
      primaryColor: "#0f172a",
      customDomain: null,
      customDomainVerified: false,
      customDomainSslStatus: "PENDING",
      supportEmail: "support@demo.ricozknow.local",
      supportedLanguages: "en,es,fr"
    };
    this.tenants.set(demoTenant.tenantId, demoTenant);

    const adminUser: UserRecord = {
      id: "user-admin-1",
      tenantId: demoTenant.tenantId,
      email: "admin@demo.ricozknow.local",
      name: "Alex Rivera",
      password: "Password123!",
      status: "ACTIVE",
      roles: ["ADMIN", "EDITOR", "REVIEWER", "AGENT_VIEWER"],
      mfaEnabled: false
    };
    const editorUser: UserRecord = {
      id: "user-editor-1",
      tenantId: demoTenant.tenantId,
      email: "editor@demo.ricozknow.local",
      name: "Jordan Lee",
      password: "Password123!",
      status: "ACTIVE",
      roles: ["EDITOR"],
      mfaEnabled: false
    };
    const reviewerUser: UserRecord = {
      id: "user-reviewer-1",
      tenantId: demoTenant.tenantId,
      email: "reviewer@demo.ricozknow.local",
      name: "Morgan Patel",
      password: "Password123!",
      status: "ACTIVE",
      roles: ["REVIEWER"],
      mfaEnabled: false
    };
    for (const u of [adminUser, editorUser, reviewerUser]) {
      this.users.set(u.id, u);
    }

    const categories: CategoryRecord[] = [
      { id: "cat-getting-started", tenantId: demoTenant.tenantId, name: "Getting Started", slug: "getting-started", parentId: null, sortOrder: 1 },
      { id: "cat-security", tenantId: demoTenant.tenantId, name: "Security & Access", slug: "security-access", parentId: null, sortOrder: 2 },
      { id: "cat-api", tenantId: demoTenant.tenantId, name: "API & Integrations", slug: "api-integrations", parentId: null, sortOrder: 3 },
      { id: "cat-billing", tenantId: demoTenant.tenantId, name: "Billing & Plans", slug: "billing-plans", parentId: null, sortOrder: 4 }
    ];
    for (const c of categories) {
      this.categories.set(c.id, c);
    }

    const seedArticle = (opts: {
      id: string;
      slug: string;
      title: string;
      status: ArticleStatus;
      visibility: Visibility;
      categoryId: string;
      pinned?: boolean;
      featured?: boolean;
      tags?: string[];
      views?: number;
      helpfulCount?: number;
      unhelpfulCount?: number;
      expiresAt?: string | null;
      blocks: RichTextBlock[];
      versionStatus?: VersionStatus;
      versionNumber?: number;
    }) => {
      const versionId = `ver-${opts.id}-1`;
      const versionStatus: VersionStatus =
        opts.versionStatus ??
        (opts.status === "PUBLISHED" ? "PUBLISHED" : opts.status === "IN_REVIEW" ? "IN_REVIEW" : "DRAFT");
      const versionNumber = opts.versionNumber ?? 1;
      const article: ArticleRecord = {
        id: opts.id,
        tenantId: demoTenant.tenantId,
        slug: opts.slug,
        title: opts.title,
        status: opts.status,
        visibility: opts.visibility,
        categoryId: opts.categoryId,
        activeVersionId: opts.status === "PUBLISHED" ? versionId : null,
        activeVersionNumber: opts.status === "PUBLISHED" ? versionNumber : null,
        language: "en",
        articleType: "GUIDE",
        pinned: Boolean(opts.pinned),
        featured: Boolean(opts.featured),
        tags: opts.tags ?? [],
        relatedArticleIds: [],
        scheduledPublishAt: null,
        scheduledUnpublishAt: null,
        ownerId: adminUser.id,
        reviewIntervalDays: 90,
        nextReviewAt: daysFromNow(30),
        verifiedAt: opts.status === "PUBLISHED" ? isoNow : null,
        verifiedBy: opts.status === "PUBLISHED" ? reviewerUser.id : null,
        views: opts.views ?? 42,
        helpfulCount: opts.helpfulCount ?? 18,
        unhelpfulCount: opts.unhelpfulCount ?? 2
      };
      this.articles.set(article.id, article);
      this.versions.set(article.id, [
        {
          id: versionId,
          articleId: article.id,
          versionNumber,
          content: { blocks: opts.blocks },
          changeSummary: "Initial governed release",
          status: versionStatus,
          createdBy: editorUser.id,
          reviewedBy: versionStatus === "PUBLISHED" ? reviewerUser.id : null,
          reviewedAt: versionStatus === "PUBLISHED" ? isoNow : null,
          publishedAt: versionStatus === "PUBLISHED" ? isoNow : null,
          expiresAt: opts.expiresAt ?? null
        }
      ]);
      if (versionStatus === "PUBLISHED") {
        this.reviewHistory.set(article.id, [
          {
            id: uid("rev"),
            articleId: article.id,
            versionId,
            reviewerId: reviewerUser.id,
            decision: "APPROVED",
            comment: "Verified accuracy and tone.",
            createdAt: isoNow
          }
        ]);
      }
    };

    seedArticle({
      id: "art-getting-started",
      slug: "getting-started-with-ricozknow",
      title: "Getting Started with Your Knowledge Base",
      status: "PUBLISHED",
      visibility: "PUBLIC",
      categoryId: "cat-getting-started",
      featured: true,
      pinned: true,
      tags: ["onboarding", "setup", "workspace"],
      views: 312,
      helpfulCount: 94,
      unhelpfulCount: 4,
      blocks: [
        { id: "b1", type: "heading", level: 2, text: "Welcome to RicozKnow" },
        { id: "b2", type: "paragraph", text: "RicozKnow gives teams a single governed workspace to author, review, publish, and serve trusted answers to customers and AI agents." },
        { id: "b3", type: "heading", level: 3, text: "Core Workflow" },
        {
          id: "b4",
          type: "list",
          items: [
            "Organize topics with hierarchical categories.",
            "Draft articles using structured content blocks or the collaborative editor.",
            "Submit drafts to the review queue before publishing live.",
            "Expose approved content via the public Help Center, search, and RAG APIs."
          ]
        }
      ]
    });

    seedArticle({
      id: "art-password-reset",
      slug: "password-reset-procedure",
      title: "Password Reset and Account Recovery Procedure",
      status: "PUBLISHED",
      visibility: "PUBLIC",
      categoryId: "cat-security",
      featured: true,
      tags: ["security", "password", "mfa", "recovery"],
      views: 248,
      helpfulCount: 71,
      unhelpfulCount: 3,
      blocks: [
        { id: "b1", type: "heading", level: 2, text: "Self-Service Password Reset" },
        { id: "b2", type: "paragraph", text: "Users who forget their workspace password can trigger a time-limited recovery link directly from the Sign In page." },
        {
          id: "b3",
          type: "list",
          items: [
            "Open the Sign In page and select Forgot password.",
            "Enter your organization tenant slug and work email address.",
            "Follow the verification link and set a new password with at least 12 characters."
          ]
        },
        { id: "b4", type: "heading", level: 3, text: "MFA Recovery" },
        { id: "b5", type: "paragraph", text: "If an authenticator device is lost, use one of your single-use recovery codes or request an administrator MFA recovery ticket from Security settings." }
      ]
    });

    seedArticle({
      id: "art-agent-api",
      slug: "connecting-ai-agents-with-rag-api",
      title: "Connecting AI Agents with the Governed RAG API",
      status: "PUBLISHED",
      visibility: "PUBLIC",
      categoryId: "cat-api",
      pinned: true,
      tags: ["api", "rag", "ai-agent", "integration"],
      views: 185,
      helpfulCount: 52,
      unhelpfulCount: 2,
      blocks: [
        { id: "b1", type: "heading", level: 2, text: "Read-Only Agent Retrieval" },
        { id: "b2", type: "paragraph", text: "Generate a scoped RAG API key in Administration to let copilots and support bots query only approved, published versions." },
        {
          id: "b3",
          type: "code",
          text: `curl -X POST /api/v1/rag/search \\\n  -H "X-API-Key: rk_demo_live_key" \\\n  -H "Content-Type: application/json" \\\n  -d '{"query":"How do I reset my password?","topK":5}'`
        }
      ]
    });

    seedArticle({
      id: "art-sso-scim",
      slug: "configuring-sso-and-scim-provisioning",
      title: "Configuring SAML SSO and SCIM Provisioning",
      status: "PUBLISHED",
      visibility: "PUBLIC",
      categoryId: "cat-security",
      tags: ["sso", "saml", "oidc", "scim"],
      views: 129,
      helpfulCount: 38,
      unhelpfulCount: 5,
      expiresAt: daysFromNow(8),
      blocks: [
        { id: "b1", type: "heading", level: 2, text: "Enterprise Identity Integration" },
        { id: "b2", type: "paragraph", text: "Connect Okta, Microsoft Entra ID, or Google Workspace via OIDC or SAML 2.0 from the Enterprise console." },
        { id: "b3", type: "paragraph", text: "Enable SCIM bearer tokens to synchronize group memberships and automatically disable departed employees." }
      ]
    });

    seedArticle({
      id: "art-billing-faq",
      slug: "billing-and-subscription-faq",
      title: "Billing, Usage Quotas, and Subscription FAQ",
      status: "IN_REVIEW",
      visibility: "PUBLIC",
      categoryId: "cat-billing",
      tags: ["billing", "plans", "invoices"],
      views: 64,
      helpfulCount: 15,
      unhelpfulCount: 6,
      expiresAt: daysFromNow(-4),
      blocks: [
        { id: "b1", type: "heading", level: 2, text: "Plan Tiers & Metering" },
        { id: "b2", type: "paragraph", text: "Workspaces track monthly API requests, RAG queries, active seats, and storage quotas in real time." }
      ]
    });

    seedArticle({
      id: "art-webhook-guide",
      slug: "webhook-events-and-signatures",
      title: "Webhook Events and Payload Verification",
      status: "DRAFT",
      visibility: "PRIVATE",
      categoryId: "cat-api",
      tags: ["webhooks", "automation"],
      views: 12,
      helpfulCount: 4,
      unhelpfulCount: 0,
      blocks: [
        { id: "b1", type: "heading", level: 2, text: "Subscribing to Article Lifecycle Events" },
        { id: "b2", type: "paragraph", text: "Subscribe downstream search pipelines or Slack bots to ARTICLE_PUBLISHED and ARTICLE_ARCHIVED events." }
      ]
    });

    this.articles.get("art-getting-started")!.relatedArticleIds = ["art-password-reset", "art-agent-api"];
    this.articles.get("art-password-reset")!.relatedArticleIds = ["art-sso-scim"];

    const synonyms: SynonymRecord[] = [
      { id: "syn-1", tenantId: demoTenant.tenantId, term: "password", synonyms: ["passcode", "credentials", "login reset"] },
      { id: "syn-2", tenantId: demoTenant.tenantId, term: "sso", synonyms: ["single sign-on", "saml", "oidc"] }
    ];
    for (const s of synonyms) this.synonyms.set(s.id, s);

    const ragKey: RagApiKeyRecord = {
      id: "ragkey-1",
      tenantId: demoTenant.tenantId,
      name: "Support Copilot Key",
      key: "rk_demo_live_key",
      prefix: "rk_demo_",
      status: "ACTIVE",
      scopes: ["rag:search", "rag:answer"],
      createdAt: isoNow,
      expiresAt: null,
      lastUsedAt: isoNow,
      ipAllowlist: null
    };
    this.ragKeys.set(ragKey.id, ragKey);

    const webhook: WebhookSubscriptionRecord = {
      id: "wh-1",
      tenantId: demoTenant.tenantId,
      url: "https://hooks.example.com/ricozknow",
      eventTypes: ["ARTICLE_PUBLISHED", "ARTICLE_ARCHIVED"],
      active: true,
      secret: "whsec_demo_0123456789"
    };
    this.webhooks.set(webhook.id, webhook);

    const notif: NotificationRecord = {
      id: "notif-1",
      tenantId: demoTenant.tenantId,
      type: "REVIEW_REQUEST",
      title: "Article awaiting review",
      message: "Billing, Usage Quotas, and Subscription FAQ was submitted for review.",
      articleId: "art-billing-faq",
      versionId: "ver-art-billing-faq-1",
      read: false,
      createdAt: isoNow
    };
    this.notifications.set(notif.id, notif);

    const ks: KnowledgeSourceRecord = {
      id: "ks-1",
      tenantId: demoTenant.tenantId,
      type: "WEBSITE",
      name: "Product Documentation Site",
      url: "https://docs.example.com",
      mimeType: "text/html",
      language: "en",
      status: "COMPLETED",
      syncIntervalMinutes: 1440,
      nextSyncAt: daysFromNow(1),
      lastSyncedAt: isoNow,
      discoveredPages: 42,
      lastError: null,
      processingPhase: "INDEXED",
      retryCount: 0,
      maxRetries: 3,
      lastAttemptAt: isoNow
    };
    this.knowledgeSources.set(ks.id, ks);

    this.reusableBlocks.push({
      id: "blk-1",
      name: "Support Escalation Notice",
      block: { type: "paragraph", text: "Need more help? Reach out to support@demo.ricozknow.local with your workspace slug." }
    });

    this.customTemplates.push({
      id: "tpl-1",
      key: "troubleshooting",
      name: "Troubleshooting Runbook",
      description: "Standardized incident and symptom resolution structure.",
      content: {
        blocks: [
          { type: "heading", level: 2, text: "Symptoms" },
          { type: "paragraph", text: "Describe what the user observes." },
          { type: "heading", level: 2, text: "Resolution Steps" },
          { type: "list", items: ["Verify configuration", "Re-run verification", "Confirm expected behavior"] }
        ]
      }
    });

    this.tenantSettingsByTenant.set(demoTenant.tenantId, {
      tenantId: demoTenant.tenantId,
      timezone: "UTC",
      defaultLanguage: "en",
      maxUsers: 100,
      maxStorageBytes: 10737418240,
      maxApiRequestsMonthly: 500000,
      maxRagQueriesMonthly: 100000,
      maxAiCostCentsMonthly: 50000,
      settingsJson: JSON.stringify({ requireIndependentReview: false }),
      createdAt: isoNow,
      updatedAt: isoNow
    });

    this.groups.push({ id: "grp-1", name: "Knowledge Maintainers", externalId: "okta-km-01", members: [adminUser, editorUser] });
    this.customRoles.push(
      { id: "role-admin", name: "ADMIN", custom: false, permissions: "ALL" },
      { id: "role-editor", name: "EDITOR", custom: false, permissions: "ARTICLES_WRITE,CATEGORIES_WRITE" },
      { id: "role-reviewer", name: "REVIEWER", custom: false, permissions: "ARTICLES_REVIEW" },
      { id: "role-agent", name: "AGENT_VIEWER", custom: false, permissions: "AGENT_READ" }
    );
    this.experiments.push({
      id: "exp-1",
      key: "semantic-rerank",
      name: "Semantic Reranking Boost",
      target: "PORTAL_SEARCH",
      enabled: true,
      trafficPercent: 50,
      variantsJson: JSON.stringify({ control: 50, treatment: 50 })
    });
    this.aiAgents.push(
      {
        id: "agent-1",
        name: "Tier-1 Support Copilot",
        instructions: "Answer customer questions strictly using published help center articles with citations.",
        enabled: true,
        tool: "RAG_SEARCH",
        sourceScope: "PUBLIC_ARTICLES",
        requiresApproval: false,
        createdAt: isoNow
      },
      {
        id: "agent-2",
        name: "Security Policy Advisor",
        instructions: "Draft answers for SSO, SAML, SCIM, and MFA questions and require human sign-off before sending.",
        enabled: true,
        tool: "ENTERPRISE_SEARCH",
        sourceScope: "ALL_GOVERNED",
        requiresApproval: true,
        createdAt: isoNow
      }
    );

    this.graphEdges.push(
      { from_article_id: "art-getting-started", to_article_id: "art-password-reset", relation: "LINKS_TO" },
      { from_article_id: "art-getting-started", to_article_id: "art-agent-api", relation: "REFERENCES" },
      { from_article_id: "art-password-reset", to_article_id: "art-sso-scim", relation: "PREREQUISITE" }
    );

    this.graphProposals.set("prop-1", {
      id: "prop-1",
      category: "MISSING_LINK",
      status: "OPEN",
      explanation: "Password Reset references MFA recovery; link directly to Configuring SAML SSO and SCIM Provisioning.",
      article_id: "art-password-reset",
      related_article_id: "art-sso-scim"
    });
    this.graphProposals.set("prop-2", {
      id: "prop-2",
      category: "STALE_CONTENT",
      status: "OPEN",
      explanation: "Billing, Usage Quotas, and Subscription FAQ has passed its scheduled expiry window and needs review.",
      article_id: "art-billing-faq",
      related_article_id: null
    });

    this.evalCases.push(
      { id: "eval-1", question: "How do I reset my password?", expectedArticleId: "art-password-reset", expectedPhrase: "recovery link" },
      { id: "eval-2", question: "How do I authenticate RAG API calls?", expectedArticleId: "art-agent-api", expectedPhrase: "X-API-Key" }
    );
    this.enterpriseEvalCases.push(
      { id: "eeval-1", question: "SAML SSO Okta setup", expectedIds: ["art-sso-scim"] },
      { id: "eeval-2", question: "reset workspace password", expectedIds: ["art-password-reset"] }
    );
    this.ragEvalCases.push(
      { id: "reval-1", question: "How do I reset a forgotten password?", expectedArticleIds: ["art-password-reset"], expectedAnswer: "Use Forgot password on Sign In.", enabled: true }
    );
    this.ragEvalRuns.push({
      id: "rrun-1",
      status: "COMPLETED",
      totalCases: 1,
      passedCases: 1,
      hitRate: 1,
      averageLatencyMs: 38,
      createdAt: isoNow
    });

    this.knowledgeGapTickets.push(
      {
        id: "gap-1",
        query: "scim group mapping azure ad",
        volumeCount: 28,
        zeroResultCount: 14,
        unsuccessfulCount: 19,
        gapScore: 86,
        status: "OPEN",
        suggestion: null,
        draftArticleId: null
      },
      {
        id: "gap-2",
        query: "custom domain ssl certificate renewal",
        volumeCount: 19,
        zeroResultCount: 9,
        unsuccessfulCount: 12,
        gapScore: 74,
        status: "OPEN",
        suggestion: null,
        draftArticleId: null
      },
      {
        id: "gap-3",
        query: "webhook hmac signature verification nodejs",
        volumeCount: 15,
        zeroResultCount: 6,
        unsuccessfulCount: 9,
        gapScore: 68,
        status: "OPEN",
        suggestion: null,
        draftArticleId: null
      }
    );

    const providers = ["confluence", "notion", "github", "slack", "google-drive", "jira", "sharepoint"];
    for (const provider of providers) {
      this.connectorOauth.set(provider, {
        provider,
        connected: provider === "confluence" || provider === "github",
        configured: true
      });
    }

    const seedSourceId = "csrc-confluence-eng";
    this.connectorSources.set(seedSourceId, {
      id: seedSourceId,
      provider: "confluence",
      remoteId: "ENG-SPACE",
      name: "Engineering Runbooks (Confluence)",
      status: "ACTIVE",
      documents: 2,
      intervalMinutes: 60,
      lastSyncedAt: isoNow,
      nextSyncAt: daysFromNow(1),
      lastError: null
    });
    this.connectorRuns.set(seedSourceId, [
      {
        id: "crun-1",
        startedAt: isoNow,
        state: "COMPLETED",
        inserted: 2,
        updated: 0,
        deleted: 0,
        error: null
      }
    ]);
    this.connectorDocuments.push(
      {
        id: "cdoc-1",
        sourceId: seedSourceId,
        provider: "confluence",
        title: "Incident Escalation Runbook",
        excerpt: "Follow the P1 incident checklist: declare severity in #incidents, page on-call, and post status updates every 30 minutes.",
        url: "https://confluence.example.com/spaces/ENG/pages/incident-escalation",
        modifiedAt: isoNow
      },
      {
        id: "cdoc-2",
        sourceId: seedSourceId,
        provider: "confluence",
        title: "SCIM Directory Sync Troubleshooting",
        excerpt: "Verify bearer token expiration and confirm Okta or Entra ID group push attributes match tenant group external IDs.",
        url: "https://confluence.example.com/spaces/ENG/pages/scim-troubleshooting",
        modifiedAt: isoNow
      }
    );

    this.billingPlans.push(
      { code: "STARTER", name: "Starter", monthlyPriceMinor: 4900, currency: "USD", maxSeats: 10, maxArticles: 250, maxConnectors: 2, maxAiRequests: 5000, contactSales: false, stripe_configured: true },
      { code: "GROWTH", name: "Growth", monthlyPriceMinor: 19900, currency: "USD", maxSeats: 50, maxArticles: 2500, maxConnectors: 8, maxAiRequests: 50000, contactSales: false, stripe_configured: true },
      { code: "ENTERPRISE", name: "Enterprise", monthlyPriceMinor: 59900, currency: "USD", maxSeats: 250, maxArticles: 25000, maxConnectors: 25, maxAiRequests: 250000, contactSales: true, stripe_configured: true }
    );
    this.billingSubscriptions.set(demoTenant.tenantId, {
      plan: "GROWTH",
      status: "ACTIVE",
      periodEnd: daysFromNow(24),
      trialEnd: null,
      cancelAtPeriodEnd: false
    });
    this.billingInvoices.push(
      { id: "inv-2026-04", status: "PAID", currency: "USD", due: 19900, paid: 19900, url: null, paidAt: new Date(now.getTime() - 28 * 86400000).toISOString() },
      { id: "inv-2026-03", status: "PAID", currency: "USD", due: 19900, paid: 19900, url: null, paidAt: new Date(now.getTime() - 58 * 86400000).toISOString() }
    );

    this.supportTickets.push(
      {
        id: "tkt-seed-1",
        tenantId: demoTenant.tenantId,
        email: "customer@acme.example",
        subject: "Need help configuring SAML SSO with Okta",
        description: "We configured the ACS URL and need confirmation on the attribute mapping.",
        status: "OPEN",
        provider: "EMAIL",
        conversationId: "conv-seed-1",
        created_at: new Date(now.getTime() - 3600000 * 6).toISOString(),
        createdAt: new Date(now.getTime() - 3600000 * 6).toISOString()
      },
      {
        id: "tkt-seed-2",
        tenantId: demoTenant.tenantId,
        email: "ops@globex.example",
        subject: "RAG API key rotation window",
        description: "Confirming whether key rotation immediately invalidates the old key.",
        status: "RESOLVED",
        provider: "EMAIL",
        conversationId: null,
        created_at: new Date(now.getTime() - 86400000 * 2).toISOString(),
        createdAt: new Date(now.getTime() - 86400000 * 2).toISOString()
      }
    );

    this.portalConversations.set("conv-seed-1", {
      id: "conv-seed-1",
      tenantId: demoTenant.tenantId,
      customerEmail: "customer@acme.example",
      language: "en",
      created_at: new Date(now.getTime() - 3600000 * 7).toISOString(),
      messages: [
        { id: "msg-1", role: "user", content: "How do I configure SAML SSO?", created_at: new Date(now.getTime() - 3600000 * 7).toISOString() },
        { id: "msg-2", role: "assistant", content: "Connect Okta, Microsoft Entra ID, or Google Workspace via OIDC or SAML 2.0 from the Enterprise console.", created_at: new Date(now.getTime() - 3600000 * 7 + 3000).toISOString() }
      ]
    });

    const rule1Id = "rule-1";
    this.automationRules.set(rule1Id, {
      id: rule1Id,
      name: "Flag overdue security articles for review",
      triggerType: "REVIEW_OVERDUE",
      actionType: "FLAG_FOR_REVIEW",
      enabled: true,
      articleIdFilter: null,
      cooldownMinutes: 60,
      maxAttempts: 5
    });
    this.automationRuns.set(rule1Id, [
      {
        id: "arun-1",
        event_key: "REVIEW_OVERDUE:art-billing-faq",
        state: "COMPLETED",
        attempts: 1,
        last_error: null,
        created_at: new Date(now.getTime() - 7200000).toISOString()
      },
      {
        id: "arun-2",
        event_key: "REVIEW_OVERDUE:art-sso-scim",
        state: "DEAD",
        attempts: 5,
        last_error: "Downstream notification webhook timed out after 5 attempts.",
        created_at: new Date(now.getTime() - 3600000).toISOString()
      }
    ]);

    this.translationProposals.set("tr-1", {
      id: "tr-1",
      source_article_id: "art-getting-started",
      target_language: "fr",
      translated_title: "[FR] Getting Started with Your Knowledge Base",
      translated_content: "## [FR] Welcome to RicozKnow\n\nRicozKnow offre aux équipes un espace gouverné unique pour rédiger, réviser et publier des réponses fiables.",
      state: "PENDING",
      created_by: editorUser.id,
      created_at: new Date(now.getTime() - 86400000).toISOString()
    });

    this.developerUsage.push(
      { api_key_id: "ragkey-1", method: "POST", tool_name: "rag/search", requests: 1842, failures: 4, average_latency_ms: 42 },
      { api_key_id: "ragkey-1", method: "POST", tool_name: "rag/answer", requests: 512, failures: 2, average_latency_ms: 185 },
      { api_key_id: "ragkey-1", method: "POST", tool_name: "mcp/knowledge.search", requests: 318, failures: 0, average_latency_ms: 54 }
    );
    this.developerReviewRequests.set("drev-1", {
      id: "drev-1",
      status: "OPEN",
      article_id: "art-agent-api",
      reason: "SDK agent reported outdated header example for scoped RAG API keys."
    });

    this.webhookDeliveries.set("wh-1", [
      {
        id: "whdel-1",
        eventType: "ARTICLE_PUBLISHED",
        status: "DELIVERED",
        attemptCount: 1,
        responseCode: 200,
        lastError: null,
        createdAt: isoNow
      }
    ]);
    this.webhookDlq.push({
      id: "dlq-1",
      subscriptionId: "wh-1",
      eventType: "ARTICLE_ARCHIVED",
      reason: "Remote endpoint returned HTTP 503",
      attemptCount: 5,
      replayedAt: null,
      createdAt: new Date(now.getTime() - 43200000).toISOString()
    });

    this.aiCostEvents.push(
      { id: "cost-1", provider: "ricoz-grounded", model: "grounded-v1", operation: "RAG_ANSWER", inputTokens: 1420, outputTokens: 280, costMicros: 1240, createdAt: isoNow },
      { id: "cost-2", provider: "ricoz-grounded", model: "grounded-v1", operation: "WRITING_ASSIST", inputTokens: 890, outputTokens: 410, costMicros: 980, createdAt: new Date(now.getTime() - 86400000).toISOString() }
    );

    this.verificationHistory.set("art-getting-started", [
      { action: "VERIFIED", actorId: reviewerUser.id, details: "Initial governance verification completed.", at: isoNow }
    ]);

    this.auditLogs.push({
      id: "aud-1",
      actorUserId: adminUser.id,
      entityType: "ARTICLE",
      entityId: "art-getting-started",
      action: "PUBLISH",
      oldState: "IN_REVIEW",
      newState: "PUBLISHED",
      ipAddress: "127.0.0.1",
      userAgent: "RicozKnow Studio",
      requestId: "req-seed-1",
      source: "STUDIO",
      metadata: null,
      createdAt: isoNow
    });

    const seedRequests = [
      {
        id: "kreq-1",
        tenantId: demoTenant.tenantId,
        title: "Okta SCIM Group-to-Role Mapping Guide",
        description: "Support agents received 11 tickets this week asking how Okta group push maps to RicozKnow Editor and Reviewer roles.",
        categoryId: "cat-security",
        priority: "URGENT" as const,
        status: "OPEN" as const,
        source: "SUPPORT" as const,
        requesterEmail: "support-lead@demo.ricozknow.local",
        assigneeEmail: editorUser.email,
        upvotes: 14,
        linkedArticleId: null,
        createdAt: new Date(now.getTime() - 172800000).toISOString(),
        updatedAt: isoNow
      },
      {
        id: "kreq-2",
        tenantId: demoTenant.tenantId,
        title: "Webhook Retry Schedule & Dead-Letter Replay Policy",
        description: "Document exponential backoff timings, 5xx handling, and how admins replay failed deliveries from the DLQ.",
        categoryId: "cat-api",
        priority: "HIGH" as const,
        status: "IN_PROGRESS" as const,
        source: "INTERNAL" as const,
        requesterEmail: "devrel@demo.ricozknow.local",
        assigneeEmail: adminUser.email,
        upvotes: 9,
        linkedArticleId: "art-webhook-guide",
        createdAt: new Date(now.getTime() - 259200000).toISOString(),
        updatedAt: isoNow
      },
      {
        id: "kreq-3",
        tenantId: demoTenant.tenantId,
        title: "Annual Enterprise Invoicing & PO Numbers",
        description: "Customers on annual contracts ask how to attach a purchase order number and download VAT-compliant receipts.",
        categoryId: "cat-billing",
        priority: "MEDIUM" as const,
        status: "OPEN" as const,
        source: "CUSTOMER" as const,
        requesterEmail: "finance@acme.example",
        assigneeEmail: null,
        upvotes: 6,
        linkedArticleId: null,
        createdAt: new Date(now.getTime() - 432000000).toISOString(),
        updatedAt: isoNow
      },
      {
        id: "kreq-4",
        tenantId: demoTenant.tenantId,
        title: "GDPR Data Subject Export & Anonymization Checklist",
        description: "Step-by-step compliance runbook for tenant admins executing privacy exports and right-to-be-forgotten requests.",
        categoryId: "cat-security",
        priority: "LOW" as const,
        status: "COMPLETED" as const,
        source: "INTERNAL" as const,
        requesterEmail: "privacy@demo.ricozknow.local",
        assigneeEmail: reviewerUser.email,
        upvotes: 5,
        linkedArticleId: "art-sso-scim",
        createdAt: new Date(now.getTime() - 604800000).toISOString(),
        updatedAt: isoNow
      }
    ];
    for (const r of seedRequests) {
      this.knowledgeRequests.set(r.id, r);
    }

    const seedPlaybooks = [
      {
        id: "pb-sso-triage",
        tenantId: demoTenant.tenantId,
        title: "Diagnose SAML SSO Login Errors",
        summary: "Walk through ACS URL mismatches, expired certificates, and missing role claims in under two minutes.",
        category: "Security & Access",
        published: true,
        runsCount: 38,
        resolvedCount: 31,
        updatedAt: isoNow,
        steps: [
          {
            id: "start",
            prompt: "What error appears when the user tries to sign in with SSO?",
            guidance: "Check the message returned after your identity provider redirects back to RicozKnow.",
            options: [
              { label: "Invalid SAML response or signature mismatch", nextStepId: "cert-check" },
              { label: "User signed in, but sees 'Access denied' or wrong workspace role", nextStepId: "role-check" },
              { label: "Login loops back to the Sign In screen", nextStepId: "loop-resolution" }
            ]
          },
          {
            id: "cert-check",
            prompt: "Has your Identity Provider rotated its X.509 signing certificate in the last 30 days?",
            guidance: "Compare the certificate fingerprint in Enterprise Controls -> SSO with your IdP metadata XML.",
            options: [
              { label: "Yes, the certificate was rotated or updated", nextStepId: "cert-resolution" },
              { label: "No, the certificate matches our IdP", nextStepId: "acs-resolution" }
            ]
          },
          {
            id: "role-check",
            prompt: "Is SCIM group provisioning enabled for this tenant?",
            guidance: "Verify whether an active SCIM bearer token exists in Enterprise Controls.",
            options: [
              { label: "Yes, SCIM is enabled", nextStepId: "scim-resolution" },
              { label: "No, we assign roles manually", nextStepId: "manual-role-resolution" }
            ]
          },
          {
            id: "cert-resolution",
            prompt: "Resolution: Refresh IdP Metadata Certificate",
            resolution: {
              title: "Update the SAML metadata URL and re-save SSO",
              summary: "Paste the updated metadata URL in Administration -> Enterprise -> SSO and click Save SSO. Existing active sessions remain unaffected while new logins immediately validate against the rotated certificate.",
              articleSlug: "configuring-saml-sso-scim",
              actionLabel: "Open SAML SSO & SCIM Guide"
            }
          },
          {
            id: "acs-resolution",
            prompt: "Resolution: Verify ACS URL & Entity ID",
            resolution: {
              title: "Confirm exact ACS callback URL and Tenant Slug",
              summary: "Ensure the Assertion Consumer Service URL uses HTTPS and matches your tenant's exact slug without trailing slashes. Check for clock skew greater than 2 minutes on the IdP.",
              articleSlug: "configuring-saml-sso-scim",
              actionLabel: "Review SSO Configuration"
            }
          },
          {
            id: "scim-resolution",
            prompt: "Resolution: Re-sync SCIM Group Membership",
            resolution: {
              title: "Trigger a group push from your Identity Provider",
              summary: "Confirm the user belongs to a mapped IdP group and that the SCIM token in Enterprise Controls has not expired. Re-push group memberships to immediately restore role access.",
              articleSlug: "configuring-saml-sso-scim",
              actionLabel: "Open SCIM Provisioning Guide"
            }
          },
          {
            id: "manual-role-resolution",
            prompt: "Resolution: Assign Workspace Role in Users",
            resolution: {
              title: "Grant EDITOR, REVIEWER, or ADMIN role",
              summary: "Open Administration -> Users, locate the member, and assign the required workspace role. Ask the user to refresh their session.",
              articleSlug: "getting-started-with-ricozknow",
              actionLabel: "View Getting Started Guide"
            }
          },
          {
            id: "loop-resolution",
            prompt: "Resolution: Clear Cookies & Verify Tenant Slug",
            resolution: {
              title: "Confirm third-party cookie policy and tenant slug",
              summary: "Verify the tenant slug entered on the sign-in form matches the tenant linked to the SSO connection, clear stale session cookies, and retry login.",
              articleSlug: "password-reset-procedure",
              actionLabel: "Open Account Recovery Guide"
            }
          }
        ]
      },
      {
        id: "pb-webhook-debug",
        tenantId: demoTenant.tenantId,
        title: "Webhook Delivery & Signature Troubleshooter",
        summary: "Isolate HMAC verification failures, HTTP 5xx timeouts, and dead-letter queue replays.",
        category: "API & Integrations",
        published: true,
        runsCount: 24,
        resolvedCount: 21,
        updatedAt: isoNow,
        steps: [
          {
            id: "start",
            prompt: "Are webhook deliveries arriving at your endpoint at all?",
            guidance: "Inspect Administration -> Webhooks -> Recent Deliveries for status codes.",
            options: [
              { label: "Requests arrive, but HMAC signature validation fails", nextStepId: "hmac-resolution" },
              { label: "Deliveries fail with 5xx / timeout and land in the DLQ", nextStepId: "dlq-resolution" },
              { label: "No events appear in delivery history", nextStepId: "sub-resolution" }
            ]
          },
          {
            id: "hmac-resolution",
            prompt: "Resolution: Compute HMAC on Raw Request Body",
            resolution: {
              title: "Hash the raw UTF-8 payload bytes before JSON parsing",
              summary: "Compute SHA-256 HMAC using the raw request body bytes and your subscription signing secret. Parsing and re-serializing JSON alters key ordering and breaks signature matching.",
              articleSlug: "connecting-ai-agents-with-rag-api",
              actionLabel: "Open API Integration Guide"
            }
          },
          {
            id: "dlq-resolution",
            prompt: "Resolution: Return 2xx Quickly & Replay DLQ",
            resolution: {
              title: "Acknowledge within 5 seconds and replay dead-letter items",
              summary: "Enqueue heavy work asynchronously and respond with HTTP 200 immediately. Once your receiver is healthy, open Administration -> Webhooks and click Replay on dead-letter deliveries.",
              articleSlug: "getting-started-with-ricozknow",
              actionLabel: "View Workspace Operations"
            }
          },
          {
            id: "sub-resolution",
            prompt: "Resolution: Confirm Event Filter & Active Status",
            resolution: {
              title: "Verify subscription is enabled for ARTICLE_PUBLISHED",
              summary: "Check Administration -> Webhooks to confirm the target subscription is ACTIVE and subscribed to the specific lifecycle events you expect.",
              articleSlug: "getting-started-with-ricozknow",
              actionLabel: "View Getting Started Guide"
            }
          }
        ]
      }
    ];
    for (const pb of seedPlaybooks) {
      this.playbooks.set(pb.id, pb);
    }

    const seedFeedback: ProductFeedbackRecord[] = [
      {
        id: "fb-seed-001",
        tenantId: demoTenant.tenantId,
        type: "BUG",
        severity: "HIGH",
        status: "TRIAGED",
        title: "Code block horizontal scroll clips copy button on narrow viewports",
        description: "When viewing long curl examples in the RAG API guide on a 390px phone screen, scrolling the code block slightly hides the top-right copy trigger.",
        reporterEmail: "editor@demo.ricozknow.local",
        reporterUserId: "user-editor-1",
        includeDiagnostics: true,
        pageUrl: "http://localhost:3000/portal/article/rag-api-integration?tenant=demo",
        routePath: "/portal/article/rag-api-integration?tenant=demo",
        browser: "Chrome 131",
        os: "iOS",
        userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/131.0.6778.73 Mobile/15E148 Safari/604.1",
        viewport: "390x844",
        theme: "dark",
        language: "en-US",
        createdAt: new Date(Date.now() - 2 * 3600 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - 1 * 3600 * 1000).toISOString()
      },
      {
        id: "fb-seed-002",
        tenantId: demoTenant.tenantId,
        type: "FEATURE",
        severity: "MEDIUM",
        status: "IN_PROGRESS",
        title: "Export review history and governance sign-offs as signed PDF",
        description: "Our SOC 2 auditors ask for quarterly verification sign-off bundles. Adding a one-click PDF export on the Governance Health view would save hours.",
        reporterEmail: "reviewer@demo.ricozknow.local",
        reporterUserId: "user-reviewer-1",
        includeDiagnostics: true,
        pageUrl: "http://localhost:3000/knowledge-health",
        routePath: "/knowledge-health",
        browser: "Firefox 133",
        os: "macOS",
        userAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:133.0) Gecko/20100101 Firefox/133.0",
        viewport: "1440x900",
        theme: "light",
        language: "en-US",
        createdAt: new Date(Date.now() - 18 * 3600 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - 6 * 3600 * 1000).toISOString()
      },
      {
        id: "fb-seed-003",
        tenantId: demoTenant.tenantId,
        type: "FEATURE",
        severity: "LOW",
        status: "RESOLVED",
        title: "Keyboard shortcut for quick jump across workspace pages",
        description: "Would love Cmd+K / Ctrl+K navigation to jump directly to articles, playbooks, and admin settings without clicking through the sidebar.",
        reporterEmail: "admin@demo.ricozknow.local",
        reporterUserId: "user-admin-1",
        includeDiagnostics: true,
        pageUrl: "http://localhost:3000/dashboard",
        routePath: "/dashboard",
        browser: "Chrome 131",
        os: "Windows",
        userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        viewport: "1920x1080",
        theme: "dark",
        language: "en-US",
        createdAt: new Date(Date.now() - 72 * 3600 * 1000).toISOString(),
        updatedAt: new Date(Date.now() - 24 * 3600 * 1000).toISOString()
      }
    ];
    for (const fb of seedFeedback) {
      this.productFeedback.set(fb.id, fb);
    }
  }

  recordAudit(actorUserId: string, entityType: string, entityId: string, action: string, oldState: string | null = null, newState: string | null = null): void {
    this.auditLogs.unshift({
      id: uid("aud"),
      actorUserId,
      entityType,
      entityId,
      action,
      oldState,
      newState,
      ipAddress: "127.0.0.1",
      userAgent: "RicozKnow Studio",
      requestId: uid("req"),
      source: "STUDIO",
      metadata: null,
      createdAt: new Date().toISOString()
    });
    if (this.auditLogs.length > 200) {
      this.auditLogs.length = 200;
    }
  }

  resolveTenantBySlugOrSubdomain(slugOrHost?: string | null): TenantRecord {
    const normalized = (slugOrHost ?? "").trim().toLowerCase();
    if (normalized) {
      for (const tenant of this.tenants.values()) {
        if (tenant.slug === normalized || tenant.subdomain === normalized || tenant.customDomain === normalized) {
          return tenant;
        }
      }
    }
    return this.tenants.get(this.defaultTenantId)!;
  }

  getTenantById(tenantId: string): TenantRecord {
    return this.tenants.get(tenantId) ?? this.tenants.get(this.defaultTenantId)!;
  }

  authenticateToken(token?: string | null): { user: UserRecord; tenant: TenantRecord } | null {
    if (!token) return null;
    const session = this.sessions.get(token);
    if (!session) return null;
    const user = this.users.get(session.userId);
    if (!user || user.status !== "ACTIVE") return null;
    return { user, tenant: this.getTenantById(session.tenantId) };
  }

  login(tenantSlug: string, email: string, password: string): { response: LoginResponseDto; refreshToken: string } | { error: string; status: number } {
    const slug = tenantSlug.trim().toLowerCase();
    const cleanEmail = email.trim().toLowerCase();
    const tenant = Array.from(this.tenants.values()).find((t) => t.slug === slug || t.subdomain === slug);
    if (!tenant) {
      return { error: "Workspace tenant not found.", status: 401 };
    }
    const user = Array.from(this.users.values()).find(
      (u) => u.tenantId === tenant.tenantId && u.email.toLowerCase() === cleanEmail
    );
    if (!user || user.password !== password) {
      return { error: "Invalid email or password.", status: 401 };
    }
    if (user.status !== "ACTIVE") {
      return { error: "Account is disabled.", status: 403 };
    }
    return this.issueSession(user);
  }

  issueSession(user: UserRecord): { response: LoginResponseDto; refreshToken: string } {
    const accessToken = `rk_tok_${crypto.randomUUID()}`;
    const refreshToken = `rk_ref_${crypto.randomUUID()}`;
    const now = new Date();
    const session: SessionRecord = {
      token: accessToken,
      refreshToken,
      userId: user.id,
      tenantId: user.tenantId,
      createdAt: now.toISOString(),
      expiresAt: new Date(now.getTime() + 7 * 86400000).toISOString()
    };
    this.sessions.set(accessToken, session);
    this.refreshIndex.set(refreshToken, session);
    return {
      response: {
        accessToken,
        userId: user.id,
        tenantId: user.tenantId,
        email: user.email,
        roles: [...user.roles]
      },
      refreshToken
    };
  }

  refreshSession(refreshToken?: string | null): { response: LoginResponseDto; refreshToken: string } | null {
    if (!refreshToken) return null;
    const existing = this.refreshIndex.get(refreshToken);
    if (!existing) return null;
    const user = this.users.get(existing.userId);
    if (!user || user.status !== "ACTIVE") return null;
    return this.issueSession(user);
  }

  logout(accessToken?: string | null, refreshToken?: string | null): void {
    if (accessToken) {
      const s = this.sessions.get(accessToken);
      if (s) this.refreshIndex.delete(s.refreshToken);
      this.sessions.delete(accessToken);
    }
    if (refreshToken) {
      const s = this.refreshIndex.get(refreshToken);
      if (s) this.sessions.delete(s.token);
      this.refreshIndex.delete(refreshToken);
    }
  }

  register(req: {
    organizationName: string;
    tenantSlug: string;
    subdomain: string;
    email: string;
    password: string;
    name: string;
  }): { status: number; body: Record<string, unknown> } {
    const slug = req.tenantSlug.trim().toLowerCase();
    const subdomain = req.subdomain.trim().toLowerCase();
    const email = req.email.trim().toLowerCase();
    if (!slug || !subdomain || !email || !req.password || !req.organizationName?.trim()) {
      return { status: 400, body: { message: "All registration fields are required." } };
    }
    for (const existing of this.tenants.values()) {
      if (existing.slug === slug || existing.subdomain === subdomain) {
        return { status: 409, body: { message: "That tenant slug or subdomain is already registered." } };
      }
    }
    const tenantId = uid("tenant");
    const tenant: TenantRecord = {
      tenantId,
      slug,
      subdomain,
      name: req.organizationName.trim(),
      portalTitle: `${req.organizationName.trim()} Help Center`,
      logoUrl: "",
      primaryColor: "#0f172a",
      customDomain: null,
      customDomainVerified: false,
      customDomainSslStatus: "PENDING",
      supportEmail: email,
      supportedLanguages: "en"
    };
    this.tenants.set(tenantId, tenant);

    const userId = uid("user");
    const user: UserRecord = {
      id: userId,
      tenantId,
      email,
      name: req.name.trim() || "Admin",
      password: req.password,
      status: "ACTIVE",
      roles: ["ADMIN", "EDITOR", "REVIEWER", "AGENT_VIEWER"],
      mfaEnabled: false
    };
    this.users.set(userId, user);
    this.tenantSettingsByTenant.set(tenantId, {
      tenantId,
      timezone: "UTC",
      defaultLanguage: "en",
      maxUsers: 50,
      maxStorageBytes: 5368709120,
      maxApiRequestsMonthly: 100000,
      maxRagQueriesMonthly: 25000,
      maxAiCostCentsMonthly: 10000,
      settingsJson: "{}",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    });

    return {
      status: 201,
      body: {
        tenantId,
        userId,
        tenantSlug: slug,
        email,
        role: "ADMIN",
        message: "Workspace created and activated. You can sign in right away."
      }
    };
  }

  listCategories(tenantId: string): CategoryRecord[] {
    return Array.from(this.categories.values())
      .filter((c) => c.tenantId === tenantId)
      .sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
  }

  createCategory(
    tenantId: string,
    input: { name: string; slug: string; parentId?: string | null; sortOrder?: number | null }
  ): CategoryRecord {
    const rec: CategoryRecord = {
      id: uid("cat"),
      tenantId,
      name: input.name.trim(),
      slug: input.slug.trim().toLowerCase(),
      parentId: input.parentId || null,
      sortOrder: Number.isFinite(Number(input.sortOrder)) ? Number(input.sortOrder) : 0
    };
    this.categories.set(rec.id, rec);
    return rec;
  }

  updateCategory(
    tenantId: string,
    id: string,
    input: { name: string; slug: string; parentId?: string | null; sortOrder?: number | null }
  ): CategoryRecord | null {
    const existing = this.categories.get(id);
    if (!existing || existing.tenantId !== tenantId) return null;
    existing.name = input.name.trim();
    existing.slug = input.slug.trim().toLowerCase();
    existing.parentId = input.parentId || null;
    existing.sortOrder = Number.isFinite(Number(input.sortOrder)) ? Number(input.sortOrder) : existing.sortOrder;
    return existing;
  }

  deleteCategory(tenantId: string, id: string): boolean {
    const existing = this.categories.get(id);
    if (!existing || existing.tenantId !== tenantId) return false;
    this.categories.delete(id);
    return true;
  }

  listArticles(tenantId: string, opts: { status?: string; page?: number; size?: number } = {}): PageDto<ArticleRecord> {
    let items = Array.from(this.articles.values()).filter((a) => a.tenantId === tenantId);
    if (opts.status) {
      items = items.filter((a) => a.status === opts.status);
    }
    return paginate(items, opts.page ?? 0, opts.size ?? 10);
  }

  getArticle(tenantId: string, id: string): ArticleRecord | null {
    const art = this.articles.get(id);
    if (!art || art.tenantId !== tenantId) return null;
    return art;
  }

  createArticle(
    tenantId: string,
    authorId: string,
    input: { title: string; slug: string; categoryId?: string | null; content: unknown }
  ): ArticleRecord {
    const id = uid("art");
    const versionId = uid("ver");
    const slug = (input.slug || input.title)
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9-]+/g, "-")
      .replace(/^-|-$/g, "") || id;

    const article: ArticleRecord = {
      id,
      tenantId,
      slug,
      title: input.title.trim(),
      status: "DRAFT",
      visibility: "PUBLIC",
      categoryId: input.categoryId || null,
      activeVersionId: null,
      activeVersionNumber: null,
      language: "en",
      articleType: "GUIDE",
      pinned: false,
      featured: false,
      tags: [],
      relatedArticleIds: [],
      scheduledPublishAt: null,
      scheduledUnpublishAt: null,
      ownerId: authorId,
      reviewIntervalDays: 90,
      nextReviewAt: new Date(Date.now() + 90 * 86400000).toISOString(),
      verifiedAt: null,
      verifiedBy: null,
      views: 0,
      helpfulCount: 0,
      unhelpfulCount: 0
    };
    this.articles.set(id, article);
    this.versions.set(id, [
      {
        id: versionId,
        articleId: id,
        versionNumber: 1,
        content: normalizeBlocks(input.content),
        changeSummary: "Initial draft",
        status: "DRAFT",
        createdBy: authorId,
        reviewedBy: null,
        reviewedAt: null,
        publishedAt: null,
        expiresAt: null
      }
    ]);
    return article;
  }

  getVersions(tenantId: string, articleId: string): ArticleVersionRecord[] | null {
    const article = this.getArticle(tenantId, articleId);
    if (!article) return null;
    return this.versions.get(articleId) ?? [];
  }

  createDraftVersion(tenantId: string, articleId: string, authorId: string): ArticleVersionRecord | null {
    const article = this.getArticle(tenantId, articleId);
    if (!article) return null;
    const list = this.versions.get(articleId) ?? [];
    const latest = list[list.length - 1];
    const nextNum = (latest?.versionNumber ?? 0) + 1;
    const nextVersion: ArticleVersionRecord = {
      id: uid("ver"),
      articleId,
      versionNumber: nextNum,
      content: latest ? structuredClone(latest.content) : { blocks: [] },
      changeSummary: `Draft v${nextNum}`,
      status: "DRAFT",
      createdBy: authorId,
      reviewedBy: null,
      reviewedAt: null,
      publishedAt: null,
      expiresAt: null
    };
    list.push(nextVersion);
    this.versions.set(articleId, list);
    return nextVersion;
  }

  updateDraft(
    tenantId: string,
    articleId: string,
    versionId: string,
    content: unknown,
    changeSummary?: string
  ): ArticleVersionRecord | null {
    const list = this.getVersions(tenantId, articleId);
    if (!list) return null;
    const ver = list.find((v) => v.id === versionId);
    if (!ver) return null;
    ver.content = normalizeBlocks(content);
    if (changeSummary !== undefined) ver.changeSummary = changeSummary;
    return ver;
  }

  submitForReview(tenantId: string, articleId: string, versionId: string): ArticleVersionRecord | null {
    const article = this.getArticle(tenantId, articleId);
    const list = this.getVersions(tenantId, articleId);
    if (!article || !list) return null;
    const ver = list.find((v) => v.id === versionId);
    if (!ver) return null;
    ver.status = "IN_REVIEW";
    if (article.status === "DRAFT") {
      article.status = "IN_REVIEW";
    }
    return ver;
  }

  reviewVersion(
    tenantId: string,
    articleId: string,
    versionId: string,
    reviewerId: string,
    approve: boolean,
    comment?: string
  ): ArticleVersionRecord | null {
    const article = this.getArticle(tenantId, articleId);
    const list = this.getVersions(tenantId, articleId);
    if (!article || !list) return null;
    const ver = list.find((v) => v.id === versionId);
    if (!ver) return null;
    const now = new Date().toISOString();
    ver.reviewedBy = reviewerId;
    ver.reviewedAt = now;
    if (approve) {
      ver.status = "PUBLISHED";
      ver.publishedAt = now;
      article.status = "PUBLISHED";
      article.activeVersionId = ver.id;
      article.activeVersionNumber = ver.versionNumber;
      article.verifiedAt = now;
      article.verifiedBy = reviewerId;
    } else {
      ver.status = "REJECTED";
      if (!article.activeVersionId) {
        article.status = "DRAFT";
      }
    }
    const hist = this.reviewHistory.get(articleId) ?? [];
    hist.unshift({
      id: uid("rev"),
      articleId,
      versionId,
      reviewerId,
      decision: approve ? "APPROVED" : "REJECTED",
      comment: comment || null,
      createdAt: now
    });
    this.reviewHistory.set(articleId, hist);
    return ver;
  }

  archiveArticle(tenantId: string, articleId: string): boolean {
    const article = this.getArticle(tenantId, articleId);
    if (!article) return false;
    article.status = "ARCHIVED";
    return true;
  }

  portalArticles(
    tenantId: string,
    opts: { categoryId?: string; page?: number; size?: number; pinned?: boolean; featured?: boolean; language?: string } = {}
  ) {
    let items = Array.from(this.articles.values()).filter(
      (a) => a.tenantId === tenantId && a.status === "PUBLISHED" && a.visibility !== "PRIVATE"
    );
    if (opts.categoryId) items = items.filter((a) => a.categoryId === opts.categoryId);
    if (opts.language) items = items.filter((a) => a.language === opts.language);
    if (opts.pinned) items = items.filter((a) => a.pinned);
    if (opts.featured) items = items.filter((a) => a.featured);

    const summaries = items.map((a) => ({
      id: a.id,
      slug: a.slug,
      title: a.title,
      categoryId: a.categoryId,
      pinned: a.pinned,
      featured: a.featured
    }));
    return paginate(summaries, opts.page ?? 0, opts.size ?? 10);
  }

  portalArticleBySlug(tenantId: string, slug: string) {
    const article = Array.from(this.articles.values()).find(
      (a) => a.tenantId === tenantId && a.slug === slug && a.status === "PUBLISHED" && a.visibility !== "PRIVATE"
    );
    if (!article) return null;
    article.views += 1;
    const versions = this.versions.get(article.id) ?? [];
    const active = versions.find((v) => v.id === article.activeVersionId) ?? versions[versions.length - 1];
    return {
      id: article.id,
      slug: article.slug,
      title: article.title,
      categoryId: article.categoryId,
      visibility: article.visibility,
      content: active?.content ?? { blocks: [] },
      versionNumber: active?.versionNumber ?? 1,
      pinned: article.pinned,
      featured: article.featured,
      tags: article.tags,
      relatedArticleIds: article.relatedArticleIds,
      publishedAt: active?.publishedAt ?? new Date().toISOString(),
      translations: []
    };
  }

  agentArticleById(tenantId: string, id: string) {
    const article = this.getArticle(tenantId, id);
    if (!article || article.status !== "PUBLISHED") return null;
    const versions = this.versions.get(article.id) ?? [];
    const active = versions.find((v) => v.id === article.activeVersionId) ?? versions[versions.length - 1];
    return {
      id: article.id,
      slug: article.slug,
      title: article.title,
      categoryId: article.categoryId,
      visibility: article.visibility,
      content: active?.content ?? { blocks: [] },
      versionNumber: active?.versionNumber ?? 1,
      pinned: article.pinned,
      featured: article.featured,
      tags: article.tags,
      relatedArticleIds: article.relatedArticleIds,
      publishedAt: active?.publishedAt ?? new Date().toISOString(),
      translations: []
    };
  }

  searchArticles(
    tenantId: string,
    query: string,
    opts: { publicOnly?: boolean; categoryId?: string; page?: number; size?: number; tag?: string; language?: string } = {}
  ) {
    const q = query.trim().toLowerCase();
    const stopWords = new Set([
      "a", "an", "and", "are", "be", "can", "do", "does", "for", "how", "i", "in",
      "is", "it", "me", "my", "of", "on", "or", "our", "that", "the", "this", "to",
      "we", "what", "when", "where", "why", "with", "you", "your"
    ]);
    const rawTerms = q.replace(/[^a-z0-9\s-]/g, " ").split(/\s+/).filter(Boolean);
    const meaningful = rawTerms.filter((t) => t.length > 1 && !stopWords.has(t));
    const terms = meaningful.length ? meaningful : rawTerms;
    const expandedTerms = new Set(terms);
    for (const syn of this.synonyms.values()) {
      if (syn.tenantId !== tenantId) continue;
      if (terms.includes(syn.term.toLowerCase())) {
        for (const s of syn.synonyms) expandedTerms.add(s.toLowerCase());
      }
    }

    let pool = Array.from(this.articles.values()).filter((a) => a.tenantId === tenantId);
    if (opts.publicOnly) {
      pool = pool.filter((a) => a.status === "PUBLISHED" && a.visibility !== "PRIVATE");
    }
    if (opts.categoryId) pool = pool.filter((a) => a.categoryId === opts.categoryId);
    if (opts.tag) pool = pool.filter((a) => a.tags.includes(opts.tag!));
    if (opts.language) pool = pool.filter((a) => a.language === opts.language);

    const hits = pool
      .map((a) => {
        const versions = this.versions.get(a.id) ?? [];
        const active = versions.find((v) => v.id === a.activeVersionId) ?? versions[versions.length - 1];
        const plain = extractPlainText(active?.content ?? { blocks: [] });
        const titleLower = a.title.toLowerCase();
        const slugLower = a.slug.toLowerCase();
        const haystack = `${titleLower} ${slugLower} ${a.tags.join(" ")} ${plain}`.toLowerCase();
        let score = 0;
        if (!q) {
          score = 1;
        } else {
          for (const t of expandedTerms) {
            if (titleLower.includes(t)) score += 6;
            if (slugLower.includes(t)) score += 3;
            if (a.tags.some((tag) => tag.toLowerCase().includes(t))) score += 4;
            if (haystack.includes(t)) score += 1;
          }
        }
        return {
          articleId: a.id,
          slug: a.slug,
          title: a.title,
          excerpt: plain.slice(0, 180) + (plain.length > 180 ? "…" : ""),
          categoryId: a.categoryId,
          language: a.language,
          articleType: a.articleType,
          tags: a.tags,
          score
        };
      })
      .filter((h) => h.score > 0)
      .sort((a, b) => b.score - a.score);

    const catCounts = new Map<string, number>();
    const tagCounts = new Map<string, number>();
    for (const h of hits) {
      if (h.categoryId) catCounts.set(h.categoryId, (catCounts.get(h.categoryId) ?? 0) + 1);
      for (const t of h.tags ?? []) tagCounts.set(t, (tagCounts.get(t) ?? 0) + 1);
    }

    const page = opts.page ?? 0;
    const size = opts.size ?? 10;
    const sliced = hits.slice(page * size, page * size + size);

    return {
      query,
      rewrittenQuery: query,
      results: sliced,
      total: hits.length,
      facets: {
        categories: Array.from(catCounts.entries()).map(([value, count]) => ({ value, count })),
        languages: [{ value: "en", count: hits.length }],
        tags: Array.from(tagCounts.entries()).map(([value, count]) => ({ value, count })),
        articleTypes: [{ value: "GUIDE", count: hits.length }]
      },
      relatedQuestions: ["How do I reset my password?", "How do I connect an AI agent?", "How do I configure SAML SSO?"]
    };
  }

  recordFeedback(tenantId: string, articleId: string, helpful: boolean): boolean {
    const article = this.getArticle(tenantId, articleId);
    if (!article) return false;
    if (helpful) article.helpfulCount += 1;
    else article.unhelpfulCount += 1;
    return true;
  }

  getArticlePerformance(tenantId: string) {
    return Array.from(this.articles.values())
      .filter((a) => a.tenantId === tenantId)
      .map((a) => ({
        articleId: a.id,
        title: a.title,
        views: a.views,
        helpfulCount: a.helpfulCount,
        unhelpfulCount: a.unhelpfulCount
      }));
  }

  getKnowledgeGaps() {
    return this.knowledgeGapTickets.map((g) => ({
      id: g.id,
      query: g.query,
      volumeCount: g.volumeCount,
      zeroResultCount: g.zeroResultCount,
      unsuccessfulCount: g.unsuccessfulCount,
      gapScore: g.gapScore,
      status: g.status,
      suggestion: g.suggestion,
      draftArticleId: g.draftArticleId
    }));
  }

  rollbackArticle(tenantId: string, articleId: string, targetVersionId: string, actorId: string, changeSummary?: string): ArticleVersionRecord | null {
    const article = this.getArticle(tenantId, articleId);
    const list = this.versions.get(articleId);
    if (!article || !list) return null;
    const target = list.find((v) => v.id === targetVersionId);
    if (!target) return null;
    const nextNum = (list[list.length - 1]?.versionNumber ?? 0) + 1;
    const now = new Date().toISOString();
    const rolled: ArticleVersionRecord = {
      id: uid("ver"),
      articleId,
      versionNumber: nextNum,
      content: structuredClone(target.content),
      changeSummary: changeSummary?.trim() || `Rollback to v${target.versionNumber}`,
      status: "PUBLISHED",
      createdBy: actorId,
      reviewedBy: actorId,
      reviewedAt: now,
      publishedAt: now,
      expiresAt: target.expiresAt
    };
    list.push(rolled);
    article.status = "PUBLISHED";
    article.activeVersionId = rolled.id;
    article.activeVersionNumber = rolled.versionNumber;
    this.recordAudit(actorId, "ARTICLE", articleId, "ROLLBACK", targetVersionId, rolled.id);
    return rolled;
  }

  approveForSchedule(tenantId: string, articleId: string, versionId: string, reviewerId: string, comment?: string): ArticleVersionRecord | null {
    const list = this.getVersions(tenantId, articleId);
    if (!list) return null;
    const ver = list.find((v) => v.id === versionId);
    if (!ver) return null;
    const now = new Date().toISOString();
    ver.status = "APPROVED";
    ver.reviewedBy = reviewerId;
    ver.reviewedAt = now;
    const hist = this.reviewHistory.get(articleId) ?? [];
    hist.unshift({
      id: uid("rev"),
      articleId,
      versionId,
      reviewerId,
      decision: "APPROVED_FOR_SCHEDULE",
      comment: comment || null,
      createdAt: now
    });
    this.reviewHistory.set(articleId, hist);
    this.recordAudit(reviewerId, "ARTICLE", articleId, "APPROVE_FOR_SCHEDULE", "IN_REVIEW", "APPROVED");
    return ver;
  }

  schedulePublish(tenantId: string, articleId: string, versionId: string, at: string, actorId: string): ArticleVersionRecord | null {
    const article = this.getArticle(tenantId, articleId);
    const list = this.getVersions(tenantId, articleId);
    if (!article || !list) return null;
    const ver = list.find((v) => v.id === versionId);
    if (!ver) return null;
    ver.status = "SCHEDULED";
    article.scheduledPublishAt = at;
    this.recordAudit(actorId, "ARTICLE", articleId, "SCHEDULE_PUBLISH", null, at);
    return ver;
  }

  getDeflectionSummary() {
    const resolved = this.supportTickets.filter((t) => t.status === "RESOLVED" || t.status === "CLOSED").length;
    return {
      estimatedDeflectionCount: 412 + this.portalConversations.size * 3,
      confirmedDeflectionCount: 289 + resolved,
      supportContactCount: 61 + this.supportTickets.length
    };
  }

  getStaleContent(tenantId: string) {
    const now = Date.now();
    const results: Array<{ articleId: string; versionId: string; title: string; expiresAt: string; alreadyExpired: boolean }> = [];
    for (const article of this.articles.values()) {
      if (article.tenantId !== tenantId) continue;
      const list = this.versions.get(article.id) ?? [];
      const latest = list[list.length - 1];
      if (latest?.expiresAt) {
        results.push({
          articleId: article.id,
          versionId: latest.id,
          title: article.title,
          expiresAt: latest.expiresAt,
          alreadyExpired: new Date(latest.expiresAt).getTime() <= now
        });
      }
    }
    return results;
  }

  getKnowledgeHealth(tenantId: string) {
    const items = Array.from(this.articles.values()).filter((a) => a.tenantId === tenantId);
    const staleItems = this.getStaleContent(tenantId);
    const published = items.filter((a) => a.status === "PUBLISHED").length;
    const dueArticleIds = items.filter((a) => a.status === "IN_REVIEW" || !a.verifiedAt).map((a) => a.id);
    return {
      total: items.length,
      published,
      dueForReview: dueArticleIds.length,
      unverified: items.filter((a) => !a.verifiedAt).length,
      stale: staleItems.filter((s) => s.alreadyExpired).length,
      dueArticleIds
    };
  }

  getExecutiveAnalytics(tenantId: string) {
    const articles = Array.from(this.articles.values()).filter((a) => a.tenantId === tenantId);
    const users = Array.from(this.users.values()).filter((u) => u.tenantId === tenantId);
    const tickets = this.supportTickets.filter((t) => !t.tenantId || t.tenantId === tenantId);
    const sub = this.billingSubscriptions.get(tenantId) ?? { plan: "GROWTH", status: "ACTIVE" };
    const totalViews = articles.reduce((acc, a) => acc + a.views, 0);
    const helpful = articles.reduce((acc, a) => acc + a.helpfulCount, 0);
    const unhelpful = articles.reduce((acc, a) => acc + a.unhelpfulCount, 0);
    const resolvedTickets = tickets.filter((t) => t.status === "RESOLVED" || t.status === "CLOSED").length;
    const paidInvoicesMinor = this.billingInvoices.reduce((acc, i) => acc + Number(i.paid || 0), 0);

    return {
      knowledge: {
        articleViews: totalViews,
        published: articles.filter((a) => a.status === "PUBLISHED").length,
        helpful,
        unhelpful,
        unverified: articles.filter((a) => !a.verifiedAt).length
      },
      search: {
        queries: 614 + this.enterpriseSearchCount,
        zeroResults: 24 + this.enterpriseSearchUnanswered,
        unsuccessful: 39
      },
      ai: {
        requests: this.aiRequestCount,
        evaluated: this.evalCases.length + this.ragEvalCases.length,
        evaluationHits: this.evalCases.length + this.ragEvalCases.length,
        failures: 3
      },
      support: {
        tickets: tickets.length,
        resolved: resolvedTickets,
        estimatedDeflection: Math.max(0, this.portalConversations.size * 4 + 48 - tickets.length)
      },
      connectors: {
        completed: Array.from(this.connectorRuns.values()).flat().filter((r) => r.state === "COMPLETED").length,
        failures: Array.from(this.connectorRuns.values()).flat().filter((r) => r.state === "FAILED").length,
        sources: this.connectorSources.size
      },
      productivity: {
        activeStaff: users.filter((u) => u.status === "ACTIVE").length,
        newArticles: articles.length,
        staffSeats: users.length
      },
      subscription: [{ plan_code: sub.plan, status: sub.status }],
      paidInvoicesMinor,
      providerCosts: [
        {
          provider: "ricoz-grounded",
          model: "grounded-v1",
          requests: this.aiRequestCount,
          input_tokens: this.aiRequestCount * 420,
          output_tokens: this.aiRequestCount * 135,
          latency_ms: 142,
          estimated_cost_usd: Number((this.aiRequestCount * 0.0085).toFixed(2))
        }
      ]
    };
  }

  listKnowledgeRequests(tenantId: string, filter: { status?: string; priority?: string } = {}) {
    return Array.from(this.knowledgeRequests.values()).filter((r) => {
      if (r.tenantId !== tenantId) return false;
      if (filter.status && r.status !== filter.status) return false;
      if (filter.priority && r.priority !== filter.priority) return false;
      return true;
    });
  }

  createKnowledgeRequest(
    tenantId: string,
    input: {
      title: string;
      description: string;
      categoryId?: string | null;
      priority?: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
      source?: "INTERNAL" | "SUPPORT" | "CUSTOMER";
      requesterEmail: string;
    }
  ) {
    const now = new Date().toISOString();
    const rec = {
      id: uid("kreq"),
      tenantId,
      title: input.title.trim(),
      description: input.description.trim(),
      categoryId: input.categoryId || null,
      priority: input.priority || "MEDIUM",
      status: "OPEN" as const,
      source: input.source || "INTERNAL",
      requesterEmail: input.requesterEmail,
      assigneeEmail: null,
      upvotes: 1,
      linkedArticleId: null,
      createdAt: now,
      updatedAt: now
    };
    this.knowledgeRequests.set(rec.id, rec);
    return rec;
  }

  upvoteKnowledgeRequest(tenantId: string, id: string) {
    const rec = this.knowledgeRequests.get(id);
    if (!rec || rec.tenantId !== tenantId) return null;
    rec.upvotes += 1;
    rec.updatedAt = new Date().toISOString();
    return rec;
  }

  updateKnowledgeRequest(
    tenantId: string,
    id: string,
    patch: {
      status?: "OPEN" | "IN_PROGRESS" | "DRAFT_CREATED" | "COMPLETED" | "DECLINED";
      priority?: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
      assigneeEmail?: string | null;
    }
  ) {
    const rec = this.knowledgeRequests.get(id);
    if (!rec || rec.tenantId !== tenantId) return null;
    if (patch.status) rec.status = patch.status;
    if (patch.priority) rec.priority = patch.priority;
    if (patch.assigneeEmail !== undefined) rec.assigneeEmail = patch.assigneeEmail;
    rec.updatedAt = new Date().toISOString();
    return rec;
  }

  convertKnowledgeRequestToDraft(tenantId: string, id: string, authorUserId: string, slug: string) {
    const rec = this.knowledgeRequests.get(id);
    if (!rec || rec.tenantId !== tenantId) return null;
    const uniqueSlug = `${slug}-${Date.now().toString(36).slice(-4)}`;
    const created = this.createArticle(tenantId, authorUserId, {
      title: rec.title,
      slug: uniqueSlug,
      categoryId: rec.categoryId,
      content: {
        blocks: [
          { id: "b1", type: "heading", level: 2, text: "Overview" },
          { id: "b2", type: "paragraph", text: rec.description || `Draft created from knowledge request: ${rec.title}.` },
          { id: "b3", type: "heading", level: 2, text: "Step-by-Step Procedure" },
          {
            id: "b4",
            type: "list",
            items: [
              "Confirm prerequisites and required workspace permissions.",
              "Apply the configuration steps and validate expected behavior.",
              "Verify audit logs and update related documentation links."
            ]
          }
        ]
      }
    });
    rec.linkedArticleId = created.id;
    rec.status = "DRAFT_CREATED";
    rec.updatedAt = new Date().toISOString();
    this.recordAudit(authorUserId, "KNOWLEDGE_REQUEST", rec.id, "CONVERT_TO_DRAFT", "OPEN", created.id);
    return { request: rec, article: created };
  }

  listPlaybooks(tenantId: string, publishedOnly = false) {
    return Array.from(this.playbooks.values()).filter(
      (p) => p.tenantId === tenantId && (!publishedOnly || p.published)
    );
  }

  createPlaybook(
    tenantId: string,
    input: {
      title: string;
      summary: string;
      category: string;
      published?: boolean;
      steps: Array<{
        id: string;
        prompt: string;
        guidance?: string;
        options?: Array<{ label: string; nextStepId: string }>;
        resolution?: { title: string; summary: string; articleSlug?: string; actionLabel?: string };
      }>;
    }
  ) {
    const rec = {
      id: uid("pb"),
      tenantId,
      title: input.title.trim(),
      summary: input.summary.trim(),
      category: input.category.trim() || "General",
      published: input.published ?? true,
      runsCount: 0,
      resolvedCount: 0,
      updatedAt: new Date().toISOString(),
      steps: input.steps
    };
    this.playbooks.set(rec.id, rec);
    return rec;
  }

  deletePlaybook(tenantId: string, id: string): boolean {
    const rec = this.playbooks.get(id);
    if (!rec || rec.tenantId !== tenantId) return false;
    this.playbooks.delete(id);
    return true;
  }

  recordPlaybookRun(tenantId: string, id: string, outcome: "RESOLVED" | "ESCALATED") {
    const rec = this.playbooks.get(id);
    if (!rec || rec.tenantId !== tenantId) return null;
    rec.runsCount += 1;
    if (outcome === "RESOLVED") rec.resolvedCount += 1;
    rec.updatedAt = new Date().toISOString();
    return rec;
  }

  listProductFeedback(tenantId: string, filter: { type?: string; status?: string } = {}): ProductFeedbackRecord[] {
    return Array.from(this.productFeedback.values())
      .filter((item) => {
        if (item.tenantId !== tenantId) return false;
        if (filter.type && item.type !== filter.type) return false;
        if (filter.status && item.status !== filter.status) return false;
        return true;
      })
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  createProductFeedback(
    tenantId: string,
    input: Omit<ProductFeedbackRecord, "id" | "tenantId" | "status" | "createdAt" | "updatedAt">
  ): ProductFeedbackRecord {
    const now = new Date().toISOString();
    const rec: ProductFeedbackRecord = {
      id: uid("fb"),
      tenantId,
      status: "NEW",
      ...input,
      createdAt: now,
      updatedAt: now
    };
    this.productFeedback.set(rec.id, rec);
    return rec;
  }

  updateProductFeedbackStatus(
    tenantId: string,
    id: string,
    status: ProductFeedbackRecord["status"]
  ): ProductFeedbackRecord | null {
    const rec = this.productFeedback.get(id);
    if (!rec || rec.tenantId !== tenantId) return null;
    rec.status = status;
    rec.updatedAt = new Date().toISOString();
    return rec;
  }
}

export function createStore(): RicozKnowStore {
  return new RicozKnowStore();
}
