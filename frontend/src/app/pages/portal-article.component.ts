import { CommonModule } from "@angular/common";
import { Component, HostListener, OnInit } from "@angular/core";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { ApiService } from "../core/api.service";
import { BookmarkedArticle, PortalArticleDetail, Tenant } from "../models";
import { PortalAssistantComponent } from "../shared/portal-assistant.component";
import { ThemeToggleComponent } from "../shared/theme-toggle.component";

import { FormsModule } from "@angular/forms";

const BOOKMARKS_KEY = "rk_portal_bookmarks";

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
  imports: [CommonModule, RouterLink, FormsModule, PortalAssistantComponent, ThemeToggleComponent],
  template: `
    <div class="fixed inset-x-0 top-0 z-40 h-1 bg-slate-100">
      <div
        class="h-full transition-[width] duration-100"
        [style.width.%]="readProgress"
        [style.background]="tenant?.primaryColor || '#0f172a'"
      ></div>
    </div>
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

          <div class="flex shrink-0 items-center gap-2 sm:gap-3">
            <a
              routerLink="/portal/search"
              [queryParams]="{ tenant: tenantSlug }"
              class="text-sm font-semibold text-slate-500 hover:text-slate-950"
            >
              Search
            </a>
            <rk-theme-toggle [compact]="true" />
          </div>
        </div>
      </header>

      <main class="mx-auto max-w-4xl px-4 pb-24 pt-10 sm:pt-14">
        <a
          routerLink="/portal"
          [queryParams]="{ tenant: tenantSlug }"
          class="text-sm font-semibold text-slate-600 hover:text-slate-950 hover:underline"
        >
          ← Knowledge base
        </a>

        <article *ngIf="article" class="mt-8">
          <p class="text-xs font-bold uppercase tracking-widest text-slate-400">
            Article
          </p>

          <h1 class="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">
            {{ article.title }}
          </h1>

          <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
            <p class="text-sm text-slate-500">
              Version {{ article.versionNumber }} · Published · {{ readingMinutes }} min read ({{ wordCount }} words)
            </p>
            <div class="flex flex-wrap items-center gap-2">
              <button
                type="button"
                class="rk-btn rk-btn-secondary px-3 py-1.5 text-xs"
                (click)="toggleBookmark()"
              >
                {{ isBookmarked ? "Saved ★" : "Save article ☆" }}
              </button>
              <button
                type="button"
                class="rk-btn rk-btn-secondary px-3 py-1.5 text-xs"
                (click)="copyArticleLink()"
              >
                {{ linkCopied ? "Link copied ✓" : "Copy link" }}
              </button>
              <button
                type="button"
                class="rk-btn rk-btn-secondary px-3 py-1.5 text-xs"
                (click)="printArticle()"
              >
                Print
              </button>
            </div>
          </div>
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
                  <div class="relative overflow-hidden rounded-xl bg-slate-950">
                    <div class="flex items-center justify-between border-b border-slate-800 px-4 py-2 text-[11px] font-semibold text-slate-400">
                      <span>Code snippet</span>
                      <button
                        type="button"
                        class="rounded-md border border-slate-700 px-2.5 py-0.5 text-xs text-slate-200 hover:bg-slate-800"
                        (click)="copyCode(block.text || '', $index)"
                      >
                        {{ copiedCodeIndex === $index ? "Copied ✓" : "Copy" }}
                      </button>
                    </div>
                    <pre class="overflow-x-auto p-4 text-sm leading-6 text-slate-100"><code>{{ block.text }}</code></pre>
                  </div>
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
                class="rk-btn rk-btn-primary"
                [style.background]="customAccentColor"
                [class.text-white]="!!customAccentColor"
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
              class="mt-3 text-sm font-medium text-emerald-700"
            >
              Thanks for your feedback.
            </p>
          </section>
        </article>
      </main>
      <rk-portal-assistant
        [tenantSlug]="tenantSlug || tenant?.slug || 'demo'"
        [primaryColor]="tenant?.primaryColor || '#0f172a'"
      />
    </div>
  `
})
export class PortalArticleComponent implements OnInit {
  article?: PortalArticleDetail;
  sent = false;
  articleQuery = "";
  private filteredBlocks: RichTextBlock[] = [];
  tenant?: Tenant;
  readProgress = 0;
  linkCopied = false;
  copiedCodeIndex: number | null = null;
  isBookmarked = false;

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
        this.syncBookmarkState();
      });
    }
  }

  @HostListener("window:scroll")
  onScroll(): void {
    const doc = document.documentElement;
    const total = doc.scrollHeight - doc.clientHeight;
    if (total <= 0) {
      this.readProgress = 0;
      return;
    }
    this.readProgress = Math.min(100, Math.max(0, Math.round((window.scrollY / total) * 100)));
  }

  get wordCount(): number {
    return this.contentBlocks
      .map((b) => (b.type === "list" ? (b.items || []).join(" ") : b.text || ""))
      .join(" ")
      .trim()
      .split(/\s+/)
      .filter(Boolean).length;
  }

  get readingMinutes(): number {
    return Math.max(1, Math.ceil(this.wordCount / 200));
  }

  private readBookmarks(): BookmarkedArticle[] {
    try {
      const raw = localStorage.getItem(BOOKMARKS_KEY);
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private syncBookmarkState(): void {
    if (!this.article) return;
    this.isBookmarked = this.readBookmarks().some((b) => b.slug === this.article!.slug);
  }

  toggleBookmark(): void {
    if (!this.article) return;
    const current = this.readBookmarks();
    const exists = current.some((b) => b.slug === this.article!.slug);
    const next = exists
      ? current.filter((b) => b.slug !== this.article!.slug)
      : [
          {
            slug: this.article.slug,
            title: this.article.title,
            tenantSlug: this.tenantSlug || this.tenant?.slug || "demo",
            savedAt: new Date().toISOString()
          },
          ...current
        ].slice(0, 12);
    localStorage.setItem(BOOKMARKS_KEY, JSON.stringify(next));
    this.isBookmarked = !exists;
  }

  copyArticleLink(): void {
    void navigator.clipboard?.writeText(window.location.href).catch(() => undefined);
    this.linkCopied = true;
    setTimeout(() => (this.linkCopied = false), 2000);
  }

  copyCode(code: string, index: number): void {
    void navigator.clipboard?.writeText(code).catch(() => undefined);
    this.copiedCodeIndex = index;
    setTimeout(() => {
      if (this.copiedCodeIndex === index) this.copiedCodeIndex = null;
    }, 2000);
  }

  printArticle(): void {
    window.print();
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

  get customAccentColor(): string | null {
    const color = this.tenant?.primaryColor?.trim().toLowerCase();
    if (!color || color === "#0f172a" || color === "#000000" || color === "#111827") {
      return null;
    }
    return this.tenant!.primaryColor!;
  }
}