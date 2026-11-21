import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { ApiService } from "../core/api.service";
import { PortalArticleDetail, Tenant } from "../models";

import { FormsModule } from "@angular/forms";

type RichTextBlock = {
  type: "heading" | "paragraph" | "image" | "list" | "code";
  text?: string;
  level?: number;
  items?: string[];
  assetId?: string;
};

@Component({
  selector: "rk-portal-article",
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="min-h-screen bg-white">
      <header class="border-b border-slate-200">
        <div class="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-5">
          <a
            routerLink="/portal"
            [queryParams]="{ tenant: tenantSlug }"
            class="flex min-w-0 items-center gap-3"
          >
            <img
              *ngIf="tenant?.logoUrl"
              [src]="tenant?.logoUrl"
              alt=""
              class="h-9 w-9 rounded-xl object-cover"
            />
            <span
              *ngIf="!tenant?.logoUrl"
              class="grid h-9 w-9 shrink-0 place-items-center rounded-xl font-black text-white"
              [style.background]="tenant?.primaryColor || '#0f172a'"
            >
              R
            </span>
            <span class="truncate font-bold">
              {{ tenant?.portalTitle || tenant?.name || "Knowledge Portal" }}
            </span>
          </a>

          <a
            routerLink="/portal/search"
            [queryParams]="{ tenant: tenantSlug }"
            class="text-sm font-semibold text-slate-500"
          >
            Search
          </a>
        </div>
      </header>

      <main class="mx-auto max-w-4xl px-4 py-10 sm:py-14">
        <a
          routerLink="/portal"
          [queryParams]="{ tenant: tenantSlug }"
          class="text-sm font-semibold hover:underline"
          [style.color]="tenant?.primaryColor || '#475569'"
        >
          ← Knowledge base
        </a>

        <article *ngIf="article" class="mt-8">
          <p class="text-xs font-bold uppercase tracking-widest text-slate-400">
            Article
          </p>

          <h1 class="mt-3 text-4xl font-bold tracking-tight">
            {{ article.title }}
          </h1>

          <p class="mt-3 text-sm text-slate-500">
            Version {{ article.versionNumber }} · Published
          </p>
          <div class="my-6 rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <label class="rk-label">Search within this article</label>
            <input class="rk-input bg-white" [(ngModel)]="articleQuery" (ngModelChange)="filterArticle()" placeholder="Find a word or phrase…"/>
          </div>

          <nav *ngIf="toc.length" class="my-6 rounded-2xl border border-slate-200 p-5">
            <p class="text-xs font-bold uppercase tracking-widest text-slate-400">On this page</p>
            <div class="mt-3 space-y-2">
              <a *ngFor="let item of toc" [href]="'#'+item.id" class="block text-sm text-slate-600 hover:text-slate-950" [style.padding-left.px]="(item.level-1)*14">{{item.text}}</a>
            </div>
          </nav>

          <div class="my-10 border-t border-slate-200"></div>

          <div class="space-y-6">
            @for (block of visibleBlocks; track $index) {
              @switch (block.type) {
                @case ("heading") {
                  @switch (block.level) {
                    @case (1) {
                      <h1 [id]="blockAnchor(block)" class="text-3xl font-bold text-slate-950">
                        {{ block.text }}
                      </h1>
                    }

                    @case (2) {
                      <h2 [id]="blockAnchor(block)" class="text-2xl font-bold text-slate-950">
                        {{ block.text }}
                      </h2>
                    }

                    @case (3) {
                      <h3 [id]="blockAnchor(block)" class="text-xl font-semibold text-slate-950">
                        {{ block.text }}
                      </h3>
                    }

                    @default {
                      <h4 [id]="blockAnchor(block)" class="text-lg font-semibold text-slate-950">
                        {{ block.text }}
                      </h4>
                    }
                  }
                }

                @case ("paragraph") {
                  <p class="text-base leading-8 text-slate-700">
                    {{ block.text }}
                  </p>
                }

                @case ("list") {
                  <ul
                    class="list-disc space-y-2 pl-6 text-base leading-7 text-slate-700"
                  >
                    @for (item of block.items ?? []; track $index) {
                      <li>{{ item }}</li>
                    }
                  </ul>
                }

                @case ("code") {
                  <pre
                    class="overflow-x-auto rounded-xl bg-slate-950 p-4 text-sm leading-6 text-slate-100"
                  ><code>{{ block.text }}</code></pre>
                }

                @case ("image") {
                  <div
                    class="rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-500"
                  >
                    Image asset: {{ block.assetId }}
                  </div>
                }
              }
            }
          </div>

          <div *ngIf="article?.translations?.length" class="my-8 rounded-2xl border border-slate-200 p-4">
            <p class="text-xs font-bold uppercase tracking-widest text-slate-400">Available languages</p>
            <div class="mt-3 flex flex-wrap gap-2"><a *ngFor="let t of article?.translations" [routerLink]="['/portal/article', t.slug]" [queryParams]="{tenant: tenantSlug}" class="rk-btn rk-btn-secondary text-xs">{{t.language.toUpperCase()}} · {{t.title}}</a></div>
          </div>

          <div class="my-10 border-t border-slate-200"></div>

          <section class="rounded-2xl border border-slate-200 p-6">
            <p class="font-bold">
              <span class="border-b-2 pb-0.5" [style.border-color]="tenant?.primaryColor || '#0f172a'">
                Was this article helpful?
              </span>
            </p>

            <div class="mt-3 flex gap-2">
              <button
                class="rk-btn text-white"
                [style.background]="tenant?.primaryColor || '#0f172a'"
                (click)="feedback(true)"
              >
                Yes
              </button>

              <button
                class="rk-btn rk-btn-secondary"
                (click)="feedback(false)"
              >
                No
              </button>
            </div>

            <p
              *ngIf="sent"
              class="mt-3 text-sm"
              [style.color]="tenant?.primaryColor || '#047857'"
            >
              Thanks for your feedback.
            </p>
          </section>
        </article>
      </main>
    </div>
  `
})
export class PortalArticleComponent implements OnInit {
  article?: PortalArticleDetail;
  sent = false;
  articleQuery = "";
  private filteredBlocks: RichTextBlock[] = [];
  tenant?: Tenant;

  constructor(
    private readonly api: ApiService,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.loadTenant();

    const slug = this.route.snapshot.paramMap.get("slug");

    if (slug) {
      this.api.portalArticle(slug).subscribe((article) => {
        this.article = article;
      });
    }
  }

  private loadTenant(): void {
    const tenantParam = new URLSearchParams(window.location.search).get("tenant");
    const host = window.location.hostname;

    if (!tenantParam && host !== "localhost" && host !== "127.0.0.1") {
      const parts = host.split(".");

      if (parts.length >= 3) {
        this.api.tenantLookup(host.endsWith(".ricozknow.com") ? parts[0] : host).subscribe({
          next: (tenant) => (this.tenant = tenant),
          error: () => undefined
        });
      }

      return;
    }

    if (!tenantParam) {
      return;
    }

    this.api.tenantLookup(tenantParam).subscribe({
      next: (tenant) => (this.tenant = tenant),
      error: () => undefined
    });
  }

  get contentBlocks(): RichTextBlock[] {
    if (!this.article?.content) {
      return [];
    }

    const content = this.article.content as {
      blocks?: unknown;
    };

    if (!Array.isArray(content.blocks)) {
      return [];
    }

    return content.blocks.filter(
      (block): block is RichTextBlock =>
        typeof block === "object" &&
        block !== null &&
        "type" in block &&
        typeof (block as { type?: unknown }).type === "string"
    );
  }

  get visibleBlocks(): RichTextBlock[] {
    if (!this.articleQuery.trim()) return this.contentBlocks;
    const q = this.articleQuery.toLowerCase();
    return this.contentBlocks.filter(b => `${b.text || ""} ${(b.items || []).join(" ")}`.toLowerCase().includes(q));
  }
  get toc(): {id:string;text:string;level:number}[] {
    return this.contentBlocks.filter(b => b.type === "heading" && !!b.text)
      .map(b => ({id:this.blockAnchor(b), text:b.text!, level:Math.max(1, Math.min(4, b.level || 2))}));
  }
  filterArticle(): void { /* Angular binding intentionally drives visibleBlocks */ }
  blockAnchor(block: RichTextBlock): string {
    const index = this.contentBlocks.indexOf(block);
    return `section-${index < 0 ? 0 : index}`;
  }

  feedback(helpful: boolean): void {
    if (!this.article) {
      return;
    }

    this.api.portalFeedback(this.article.id, helpful).subscribe(() => {
      this.sent = true;
    });
  }

  get tenantSlug(): string | null {
    return this.route.snapshot.queryParamMap.get("tenant");
  }
}