import { ArticleGovernance, KnowledgeHealth, ArticleQuality, ReviewHistory, ReusableBlock, SavedArticleTemplate, VerificationEvent } from "../models";
import { Injectable, inject } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import {
  Article,
  ArticlePerformance,
  ArticleVersion,
  Asset,
  Category,
  DeflectionSummary,
  KnowledgeGap,
  LoginRequest,
  LoginResponse,
  Page,
  PortalArticleDetail,
  PortalArticleSummary,
  RegisterRequest,
  RegisterResponse,
  SearchResult,
  StaleContent,
  Synonym,
  Tenant,
  User,
  Visibility,
  WebhookDelivery,
  WebhookEventType,
  WebhookSubscription,
  RagApiKey,
  CreatedRagApiKey,
  RagSearchResponse,
  SessionView, MfaEnrollment, MfaStatus, TenantSettings, UsageMeter, AiCostEvent, AuditLog, WebhookDeadLetter,
  IndexStatus, KnowledgeSource, ArticleMetadata, BlockComment, ArticleTemplate, VersionDiff, AppNotification, SavedSearch,
  ProductFeedback, CreateProductFeedbackRequest, ProductFeedbackStatus
} from "../models";

function normalizeSlug(value: string): string {
  return value.trim().toLowerCase();
}

