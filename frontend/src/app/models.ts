export type Role = "ADMIN" | "EDITOR" | "REVIEWER" | "AGENT_VIEWER";
export type ArticleStatus = "DRAFT" | "IN_REVIEW" | "PUBLISHED" | "ARCHIVED";
export type VersionStatus = "DRAFT" | "IN_REVIEW" | "REJECTED" | "APPROVED" | "SCHEDULED" | "PUBLISHED";
export type Visibility = "PUBLIC" | "AUTHENTICATED" | "PRIVATE";

export interface LoginRequest {
  tenantSlug: string;
  email: string;
  password: string;
  otp?: string;
}

export interface LoginResponse {
  accessToken: string;
  userId: string;
  tenantId: string;
  email: string;
  roles: string[];
}

export interface RegisterRequest {
  organizationName: string;
  tenantSlug: string;
  subdomain: string;
  email: string;
  password: string;
  name: string;
}

export interface RegisterResponse {
  tenantId: string;
  userId: string;
  tenantSlug: string;
  email: string;
  role: string;
  message: string;
}

export interface Tenant {
  tenantId: string;
  slug: string;
  name: string;
  portalTitle: string;
  logoUrl: string;
  primaryColor: string;
  customDomain?: string | null;
  customDomainVerified?: boolean;
  customDomainSslStatus?: string;
  supportEmail?: string | null;
  supportedLanguages?: string;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  parentId: string | null;
  sortOrder: number;
}

export interface Article {
  id: string;
  slug: string;
  title: string;
  status: ArticleStatus;
  visibility: Visibility;
  categoryId: string | null;
  activeVersionId: string | null;
  activeVersionNumber: number | null;
  language?: string;
  articleType?: string;
  pinned?: boolean;
  featured?: boolean;
  scheduledPublishAt?: string | null;
  scheduledUnpublishAt?: string | null;
}

export interface ArticleVersion {
  id: string;
  versionNumber: number;
  content: unknown;
  changeSummary: string | null;
  status: VersionStatus;
  createdBy: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  publishedAt: string | null;
  expiresAt: string | null;
}

export interface SearchHit {
  articleId: string | null;
  sourceId?: string | null;
  sourceType?: string | null;
  sourceUrl?: string | null;
  slug: string;
  title: string;
  excerpt: string;
  categoryId: string | null;
  language?: string | null;
  articleType?: string | null;
  tags?: string[];
  score: number;
}
export interface SearchFacetValue { value: string; count: number; }
export interface SearchResult {
  query: string;
  rewrittenQuery?: string;
  results: SearchHit[];
  total: number;
  facets?: {
    categories: SearchFacetValue[];
    languages: SearchFacetValue[];
    tags: SearchFacetValue[];
    articleTypes: SearchFacetValue[];
  };
  relatedQuestions?: string[];
}
export interface SavedSearch {
  id: string;
  email: string;
  queryText: string;
  categoryId?: string | null;
  language?: string | null;
  tag?: string | null;
  articleType?: string | null;
  dateFrom?: string | null;
  dateTo?: string | null;
  active: boolean;
}

export interface PortalArticleSummary {
  id: string;
  slug: string;
  title: string;
  categoryId: string | null;
  pinned?: boolean;
  featured?: boolean;
}

export interface PortalArticleDetail {
  id: string;
  slug: string;
  title: string;
  categoryId: string | null;
  visibility: Visibility;
  content: RichTextContent;
  versionNumber: number;
  pinned?: boolean;
  featured?: boolean;
  tags?: string[];
  relatedArticleIds?: string[];
  publishedAt?: string;
  translations?: { articleId: string; language: string; slug: string; title: string }[];
}

export interface User {
  id: string;
  email: string;
  name: string;
  status: string;
  roles: string[];
}

export interface Synonym {
  id: string;
  term: string;
  synonyms: string[];
}

export interface Asset {
  id: string;
  filename: string;
  mimeType: string;
  sizeBytes: number;
  downloadUrl: string;
}

