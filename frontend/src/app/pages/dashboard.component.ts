import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { RouterLink } from "@angular/router";
import { ApiService } from "../core/api.service";
import { Article, DeflectionSummary, KnowledgeGap, StaleContent, Page } from "../models";
import { MetricCardComponent, StatusComponent } from "../shared/ui";
import { AuthService } from "../core/auth.service";

@Component({
  selector: "rk-dashboard",
  standalone: true,
  imports: [CommonModule, RouterLink, MetricCardComponent, StatusComponent],
  template: `
    <div>
      <section *ngIf="isAgentViewer" class="rk-card p-8">
        <p class="text-sm font-semibold text-slate-500">Agent workspace</p>
        <h1 class="mt-1 text-3xl font-bold tracking-tight">Welcome to Agent Access</h1>
        <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-500">Use the agent workspace to search governed knowledge and open approved article results. Article management and analytics are not available for this role.</p>
        <a routerLink="/agent" class="rk-btn rk-btn-primary mt-6">Open Agent Access</a>
      </section>

      <ng-container *ngIf="!isAgentViewer">
      <div class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="text-sm font-semibold text-slate-500">Overview</p>
          <h1 class="mt-1 text-3xl font-bold tracking-tight">What needs attention?</h1>
          <p class="mt-2 text-sm text-slate-500">
            Action-oriented signals from the backend's article and analytics endpoints.
          </p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button
            *ngIf="isAdmin"
            type="button"
            class="rk-btn rk-btn-secondary"
            [attr.aria-expanded]="customizing"
            (click)="customizing = !customizing"
          >
            {{ customizing ? "Done" : "Customize" }}
          </button>
          <a *ngIf="canEdit" routerLink="/articles/new" class="rk-btn rk-btn-primary">+ New article</a>
        </div>
      </div>

      <div
        *ngIf="customizing && isAdmin"
        class="rk-card mt-4 flex flex-wrap items-center gap-x-6 gap-y-3 px-5 py-4 sm:mt-5"
      >
        <p class="text-sm font-semibold">Show summary cards</p>

        <label
          *ngFor="let card of cardOptions"
          class="flex cursor-pointer items-center gap-2 text-sm text-slate-600"
        >
          <input
            type="checkbox"
            class="h-4 w-4 rounded border-slate-300 text-slate-900"
            [checked]="visibleCards[card.key]"
            (change)="toggleCard(card.key)"
          />
          {{ card.label }}
        </label>
      </div>

      <div class="mt-5 grid gap-3 sm:mt-7 sm:gap-4 md:grid-cols-3">
        <div *ngIf="visibleCards['articles']" class="flex flex-col gap-2">
          <rk-metric-card label="Articles" [value]="articleCount" />
          <a routerLink="/articles" class="mt-auto self-end text-xs font-semibold text-slate-700 hover:underline">
            View Articles →
          </a>
        </div>

        <div *ngIf="isAdmin && visibleCards['gaps']" class="flex flex-col gap-2">
          <rk-metric-card
            label="Knowledge gaps"
            [value]="gaps.length"
            hint="top returned gaps"
            explain="Searches people made that returned no good answer. Each one is a hint for an article worth writing."
          />
          <a
            *ngIf="auth.hasRole('ADMIN')"
            routerLink="/analytics"
            class="mt-auto self-end text-xs font-semibold text-slate-700 hover:underline"
          >
            View Knowledge Gaps →
          </a>
        </div>

        <div *ngIf="isAdmin && visibleCards['deflection']" class="flex flex-col gap-2">
          <rk-metric-card
            label="Confirmed deflection"
            [value]="deflection?.confirmedDeflectionCount ?? '—'"
            explain="People who found their answer in an article and did not need to contact support."
          />
          <a
            *ngIf="auth.hasRole('ADMIN')"
            routerLink="/analytics"
            class="mt-auto self-end text-xs font-semibold text-slate-700 hover:underline"
          >
            View Analytics →
          </a>
        </div>
      </div>

      <div class="mt-5 grid gap-5 sm:mt-7 sm:gap-6 xl:grid-cols-[1.15fr_0.85fr]">
        <section class="rk-card overflow-hidden">
          <div class="flex items-center justify-between border-b border-slate-200 px-5 py-4">
            <div>
              <h2 class="font-bold">Recent articles</h2>
              <p class="mt-1 text-xs text-slate-500">Current article list returned by the API.</p>
            </div>
            <a routerLink="/articles" class="text-sm font-semibold text-slate-700">View all</a>
          </div>

          <div class="divide-y divide-slate-100">
            <div *ngFor="let article of recentArticles" class="flex items-center gap-4 px-5 py-3 sm:py-4">
              <div class="min-w-0 flex-1">
                <a
                  [routerLink]="['/articles', article.id]"
                  class="line-clamp-2 font-semibold leading-snug hover:underline"
                >
                  {{ article.title }}
                </a>
                <p class="mt-1 truncate text-xs text-slate-500">{{ article.slug }}</p>
              </div>
              <rk-status [status]="article.status" />
            </div>

            <div *ngIf="!recentArticles.length" class="px-5 py-10 text-center text-sm text-slate-500">
              No articles returned.
            </div>
          </div>
        </section>

        <section *ngIf="isAdmin" class="rk-card">
          <div class="border-b border-slate-200 px-5 py-4">
            <h2 class="font-bold">Knowledge gaps</h2>
            <p class="mt-1 text-xs text-slate-500">High-volume queries returned by analytics.</p>
          </div>

          <div class="divide-y divide-slate-100">
            <div *ngFor="let gap of gaps.slice(0, 5)" class="px-5 py-3 sm:py-4">
              <div class="flex items-center justify-between gap-4">
                <p class="font-medium">{{ gap.query }}</p>
                <span class="rk-badge bg-amber-100 text-amber-700">{{ gap.gapScore }}</span>
              </div>
              <p class="mt-1 text-xs text-slate-500">
                {{ gap.volumeCount }} searches · {{ gap.zeroResultCount }} zero results
              </p>
            </div>

            <div *ngIf="!gaps.length" class="px-5 py-10 text-center text-sm text-slate-500">
              No knowledge gaps returned for the current window.
            </div>
          </div>
        </section>
      </div>

      <section *ngIf="isAdmin" class="rk-card mt-5 sm:mt-6">
        <div class="border-b border-slate-200 px-5 py-4">
          <h2 class="font-bold">Freshness</h2>
          <p class="mt-1 text-xs text-slate-500">Content returned by the stale-content endpoint.</p>
        </div>

        <div class="divide-y divide-slate-100">
          <div *ngFor="let item of stale.slice(0, 5)" class="flex flex-wrap items-center justify-between gap-3 px-5 py-3 sm:py-4">
            <div>
              <p class="font-semibold">{{ item.title }}</p>
              <p class="mt-1 text-xs text-slate-500">Expires {{ item.expiresAt | date:"mediumDate" }}</p>
            </div>
            <span
              class="rk-badge"
              [class.bg-rose-100]="item.alreadyExpired"
              [class.text-rose-700]="item.alreadyExpired"
              [class.bg-amber-100]="!item.alreadyExpired"
              [class.text-amber-700]="!item.alreadyExpired"
            >
              {{ item.alreadyExpired ? "Expired" : "Expiring" }}
            </span>
          </div>
        </div>
      </section>
      </ng-container>
    </div>
  `
})
export class DashboardComponent implements OnInit {
  recentArticles: Article[] = [];
  articleCount = 0;
  gaps: KnowledgeGap[] = [];
  stale: StaleContent[] = [];
  deflection?: DeflectionSummary;

