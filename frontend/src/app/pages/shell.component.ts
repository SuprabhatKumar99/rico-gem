import { CommonModule } from "@angular/common";
import { Component, OnInit, inject } from "@angular/core";
import { RouterLink, RouterLinkActive, RouterOutlet } from "@angular/router";
import { AuthService } from "../core/auth.service";
import { ApiService } from "../core/api.service";
import { OnboardingComponent, OnboardingStep } from "../shared/onboarding";
import { CommandPaletteComponent, PaletteNavItem } from "../shared/command-palette.component";
import { ThemeToggleComponent } from "../shared/theme-toggle.component";
import { ArticlePerformance, KnowledgeGap, StaleContent, AppNotification } from "../models";
import { ArticleGradeBadgeComponent, ArticleGradeInfo, gradeArticle } from "./articles.component";

interface NavItem {
  label: string;
  path: string;
  roles?: string[];
}

type NotificationUrgency = "attention" | "review" | "info";

interface ShellNotification {
  id: string;
  title: string;
  detail: string;
  urgency: NotificationUrgency;
  link?: string[];
  grade?: ArticleGradeInfo;
}

interface NotificationGroup {
  label: string;
  urgency: NotificationUrgency;
  headerClass: string;
  dotClass: string;
  items: ShellNotification[];
}

const DISMISSED_KEY = "rk_dismissed_notifications";

const URGENCY_STYLES: Record<NotificationUrgency, { headerClass: string; dotClass: string }> = {
  attention: { headerClass: "text-rose-700", dotClass: "bg-rose-500" },
  review: { headerClass: "text-amber-700", dotClass: "bg-amber-500" },
  info: { headerClass: "text-slate-500", dotClass: "bg-slate-400" }
};

const URGENCY_ORDER: NotificationUrgency[] = ["attention", "review", "info"];

const URGENCY_LABELS: Record<NotificationUrgency, string> = {
  attention: "Needs attention now",
  review: "Worth reviewing",
  info: "Good to know"
};

