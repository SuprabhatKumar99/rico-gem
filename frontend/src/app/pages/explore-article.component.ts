import { CommonModule } from "@angular/common";
import { Component, OnInit, inject } from "@angular/core";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { HttpClient, HttpParams } from "@angular/common/http";
import { resolvePublicTenant } from "../core/public-tenant";

interface RichBlock { type?: string; text?: string; level?: number; items?: string[]; code?: string; url?: string; alt?: string; caption?: string; }
interface ArticleDetail { id: string; slug: string; title: string; categoryId: string | null; content: { blocks?: RichBlock[] } | RichBlock[]; versionNumber: number; publishedAt?: string; }

@Component({
  selector: "rk-explore-article",
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="min-h-screen bg-white text-slate-950">
      <header class="border-b border-slate-200">
        <div class="mx-auto flex max-w-5xl items-center justify-between px-4 py-5 sm:px-6">
          <a [routerLink]="['/']" class="font-bold">RicozKnow</a>
          <a routerLink="/explore" [queryParams]="tenantQuery" class="text-sm font-semibold text-slate-500 hover:text-slate-950">← Explore knowledge</a>
        </div>
      </header>

      <main class="mx-auto max-w-4xl px-4 py-12 sm:px-6 lg:py-16">
        <div *ngIf="!tenant" class="rounded-2xl border border-amber-200 bg-amber-50 p-8 text-center"><p class="font-bold">Tenant required</p><p class="mt-2 text-sm text-amber-900/70">Open this article from an organization's public subdomain or add <code>?tenant=testorg</code>.</p></div>
        <div *ngIf="tenant && loading" class="animate-pulse"><div class="h-4 w-24 rounded bg-slate-100"></div><div class="mt-5 h-12 w-3/4 rounded bg-slate-100"></div><div class="mt-10 h-4 w-full rounded bg-slate-100"></div><div class="mt-3 h-4 w-11/12 rounded bg-slate-100"></div></div>

        <article *ngIf="article && !loading">
          <p class="text-xs font-bold uppercase tracking-widest text-slate-400">Published article</p>
          <h1 class="mt-3 text-4xl font-bold tracking-tight sm:text-5xl">{{ article.title }}</h1>
          <p class="mt-4 text-sm text-slate-500">Version {{ article.versionNumber }} · Published knowledge</p>
          <div class="my-10 border-t border-slate-200"></div>
          <div class="space-y-6 text-base leading-8 text-slate-700">
            <ng-container *ngFor="let block of blocks">
              <h2 *ngIf="block.type === 'heading'" class="mt-10 text-2xl font-bold leading-tight text-slate-950">{{ block.text }}</h2>
              <p *ngIf="block.type === 'paragraph'" class="whitespace-pre-line">{{ block.text }}</p>
              <ul *ngIf="block.type === 'list'" class="list-disc space-y-2 pl-6"><li *ngFor="let item of block.items">{{ item }}</li></ul>
              <pre *ngIf="block.type === 'code'" class="overflow-x-auto rounded-2xl bg-slate-950 p-5 text-sm leading-6 text-slate-100"><code>{{ block.text || block.code }}</code></pre>
              <figure *ngIf="block.type === 'image' && block.url" class="my-8"><img [src]="block.url" [alt]="block.alt || ''" class="max-h-[520px] w-full rounded-2xl border border-slate-200 object-contain" /><figcaption *ngIf="block.caption" class="mt-2 text-center text-sm text-slate-500">{{ block.caption }}</figcaption></figure>
            </ng-container>
          </div>
        </article>

        <div *ngIf="tenant && !loading && !article" class="rounded-2xl border border-slate-200 p-10 text-center"><h1 class="text-xl font-bold">Article not found</h1><p class="mt-2 text-sm text-slate-500">This article may no longer be publicly available.</p><a routerLink="/explore" [queryParams]="tenantQuery" class="rk-btn rk-btn-primary mt-6 inline-flex">Back to knowledge</a></div>
      </main>
    </div>
  `
})
export class ExploreArticleComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly baseUrl = localStorage.getItem("rk_api_url")?.replace(/\/$/, "") || "http://localhost:8080";

  tenant = "";
  article?: ArticleDetail;
  blocks: RichBlock[] = [];
  loading = true;

  get tenantQuery(): Record<string, string> { return this.tenant ? { tenant: this.tenant } : {}; }

  ngOnInit(): void {
    this.tenant = this.route.snapshot.queryParamMap.get("tenant")?.trim() || resolvePublicTenant();
    const slug = this.route.snapshot.paramMap.get("slug");
    if (!this.tenant || !slug) { this.loading = false; return; }

    let params = new HttpParams().set("tenant", this.tenant);
    this.http.get<ArticleDetail>(`${this.baseUrl}/api/v1/portal/articles/${encodeURIComponent(slug)}`, { params }).subscribe({
      next: (article) => { this.article = article; this.blocks = Array.isArray(article.content) ? article.content : (article.content?.blocks || []); this.loading = false; },
      error: () => { this.article = undefined; this.loading = false; }
    });
  }
}