  customizing = false;

  readonly cardOptions = [
    { key: "articles", label: "Articles" },
    { key: "gaps", label: "Knowledge gaps" },
    { key: "deflection", label: "Confirmed deflection" }
  ];

  visibleCards: Record<string, boolean> = this.loadCardPrefs();

  private loadCardPrefs(): Record<string, boolean> {
    const saved = localStorage.getItem("rk_dashboard_cards");

    if (saved) {
      try {
        return JSON.parse(saved);
      } catch {
      }
    }

    return { articles: true, gaps: true, deflection: true };
  }

  toggleCard(key: string): void {
    this.visibleCards = { ...this.visibleCards, [key]: !this.visibleCards[key] };
    localStorage.setItem("rk_dashboard_cards", JSON.stringify(this.visibleCards));
  }

  constructor(private readonly api: ApiService, readonly auth: AuthService) {}

  get isAdmin(): boolean {
    return this.auth.hasRole("ADMIN");
  }

  get isAgentViewer(): boolean {
    return this.auth.hasRole("AGENT_VIEWER") && !this.isAdmin;
  }

  get canEdit(): boolean {
    return this.auth.hasAnyRole(["ADMIN", "EDITOR"]);
  }

  ngOnInit(): void {
    if (this.isAgentViewer) {
      return;
    }

    this.loadArticles();

    if (this.isAdmin) {
      this.loadAnalytics();
    }
  }

  private loadArticles(): void {
    this.api.listArticles(undefined, 0, 5).subscribe({
      next: (page) => {
        const response = page as Page<Article> & {
          total?: number;
          count?: number;
          page?: { totalElements?: number; total?: number; count?: number };
          pagination?: { total?: number; totalElements?: number; count?: number };
        };

        this.recentArticles = Array.isArray(response.content)
          ? response.content
          : [];

        this.articleCount = this.extractArticleCount(response);
      },
      error: () => {
        this.recentArticles = [];
        this.articleCount = 0;
      }
    });
  }

  private extractArticleCount(response: {
    content?: Article[];
    totalElements?: number;
    total?: number;
    count?: number;
    page?: { totalElements?: number; total?: number; count?: number };
    pagination?: { total?: number; totalElements?: number; count?: number };
  }): number {
    const candidates = [
      response.totalElements,
      response.total,
      response.count,
      response.page?.totalElements,
      response.page?.total,
      response.page?.count,
      response.pagination?.totalElements,
      response.pagination?.total,
      response.pagination?.count
    ];

    const total = candidates.find(
      (value): value is number => typeof value === "number" && Number.isFinite(value)
    );

    return total ?? response.content?.length ?? 0;
  }

  private loadAnalytics(): void {
    this.api.knowledgeGaps().subscribe({
      next: (items) => {
        this.gaps = items;
      }
    });

    this.api.staleContent().subscribe({
      next: (items) => {
        this.stale = items;
      }
    });

    this.api.deflection().subscribe({
      next: (summary) => {
        this.deflection = summary;
      }
    });
  }

}