@Component({
  selector: "rk-shell",
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    OnboardingComponent,
    CommandPaletteComponent,
    ThemeToggleComponent,
    ArticleGradeBadgeComponent
  ],
  styles: [
    `
      @keyframes rk-bell-glow {
        0%,
        100% {
          box-shadow: 0 0 0 0 rgb(225 29 72 / 0.4);
        }
        50% {
          box-shadow: 0 0 0 9px rgb(225 29 72 / 0);
        }
      }

      .rk-bell-alert {
        animation: rk-bell-glow 2.1s ease-in-out infinite;
      }
    `
  ],

  template: `
    <div class="min-h-screen bg-slate-50">
      <div
        *ngIf="mobileOpen"
        class="fixed inset-0 z-30 bg-slate-950/30 lg:hidden"
        (click)="mobileOpen = false"
      ></div>

      <aside
        class="fixed inset-y-0 left-0 z-40 flex w-72 flex-col border-r border-slate-200 bg-white transition-transform lg:translate-x-0"
        [class.-translate-x-full]="!mobileOpen"
      >
        <div class="flex h-16 items-center justify-between gap-3 border-b border-slate-200 px-5">
          <div class="flex items-center gap-3">
            <div class="grid h-9 w-9 place-items-center rounded-xl bg-slate-900 font-black text-white">R</div>
            <div>
              <p class="font-bold tracking-tight">RicozKnow</p>
              <p class="text-[11px] text-slate-400">Knowledge Studio</p>
            </div>
          </div>
          <button
            type="button"
            class="grid h-9 w-9 place-items-center rounded-xl border border-slate-200 text-sm text-slate-600 hover:bg-slate-50 lg:hidden"
            aria-label="Close navigation"
            (click)="mobileOpen = false"
          >
            ✕
          </button>
        </div>

        <nav class="flex-1 overflow-y-auto px-3 py-5">
          <p class="px-3 text-[11px] font-bold uppercase tracking-widest text-slate-400">Workspace</p>

          <div class="mt-2 space-y-1">
            <a
              *ngFor="let item of workspaceNav"
              [class.hidden]="!canShow(item)"
              [routerLink]="item.path"
              routerLinkActive="bg-slate-100 text-slate-950"
              class="block rounded-xl px-3 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50"
              (click)="mobileOpen = false"
            >
              {{ item.label }}
            </a>
          </div>

          <p class="mt-7 px-3 text-[11px] font-bold uppercase tracking-widest text-slate-400">Administration</p>

          <div class="mt-2 space-y-1">
            <a
              *ngFor="let item of adminNav"
              [class.hidden]="!canShow(item)"
              [routerLink]="item.path"
              routerLinkActive="bg-slate-100 text-slate-950"
              class="block rounded-xl px-3 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50"
              (click)="mobileOpen = false"
            >
              {{ item.label }}
            </a>
          </div>
        </nav>

        <div class="space-y-3 border-t border-slate-200 p-4">
          <div class="grid grid-cols-2 gap-2 sm:hidden">
            <a
              routerLink="/portal"
              [queryParams]="{ tenant: tenantSlug }"
              class="rk-btn rk-btn-secondary px-2 text-xs"
              (click)="mobileOpen = false"
            >
              Open portal
            </a>
            <button
              type="button"
              class="rk-btn rk-btn-secondary px-2 text-xs"
              (click)="auth.logout()"
            >
              Sign out
            </button>
          </div>
          <div class="flex items-center gap-3 rounded-xl bg-slate-50 p-3">
            <div class="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-slate-200 text-sm font-bold">
              {{ initials }}
            </div>
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-semibold">{{ auth.user?.email }}</p>
              <p class="truncate text-xs text-slate-500">{{ auth.user?.roles?.join(", ") }}</p>
            </div>
          </div>
        </div>
      </aside>

      <header class="sticky top-0 z-20 border-b border-slate-200 bg-white/95 backdrop-blur lg:ml-72">
        <div class="flex h-16 items-center justify-between gap-2 px-3 sm:gap-4 sm:px-6">
          <div class="flex items-center gap-2 lg:hidden">
            <button
              type="button"
              class="grid h-10 w-10 place-items-center rounded-xl border border-slate-200 bg-white text-base hover:bg-slate-50"
              aria-label="Open navigation"
              (click)="mobileOpen = true"
            >
              ☰
            </button>
            <span class="text-sm font-bold tracking-tight sm:hidden">RicozKnow</span>
          </div>

          <div class="hidden min-w-0 lg:block">
            <p class="text-xs font-medium text-slate-400">Knowledge Studio</p>
            <p class="truncate text-sm font-semibold">Governed knowledge for your organization</p>
          </div>

          <div class="ml-auto flex items-center gap-1.5 sm:gap-2">
            <button
              type="button"
              class="grid h-10 w-10 place-items-center rounded-xl border border-slate-200 bg-white text-sm font-bold text-slate-600 hover:bg-slate-50 sm:hidden"
              aria-label="Open quick jump command palette"
              (click)="palette.show()"
            >
              ⌘
            </button>

            <button
              type="button"
              class="hidden items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-600 transition hover:border-slate-300 hover:bg-white sm:inline-flex"
              aria-label="Open quick jump command palette"
              (click)="palette.show()"
            >
              <span>Quick jump…</span>
              <kbd class="rounded border border-slate-200 bg-white px-1.5 py-0.5 text-[10px] font-bold text-slate-400">⌘K</kbd>
            </button>

            <rk-theme-toggle [compact]="true" />

            <div *ngIf="auth.hasAnyRole(['ADMIN','EDITOR','REVIEWER'])" class="relative">
              <button
                type="button"
                class="relative grid h-10 w-10 place-items-center rounded-xl border border-slate-200 bg-white text-sm hover:bg-slate-50"
                [class.rk-bell-alert]="hasUrgentNotifications"
                [attr.aria-expanded]="notifOpen"
                aria-label="Notifications"
                (click)="notifOpen = !notifOpen"
              >
                🔔
                <span
                  *ngIf="notifications.length"
                  class="absolute -right-1 -top-1 grid h-5 min-w-5 place-items-center rounded-full bg-rose-600 px-1 text-[10px] font-bold text-white"
                >
                  {{ notifications.length }}
                </span>
              </button>

              <div
                *ngIf="notifOpen"
                class="rk-card fixed inset-x-3 top-[4.5rem] z-30 max-h-[min(32rem,75vh)] overflow-y-auto sm:absolute sm:inset-x-auto sm:right-0 sm:top-auto sm:mt-2 sm:w-96"
              >
                <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3">
                  <p class="text-sm font-bold">Recent activity</p>
                  <button
                    type="button"
                    class="grid h-7 w-7 place-items-center rounded-lg text-xs text-slate-400 hover:bg-slate-100 hover:text-slate-700 sm:hidden"
                    aria-label="Close notifications"
                    (click)="notifOpen = false"
                  >
                    ✕
                  </button>
                </div>

                <div *ngIf="!notifications.length" class="px-4 py-8 text-center text-sm text-slate-500">
                  Nothing needs attention right now.
                </div>

                <ng-container *ngFor="let group of notificationGroups">
                  <section *ngIf="group.items.length" class="border-b border-slate-100">
                    <div
                      class="flex items-center gap-2 bg-slate-50 px-4 py-2 text-[11px] font-bold uppercase tracking-wide"
                      [ngClass]="group.headerClass"
                    >
                      <span class="h-2 w-2 rounded-full" [ngClass]="group.dotClass"></span>
                      {{ group.label }}
                      <span class="ml-auto rounded-full bg-white px-1.5 text-[10px] text-slate-500">
                        {{ group.items.length }}
                      </span>
                    </div>

                    <div class="divide-y divide-slate-100">
                      <div *ngFor="let note of group.items" class="flex items-start gap-3 px-4 py-3">
                        <rk-grade-badge
                          *ngIf="note.grade"
                          [grade]="note.grade.grade"
                          [reason]="note.grade.reason"
                        />

                        <div class="min-w-0 flex-1">
                          <p class="text-sm font-semibold">{{ note.title }}</p>
                          <p class="mt-1 text-xs text-slate-500">{{ note.detail }}</p>
                          <a
                            *ngIf="note.link"
                            [routerLink]="note.link"
                            class="mt-1.5 inline-block text-xs font-semibold text-slate-700 hover:underline"
                            (click)="notifOpen = false"
                          >
                            Open article
                          </a>
                        </div>

                        <button
                          type="button"
                          class="grid h-6 w-6 shrink-0 place-items-center rounded-lg text-xs text-slate-400 hover:bg-slate-100 hover:text-slate-700"
                          [attr.aria-label]="'Dismiss notification: ' + note.title"
                          (click)="dismiss(note)"
                        >
                          ✕
                        </button>
                      </div>
                    </div>
                  </section>
                </ng-container>

                <p *ngIf="dismissedCount" class="px-4 py-3 text-center text-[11px] text-slate-400">
                  {{ dismissedCount }} dismissed notification{{ dismissedCount === 1 ? "" : "s" }} hidden
                  <button
                    type="button"
                    class="ml-1 font-semibold text-slate-600 hover:underline"
                    (click)="restoreDismissed()"
                  >
                    Undo
                  </button>
                </p>
              </div>
            </div>

            <a routerLink="/portal" [queryParams]="{ tenant: tenantSlug }" class="rk-btn rk-btn-secondary hidden sm:inline-flex">Open portal</a>
            <button type="button" class="rk-btn rk-btn-secondary hidden sm:inline-flex" (click)="auth.logout()">Sign out</button>
          </div>
        </div>
      </header>

      <main class="lg:ml-72">
        <div class="mx-auto max-w-[1500px] p-4 sm:p-6 lg:p-8">
          <router-outlet />
        </div>
      </main>

      <rk-command-palette
        #palette
        [navItems]="paletteItems"
        [tenantSlug]="tenantSlug"
        [canCreateArticle]="auth.hasAnyRole(['ADMIN', 'EDITOR'])"
      />

      <rk-onboarding
        [steps]="onboardingSteps"
        [visible]="showOnboarding"
        (done)="dismissOnboarding()"
      />
    </div>
  `
})
export class ShellComponent implements OnInit {
  // readonly auth = inject(AuthService);

