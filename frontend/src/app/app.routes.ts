import { Routes } from "@angular/router";
import { authGuard, roleGuard } from "./core/guards";

export const routes: Routes = [
  { path: "social/callback", loadComponent: () => import("./pages/social-callback.component").then(m => m.SocialCallbackComponent) },
  { path: "sso/callback", loadComponent: () => import("./pages/sso-callback.component").then(m => m.SsoCallbackComponent) },
  {
    path: "",
    loadComponent: () => import("./pages/landing.component").then((m) => m.LandingComponent)
  },
  {
    path: "login",
    loadComponent: () => import("./pages/login.component").then((m) => m.LoginComponent)
  },
  {
    path: "register",
    loadComponent: () => import("./pages/register.component").then((m) => m.RegisterComponent)
  },
  {
    path: "account/:action",
    loadComponent: () => import("./pages/auth-actions.component").then((m) => m.AuthActionsComponent)
  },
  {
    path: "verify-email",
    data: { action: "verify" },
    loadComponent: () => import("./pages/auth-actions.component").then((m) => m.AuthActionsComponent)
  },
  {
    path: "reset-password",
    data: { action: "reset" },
    loadComponent: () => import("./pages/auth-actions.component").then((m) => m.AuthActionsComponent)
  },
  {
    path: "accept-invitation",
    data: { action: "invite" },
    loadComponent: () => import("./pages/auth-actions.component").then((m) => m.AuthActionsComponent)
  },
  {
    path: "widget",
    loadComponent: () => import("./pages/widget.component").then((m) => m.WidgetComponent)
  },
  {
    path: "portal",
    loadComponent: () => import("./pages/portal.component").then((m) => m.PortalComponent)
  },
  {
    path: "portal/history",
    loadComponent: () => import("./pages/portal-history.component").then(m => m.PortalHistoryComponent)
  },
  {
    path: "portal/search",
    loadComponent: () => import("./pages/portal-search.component").then((m) => m.PortalSearchComponent)
  },
  {
    path: "portal/article/:slug",
    loadComponent: () => import("./pages/portal-article.component").then((m) => m.PortalArticleComponent)
  },
  {
    path: "explore",
    loadComponent: () => import("./pages/explore.component").then((m) => m.ExploreComponent)
  },
  {
    path: "explore/article/:slug",
    loadComponent: () => import("./pages/explore-article.component").then((m) => m.ExploreArticleComponent)
  },
  {
    path: "",
    canActivate: [authGuard],
    loadComponent: () => import("./pages/shell.component").then((m) => m.ShellComponent),
    children: [
      {
        path: "dashboard",
        loadComponent: () => import("./pages/dashboard.component").then((m) => m.DashboardComponent)
      },
      {
        path: "articles",
        canActivate: [roleGuard(["ADMIN", "EDITOR", "REVIEWER"])],
        loadComponent: () => import("./pages/articles.component").then((m) => m.ArticlesComponent)
      },
      {
        path: "articles/new",
        canActivate: [roleGuard(["ADMIN", "EDITOR"])],
        loadComponent: () => import("./pages/article-editor.component").then((m) => m.ArticleEditorComponent)
      },
      {
        path: "articles/:id/review",
        canActivate: [roleGuard(["ADMIN", "EDITOR", "REVIEWER"])],
        loadComponent: () => import("./pages/article-review.component").then(m => m.ArticleReviewComponent)
      },
      {
        path: "articles/:id",
        canActivate: [roleGuard(["ADMIN", "EDITOR", "REVIEWER"])],
        loadComponent: () => import("./pages/article-editor.component").then((m) => m.ArticleEditorComponent)
      },
      {
        path: "collaborate/:articleId/:versionId",
        canActivate: [roleGuard(["ADMIN", "EDITOR"])],
        loadComponent: () => import("./pages/collaboration.component").then(m => m.CollaborationComponent)
      },
      {
        path: "review",
        canActivate: [roleGuard(["ADMIN", "REVIEWER"])],
        loadComponent: () => import("./pages/review.component").then((m) => m.ReviewComponent)
      },
      {
        path: "categories",
        loadComponent: () => import("./pages/categories.component").then((m) => m.CategoriesComponent)
      },
      {
        path: "requests",
        canActivate: [roleGuard(["ADMIN", "EDITOR", "REVIEWER"])],
        loadComponent: () => import("./pages/knowledge-requests.component").then((m) => m.KnowledgeRequestsComponent)
      },
      {
        path: "playbooks",
        canActivate: [roleGuard(["ADMIN", "EDITOR", "REVIEWER", "AGENT_VIEWER"])],
        loadComponent: () => import("./pages/playbooks.component").then((m) => m.PlaybooksComponent)
      },
      {
        path: "knowledge-sources",
        canActivate: [roleGuard(["ADMIN", "EDITOR", "REVIEWER"])],
        loadComponent: () => import("./pages/knowledge-sources.component").then((m) => m.KnowledgeSourcesComponent)
      },
      {
        path: "enterprise-search", canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/enterprise-search.component").then(m => m.EnterpriseSearchComponent)
      },
      {
        path: "connectors", canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/connectors.component").then(m => m.ConnectorsComponent)
      },
      {
        path: "innovation",
        canActivate: [roleGuard(["ADMIN", "EDITOR", "REVIEWER"])],
        loadComponent: () => import("./pages/innovation.component").then(m => m.InnovationComponent)
      },
      {
        path: "knowledge-health",
        canActivate: [roleGuard(["ADMIN", "EDITOR", "REVIEWER"])],
        loadComponent: () => import("./pages/knowledge-health.component").then(m => m.KnowledgeHealthComponent)
      },
      {path:"developer-portal",canActivate:[roleGuard(["ADMIN"])],loadComponent:()=>import("./pages/developer-portal.component").then(m=>m.DeveloperPortalComponent)},
      {path:"billing",canActivate:[roleGuard(["ADMIN"])],loadComponent:()=>import("./pages/billing.component").then(m=>m.BillingComponent)},
      {path:"business-intelligence",canActivate:[roleGuard(["ADMIN"])],loadComponent:()=>import("./pages/executive-analytics.component").then(m=>m.ExecutiveAnalyticsComponent)},
      {path:"platform-admin",canActivate:[roleGuard(["ADMIN"])],loadComponent:()=>import("./pages/platform-admin.component").then(m=>m.PlatformAdminComponent)},
      {
        path: "analytics",
        canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/analytics.component").then((m) => m.AnalyticsComponent)
      },
      {
        path: "users",
        canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/users.component").then((m) => m.UsersComponent)
      },
      {
        path: "branding",
        canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/branding.component").then((m) => m.BrandingComponent)
      },
      {
        path: "synonyms",
        canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/synonyms.component").then((m) => m.SynonymsComponent)
      },
      {
        path: "admin-tools",
        canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/admin-tools.component").then((m) => m.AdminToolsComponent)
      },
      {
        path: "webhooks",
        canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/webhooks.component").then((m) => m.WebhooksComponent)
      },
      {
        path: "operations",
        canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/operations.component").then((m) => m.OperationsComponent)
      },
      {
        path: "security",
        loadComponent: () => import("./pages/security.component").then((m) => m.SecurityComponent)
      },
      {
        path: "enterprise", canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/enterprise.component").then((m) => m.EnterpriseComponent)
      },
      {
        path: "support-operations", canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/support-operations.component").then(m => m.SupportOperationsComponent)
      },
      {
        path: "translation-proposals", canActivate: [roleGuard(["ADMIN", "EDITOR", "REVIEWER"])],
        loadComponent: () => import("./pages/translation-proposals.component").then(m => m.TranslationProposalsComponent)
      },
      {
        path: "automation", canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/automation.component").then(m => m.AutomationComponent)
      },
      {
        path: "ai-intelligence",
        canActivate: [roleGuard(["ADMIN", "EDITOR"])],
        loadComponent: () => import("./pages/ai-intelligence.component").then(m => m.AiIntelligenceComponent)
      },
      {
        path: "ai-studio",
        canActivate: [roleGuard(["ADMIN", "EDITOR"])],
        loadComponent: () => import("./pages/ai-studio.component").then((m) => m.AiStudioComponent)
      },
      {
        path: "rag-workbench",
        canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/rag-workbench.component").then((m) => m.RagWorkbenchComponent)
      },
      {
        path: "rag",
        canActivate: [roleGuard(["ADMIN"])],
        loadComponent: () => import("./pages/rag.component").then((m) => m.RagComponent)
      },
      {
        path: "agent",
        canActivate: [roleGuard(["ADMIN", "AGENT_VIEWER"])],
        loadComponent: () => import("./pages/agent.component").then((m) => m.AgentComponent)
      },
      {
        path: "",
        pathMatch: "full",
        redirectTo: "dashboard"
      }
    ]
  },
  {
    path: "**",
    redirectTo: "portal"
  }
];