@Injectable({ providedIn: "root" })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl =
    localStorage.getItem("rk_api_url")?.replace(/\/$/, "") || "";

  // Social sign-in: no provider credentials or OAuth code are persisted in the browser.
  socialStart(provider: "google" | "microsoft", tenantSlug: string): Observable<{authorizationUrl:string;state:string}> {
    return this.http.get<{authorizationUrl:string;state:string}>(`${this.baseUrl}/api/v1/auth/social/${provider}/start`,
      {params:{tenantSlug: normalizeSlug(tenantSlug)}});
  }
  socialLinkStart(provider: "google" | "microsoft", proof: string): Observable<{authorizationUrl:string;state:string}> {
    return this.http.post<{authorizationUrl:string;state:string}>(`${this.baseUrl}/api/v1/auth/social/${provider}/link/start`,{proof});
  }
  socialFinish(ticket: string, otp?: string): Observable<{mfaRequired:boolean;session?:LoginResponse}> {
    return this.http.post<{mfaRequired:boolean;session?:LoginResponse}>(`${this.baseUrl}/api/v1/auth/social/finish`, {ticket, otp:otp||null});
  }
  // Phase 8 developer ecosystem: staff JWT only; never call /mcp from the portal.
  developerKeys():Observable<any[]>{return this.http.get<any[]>(`${this.baseUrl}/api/v1/admin/rag-api-keys`);}
  developerCreateKey(name:string,scopes:string[],expiresAt?:string):Observable<any>{
    return this.http.post<any>(`${this.baseUrl}/api/v1/admin/rag-api-keys`,{name,scopes,expiresAt:expiresAt||null});
  }
  developerRevokeKey(id:string):Observable<void>{return this.http.post<void>(`${this.baseUrl}/api/v1/admin/rag-api-keys/${encodeURIComponent(id)}/revoke`,{});}
  developerUsage():Observable<any[]>{return this.http.get<any[]>(`${this.baseUrl}/api/v1/admin/developer/usage`);}
  developerReviewRequests():Observable<any[]>{return this.http.get<any[]>(`${this.baseUrl}/api/v1/admin/developer/review-requests`);}
  developerResolveReview(id:string):Observable<void>{return this.http.post<void>(`${this.baseUrl}/api/v1/admin/developer/review-requests/${encodeURIComponent(id)}/resolve`,{});}
  // Phase 5: all AI platform endpoints are staff-only; server enforces roles and tenant boundaries.
  aiPlatformWrite(body:{operation:string;text:string;language:string;guidance:string}):Observable<any>{return this.http.post<any>(`${this.baseUrl}/api/v1/ai-platform/writing`,body);}
  aiPlatformAgents():Observable<any[]>{return this.http.get<any[]>(`${this.baseUrl}/api/v1/ai-platform/agents`);}
  aiPlatformAgentCreate(body:any):Observable<any>{return this.http.post<any>(`${this.baseUrl}/api/v1/ai-platform/agents`,body);}
  aiPlatformAgentDisable(id:string):Observable<void>{return this.http.delete<void>(`${this.baseUrl}/api/v1/ai-platform/agents/${encodeURIComponent(id)}`);}
  aiPlatformAgentRun(id:string,question:string):Observable<any>{return this.http.post<any>(`${this.baseUrl}/api/v1/ai-platform/agents/${encodeURIComponent(id)}/runs`,{question});}
  aiPlatformAgentApprove(id:string):Observable<any>{return this.http.post<any>(`${this.baseUrl}/api/v1/ai-platform/agents/runs/${encodeURIComponent(id)}/approve`,{});}
  aiPlatformGraph():Observable<any>{return this.http.get<any>(`${this.baseUrl}/api/v1/ai-platform/graph`);}
  aiPlatformGraphRebuild():Observable<any>{return this.http.post<any>(`${this.baseUrl}/api/v1/ai-platform/graph/rebuild`,{});}
  aiPlatformQualityScan():Observable<any>{return this.http.post<any>(`${this.baseUrl}/api/v1/ai-platform/graph/quality/scan`,{});}
  aiPlatformQualityProposals():Observable<any[]>{return this.http.get<any[]>(`${this.baseUrl}/api/v1/ai-platform/graph/quality/proposals`);}
  aiPlatformReviewProposal(id:string,status:string):Observable<void>{return this.http.post<void>(`${this.baseUrl}/api/v1/ai-platform/graph/quality/proposals/${encodeURIComponent(id)}/review`,{status});}
  aiPlatformEvalCases():Observable<any[]>{return this.http.get<any[]>(`${this.baseUrl}/api/v1/ai-platform/evaluation/cases`);}
  aiPlatformEvalAdd(question:string,expectedArticleId:string,expectedPhrase:string):Observable<any>{return this.http.post<any>(`${this.baseUrl}/api/v1/ai-platform/evaluation/cases`,{question,expectedArticleId,expectedPhrase});}
  aiPlatformEvalRun():Observable<any>{return this.http.post<any>(`${this.baseUrl}/api/v1/ai-platform/evaluation/run`,{});}
  aiPlatformUsage():Observable<any>{return this.http.get<any>(`${this.baseUrl}/api/v1/ai-platform/governance/usage`);}
  collaborationTicket(articleId:string,versionId:string):Observable<{token:string;room:string;websocketUrl:string;expiresInSeconds:number}> {
    return this.http.post<{token:string;room:string;websocketUrl:string;expiresInSeconds:number}>(
      `${this.baseUrl}/api/v1/articles/${articleId}/collaboration/${versionId}/ticket`,{});
  }
  collaborationSnapshot(articleId: string, versionId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/api/v1/articles/${articleId}/collaboration/${versionId}`);
  }
  collaborationSave(articleId: string, versionId: string, value: any): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/api/v1/articles/${articleId}/collaboration/${versionId}`,value);
  }
  collaborationHeartbeat(articleId: string, versionId: string): Observable<any[]> {
    return this.http.post<any[]>(`${this.baseUrl}/api/v1/articles/${articleId}/collaboration/${versionId}/presence`,{});
  }
  collaborationLeave(articleId: string, versionId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/articles/${articleId}/collaboration/${versionId}/presence`);
  }
  knowledgeDiagnostics(): Observable<{ total: number; published: number; drafts: number; inReview: number; searchFailures: number; ragFailures: number; issueCount: number; generatedAt: string; issues: {articleId: string; title: string; code: string; severity: string; detail: string}[] }> {
    return this.http.get<any>(`${this.baseUrl}/api/v1/knowledge-health`);
  }

  innovationQuality(): Observable<any> { return this.http.get<any>(`${this.baseUrl}/api/v1/innovation/quality`); }
  oauthConnections(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/v1/connectors/oauth/connections`); }
  oauthAuthorize(provider:string): Observable<{url:string}> { return this.http.post<{url:string}>(`${this.baseUrl}/api/v1/connectors/oauth/${encodeURIComponent(provider)}/authorize`,{}); }
  oauthDisconnect(provider:string): Observable<void> { return this.http.delete<void>(`${this.baseUrl}/api/v1/connectors/oauth/${encodeURIComponent(provider)}`); }
  enterpriseSearch(query:string, provider?:string, language?:string, limit=15, categoryId?:string, from?:string, to?:string):Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/api/v1/enterprise-search/query`,
      {query,provider:provider||null,language:language||null,limit,categoryId:categoryId||null,from:from||null,to:to||null,articles:true,connectors:true});
  }
  enterpriseAnswer(query:string,provider?:string):Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/api/v1/enterprise-search/answer`,{query,provider:provider||null,limit:6});
  }
  enterpriseSearchFeedback(id:string,sourceType:string):Observable<void>{return this.http.post<void>(`${this.baseUrl}/api/v1/enterprise-search/feedback`,{id,sourceType});}
  enterpriseSearchInsights():Observable<any>{return this.http.get<any>(`${this.baseUrl}/api/v1/enterprise-search/insights`);}
  enterpriseSearchRewrite(query:string):Observable<{query:string;alternatives:string[]}>{return this.http.post<any>(`${this.baseUrl}/api/v1/enterprise-search/rewrite`,{query});}
  enterpriseSearchEvalCases():Observable<any[]>{return this.http.get<any[]>(`${this.baseUrl}/api/v1/enterprise-search/evaluation/cases`);}
  enterpriseSearchEvalAdd(question:string,expectedIds:string[]):Observable<any>{return this.http.post<any>(`${this.baseUrl}/api/v1/enterprise-search/evaluation/cases`,{question,expectedIds});}
  enterpriseSearchEvalRun():Observable<any>{return this.http.post<any>(`${this.baseUrl}/api/v1/enterprise-search/evaluation/run`,{});}
    connectorCatalog(): Observable<any[]> {return this.http.get<any[]>(`${this.baseUrl}/api/v1/connectors/catalog`);}
  connectorBrowse(provider:string,cursor?:string):Observable<{items:any[];nextCursor:string|null}>{return this.http.get<any>(`${this.baseUrl}/api/v1/connectors/${encodeURIComponent(provider)}/browse`,{params:this.params({cursor})});}
  connectorSources():Observable<any[]>{return this.http.get<any[]>(`${this.baseUrl}/api/v1/connectors/sources`);}
  connectorSelect(provider:string,remoteId:string,name:string,intervalMinutes:number):Observable<any>{return this.http.post<any>(`${this.baseUrl}/api/v1/connectors/${encodeURIComponent(provider)}/sources`,{remoteId,name,intervalMinutes});}
  connectorSync(id:string):Observable<void>{return this.http.post<void>(`${this.baseUrl}/api/v1/connectors/sources/${id}/sync`,{});}
  connectorReindex(id:string):Observable<void>{return this.http.post<void>(`${this.baseUrl}/api/v1/connectors/sources/${id}/reindex`,{});}
  connectorDisable(id:string):Observable<void>{return this.http.post<void>(`${this.baseUrl}/api/v1/connectors/sources/${id}/disable`,{});}
  connectorRemove(id:string):Observable<void>{return this.http.delete<void>(`${this.baseUrl}/api/v1/connectors/sources/${id}`);}
  connectorRuns(id:string):Observable<any[]>{return this.http.get<any[]>(`${this.baseUrl}/api/v1/connectors/sources/${id}/runs`);}
  connectorSearch(q:string):Observable<any[]>{return this.http.get<any[]>(`${this.baseUrl}/api/v1/connectors/documents/search`,{params:this.params({q})});}
  innovationCatalog(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/v1/innovation/connectors/catalog`); }
  innovationSources(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/v1/innovation/connectors/sources`); }
  innovationAsk(question: string, conversationId: string | null): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/api/v1/innovation/copilot/ask`, {question, conversationId});
  }

  private params(values: Record<string, string | number | boolean | undefined | null>): HttpParams {
    let params = new HttpParams();

    for (const [key, value] of Object.entries(values)) {
      if (value !== undefined && value !== null && value !== "") {
        params = params.set(key, String(value));
      }
    }

    return params;
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      `${this.baseUrl}/api/v1/auth/login`,
      {
        ...request,
        tenantSlug: normalizeSlug(request.tenantSlug),
        email: request.email.trim().toLowerCase()
      },
      { withCredentials: true }
    );
  }

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.baseUrl}/api/v1/auth/register`, {
      ...request,
      tenantSlug: normalizeSlug(request.tenantSlug),
      subdomain: normalizeSlug(request.subdomain),
      email: request.email.trim().toLowerCase()
    });
  }

  refresh(): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/api/v1/auth/refresh`, {}, { withCredentials: true });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/auth/logout`, {}, { withCredentials: true });
  }

  tenantLookup(subdomain: string): Observable<Tenant> {
    return this.http.get<Tenant>(`${this.baseUrl}/api/v1/tenants/lookup`, {
      params: this.params({ subdomain })
    });
  }

  listArticles(status?: string, page = 0, size = 10): Observable<Page<Article>> {
    return this.http.get<Page<Article>>(`${this.baseUrl}/api/v1/articles`, {
      params: this.params({ status, page, size })
    });
  }

  getArticle(id: string): Observable<Article> {
    return this.http.get<Article>(`${this.baseUrl}/api/v1/articles/${id}`);
  }

  getVersions(articleId: string): Observable<ArticleVersion[]> {
    return this.http.get<ArticleVersion[]>(`${this.baseUrl}/api/v1/articles/${articleId}/versions`);
  }

  createArticle(request: {
    title: string;
    slug: string;
    categoryId: string | null;
    content: unknown;
  }): Observable<Article> {
    return this.http.post<Article>(`${this.baseUrl}/api/v1/articles`, request);
  }

  updateDraft(articleId: string, versionId: string, content: unknown, changeSummary: string): Observable<ArticleVersion> {
    return this.http.put<ArticleVersion>(
      `${this.baseUrl}/api/v1/articles/${articleId}/versions/${versionId}`,
      { content, changeSummary }
    );
  }

  createDraftVersion(articleId: string): Observable<ArticleVersion> {
    return this.http.post<ArticleVersion>(`${this.baseUrl}/api/v1/articles/${articleId}/versions`, {});
  }

  submitForReview(articleId: string, versionId: string): Observable<ArticleVersion> {
    return this.http.post<ArticleVersion>(
      `${this.baseUrl}/api/v1/articles/${articleId}/versions/${versionId}/submit`,
      {}
    );
  }

  reviewVersion(
    articleId: string,
    versionId: string,
    approve: boolean,
    comment: string
  ): Observable<ArticleVersion> {
    return this.http.post<ArticleVersion>(
      `${this.baseUrl}/api/v1/articles/${articleId}/versions/${versionId}/review`,
      { approve, comment }
    );
  }

  articleTemplates(): Observable<ArticleTemplate[]> {
    return this.http.get<ArticleTemplate[]>(`${this.baseUrl}/api/v1/articles/templates`);
  }

  articleMetadata(articleId: string): Observable<ArticleMetadata> {
    return this.http.get<ArticleMetadata>(`${this.baseUrl}/api/v1/articles/${articleId}/metadata`);
  }

  updateArticleMetadata(articleId: string, request: Partial<ArticleMetadata>): Observable<ArticleMetadata> {
    return this.http.put<ArticleMetadata>(`${this.baseUrl}/api/v1/articles/${articleId}/metadata`, request);
  }

  blockComments(articleId: string, versionId: string): Observable<BlockComment[]> {
    return this.http.get<BlockComment[]>(`${this.baseUrl}/api/v1/articles/${articleId}/versions/${versionId}/comments`);
  }

  addBlockComment(articleId: string, versionId: string, blockId: string, comment: string): Observable<BlockComment> {
    return this.http.post<BlockComment>(`${this.baseUrl}/api/v1/articles/${articleId}/versions/${versionId}/comments`, { blockId, comment });
  }

  resolveBlockComment(articleId: string, commentId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/articles/${articleId}/comments/${commentId}/resolve`, {});
  }

  versionDiff(articleId: string, fromVersionId: string, toVersionId: string): Observable<VersionDiff> {
    return this.http.get<VersionDiff>(`${this.baseUrl}/api/v1/articles/${articleId}/versions/diff`, {
      params: this.params({ fromVersionId, toVersionId })
    });
  }

  rollbackArticle(articleId: string, versionId: string, changeSummary?: string): Observable<ArticleVersion> {
    return this.http.post<ArticleVersion>(`${this.baseUrl}/api/v1/articles/${articleId}/rollback`, { versionId, changeSummary });
  }

  schedulePublish(articleId: string, versionId: string, at: string): Observable<ArticleVersion> {
    return this.http.post<ArticleVersion>(`${this.baseUrl}/api/v1/articles/${articleId}/versions/${versionId}/schedule-publish`, { at });
  }

  scheduleUnpublish(articleId: string, at: string): Observable<Article> {
    return this.http.post<Article>(`${this.baseUrl}/api/v1/articles/${articleId}/schedule-unpublish`, { at });
  }

  clearArticleSchedule(articleId: string): Observable<Article> {
    return this.http.delete<Article>(`${this.baseUrl}/api/v1/articles/${articleId}/schedule`);
  }

  importArticles(files: File[]): Observable<{ imported: number; articles: string[] }> {
    const body = new FormData();
    files.forEach(file => body.append("files", file));
    return this.http.post<{ imported: number; articles: string[] }>(`${this.baseUrl}/api/v1/articles/bulk/import`, body);
  }

  exportArticles(format: "csv" | "markdown" | "html" | "pdf" | "docx"): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/api/v1/articles/bulk/export`, {
      params: this.params({ format }),
      responseType: "blob"
    });
  }

  approveVersionForSchedule(articleId:string,versionId:string,comment:string): Observable<ArticleVersion> {
    return this.http.post<ArticleVersion>(`${this.baseUrl}/api/v1/articles/${articleId}/versions/${versionId}/approve-for-schedule`,{approve:true,comment});
  }
  reviewHistory(articleId:string): Observable<ReviewHistory[]> {
    return this.http.get<ReviewHistory[]>(`${this.baseUrl}/api/v1/articles/${articleId}/review-history`);
  }
  articleGovernance(articleId:string): Observable<ArticleGovernance> {
    return this.http.get<ArticleGovernance>(`${this.baseUrl}/api/v1/articles/governance/${articleId}`);
  }
  updateGovernance(articleId:string,request:{ownerId?:string|null;reviewIntervalDays?:number}): Observable<ArticleGovernance> {
    return this.http.put<ArticleGovernance>(`${this.baseUrl}/api/v1/articles/governance/${articleId}`,request);
  }
  verifyArticle(articleId:string): Observable<ArticleGovernance> {
    return this.http.post<ArticleGovernance>(`${this.baseUrl}/api/v1/articles/governance/${articleId}/verify`,{});
  }
  verificationHistory(articleId:string): Observable<VerificationEvent[]> {
    return this.http.get<VerificationEvent[]>(`${this.baseUrl}/api/v1/articles/governance/${articleId}/history`);
  }
  articleQuality(articleId:string): Observable<ArticleQuality> {
    return this.http.get<ArticleQuality>(`${this.baseUrl}/api/v1/articles/governance/${articleId}/quality`);
  }
  knowledgeHealth():Observable<KnowledgeHealth> {
    return this.http.get<KnowledgeHealth>(`${this.baseUrl}/api/v1/articles/governance/health`);
  }
  reusableBlocks():Observable<ReusableBlock[]> {
    return this.http.get<ReusableBlock[]>(`${this.baseUrl}/api/v1/articles/library/blocks`);
  }
  createReusableBlock(name:string,block:Record<string,unknown>):Observable<ReusableBlock> {
    return this.http.post<ReusableBlock>(`${this.baseUrl}/api/v1/articles/library/blocks`,{name,block});
  }
  customArticleTemplates():Observable<SavedArticleTemplate[]> {
    return this.http.get<SavedArticleTemplate[]>(`${this.baseUrl}/api/v1/articles/library/templates`);
  }
  createCustomArticleTemplate(key:string,name:string,content:unknown):Observable<SavedArticleTemplate> {
    return this.http.post<SavedArticleTemplate>(`${this.baseUrl}/api/v1/articles/library/templates`,{key,name,content});
  }
  addThreadReply(articleId:string,versionId:string,blockId:string,comment:string,parentId:string|null):Observable<import("../models").BlockComment> {
    return this.http.post<import("../models").BlockComment>(`${this.baseUrl}/api/v1/articles/${articleId}/versions/${versionId}/comments`,{blockId,comment,parentId});
  }
  notifications(): Observable<{ items: AppNotification[]; unreadCount: number }> {
    return this.http.get<{ items: AppNotification[]; unreadCount: number }>(`${this.baseUrl}/api/v1/notifications`);
  }

  markNotificationRead(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/notifications/${id}/read`, {});
  }

  markAllNotificationsRead(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/notifications/read-all`, {});
  }

  archiveArticle(articleId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/articles/${articleId}/archive`, {});
  }

  setVisibility(articleId: string, visibility: Visibility): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/api/v1/articles/${articleId}/visibility`, null, {
      params: this.params({ visibility })
    });
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.baseUrl}/api/v1/categories`);
  }

  createCategory(request: {
    name: string;
    slug: string;
    parentId: string | null;
    sortOrder: number | null;
  }): Observable<Category> {
    return this.http.post<Category>(`${this.baseUrl}/api/v1/categories`, request);
  }

  updateCategory(id: string, request: {
    name: string;
    slug: string;
    parentId: string | null;
    sortOrder: number | null;
  }): Observable<Category> {
    return this.http.put<Category>(`${this.baseUrl}/api/v1/categories/${id}`, request);
  }

  deleteCategory(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/categories/${id}`);
  }

  search(query: string, category?: string, page = 0): Observable<SearchResult> {
    return this.http.get<SearchResult>(`${this.baseUrl}/api/v1/search`, {
      params: this.params({ q: query, category, page })
    });
  }

  synonyms(): Observable<Synonym[]> {
    return this.http.get<Synonym[]>(`${this.baseUrl}/api/v1/admin/synonyms`);
  }

  upsertSynonym(term: string, synonyms: string[]): Observable<Synonym> {
    return this.http.post<Synonym>(`${this.baseUrl}/api/v1/admin/synonyms`, { term, synonyms });
  }

  deleteSynonym(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/admin/synonyms/${id}`);
  }

  users(): Observable<User[]> {
    return this.http.get<User[]>(`${this.baseUrl}/api/v1/users`);
  }

  createUser(request: {
    email: string;
    name: string;
    password: string;
    roles: string[];
  }): Observable<User> {
    return this.http.post<User>(`${this.baseUrl}/api/v1/users`, request);
  }

  disableUser(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/users/${id}/disable`, {});
  }

  uploadAsset(file: File, articleId?: string): Observable<Asset> {
    const body = new FormData();
    body.append("file", file);

    if (articleId) {
      body.append("articleId", articleId);
    }

    return this.http.post<Asset>(`${this.baseUrl}/api/v1/assets`, body);
  }

  performance(days = 30): Observable<ArticlePerformance[]> {
    return this.http.get<ArticlePerformance[]>(`${this.baseUrl}/api/v1/analytics/articles/performance`, {
      params: this.params({ days })
    });
  }

  knowledgeGaps(days = 30, limit = 20): Observable<KnowledgeGap[]> {
    return this.http.get<KnowledgeGap[]>(`${this.baseUrl}/api/v1/analytics/knowledge-gaps`, {
      params: this.params({ days, limit })
    });
  }

  deflection(days = 30): Observable<DeflectionSummary> {
    return this.http.get<DeflectionSummary>(`${this.baseUrl}/api/v1/analytics/deflection`, {
      params: this.params({ days })
    });
  }

  staleContent(horizonDays = 30): Observable<StaleContent[]> {
    return this.http.get<StaleContent[]>(`${this.baseUrl}/api/v1/analytics/stale-content`, {
      params: this.params({ horizonDays })
    });
  }

  aggregate(date?: string): Observable<{ status: string; date: string }> {
    return this.http.post<{ status: string; date: string }>(
      `${this.baseUrl}/api/v1/analytics/aggregate`,
      {},
      { params: this.params({ date }) }
    );
  }

  reindex(): Observable<{ indexed: number }> {
    return this.http.post<{ indexed: number }>(`${this.baseUrl}/api/v1/admin/search/reindex`, {});
  }

  updateBranding(request: { portalTitle: string; logoUrl: string; primaryColor: string; customDomain?: string | null; supportEmail?: string | null; supportedLanguages?: string }): Observable<Tenant> {
    return this.http.put<Tenant>(`${this.baseUrl}/api/v1/admin/branding`, request);
  }

  ragApiKeys(): Observable<RagApiKey[]> {
    return this.http.get<RagApiKey[]>(`${this.baseUrl}/api/v1/admin/rag-api-keys`);
  }

  createRagApiKey(request: { name: string; expiresAt?: string }): Observable<CreatedRagApiKey> {
    return this.http.post<CreatedRagApiKey>(`${this.baseUrl}/api/v1/admin/rag-api-keys`, request);
  }

  revokeRagApiKey(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/admin/rag-api-keys/${id}/revoke`, {});
  }

  ragSearch(
    apiKey: string,
    query: string,
    topK = 5,
    categoryId?: string
  ): Observable<RagSearchResponse> {
    return this.http.post<RagSearchResponse>(
      `${this.baseUrl}/api/v1/rag/search`,
      { query, topK, categoryId: categoryId || null },
      { headers: { "X-API-Key": apiKey } }
    );
  }

  webhooks(): Observable<WebhookSubscription[]> { return this.http.get<WebhookSubscription[]>(`${this.baseUrl}/api/v1/admin/webhooks`); }
  createWebhook(request: { url: string; eventTypes: WebhookEventType[] }): Observable<WebhookSubscription> { return this.http.post<WebhookSubscription>(`${this.baseUrl}/api/v1/admin/webhooks`, request); }
  deactivateWebhook(id: string): Observable<void> { return this.http.post<void>(`${this.baseUrl}/api/v1/admin/webhooks/${id}/deactivate`, {}); }
  deleteWebhook(id: string): Observable<void> { return this.http.delete<void>(`${this.baseUrl}/api/v1/admin/webhooks/${id}`); }
  webhookDeliveries(id: string): Observable<WebhookDelivery[]> { return this.http.get<WebhookDelivery[]>(`${this.baseUrl}/api/v1/admin/webhooks/${id}/deliveries`); }

  // portalArticles(categoryId?: string, page = 0): Observable<Page<PortalArticleSummary>> {
  //   return this.http.get<Page<PortalArticleSummary>>(`${this.baseUrl}/api/v1/portal/articles`, {
  //     params: this.params({ categoryId, page })
  //   });
  // }

  // portalArticle(slug: string): Observable<PortalArticleDetail> {
  //   return this.http.get<PortalArticleDetail>(
  //     `${this.baseUrl}/api/v1/portal/articles/${encodeURIComponent(slug)}`
  //   );
  // }

  // portalSearch(query: string, categoryId?: string, page = 0): Observable<SearchResult> {
  //   return this.http.get<SearchResult>(`${this.baseUrl}/api/v1/portal/search`, {
  //     params: this.params({ q: query, categoryId, page })
  //   });
  // }

  // portalFeedback(articleId: string, helpful: boolean): Observable<void> {
  //   return this.http.post<void>(
  //     `${this.baseUrl}/api/v1/portal/articles/${articleId}/feedback`,
  //     null,
  //     { params: this.params({ helpful }) }
  //   );
  // }

  // portalArticles(
  //   categoryId?: string,
  //   page = 0
  // ): Observable<Page<PortalArticleSummary>> {
  //   return this.http.get<Page<PortalArticleSummary>>(
  //     `${this.baseUrl}/api/v1/portal/articles`,
  //     {
  //       params: this.params({
  //         categoryId,
  //         page,
  //         language,
  //         tenant: this.portalTenant()
  //       }), withCredentials: true
  //     }
  //   );
  // }

  portalArticles(
    categoryId?: string,
    page = 0,
    language?: string,
    options?: { pinned?: boolean; featured?: boolean }
  ): Observable<Page<PortalArticleSummary>> {
    return this.http.get<Page<PortalArticleSummary>>(
      `${this.baseUrl}/api/v1/portal/articles`,
      {
        params: this.params({
          categoryId,
          page,
          language,
          pinned: options?.pinned,
          featured: options?.featured,
          tenant: this.portalTenant()
        }),
        withCredentials: true
      }
    );
  }

  portalArticle(slug: string): Observable<PortalArticleDetail> {
    return this.http.get<PortalArticleDetail>(
      `${this.baseUrl}/api/v1/portal/articles/${encodeURIComponent(slug)}`,
      {
        params: this.params({
          tenant: this.portalTenant()
        }), withCredentials: true
      }
    );
  }

  portalSearch(
    query: string,
    categoryId?: string,
    page = 0,
    language?: string,
    filters?: { tag?: string; articleType?: string; dateFrom?: string; dateTo?: string; sourceType?: string }
  ): Observable<SearchResult> {
    return this.http.get<SearchResult>(`${this.baseUrl}/api/v1/portal/search`, {
      params: this.params({
        q: query, categoryId, page, language,
        tag: filters?.tag, articleType: filters?.articleType,
        dateFrom: filters?.dateFrom, dateTo: filters?.dateTo,
        sourceType: filters?.sourceType, tenant: this.portalTenant()
      }), withCredentials: true
    });
  }

  saveSearch(request: {email:string;query:string;categoryId?:string;language?:string;tag?:string;articleType?:string;dateFrom?:string;dateTo?:string}): Observable<SavedSearch> {
    return this.http.post<SavedSearch>(`${this.baseUrl}/api/v1/portal/search/saved`, request, { params: this.params({tenant:this.portalTenant()}), withCredentials:true });
  }
  savedSearches(): Observable<SavedSearch[]> {
    return this.http.get<SavedSearch[]>(`${this.baseUrl}/api/v1/portal/search/saved`, { params: this.params({tenant:this.portalTenant()}), withCredentials:true });
  }
  deleteSavedSearch(id:string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/portal/search/saved/${id}`, { params:this.params({tenant:this.portalTenant()}), withCredentials:true });
  }

  portalFeedback(
    articleId: string,
    helpful: boolean
  ): Observable<void> {
    return this.http.post<void>(
      `${this.baseUrl}/api/v1/portal/articles/${articleId}/feedback`,
      null,
      {
        params: this.params({
          helpful,
          tenant: this.portalTenant()
        })
      }
    );
  }

  agentSearch(query: string, category?: string, page = 0): Observable<SearchResult> {
    return this.http.get<SearchResult>(`${this.baseUrl}/api/v1/agent/search`, {
      params: this.params({ q: query, category, page })
    });
  }

  agentArticle(id: string): Observable<PortalArticleDetail> {
    return this.http.get<PortalArticleDetail>(`${this.baseUrl}/api/v1/agent/articles/${id}`);
  }


  getCurrentBranding(): Observable<Tenant> {
    return this.http.get<Tenant>(
      `${this.baseUrl}/api/v1/tenants/me`
    );
  }


  private portalTenant(): string | null {
    const explicit = new URLSearchParams(window.location.search).get("tenant");
    if (explicit) return explicit;
    // Local development often serves Angular from a tenant subdomain while the
    // API runs on localhost:8080. Pass the tenant slug explicitly in that case.
    const hostname = window.location.hostname.toLowerCase();
    const baseDomain = ".ricozknow.com";
    if (hostname.endsWith(baseDomain)) {
      const prefix = hostname.slice(0, -baseDomain.length);
      if (prefix && !prefix.includes(".")) return prefix;
    }
    return null;
  }



  getCategory(id: string): Observable<Category> {
    return this.http.get<Category>(`${this.baseUrl}/api/v1/categories/${id}`);
  }

  getAsset(id: string): Observable<Asset> {
    return this.http.get<Asset>(`${this.baseUrl}/api/v1/assets/${id}`);
  }

  portalSearchClick(articleId: string, query: string, position: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/portal/search/click`, null, {
      params: this.params({ articleId, q: query, position, tenant: this.portalTenant() })
    });
  }

  verifyEmail(token: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/auth/verify-email`, { token });
  }

  resendVerification(tenantSlug: string, email: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/auth/resend-verification`, {
      tenantSlug: normalizeSlug(tenantSlug), email: email.trim().toLowerCase()
    });
  }

  forgotPassword(tenantSlug: string, email: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/auth/forgot-password`, {
      tenantSlug: normalizeSlug(tenantSlug), email: email.trim().toLowerCase()
    });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/auth/reset-password`, { token, newPassword });
  }

  acceptInvitation(token: string, password: string, name: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/auth/accept-invitation`, { token, password, name });
  }

  // --- Account, security and tenant administration ---
  updateUser(id: string, request: { email: string; name: string; roles: string[] }): Observable<User> {
    return this.http.put<User>(`${this.baseUrl}/api/v1/users/${id}`, request);
  }

  enableUser(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/users/${id}/enable`, {});
  }

  revokeUserSessions(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/users/${id}/revoke-sessions`, {});
  }

  inviteUser(request: { email: string; name: string; roles: string[] }): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/users/invite`, request);
  }

  sessions(): Observable<SessionView[]> {
    return this.http.get<SessionView[]>(`${this.baseUrl}/api/v1/auth/sessions`);
  }

  revokeSession(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/auth/sessions/${id}`);
  }

  mfaStatus(): Observable<MfaStatus> {
    return this.http.get<MfaStatus>(`${this.baseUrl}/api/v1/auth/mfa/status`);
  }

  enrollMfa(): Observable<MfaEnrollment> {
    return this.http.post<MfaEnrollment>(`${this.baseUrl}/api/v1/auth/mfa/enroll`, {});
  }

  enableMfa(code: string): Observable<{ recoveryCodes: string[] }> {
    return this.http.post<{ recoveryCodes: string[] }>(`${this.baseUrl}/api/v1/auth/mfa/enable`, { code });
  }

  regenerateMfaRecoveryCodes(code: string): Observable<{ recoveryCodes: string[] }> {
    return this.http.post<{ recoveryCodes: string[] }>(
      `${this.baseUrl}/api/v1/auth/mfa/recovery-codes/regenerate`, { code });
  }

  disableMfa(code: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/auth/mfa/disable`, { code });
  }

  adminMfaRecoveryRequests(): Observable<{content: any[]}> {
    return this.http.get<{content: any[]}>(`${this.baseUrl}/api/v1/admin/mfa-recovery`);
  }
  requestAdminMfaRecovery(targetUserId: string, externalTicket: string, reason: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/api/v1/admin/mfa-recovery`,
      { targetUserId, externalTicket, reason });
  }
  approveAdminMfaRecovery(id: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/api/v1/admin/mfa-recovery/${id}/approve`, {});
  }

  tenantSettings(): Observable<TenantSettings> {
    return this.http.get<TenantSettings>(`${this.baseUrl}/api/v1/admin/tenant-settings`);
  }

  saveTenantSettings(settings: Partial<TenantSettings>): Observable<TenantSettings> {
    return this.http.put<TenantSettings>(`${this.baseUrl}/api/v1/admin/tenant-settings`, settings);
  }

  adminHealth(): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.baseUrl}/api/v1/admin/health`);
  }

  usage(): Observable<UsageMeter[]> {
    return this.http.get<UsageMeter[]>(`${this.baseUrl}/api/v1/admin/usage`);
  }

  aiCosts(from: string, to: string): Observable<AiCostEvent[]> {
    return this.http.get<AiCostEvent[]>(`${this.baseUrl}/api/v1/admin/usage/ai-costs`, {
      params: this.params({ from, to })
    });
  }

  auditLogs(page = 0, size = 50): Observable<Page<AuditLog>> {
    return this.http.get<Page<AuditLog>>(`${this.baseUrl}/api/v1/admin/audit-logs`, {
      params: this.params({ page, size })
    });
  }

  webhookDlq(): Observable<WebhookDeadLetter[]> {
    return this.http.get<WebhookDeadLetter[]>(`${this.baseUrl}/api/v1/admin/webhooks/dlq`);
  }

  replayWebhookDlq(id: string): Observable<WebhookDeadLetter> {
    return this.http.post<WebhookDeadLetter>(`${this.baseUrl}/api/v1/admin/webhooks/dlq/${id}/replay`, {});
  }

  reconcileSearch(): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/api/v1/admin/search/reconcile`, {});
  }

  indexStatus(articleId?: string): Observable<IndexStatus[]> {
    return this.http.get<IndexStatus[]>(`${this.baseUrl}/api/v1/admin/search/index-status`, {
      params: this.params({ articleId })
    });
  }

  pendingIndexJobs(): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.baseUrl}/api/v1/admin/search/jobs/pending`);
  }

  indexJobs(): Observable<Record<string, unknown>[]> {
    return this.http.get<Record<string, unknown>[]>(`${this.baseUrl}/api/v1/admin/search/jobs`);
  }

  // --- Knowledge ingestion ---
  knowledgeSources(): Observable<KnowledgeSource[]> {
    return this.http.get<KnowledgeSource[]>(`${this.baseUrl}/api/v1/knowledge-sources`);
  }

  uploadKnowledgeDocument(file: File, language = "en", syncIntervalMinutes?: number): Observable<KnowledgeSource> {
    const body = new FormData();
    body.append("file", file);
    return this.http.post<KnowledgeSource>(`${this.baseUrl}/api/v1/knowledge-sources/documents`, body, {
      params: this.params({ language, syncIntervalMinutes })
    });
  }

  uploadKnowledgeDocuments(files: File[], language = "en", syncIntervalMinutes?: number): Observable<KnowledgeSource[]> {
    const body = new FormData();
    for (const file of files) body.append("files", file);
    return this.http.post<KnowledgeSource[]>(`${this.baseUrl}/api/v1/knowledge-sources/documents/bulk`, body, {
      params: this.params({ language, syncIntervalMinutes })
    });
  }

  addKnowledgeUrl(url: string, name?: string, language = "en", syncIntervalMinutes?: number, maxPages = 25): Observable<KnowledgeSource> {
    return this.http.post<KnowledgeSource>(`${this.baseUrl}/api/v1/knowledge-sources/url`, null, {
      params: this.params({ url, name, language, syncIntervalMinutes, maxPages })
    });
  }

  addKnowledgeWebsite(url: string, name?: string, language = "en", syncIntervalMinutes?: number, maxPages = 25): Observable<KnowledgeSource> {
    return this.http.post<KnowledgeSource>(`${this.baseUrl}/api/v1/knowledge-sources/website`, null, {
      params: this.params({ url, name, language, syncIntervalMinutes, maxPages })
    });
  }

  syncKnowledgeSource(id: string): Observable<KnowledgeSource> {
    return this.http.post<KnowledgeSource>(`${this.baseUrl}/api/v1/knowledge-sources/${id}/sync`, {});
  }

  reindexKnowledgeSource(id: string): Observable<KnowledgeSource> {
    return this.http.post<KnowledgeSource>(`${this.baseUrl}/api/v1/knowledge-sources/${id}/reindex`, {});
  }

  retryKnowledgeSource(id: string): Observable<KnowledgeSource> {
    return this.http.post<KnowledgeSource>(`${this.baseUrl}/api/v1/knowledge-sources/${id}/retry`, {});
  }

  deleteKnowledgeSource(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/knowledge-sources/${id}`);
  }

  // --- AI knowledge intelligence ---
  aiGenerate(request: { topic: string; sourceMaterial?: string; categoryId?: string; audience?: string; language?: string }): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/api/v1/articles/ai/generate`, request);
  }

  aiImprove(content: unknown, instruction?: string): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/api/v1/articles/ai/improve`, { content, instruction });
  }

  aiGenerateAndCreateDraft(request: { topic: string; sourceMaterial?: string; categoryId?: string; audience?: string; language?: string }): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/api/v1/articles/ai/generate-and-create-draft`, request);
  }

  articleIntelligence(articleId: string): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.baseUrl}/api/v1/articles/${articleId}/intelligence`);
  }

  analyzeArticleIntelligence(articleId: string): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/api/v1/articles/${articleId}/intelligence/analyze`, {});
  }

  detectArticleDependencies(articleId: string): Observable<Record<string, unknown>[]> {
    return this.http.post<Record<string, unknown>[]>(`${this.baseUrl}/api/v1/articles/${articleId}/dependencies/detect`, {});
  }

  articleDependencies(articleId: string): Observable<Record<string, unknown>[]> {
    return this.http.get<Record<string, unknown>[]>(`${this.baseUrl}/api/v1/articles/${articleId}/dependencies`);
  }

  articleDependents(articleId: string): Observable<Record<string, unknown>[]> {
    return this.http.get<Record<string, unknown>[]>(`${this.baseUrl}/api/v1/articles/${articleId}/dependents`);
  }

  knowledgeGapTickets(days = 30, limit = 20): Observable<Record<string, unknown>[]> {
    return this.http.get<Record<string, unknown>[]>(`${this.baseUrl}/api/v1/admin/knowledge-gaps`, {
      params: this.params({ days, limit })
    });
  }

  generateGapSuggestion(id: string): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/api/v1/admin/knowledge-gaps/${id}/generate-suggestion`, {});
  }

  createGapDraft(id: string): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/api/v1/admin/knowledge-gaps/${id}/create-draft`, {});
  }

  // --- RAG answer, conversations and evaluation ---
  ragAnswer(request: {
    query: string; conversationId?: string; topK?: number; categoryId?: string;
    visibilities?: string[]; articleIds?: string[]; language?: string;
  }, apiKey: string): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/api/v1/rag/answer`, request, {
      headers: { "X-API-Key": apiKey }
    });
  }

  ragConversations(apiKey: string): Observable<Record<string, unknown>[]> {
    return this.http.get<Record<string, unknown>[]>(`${this.baseUrl}/api/v1/rag/conversations`, { headers: { "X-API-Key": apiKey } });
  }

  createRagConversation(apiKey: string): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/api/v1/rag/conversations`, {}, { headers: { "X-API-Key": apiKey } });
  }

  ragConversation(id: string, apiKey: string): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.baseUrl}/api/v1/rag/conversations/${id}`, { headers: { "X-API-Key": apiKey } });
  }

  renameRagConversation(id: string, name: string, apiKey: string): Observable<Record<string, unknown>> {
    return this.http.patch<Record<string, unknown>>(`${this.baseUrl}/api/v1/rag/conversations/${id}`, { name }, { headers: { "X-API-Key": apiKey } });
  }

  deleteRagConversation(id: string, apiKey: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/rag/conversations/${id}`, { headers: { "X-API-Key": apiKey } });
  }

  ragFeedback(request: { answerId: string; rating: string; comment?: string }, apiKey: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/rag/feedback`, request, { headers: { "X-API-Key": apiKey } });
  }

  createRagEvalCase(request: { question: string; expectedArticleIds?: string[]; expectedAnswer?: string; enabled?: boolean }): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/api/v1/admin/rag/evaluation/cases`, request);
  }

  runRagEvaluation(): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/api/v1/admin/rag/evaluation/runs`, {});
  }

  ragEvaluationRuns(): Observable<Record<string, unknown>[]> {
    return this.http.get<Record<string, unknown>[]>(`${this.baseUrl}/api/v1/admin/rag/evaluation/runs`);
  }

  apiKeyScopes(id: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/api/v1/admin/api-keys/${id}/scopes`);
  }

  updateApiKeyScopes(id: string, scopes: string[]): Observable<string[]> {
    return this.http.put<string[]>(`${this.baseUrl}/api/v1/admin/api-keys/${id}/scopes`, scopes);
  }

  getCurrentTenant(): Observable<Tenant> {
    return this.http.get<Tenant>(
      `${this.baseUrl}/api/v1/tenants/me`
    );
  }
  enterpriseSso(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/v1/admin/sso`); }
  saveEnterpriseSso(request: any): Observable<any> { return this.http.put<any>(`${this.baseUrl}/api/v1/admin/sso`, request); }
  enterpriseGroups(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/v1/admin/groups`); }
  createEnterpriseGroup(request: {name:string;externalId?:string}): Observable<any> { return this.http.post<any>(`${this.baseUrl}/api/v1/admin/groups`, request); }
  addGroupMember(groupId:string,userId:string): Observable<any> { return this.http.post<any>(`${this.baseUrl}/api/v1/admin/groups/${groupId}/members`, {userId}); }
  removeGroupMember(groupId:string,userId:string): Observable<void> { return this.http.delete<void>(`${this.baseUrl}/api/v1/admin/groups/${groupId}/members/${userId}`); }
  enterpriseRoles(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/v1/admin/roles`); }
  createEnterpriseRole(request:{name:string;permissions:string[]}): Observable<any> { return this.http.post<any>(`${this.baseUrl}/api/v1/admin/roles`,request); }
  enterpriseExperiments(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/v1/admin/experiments`); }
  saveEnterpriseExperiment(request:any): Observable<any> { return this.http.post<any>(`${this.baseUrl}/api/v1/admin/experiments`,request); }
  exportUserData(id:string): Observable<any> { return this.http.get<any>(`${this.baseUrl}/api/v1/admin/privacy/users/${id}/export`); }
  deleteUserData(id:string): Observable<void> { return this.http.delete<void>(`${this.baseUrl}/api/v1/admin/privacy/users/${id}`); }
  exportAuditLogs(from?:string,to?:string): Observable<Blob> { return this.http.get(`${this.baseUrl}/api/v1/admin/audit-logs/export`,{params:this.params({from,to}),responseType:"blob"}); }
  rotateRagApiKey(id:string): Observable<any> { return this.http.post<any>(`${this.baseUrl}/api/v1/admin/rag-api-keys/${id}/rotate`,{}); }
  setRagApiKeyIpAllowlist(id:string,allowlist:string): Observable<void> { return this.http.post<void>(`${this.baseUrl}/api/v1/admin/rag-api-keys/${id}/ip-allowlist`,{allowlist}); }
  status(): Observable<any> { return this.http.get<any>(`${this.baseUrl}/api/v1/status`); }

  scimTokens(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/v1/admin/scim/tokens`); }
  createScimToken(name:string): Observable<any> { return this.http.post<any>(`${this.baseUrl}/api/v1/admin/scim/tokens`,{name}); }
  revokeScimToken(id:string): Observable<void> { return this.http.post<void>(`${this.baseUrl}/api/v1/admin/scim/tokens/${id}/revoke`,{}); }

  ssoStart(tenantSlug:string): Observable<{authorizationUrl:string;state:string}> { return this.http.get<{authorizationUrl:string;state:string}>(`${this.baseUrl}/api/v1/auth/sso/${encodeURIComponent(tenantSlug)}/oidc/start`); }
  ssoCallback(code:string,state:string): Observable<LoginResponse> { return this.http.get<LoginResponse>(`${this.baseUrl}/api/v1/auth/sso/oidc/callback`,{params:this.params({code,state})}); }
  samlStartUrl(tenantSlug:string): string { return `${this.baseUrl}/api/v1/auth/sso/saml/${encodeURIComponent(tenantSlug)}/start`; }
  samlAcsUrl(tenantSlug:string): string { return `${this.baseUrl}/login/saml2/sso/${encodeURIComponent(tenantSlug)}`; }
  samlMetadataUrl(tenantSlug:string): string { return `${this.baseUrl}/saml2/metadata/${encodeURIComponent(tenantSlug)}`; }
  samlExchange(code:string): Observable<LoginResponse> { return this.http.post<LoginResponse>(`${this.baseUrl}/api/v1/auth/sso/saml/exchange`, null, {params:this.params({code})}); }


  customerMagicLink(tenantSlug: string, email: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/customer/auth/magic-link`, { tenantSlug, email }, { params: this.params({ tenant: tenantSlug }), withCredentials: true });
  }
  customerConsumeMagic(token: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/customer/auth/consume`, { token }, { params: this.params({ tenant: this.portalTenant() }), withCredentials: true });
  }
  customerLogout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/customer/auth/logout`, {}, { params: this.params({ tenant: this.portalTenant() }), withCredentials: true });
  }
  customerMe(): Observable<{id:string;email:string;name:string|null}> {
    return this.http.get<{id:string;email:string;name:string|null}>(`${this.baseUrl}/api/v1/customer/auth/me`, { params: this.params({ tenant: this.portalTenant() }), withCredentials: true });
  }
  searchSuggestions(query?: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/api/v1/portal/search/suggestions`, { params: this.params({ q: query }) });
  }
  supportTicket(request: {email:string;subject:string;description:string;conversationId?:string|null}): Observable<{id:string;status:string;provider:string}> {
    return this.http.post<{id:string;status:string;provider:string}>(`${this.baseUrl}/api/v1/portal/support/tickets`, request, { params: this.params({ tenant: this.portalTenant() }), withCredentials: true });
  }
  widgetAnswer(tenant: string, query: string, session: string, language?: string,conversationId?:string|null): Observable<{answer:string;articleSlugs:string[];needsHandoff:boolean;conversationId?:string|null}> {
    return this.http.post<{answer:string;articleSlugs:string[];needsHandoff:boolean}>(`${this.baseUrl}/api/v1/widget/answer`, {query,session,language,conversationId}, {params: this.params({tenant}), withCredentials: true});
  }
  saveBrandingExtended(request: {portalTitle:string;logoUrl:string;primaryColor:string;customDomain?:string|null;supportEmail?:string|null;supportedLanguages?:string}): Observable<Tenant> {
    return this.http.put<Tenant>(`${this.baseUrl}/api/v1/admin/branding`, request);
  }

  supportIntegration(): Observable<any> { return this.http.get<any>(`${this.baseUrl}/api/v1/admin/support`); }
  saveSupportIntegration(request: any): Observable<any> { return this.http.put<any>(`${this.baseUrl}/api/v1/admin/support`, request); }


  // Phase 7: tenant-authorized SaaS billing and operational analytics.
  billingOverview(): Observable<any> { return this.http.get<any>(`${this.baseUrl}/api/v1/billing/overview`); }
  billingPlans(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/v1/billing/plans`); }
  billingInvoices(): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/v1/billing/invoices`); }
  billingCheckout(planCode:string): Observable<{sessionId:string;url:string}> { return this.http.post<any>(`${this.baseUrl}/api/v1/billing/checkout`,{planCode}); }
  billingPortal(): Observable<{url:string}> { return this.http.post<any>(`${this.baseUrl}/api/v1/billing/portal`,{}); }
  billingCancel(atPeriodEnd:boolean): Observable<void> { return this.http.post<void>(`${this.baseUrl}/api/v1/billing/cancel`,{atPeriodEnd}); }
  executiveAnalytics(days:number): Observable<any> { return this.http.get<any>(`${this.baseUrl}/api/v1/analytics/executive`,{params:this.params({days})}); }
  executiveDaily(days:number): Observable<any[]> { return this.http.get<any[]>(`${this.baseUrl}/api/v1/analytics/executive/daily`,{params:this.params({days})}); }
  platformConfigurePrice(code:string,stripePriceId:string): Observable<any> { return this.http.put<any>(`${this.baseUrl}/api/v1/platform/plans/${encodeURIComponent(code)}/stripe-price`,{stripePriceId}); }
  platformOverview(): Observable<any> { return this.http.get<any>(`${this.baseUrl}/api/v1/platform/overview`); }

  knowledgeRequests(filters?: { status?: string; priority?: string }): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/api/v1/knowledge-requests`, {
      params: this.params({ status: filters?.status, priority: filters?.priority })
    });
  }
  createKnowledgeRequest(payload: {
    title: string;
    description: string;
    categoryId?: string | null;
    priority?: string;
    source?: string;
    requesterEmail?: string;
  }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/api/v1/knowledge-requests`, payload);
  }
  upvoteKnowledgeRequest(id: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/api/v1/knowledge-requests/${encodeURIComponent(id)}/upvote`, {});
  }
  updateKnowledgeRequest(id: string, patch: { status?: string; priority?: string; assigneeEmail?: string | null }): Observable<any> {
    return this.http.patch<any>(`${this.baseUrl}/api/v1/knowledge-requests/${encodeURIComponent(id)}`, patch);
  }
  convertKnowledgeRequestToDraft(id: string): Observable<{ request: any; article: any }> {
    return this.http.post<{ request: any; article: any }>(`${this.baseUrl}/api/v1/knowledge-requests/${encodeURIComponent(id)}/convert-to-draft`, {});
  }

  playbooks(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/api/v1/playbooks`);
  }
  portalPlaybooks(tenant?: string | null): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/api/v1/portal/playbooks`, {
      params: this.params({ tenant: tenant || this.portalTenant() })
    });
  }
  createPlaybook(payload: { title: string; summary: string; category: string; published?: boolean; steps: any[] }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/api/v1/playbooks`, payload);
  }
  deletePlaybook(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/playbooks/${encodeURIComponent(id)}`);
  }
  recordPlaybookRun(id: string, outcome: "RESOLVED" | "ESCALATED", tenant?: string | null): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/api/v1/playbooks/${encodeURIComponent(id)}/run`,
      { outcome },
      { params: this.params({ tenant: tenant || this.portalTenant() }) }
    );
  }

  submitProductFeedback(payload: CreateProductFeedbackRequest, tenant?: string | null): Observable<ProductFeedback> {
    return this.http.post<ProductFeedback>(
      `${this.baseUrl}/api/v1/product-feedback`,
      payload,
      { params: this.params({ tenant: tenant || this.portalTenant() }) }
    );
  }

  productFeedback(filters?: { type?: string; status?: string }): Observable<ProductFeedback[]> {
    return this.http.get<ProductFeedback[]>(`${this.baseUrl}/api/v1/product-feedback`, {
      params: this.params({ type: filters?.type, status: filters?.status })
    });
  }

  updateProductFeedbackStatus(id: string, status: ProductFeedbackStatus): Observable<ProductFeedback> {
    return this.http.patch<ProductFeedback>(
      `${this.baseUrl}/api/v1/product-feedback/${encodeURIComponent(id)}`,
      { status }
    );
  }
}