  constructor(readonly auth: AuthService , private readonly api: ApiService){}

  mobileOpen = false;
  tenantSlug = "";

  notifOpen = false;
  notifications: ShellNotification[] = [];
  dismissedIds = new Set<string>();

  private readonly performanceByArticle = new Map<string, ArticlePerformance>();

  readonly workspaceNav: NavItem[] = [
    { label: "Dashboard", path: "/dashboard" },
    { label: "Articles", path: "/articles" },
    { label: "Knowledge requests", path: "/requests", roles: ["ADMIN", "EDITOR", "REVIEWER"] },
    { label: "Guided playbooks", path: "/playbooks", roles: ["ADMIN", "EDITOR", "REVIEWER", "AGENT_VIEWER"] },
    { label: "Review queue", path: "/review", roles: ["ADMIN", "REVIEWER"] },
    { label: "Categories", path: "/categories" },
    { label: "Knowledge sources", path: "/knowledge-sources", roles: ["ADMIN", "EDITOR", "REVIEWER"] },
    { label: "Connector marketplace", path: "/connectors", roles: ["ADMIN"] },
    { label: "Enterprise search", path: "/enterprise-search", roles: ["ADMIN"] },
    { label: "Knowledge health", path: "/knowledge-health", roles: ["ADMIN", "EDITOR", "REVIEWER"] },
    { label: "Knowledge innovation", path: "/innovation", roles: ["ADMIN", "EDITOR", "REVIEWER"] },
    { label: "Analytics", path: "/analytics", roles: ["ADMIN"] },
    { label: "Business intelligence", path: "/business-intelligence", roles: ["ADMIN"] },
    { label: "AI Studio", path: "/ai-studio", roles: ["ADMIN", "EDITOR"] },
    { label: "AI Intelligence", path: "/ai-intelligence", roles: ["ADMIN", "EDITOR"] },
    { label: "Translation approvals", path: "/translation-proposals", roles: ["ADMIN", "EDITOR", "REVIEWER"] }
  ];

