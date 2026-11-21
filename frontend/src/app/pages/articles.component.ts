import { CommonModule } from "@angular/common";
import { Component, Input, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { Observable, catchError, concatMap, from, map, of, switchMap, toArray } from "rxjs";
import { AuthService } from "../core/auth.service";
import { ApiService } from "../core/api.service";
import {
  Article,
  ArticlePerformance,
  ArticleVersion,
  Category,
  Page,
  SearchHit,
  StaleContent
} from "../models";
import { StatusComponent } from "../shared/ui";

interface BulkActionResult {
  id: string;
  title: string;
  ok: boolean;
  message: string;
}

export type ArticleGrade = "A" | "B" | "C" | "D";

export interface ArticleGradeInfo {
  grade: ArticleGrade;
  reason: string;
}

export interface ArticleGradeInput {
  views: number;
  helpfulCount: number;
  unhelpfulCount: number;
  expiresAt: string | null;
}

const EXPIRY_WARNING_DAYS = 30;

function expiryState(expiresAt: string | null, now: Date): "none" | "soon" | "expired" | null {
  if (!expiresAt) {
    return "none";
  }

  const date = new Date(expiresAt);

  if (Number.isNaN(date.getTime())) {
    return null;
  }

  if (date.getTime() <= now.getTime()) {
    return "expired";
  }

  const daysLeft = Math.ceil((date.getTime() - now.getTime()) / 86400000);

  return daysLeft <= EXPIRY_WARNING_DAYS ? "soon" : "none";
}

export function gradeArticle(input: ArticleGradeInput, now = new Date()): ArticleGradeInfo {
  const views = Math.max(0, input.views || 0);
  const helpful = Math.max(0, input.helpfulCount || 0);
  const unhelpful = Math.max(0, input.unhelpfulCount || 0);
  const totalVotes = helpful + unhelpful;
  const unhelpfulShare = totalVotes > 0 ? unhelpful / totalVotes : null;
  const expiry = expiryState(input.expiresAt, now);

  if (expiry === "expired") {
    return {
      grade: "D",
      reason: `Expired on ${new Date(input.expiresAt as string).toLocaleDateString()} - needs a refresh`
    };
  }

  if (expiry === "soon") {
    const daysLeft = Math.max(
      0,
      Math.ceil((new Date(input.expiresAt as string).getTime() - now.getTime()) / 86400000)
    );
    return {
      grade: "D",
      reason: `Expires in ${daysLeft} day${daysLeft === 1 ? "" : "s"} - review before it goes stale`
    };
  }

  if (views < 10) {
    return { grade: "D", reason: `Views low (${views} view${views === 1 ? "" : "s"}) - little traffic reaching it` };
  }

  if (unhelpfulShare !== null && totalVotes >= 2 && unhelpfulShare > 0.5) {
    return {
      grade: "D",
      reason: `Mostly marked unhelpful (${unhelpful} of ${totalVotes} votes)`
    };
  }

  let score = 0;
  const good: string[] = [];

  if (views >= 50) {
    score += 2;
    good.push(`${views} views`);
  } else if (views >= 20) {
    score += 1;
    good.push(`${views} views`);
  }

  if (unhelpfulShare === null) {
    score += 1;
    good.push("no unhelpful votes yet");
  } else if (unhelpfulShare <= 0.25) {
    score += 2;
    good.push(`${helpful} of ${totalVotes} votes helpful`);
  } else if (unhelpfulShare <= 0.5) {
    score += 1;
    good.push(`mixed feedback (${helpful} of ${totalVotes} votes helpful)`);
  }

  if (expiry === "none") {
    score += 1;
    good.push("no expiry date set");
  }

  if (score >= 4) {
    return { grade: "A", reason: `Healthy: ${good.join(", ")}` };
  }

  if (score >= 2) {
    return { grade: "B", reason: `Fairly healthy: ${good.join(", ")}` };
  }

  return {
    grade: "C",
    reason: unhelpfulShare === null
      ? `Some interest (${views} views) but not enough feedback yet`
      : `Needs attention: ${views} views, ${unhelpful} of ${totalVotes} votes unhelpful`
  };
}

@Component({
  selector: "rk-grade-badge",
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="group/grade relative inline-flex shrink-0">
      <span
        class="grid h-6 w-6 place-items-center rounded-full text-[11px] font-black leading-none text-white"
        [ngClass]="badgeClass"
        tabindex="0"
      >
        {{ grade }}
      </span>
      <span
        role="tooltip"
        class="pointer-events-none absolute bottom-full left-1/2 z-30 mb-2 w-56 -translate-x-1/2 translate-y-1 rounded-lg bg-slate-900 px-2.5 py-1.5 text-left text-[11px] font-medium leading-4 text-white opacity-0 shadow-lg transition group-hover/grade:opacity-100 group-focus-within/grade:opacity-100"
      >
        <span class="mb-0.5 block font-bold text-white/70">Grade {{ grade }}</span>
        {{ reason }}
      </span>
    </span>
  `
})
export class ArticleGradeBadgeComponent {
  @Input() grade: ArticleGrade = "C";
  @Input() reason = "";

  get badgeClass(): string {
    switch (this.grade) {
      case "A":
        return "bg-emerald-500";
      case "B":
        return "bg-amber-500";
      case "C":
        return "bg-sky-500";
      default:
        return "bg-rose-500";
    }
  }
}


@Component({
  selector: "rk-articles",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StatusComponent, ArticleGradeBadgeComponent],
  template: `
    <div>
      <div class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="text-sm font-semibold text-slate-500">Knowledge Studio</p>
          <h1 class="mt-1 text-3xl font-bold tracking-tight">Articles</h1>
          <p class="mt-2 text-sm text-slate-500">
            Table-first browsing for governed article content.
          </p>
        </div>
        <div class="flex flex-wrap gap-2">
          <label *ngIf="canEdit" class="rk-btn rk-btn-secondary cursor-pointer">
            Import
            <input type="file" class="hidden" multiple accept=".md,.markdown,.csv,.html,.htm,.pdf,.docx,.zip" (change)="importFiles($event)" />
          </label>
          <button *ngIf="canEdit" class="rk-btn rk-btn-secondary" (click)="exportArticles('csv')">Export CSV</button>
          <button *ngIf="canEdit" class="rk-btn rk-btn-secondary" (click)="exportArticles('markdown')">Export Markdown</button>
          <button *ngIf="canEdit" class="rk-btn rk-btn-secondary" (click)="exportArticles('html')">Export HTML</button>
          <button *ngIf="canEdit" class="rk-btn rk-btn-secondary" (click)="exportArticles('pdf')">Export PDF</button>
          <button *ngIf="canEdit" class="rk-btn rk-btn-secondary" (click)="exportArticles('docx')">Export DOCX</button>
          <a *ngIf="canEdit" routerLink="/articles/new" class="rk-btn rk-btn-primary">+ New article</a>
        </div>
      </div>

      <div *ngIf="health" class="mt-4 rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-600">
        Knowledge health: <strong>{{ health.total }}</strong> articles · <strong>{{ health.published }}</strong> published ·
        <strong>{{ health.dueForReview }}</strong> due for review · <strong>{{ health.unverified }}</strong> unverified ·
        <strong>{{ health.stale }}</strong> expired
        <div *ngIf="health.dueArticleIds.length" class="mt-2 flex flex-wrap gap-2 text-xs">
          <a *ngFor="let id of health.dueArticleIds.slice(0,6)" [routerLink]="['/articles',id]" class="font-semibold text-indigo-700 underline">Review {{ id.slice(0,8) }}</a>
        </div>
      </div>
      <div *ngIf="importMessage" class="mt-4 rounded-xl border border-slate-200 bg-white p-3 text-sm text-slate-600">{{ importMessage }}</div>

      <div class="rk-card mt-7 p-4">
        <div class="grid gap-3 md:grid-cols-[1fr_180px_auto]">
          <input
            class="rk-input"
            placeholder="Search articles..."
            [(ngModel)]="query"
            (keyup.enter)="search()"
            aria-label="Search articles"
          />

          <select class="rk-select" [(ngModel)]="status" (change)="load()">
            <option value="">All statuses</option>
            <option value="DRAFT">Draft</option>
            <option value="IN_REVIEW">In review</option>
            <option value="PUBLISHED">Published</option>
            <option value="ARCHIVED">Archived</option>
          </select>

          <button class="rk-btn rk-btn-secondary" (click)="load()">Refresh</button>
        </div>

        <div *ngIf="searchResults.length" class="mt-5 border-t border-slate-200 pt-5">
          <p class="text-sm font-semibold">{{ searchResults.length }} search results</p>
          <div class="mt-3 grid gap-2">
            <a
              *ngFor="let result of searchResults"
              [routerLink]="['/articles', result.articleId]"
              class="rounded-xl border border-slate-200 p-3 hover:bg-slate-50"
            >
              <p class="font-semibold">{{ result.title }}</p>
              <p class="mt-1 text-sm text-slate-500">{{ result.excerpt }}</p>
            </a>
          </div>
        </div>
      </div>

      <div class="rk-card mt-4 overflow-hidden">
        <div
          *ngIf="selectedIds.size"
          class="flex flex-wrap items-center gap-3 border-b border-slate-200 bg-slate-50 px-5 py-3"
        >
          <p class="text-sm font-semibold">{{ selectedIds.size }} selected</p>

          <div class="flex flex-wrap items-center gap-2">
            <button *ngIf="isAdmin" class="rk-btn rk-btn-primary" [disabled]="bulkBusy" (click)="publishSelected()">
              Publish selected
            </button>
            <button *ngIf="canEdit" class="rk-btn rk-btn-danger" [disabled]="bulkBusy" (click)="archiveSelected()">
              Archive selected
            </button>
            <button
              class="rk-btn rk-btn-secondary cursor-not-allowed"
              disabled
              title="Changing the category of an existing article needs a backend endpoint that does not exist yet."
            >
              Change category for selected
            </button>
            <button class="rk-btn rk-btn-secondary" [disabled]="bulkBusy" (click)="clearSelection()">
              Clear selection
            </button>
          </div>

          <p *ngIf="bulkBusy" class="text-sm text-slate-500">Working on {{ bulkPending }} article{{ bulkPending === 1 ? "" : "s" }}…</p>
        </div>

        <div
          *ngIf="bulkResults.length"
          class="border-b border-slate-200 px-5 py-4"
        >
          <p class="text-sm font-bold">
            Bulk action finished:
            {{ succeededCount }} succeeded, {{ failedCount }} failed
          </p>

          <ul class="mt-2 space-y-1.5">
            <li *ngFor="let result of bulkResults" class="flex flex-wrap items-baseline gap-2 text-xs">
              <span
                class="rk-badge"
                [ngClass]="result.ok ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'"
              >
                {{ result.ok ? "Done" : "Failed" }}
              </span>
              <span class="font-semibold text-slate-700">{{ result.title }}</span>
              <span class="text-slate-500">{{ result.message }}</span>
            </li>
          </ul>
        </div>

        <div class="divide-y divide-slate-100 md:hidden">
          <div *ngFor="let article of page.content" class="px-4 py-4">
            <div class="flex items-start gap-3">
              <input
                *ngIf="canEdit"
                type="checkbox"
                class="mt-1 h-4 w-4 shrink-0 rounded border-slate-300 text-slate-900"
                [checked]="isSelected(article)"
                [attr.aria-label]="'Select ' + article.title"
                (change)="toggleSelection(article)"
              />

              <div class="min-w-0 flex-1">
                <div class="flex items-start gap-2">
                  <a
                    [routerLink]="['/articles', article.id]"
                    class="min-w-0 break-words font-semibold leading-snug hover:underline"
                  >
                    {{ article.title }}
                  </a>
                  <rk-grade-badge
                    *ngIf="gradeOf(article) as grade"
                    [grade]="grade.grade"
                    [reason]="grade.reason"
                  />
                </div>

                <p class="mt-1 break-all text-xs text-slate-500">{{ article.slug }}</p>

                <div class="mt-2 flex flex-wrap items-center gap-2">
                  <rk-status [status]="article.status" />
                  <span class="text-xs text-slate-500">{{ article.visibility }}</span>
                </div>

                <dl class="mt-2 grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
                  <div>
                    <dt class="text-slate-400">Category</dt>
                    <dd class="text-slate-700">{{ categoryName(article.categoryId) }}</dd>
                  </div>
                  <div>
                    <dt class="text-slate-400">Version</dt>
                    <dd class="text-slate-700">{{ article.activeVersionNumber ?? "—" }}</dd>
                  </div>
                </dl>

                <a [routerLink]="['/articles', article.id]" class="rk-btn rk-btn-secondary mt-3">Open</a>
              </div>
            </div>
          </div>
        </div>

        <div class="hidden overflow-x-auto md:block">
          <table class="w-full min-w-[760px] text-left text-sm">
            <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th *ngIf="canEdit" class="w-10 px-5 py-3">
                  <input
                    type="checkbox"
                    class="h-4 w-4 rounded border-slate-300 text-slate-900"
                    [checked]="allSelected"
                    [disabled]="!page.content.length"
                    aria-label="Select all articles on this page"
                    (change)="toggleAll()"
                  />
                </th>
                <th class="px-5 py-3 font-semibold">Article</th>
                <th class="px-5 py-3 font-semibold">Status</th>
                <th class="px-5 py-3 font-semibold">Visibility</th>
                <th class="px-5 py-3 font-semibold">Category</th>
                <th class="px-5 py-3 font-semibold">Version</th>
                <th class="px-5 py-3"></th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr *ngFor="let article of page.content" class="hover:bg-slate-50/70">
                <td *ngIf="canEdit" class="px-5 py-4">
                  <input
                    type="checkbox"
                    class="h-4 w-4 rounded border-slate-300 text-slate-900"
                    [checked]="isSelected(article)"
                    [attr.aria-label]="'Select ' + article.title"
                    (change)="toggleSelection(article)"
                  />
                </td>
                <td class="px-5 py-4">
                  <div class="flex items-center gap-2">
                    <a
                      [routerLink]="['/articles', article.id]"
                      class="min-w-0 break-words font-semibold leading-snug hover:underline"
                    >
                      {{ article.title }}
                    </a>
                    <rk-grade-badge
                      *ngIf="gradeOf(article) as grade"
                      [grade]="grade.grade"
                      [reason]="grade.reason"
                    />
                  </div>
                  <p class="mt-1 text-xs text-slate-500">{{ article.slug }}</p>
                </td>
                <td class="px-5 py-4">
                  <rk-status [status]="article.status" />
                </td>
                <td class="px-5 py-4 text-slate-600">{{ article.visibility }}</td>
                <td class="px-5 py-4 text-slate-600">{{ categoryName(article.categoryId) }}</td>
                <td class="px-5 py-4 text-slate-600">{{ article.activeVersionNumber ?? "—" }}</td>
                <td class="px-5 py-4 text-right">
                  <a [routerLink]="['/articles', article.id]" class="rk-btn rk-btn-secondary">Open</a>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div *ngIf="!page.content.length" class="border-t border-slate-200 px-5 py-12 text-center">
          <p class="font-semibold">No articles found</p>
          <p class="mt-1 text-sm text-slate-500">Try another status or create the first article.</p>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 px-5 py-4 text-sm">
          <span class="text-slate-500">{{ page.totalElements }} total articles</span>
          <div class="flex gap-2">
            <button class="rk-btn rk-btn-secondary" [disabled]="page.first" (click)="load(page.number - 1)">
              Previous
            </button>
            <button class="rk-btn rk-btn-secondary" [disabled]="page.last" (click)="load(page.number + 1)">
              Next
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ArticlesComponent implements OnInit {
  page: Page<Article> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 10,
    first: true,
    last: true
  };

  categories: Category[] = [];
  status = "";
  query = "";
  // searchResults = [];
  searchResults: SearchHit[] = [];

  private readonly performanceByArticle = new Map<string, ArticlePerformance>();
  private readonly expiryByArticle = new Map<string, StaleContent>();
  private gradeDataLoaded = false;

  selectedIds = new Set<string>();
  bulkBusy = false;
  bulkResults: BulkActionResult[] = [];
  bulkTotal = 0;
  importBusy = false;
  importMessage = '';
  health: import("../models").KnowledgeHealth|null = null;

  constructor(private readonly api: ApiService, private readonly auth: AuthService) {}

  get canEdit(): boolean {
    return this.auth.hasAnyRole(["ADMIN", "EDITOR"]);
  }

  get isAdmin(): boolean {
    return this.auth.hasRole("ADMIN");
  }


  ngOnInit(): void {
    this.api.knowledgeHealth().subscribe({next:h=>this.health=h,error:()=>undefined});
    this.api.categories().subscribe((items) => {
      this.categories = items;
    });

    this.loadGradeSignals();
    this.load();
  }

  private loadGradeSignals(): void {
    // Analytics endpoints are ADMIN-only on the backend. Do not call them for
    // editors/reviewers/agent viewers; those calls otherwise produce 401/403
    // noise and can trigger the refresh-token flow unnecessarily.
    if (!this.auth.hasRole("ADMIN")) {
      this.gradeDataLoaded = true;
      return;
    }

    this.api.performance(30).subscribe({
      next: (items) => {
        for (const item of items) {
          this.performanceByArticle.set(item.articleId, item);
        }
        this.gradeDataLoaded = true;
      },
      error: () => {
        this.gradeDataLoaded = true;
      }
    });

    this.api.staleContent(EXPIRY_WARNING_DAYS + 15).subscribe({
      next: (items) => {
        for (const item of items) {
          this.expiryByArticle.set(item.articleId, item);
        }
      },
      error: () => undefined
    });
  }

  gradeOf(article: Article): ArticleGradeInfo | null {
    if (!this.isAdmin || !this.gradeDataLoaded) {
      return null;
    }

    const performance = this.performanceByArticle.get(article.id);
    const expiry = this.expiryByArticle.get(article.id);

    return gradeArticle({
      views: performance?.views ?? 0,
      helpfulCount: performance?.helpfulCount ?? 0,
      unhelpfulCount: performance?.unhelpfulCount ?? 0,
      expiresAt: expiry?.expiresAt ?? null
    });
  }

  load(page = 0): void {
    this.api.listArticles(this.status || undefined, page, 10).subscribe((result) => {
      this.page = result;
      this.selectedIds = new Set();
    });
  }

  isSelected(article: Article): boolean {
    return this.selectedIds.has(article.id);
  }

  get allSelected(): boolean {
    return this.page.content.length > 0 && this.page.content.every((article) => this.selectedIds.has(article.id));
  }

  toggleSelection(article: Article): void {
    const next = new Set(this.selectedIds);

    if (next.has(article.id)) {
      next.delete(article.id);
    } else {
      next.add(article.id);
    }

    this.selectedIds = next;
  }

  toggleAll(): void {
    this.selectedIds = this.allSelected
      ? new Set()
      : new Set(this.page.content.map((article) => article.id));
  }

  clearSelection(): void {
    this.selectedIds = new Set();
  }

  get selectedArticles(): Article[] {
    return this.page.content.filter((article) => this.selectedIds.has(article.id));
  }

  get bulkPending(): number {
    return this.bulkTotal - this.bulkResults.length;
  }

  get succeededCount(): number {
    return this.bulkResults.filter((result) => result.ok).length;
  }

  get failedCount(): number {
    return this.bulkResults.length - this.succeededCount;
  }

  publishSelected(): void {
    if (!this.isAdmin) {
      return;
    }

    const targets = this.selectedArticles;

    if (!targets.length || this.bulkBusy) {
      return;
    }

    this.bulkBusy = true;
    this.bulkResults = [];
    this.bulkTotal = targets.length;

    from(targets)
      .pipe(
        concatMap((article) => this.publishOne(article)),
        toArray()
      )
      .subscribe((results) => {
        this.bulkResults = results;
        this.bulkBusy = false;
        this.load(this.page.number);
      });
  }

  private publishOne(article: Article): Observable<BulkActionResult> {
    return this.api.getVersions(article.id).pipe(
      switchMap((versions) => {
        const version = this.publishableVersion(versions);

        if (!version) {
          return of(this.result(article, false, "no draft or in-review version to publish"));
        }

        const ready$ =
          version.status === "IN_REVIEW"
            ? of(version)
            : this.api.submitForReview(article.id, version.id);

        return ready$.pipe(
          switchMap((submitted) =>
            this.api.reviewVersion(article.id, submitted.id, true, "Published in bulk from the articles list")
          ),
          map(() => this.result(article, true, `version ${version.versionNumber} published`))
        );
      }),
      catchError((error) =>
        of(this.result(article, false, error?.error?.message || "could not be published"))
      )
    );
  }

  private publishableVersion(versions: ArticleVersion[]): ArticleVersion | undefined {
    return versions.find((version) => version.status === "IN_REVIEW") ??
      versions.find((version) => version.status === "DRAFT");
  }

  archiveSelected(): void {
    if (!this.canEdit) {
      return;
    }

    const targets = this.selectedArticles;

    if (!targets.length || this.bulkBusy) {
      return;
    }

    if (!confirm(`Archive ${targets.length} article${targets.length === 1 ? "" : "s"}?`)) {
      return;
    }

    this.bulkBusy = true;
    this.bulkResults = [];
    this.bulkTotal = targets.length;

    from(targets)
      .pipe(
        concatMap((article) =>
          this.api.archiveArticle(article.id).pipe(
            map(() => this.result(article, true, "archived")),
            catchError((error) =>
              of(this.result(article, false, error?.error?.message || "could not be archived"))
            )
          )
        ),
        toArray()
      )
      .subscribe((results) => {
        this.bulkResults = results;
        this.bulkBusy = false;
        this.load(this.page.number);
      });
  }

  private result(article: Article, ok: boolean, message: string): BulkActionResult {
    return { id: article.id, title: article.title, ok, message };
  }

  importFiles(event: Event): void {
    const files = Array.from((event.target as HTMLInputElement).files ?? []);
    if (!files.length) return;
    this.importBusy = true; this.importMessage = "";
    this.api.importArticles(files).subscribe({
      next: result => { this.importMessage = `${result.imported} article${result.imported === 1 ? "" : "s"} imported.`; this.importBusy = false; this.load(); },
      error: e => { this.importMessage = e?.error?.message || "Import failed."; this.importBusy = false; }
    });
  }

  exportArticles(format: "csv"|"markdown"|"html"|"pdf"|"docx"): void {
    this.api.exportArticles(format).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob); const a = document.createElement("a");
        a.href = url; a.download = format === "csv" ? "ricozknow-articles.csv" : "ricozknow-articles.zip"; a.click(); URL.revokeObjectURL(url);
      },
      error: e => this.importMessage = e?.error?.message || "Export failed."
    });
  }

  search(): void {
    if (!this.query.trim()) {
      this.searchResults = [];
      return;
    }

    this.api.search(this.query.trim()).subscribe((result) => {
      this.searchResults = result.results;
    });
  }

  categoryName(id: string | null): string {
    return this.categories.find((category) => category.id === id)?.name ?? "—";
  }
}