export interface ArticlePerformance {
  articleId: string;
  title: string;
  views: number;
  helpfulCount: number;
  unhelpfulCount: number;
}

export interface KnowledgeGap {
  query: string;
  volumeCount: number;
  zeroResultCount: number;
  unsuccessfulCount: number;
  gapScore: number;
}

export interface DeflectionSummary {
  estimatedDeflectionCount: number;
  confirmedDeflectionCount: number;
  supportContactCount: number;
}

export interface StaleContent {
  articleId: string;
  versionId: string;
  title: string;
  expiresAt: string;
  alreadyExpired: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

// export interface RichTextBlock {
//   [key: string]: unknown;
// }

// export interface RichTextContent {
//   blocks: RichTextBlock[];
// }

export interface RichTextBlock {
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

export interface RagApiKey {
  id: string;
  name: string;
  prefix: string;
  status: "ACTIVE" | "REVOKED";
  createdAt: string;
  expiresAt: string | null;
  lastUsedAt: string | null;
  ipAllowlist?: string | null;
}

export interface CreatedRagApiKey {
  id: string;
  name: string;
  key: string;
  prefix: string;
  expiresAt: string | null;
}

export interface RagSearchResult {
  chunkId: string;
  articleId: string;
  versionId: string;
  slug: string;
  title: string;
  content: string;
  score: number;
  categoryId: string | null;
  categoryName: string | null;
  visibility: string;
  publishedAt: string | null;
}

export interface RagSearchResponse {
  query: string;
  results: RagSearchResult[];
}

export type WebhookEventType = "ARTICLE_PUBLISHED" | "ARTICLE_ARCHIVED";
export type WebhookDeliveryStatus = "PENDING" | "SUCCESS" | "FAILED";
export interface WebhookSubscription { id: string; url: string; eventTypes: WebhookEventType[]; active: boolean; secret: string | null; }
export interface WebhookDelivery { id: string; eventType: WebhookEventType; status: WebhookDeliveryStatus; attemptCount: number; lastError: string | null; createdAt: string; deliveredAt: string | null; }


export interface SessionView {
  id: string;
  deviceId: string | null;
  deviceName: string | null;
  userAgent: string | null;
  ipAddress: string | null;
  lastUsedAt: string | null;
  expiresAt: string | null;
}

export interface MfaEnrollment {
  secret: string;
  otpauthUri: string;
}

export interface MfaStatus {
  enabled: boolean;
}

export interface TenantSettings {
  tenantId: string;
  timezone: string;
  defaultLanguage: string;
  maxUsers: number;
  maxStorageBytes: number;
  maxApiRequestsMonthly: number;
  maxRagQueriesMonthly: number;
  maxAiCostCentsMonthly: number;
  settingsJson: string;
  createdAt: string;
  updatedAt: string;
}

export interface UsageMeter {
  id?: string;
  metric: string;
  periodStart: string;
  quantity: number | string;
}

export interface AiCostEvent {
  id?: string;
  provider: string;
  model: string;
  operation: string;
  inputTokens: number;
  outputTokens: number;
  embeddingTokens: number;
  costMicros: number;
  requestId: string | null;
  createdAt: string;
}

export interface AuditLog {
  id: string;
  actorUserId: string | null;
  entityType: string;
  entityId: string | null;
  action: string;
  oldState: string | null;
  newState: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  requestId: string | null;
  source: string | null;
  metadata: string | null;
  createdAt?: string;
}

export interface WebhookDeadLetter {
  id: string;
  deliveryId: string | null;
  subscriptionId: string | null;
  eventType: string;
  payload: string;
  reason: string | null;
  attemptCount: number;
  replayedAt: string | null;
}

export interface IndexStatus {
  articleId: string;
  activeVersionId: string | null;
  searchStatus: string;
  ragStatus: string;
  searchIndexedAt: string | null;
  ragIndexedAt: string | null;
  searchError: string | null;
  ragError: string | null;
}

export interface KnowledgeSource {
  id: string;
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


export interface SsoConnection { id:string; provider:"OIDC"|"SAML"; enabled:boolean; clientId:string|null; issuerUrl:string|null; authorizationUrl:string|null; tokenUrl:string|null; userinfoUrl:string|null; metadataUrl:string|null; entityId:string|null; settingsJson:string; }
export interface EnterpriseGroup { id:string; name:string; externalId:string|null; members?: User[]; }
export interface EnterpriseRole { id:string; name:string; custom:boolean; permissions:string; }
export interface AbExperiment { id:string; key:string; name:string; target:string; enabled:boolean; trafficPercent:number; variantsJson:string; }

export interface ArticleMetadata {
  tags: string[];
  relatedArticleIds: string[];
  pinned: boolean;
  featured: boolean;
}
export interface BlockComment {
  id: string;
  articleId: string;
  versionId: string;
  blockId: string;
  authorId: string;
  comment: string;
  parentId?: string | null;
  resolved: boolean;
  createdAt: string;
  resolvedAt: string | null;
}
export interface ArticleTemplate {
  key: string;
  name: string;
  description: string;
  blocks: { type: string; level: number | null; text: string | null; items: string[] | null }[];
}
export interface VersionDiff {
  fromVersionId: string;
  toVersionId: string;
  blocks: { blockId: string; change: "ADDED"|"REMOVED"|"CHANGED"|"MOVED"; before: unknown; after: unknown }[];
}
export interface AppNotification {
  id: string;
  type: string;
  title: string;
  message: string;
  articleId: string | null;
  versionId: string | null;
  read: boolean;
  createdAt: string;
}

export interface ArticleGovernance {articleId:string;ownerId:string|null;reviewIntervalDays:number;nextReviewAt:string|null;verifiedAt:string|null;verifiedBy:string|null;reviewDue:boolean;}
export interface KnowledgeHealth {total:number;published:number;dueForReview:number;unverified:number;stale:number;dueArticleIds:string[];}
export interface ArticleQuality {articleId:string;versionId:string|null;score:number;readyForVerification:boolean;warnings:string[];}
export interface ReviewHistory {id:string;versionId:string;reviewerId:string;decision:string;comment:string|null;createdAt:string;}
export interface ReusableBlock {id:string;name:string;block:Record<string,unknown>;}
export interface SavedArticleTemplate {id:string;key:string;name:string;description:string|null;content:{blocks:Record<string,unknown>[]};}

export interface VerificationEvent {action:string;actorId:string|null;details:string|null;at:string;}

export interface KnowledgeRequest {
  id: string;
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
}

export interface PlaybookStep {
  id: string;
  prompt: string;
  guidance?: string;
  options?: { label: string; nextStepId: string }[];
  resolution?: {
    title: string;
    summary: string;
    articleSlug?: string;
    actionLabel?: string;
  };
}

export interface Playbook {
  id: string;
  title: string;
  summary: string;
  category: string;
  published: boolean;
  runsCount: number;
  resolvedCount: number;
  updatedAt: string;
  steps: PlaybookStep[];
}

export interface BookmarkedArticle {
  slug: string;
  title: string;
  tenantSlug: string;
  savedAt: string;
}

export type ProductFeedbackType = "BUG" | "FEATURE";
export type ProductFeedbackSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type ProductFeedbackStatus = "NEW" | "TRIAGED" | "IN_PROGRESS" | "RESOLVED" | "WONT_FIX";

export interface ProductFeedback {
  id: string;
  tenantId: string;
  type: ProductFeedbackType;
  severity: ProductFeedbackSeverity;
  status: ProductFeedbackStatus;
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

export interface CreateProductFeedbackRequest {
  type: ProductFeedbackType;
  severity: ProductFeedbackSeverity;
  title: string;
  description: string;
  reporterEmail?: string | null;
  includeDiagnostics: boolean;
  pageUrl?: string | null;
  routePath?: string | null;
  userAgent?: string | null;
  viewport?: string | null;
  theme?: "light" | "dark" | null;
  language?: string | null;
}