  readonly adminNav: NavItem[] = [
    { label: "Enterprise", path: "/enterprise", roles: ["ADMIN"] },
    { label: "Subscription & billing", path: "/billing", roles: ["ADMIN"] },
    { label: "Platform operations", path: "/platform-admin", roles: ["ADMIN"] },
    { label: "Users", path: "/users", roles: ["ADMIN"] },
    { label: "Branding", path: "/branding", roles: ["ADMIN"] },
    { label: "Automation", path: "/automation", roles: ["ADMIN"] },
    { label: "Support operations", path: "/support-operations", roles: ["ADMIN"] },
    { label: "Synonyms", path: "/synonyms", roles: ["ADMIN"] },
    { label: "Admin tools", path: "/admin-tools", roles: ["ADMIN"] },
    { label: "Webhooks", path: "/webhooks", roles: ["ADMIN"] },
    { label: "RAG", path: "/rag", roles: ["ADMIN"] },
    { label: "RAG Workbench", path: "/rag-workbench", roles: ["ADMIN"] },
    { label: "Developer portal", path: "/developer-portal", roles: ["ADMIN"] },
    { label: "Operations", path: "/operations", roles: ["ADMIN"] },
    { label: "Security", path: "/security" },
    { label: "Agent access", path: "/agent", roles: ["ADMIN", "AGENT_VIEWER"] }
  ];

  ngOnInit(): void {
    this.api.getCurrentTenant().subscribe({
      next: (tenant) => {
        this.tenantSlug = tenant.slug;
      }, error: () => {
        this.tenantSlug = "";
      }
    });

    this.loadNotifications();
  }

  private loadNotifications(): void {
    if (!this.auth.hasAnyRole(["ADMIN", "EDITOR", "REVIEWER"])) return;
    this.dismissedIds = new Set(this.readDismissed());

    this.api.notifications().subscribe({
      next: result => {
        const notes: ShellNotification[] = result.items.filter(n => !n.read).map(n => ({
          id: `backend:${n.id}`,
          title: n.title,
          detail: n.message,
          urgency: n.type === "REVIEW_REQUEST" || n.type === "CONTENT_REVIEW" ? "review" : "info",
          link: n.articleId ? ["/articles", n.articleId] : undefined
        }));
        this.notifications = notes;
      },
      error: () => undefined
    });

    if (!this.auth.hasRole("ADMIN")) return;

    this.api.performance(30).subscribe({
      next: (items: ArticlePerformance[]) => {
        for (const item of items) {
          this.performanceByArticle.set(item.articleId, item);
        }
        this.refreshNotifications();
      }
    });

    this.api.knowledgeGaps(30, 3).subscribe({
      next: (gaps: KnowledgeGap[]) => {
        const gapNotes: ShellNotification[] = gaps.map((gap) => ({
          id: `gap:${gap.query}`,
          title: `New knowledge gap: "${gap.query}"`,
          detail: `${gap.volumeCount} searches, ${gap.zeroResultCount} with zero results.`,
          urgency: "review"
        }));

        this.notifications = [...this.notifications, ...gapNotes];
        this.refreshNotifications();
      }
    });

    this.api.staleContent(14).subscribe({
      next: (items: StaleContent[]) => {
        const staleNotes: ShellNotification[] = items.slice(0, 3).map((item) => ({
          id: `stale:${item.articleId}`,
          title: item.alreadyExpired ? `${item.title} has expired` : `${item.title} is expiring soon`,
          detail: item.alreadyExpired
            ? "Review and refresh this article."
            : "Set to expire within 14 days.",
          urgency: item.alreadyExpired ? "attention" : "review",
          link: ["/articles", item.articleId],
          grade: this.gradeFor(item.articleId, item.expiresAt)
        }));

        this.notifications = [...this.notifications, ...staleNotes];
        this.refreshNotifications();
      }
    });
  }

  private gradeFor(articleId: string, expiresAt: string | null): ArticleGradeInfo {
    const performance = this.performanceByArticle.get(articleId);

    return gradeArticle({
      views: performance?.views ?? 0,
      helpfulCount: performance?.helpfulCount ?? 0,
      unhelpfulCount: performance?.unhelpfulCount ?? 0,
      expiresAt
    });
  }

