import { CommonModule } from "@angular/common";
import { Component, OnInit, inject } from "@angular/core";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { HttpClient, HttpParams } from "@angular/common/http";
import { resolvePublicTenant } from "../core/public-tenant";

interface PageResponse<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number; first: boolean; last: boolean; }
interface ArticleSummary { id: string; slug: string; title: string; categoryId: string | null; }
interface SearchHit { articleId: string; slug: string; title: string; excerpt: string; categoryId: string | null; score: number; }
interface SearchResponse { query: string; results: SearchHit[]; total: number; }

@Component({
  selector: "rk-explore",
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="min-h-screen bg-slate-50 text-slate-950">
      <header class="border-b border-slate-200 bg-white">
        <div class="mx-auto flex max-w-7xl items-center justify-between px-4 py-5 sm:px-6 lg:px-8">
          <a [routerLink]="['/']" class="flex items-center gap-3">
            <span class="grid h-9 w-9 place-items-center rounded-xl bg-slate-950 font-black text-white">R</span>
            <div><p class="font-bold">RicozKnow</p><p class="text-[10px] uppercase tracking-widest text-slate-400">Knowledge portal</p></div>
          </a>
          <div class="flex items-center gap-2">
            <a routerLink="/login" class="rk-btn rk-btn-secondary">Sign in</a>
            <a routerLink="/register" class="rk-btn rk-btn-primary">Get started</a>
          </div>
        </div>
      </header>

      <main class="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8 lg:py-16">
        <section *ngIf="!tenant" class="mx-auto max-w-2xl rounded-3xl border border-amber-200 bg-amber-50 p-8 text-center">
          <p class="text-xs font-bold uppercase tracking-widest text-amber-700">Tenant required</p>
          <h1 class="mt-3 text-2xl font-bold">This knowledge portal needs an organization.</h1>
          <p class="mt-3 text-sm leading-6 text-amber-900/70">Open this page from an organization's public subdomain, or include a tenant query parameter during local development.</p>
          <code class="mt-5 inline-block rounded-lg bg-white px-3 py-2 text-sm text-slate-700">/explore?tenant=testorg</code>
        </section>

        <ng-container *ngIf="tenant">
          <section class="mx-auto max-w-3xl text-center">
            <p class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">Explore the knowledge base</p>
            <h1 class="mt-3 text-4xl font-bold tracking-tight sm:text-5xl">Find the answer you need.</h1>
            <p class="mx-auto mt-4 max-w-2xl text-base leading-7 text-slate-600">Browse published public knowledge from this organization.</p>
            <div class="mt-7 rounded-2xl border border-slate-200 bg-white p-2 shadow-sm">
              <div class="flex gap-2">
                <input #searchBox class="rk-input border-0 shadow-none focus:ring-0" placeholder="Search published knowledge..." (keyup.enter)="search(searchBox.value)" />
                <button class="rk-btn rk-btn-primary shrink-0" (click)="search(searchBox.value)">Search</button>
              </div>
            </div>
          </section>

          <section class="mt-12">
            <div class="flex items-end justify-between gap-4">
              <div><p class="text-xs font-bold uppercase tracking-widest text-slate-400">Published articles</p><h2 class="mt-1 text-2xl font-bold">Knowledge you can explore</h2></div>
              <button *ngIf="query" class="text-sm font-semibold text-slate-500 hover:text-slate-950" (click)="clearSearch(searchBox)">Clear search</button>
            </div>

            <div *ngIf="loading" class="mt-6 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              <div *ngFor="let item of [1,2,3,4,5,6]" class="h-40 animate-pulse rounded-2xl border border-slate-200 bg-white"></div>
            </div>

            <div *ngIf="!loading && articles.length" class="mt-6 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              <a *ngFor="let article of articles" [routerLink]="['/explore/article', article.slug]" [queryParams]="tenantQuery" class="group rounded-2xl border border-slate-200 bg-white p-6 transition hover:-translate-y-0.5 hover:border-slate-300 hover:shadow-lg">
                <p class="text-xs font-bold uppercase tracking-widest text-slate-400">Published article</p>
                <h3 class="mt-3 text-xl font-bold leading-7 group-hover:underline group-hover:underline-offset-4">{{ article.title }}</h3>
                <p class="mt-3 text-sm text-slate-500">Read the published article →</p>
              </a>
            </div>

            <div *ngIf="!loading && !articles.length" class="mt-6 rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center">
              <p class="font-bold">{{ query ? 'No matching articles' : 'No public articles available' }}</p>
              <p class="mt-2 text-sm text-slate-500">{{ query ? 'Try a different search term.' : 'Published public knowledge will appear here.' }}</p>
            </div>

            <div *ngIf="totalPages > 1" class="mt-8 flex items-center justify-center gap-2">
              <button class="rk-btn rk-btn-secondary" [disabled]="page === 0" (click)="load(page - 1)">Previous</button>
              <span class="px-3 text-sm text-slate-500">Page {{ page + 1 }} of {{ totalPages }}</span>
              <button class="rk-btn rk-btn-secondary" [disabled]="page >= totalPages - 1" (click)="load(page + 1)">Next</button>
            </div>
          </section>
        </ng-container>
      </main>
    </div>
  `
})
export class ExploreComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly baseUrl = localStorage.getItem("rk_api_url")?.replace(/\/$/, "") || "http://localhost:8080";

  tenant = "";
  articles: ArticleSummary[] = [];
  query = "";
  page = 0;
  totalPages = 0;
  loading = false;

  get tenantQuery(): Record<string, string> {
    return this.tenant ? { tenant: this.tenant } : {};
  }

  ngOnInit(): void {
    // Query parameter wins, otherwise resolve the production subdomain.
    this.tenant = this.route.snapshot.queryParamMap.get("tenant")?.trim() || resolvePublicTenant();
    if (this.tenant) this.load(0);
  }

  load(page: number): void {
    if (!this.tenant) return;
    this.loading = true;
    this.page = page;
    const params = this.params({ page, size: 9 });
    this.http.get<PageResponse<ArticleSummary>>(`${this.baseUrl}/api/v1/portal/articles`, { params }).subscribe({
      next: (result) => { this.articles = result.content; this.totalPages = result.totalPages; this.loading = false; },
      error: () => { this.articles = []; this.totalPages = 0; this.loading = false; }
    });
  }

  search(value: string): void {
    this.query = value.trim();
    if (!this.query) { this.load(0); return; }
    this.loading = true;
    this.http.get<SearchResponse>(`${this.baseUrl}/api/v1/portal/search`, { params: this.params({ q: this.query, page: 0 }) }).subscribe({
      next: (result) => { this.articles = result.results.map((item) => ({ id: item.articleId, slug: item.slug, title: item.title, categoryId: item.categoryId })); this.page = 0; this.totalPages = 1; this.loading = false; },
      error: () => { this.articles = []; this.totalPages = 0; this.loading = false; }
    });
  }

  clearSearch(input: HTMLInputElement): void { this.query = ""; input.value = ""; this.load(0); }

  private params(values: Record<string, string | number>): HttpParams {
    let params = new HttpParams();
    if (this.tenant) params = params.set("tenant", this.tenant);
    for (const [key, value] of Object.entries(values)) params = params.set(key, String(value));
    return params;
  }
}