  private refreshNotifications(): void {
    this.notifications = this.notifications.filter((note) => !this.dismissedIds.has(note.id));
  }

  get notificationGroups(): NotificationGroup[] {
    const buckets = new Map<NotificationUrgency, ShellNotification[]>();

    for (const urgency of URGENCY_ORDER) {
      buckets.set(urgency, []);
    }

    for (const note of this.notifications) {
      buckets.get(note.urgency)?.push(note);
    }

    return URGENCY_ORDER.map((urgency) => ({
      label: URGENCY_LABELS[urgency],
      urgency,
      headerClass: URGENCY_STYLES[urgency].headerClass,
      dotClass: URGENCY_STYLES[urgency].dotClass,
      items: buckets.get(urgency) ?? []
    }));
  }

  get hasUrgentNotifications(): boolean {
    return this.notifications.some((note) => note.urgency === "attention");
  }

  get dismissedCount(): number {
    return this.dismissedIds.size;
  }

  dismiss(note: ShellNotification): void {
    if (note.id.startsWith("backend:")) this.api.markNotificationRead(note.id.substring(8)).subscribe({ error: () => undefined });
    this.dismissedIds.add(note.id);
    this.refreshNotifications();
    localStorage.setItem(DISMISSED_KEY, JSON.stringify([...this.dismissedIds]));
  }

  restoreDismissed(): void {
    this.dismissedIds = new Set();
    localStorage.removeItem(DISMISSED_KEY);
    this.notifications = [];
    this.loadNotifications();
  }

  private readDismissed(): string[] {
    const saved = localStorage.getItem(DISMISSED_KEY);

    if (!saved) {
      return [];
    }

    try {
      const parsed = JSON.parse(saved);
      return Array.isArray(parsed) ? parsed.filter((value): value is string => typeof value === "string") : [];
    } catch {
      return [];
    }
  }

  canShow(item: NavItem): boolean {
    return !item.roles || this.auth.hasAnyRole(item.roles);
  }

  get paletteItems(): PaletteNavItem[] {
    return [
      ...this.workspaceNav
        .filter((item) => this.canShow(item))
        .map((item) => ({ label: item.label, path: item.path, section: "Workspace" as const })),
      ...this.adminNav
        .filter((item) => this.canShow(item))
        .map((item) => ({ label: item.label, path: item.path, section: "Administration" as const }))
    ];
  }

  showOnboarding = !localStorage.getItem("rk_onboarded");

  get onboardingSteps(): OnboardingStep[] {
    if (this.auth.hasRole("AGENT_VIEWER") && !this.auth.hasRole("ADMIN")) {
      return [
        { title: "Welcome to RicozKnow", body: "Use Agent Access to search governed knowledge and open approved article results." },
        { title: "Agent Access", body: "Search the knowledge base and open article details without changing governed content." },
        { title: "Account security", body: "Manage MFA and active sessions from Security." }
      ];
    }

    if (this.auth.hasRole("REVIEWER") && !this.auth.hasAnyRole(["ADMIN", "EDITOR"])) {
      return [
        { title: "Welcome to RicozKnow", body: "Review governed knowledge, inspect article intelligence, and manage review decisions." },
        { title: "Review queue", body: "Open submitted articles, inspect their versions, and approve or reject them." },
        { title: "Knowledge sources", body: "Browse ingestion sources in read-only mode and monitor their status." }
      ];
    }

    if (this.auth.hasRole("EDITOR") && !this.auth.hasRole("ADMIN")) {
      return [
        { title: "Welcome to RicozKnow", body: "Create and maintain governed knowledge for your organization." },
        { title: "Articles", body: "Create drafts, update content, submit versions for review, and manage article visibility." },
        { title: "AI Studio", body: "Generate and improve content and inspect article intelligence before submitting changes for review." }
      ];
    }

    return [
      { title: "Welcome to RicozKnow", body: "This is your Knowledge Studio for governing, reviewing, and operating your organization knowledge." },
      { title: "Dashboard", body: "Your dashboard shows knowledge gaps, article activity, deflection, and content freshness." },
      { title: "Administration", body: "Manage users, security, integrations, operations, and tenant-wide settings from the administration tools." }
    ];
  }

  dismissOnboarding(): void {
    this.showOnboarding = false;
    localStorage.setItem("rk_onboarded", "1");
  }

  get initials(): string {
    const email = this.auth.user?.email ?? "";
    return email.slice(0, 2).toUpperCase();
  }

}
