import { CommonModule } from "@angular/common";
import { Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { ApiService } from "../core/api.service";
import { Article } from "../models";

export interface PaletteNavItem {
  label: string;
  path: string;
  section: "Workspace" | "Administration" | "Quick Action";
  queryParams?: Record<string, string>;
}

interface PaletteResult {
  id: string;
  title: string;
  subtitle: string;
  group: "Quick Actions" | "Pages" | "Articles";
  path: string[];
  queryParams?: Record<string, string>;
}

@Component({
  selector: "rk-command-palette",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div
      *ngIf="open"
      class="fixed inset-0 z-50 flex items-start justify-center bg-slate-950/45 px-3 pt-10 backdrop-blur-[2px] sm:px-4 sm:pt-20"
      role="dialog"
      aria-modal="true"
      aria-label="Quick command palette"
      (click)="close()"
    >
      <div
        class="w-full max-w-2xl overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl"
        (click)="$event.stopPropagation()"
      >
        <div class="flex items-center gap-3 border-b border-slate-200 px-4 py-3.5">
          <span class="text-sm font-bold text-slate-400" aria-hidden="true">⌘</span>
          <input
            #searchInput
            class="w-full bg-transparent text-sm text-slate-900 outline-none placeholder:text-slate-400"
            [(ngModel)]="query"
            (ngModelChange)="onQueryChange()"
            placeholder="Jump to a page, article, or action…"
            aria-label="Search pages, articles, and actions"
          />
          <kbd class="rounded-md border border-slate-200 bg-slate-50 px-2 py-0.5 text-[11px] font-semibold text-slate-500">ESC</kbd>
        </div>

        <div class="max-h-[min(26rem,62vh)] overflow-y-auto p-2">
          <div *ngIf="!filteredResults.length" class="px-4 py-10 text-center text-sm text-slate-500">
            No matching pages, articles, or actions for “{{ query }}”.
          </div>

          <ng-container *ngFor="let group of groupedResults">
            <div *ngIf="group.items.length" class="mb-2">
              <p class="px-3 py-1.5 text-[11px] font-bold uppercase tracking-widest text-slate-400">
                {{ group.label }}
              </p>
              <button
                *ngFor="let item of group.items"
                type="button"
                class="flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2.5 text-left transition"
                [class.bg-slate-900]="isActive(item)"
                [class.text-white]="isActive(item)"
                [class.hover:bg-slate-100]="!isActive(item)"
                (mouseenter)="selectItem(item)"
                (click)="execute(item)"
              >
                <div class="min-w-0">
                  <p class="truncate text-sm font-semibold">{{ item.title }}</p>
                  <p
                    class="truncate text-xs"
                    [class.text-slate-300]="isActive(item)"
                    [class.text-slate-500]="!isActive(item)"
                  >
                    {{ item.subtitle }}
                  </p>
                </div>
                <span
                  class="shrink-0 text-xs font-medium"
                  [class.text-slate-300]="isActive(item)"
                  [class.text-slate-400]="!isActive(item)"
                >
                  ↵
                </span>
              </button>
            </div>
          </ng-container>
        </div>

        <div class="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-4 py-2.5 text-xs text-slate-500">
          <span>Use ↑ ↓ to navigate · Enter to open</span>
          <span>{{ filteredResults.length }} result{{ filteredResults.length === 1 ? "" : "s" }}</span>
        </div>
      </div>
    </div>
  `
})
export class CommandPaletteComponent {
  @Input() navItems: PaletteNavItem[] = [];
  @Input() tenantSlug = "";
  @Input() canCreateArticle = false;
  @Output() openChange = new EventEmitter<boolean>();
  @ViewChild("searchInput") searchInput?: ElementRef<HTMLInputElement>;

  open = false;
  query = "";
  activeIndex = 0;
  private articlesLoaded = false;
  private articles: Article[] = [];

  constructor(
    private readonly router: Router,
    private readonly api: ApiService
  ) {}

  @HostListener("window:keydown", ["$event"])
  onKeyDown(event: KeyboardEvent): void {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
      event.preventDefault();
      if (this.open) {
        this.close();
      } else {
        this.show();
      }
      return;
    }

    if (!this.open) return;

    if (event.key === "Escape") {
      event.preventDefault();
      this.close();
      return;
    }

    const total = this.filteredResults.length;
    if (!total) return;

    if (event.key === "ArrowDown") {
      event.preventDefault();
      this.activeIndex = (this.activeIndex + 1) % total;
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      this.activeIndex = (this.activeIndex - 1 + total) % total;
    } else if (event.key === "Enter") {
      event.preventDefault();
      const target = this.filteredResults[this.activeIndex];
      if (target) this.execute(target);
    }
  }

  show(): void {
    this.open = true;
    this.query = "";
    this.activeIndex = 0;
    this.openChange.emit(true);
    this.loadArticlesIfNeeded();
    setTimeout(() => this.searchInput?.nativeElement?.focus(), 0);
  }

  close(): void {
    this.open = false;
    this.openChange.emit(false);
  }

  onQueryChange(): void {
    this.activeIndex = 0;
  }

  private loadArticlesIfNeeded(): void {
    if (this.articlesLoaded) return;
    this.articlesLoaded = true;
    this.api.listArticles(undefined, 0, 40).subscribe({
      next: (page) => {
        this.articles = page.content || [];
      },
      error: () => {
        this.articlesLoaded = false;
      }
    });
  }

  get filteredResults(): PaletteResult[] {
    const q = this.query.trim().toLowerCase();
    const actions: PaletteResult[] = [];

    if (this.canCreateArticle) {
      actions.push({
        id: "act:new-article",
        title: "Create new article",
        subtitle: "Open structured editor for a fresh draft",
        group: "Quick Actions",
        path: ["/articles/new"]
      });
    }
    actions.push(
      {
        id: "act:portal",
        title: "Open public Help Center portal",
        subtitle: "Preview customer-facing knowledge base",
        group: "Quick Actions",
        path: ["/portal"],
        queryParams: this.tenantSlug ? { tenant: this.tenantSlug } : undefined
      },
      {
        id: "act:widget",
        title: "Launch embeddable Q&A widget",
        subtitle: "Test grounded customer answers and citations",
        group: "Quick Actions",
        path: ["/widget"],
        queryParams: this.tenantSlug ? { tenant: this.tenantSlug } : undefined
      }
    );

    const pages: PaletteResult[] = this.navItems.map((item) => ({
      id: `nav:${item.path}`,
      title: item.label,
      subtitle: `${item.section} · ${item.path}`,
      group: "Pages",
      path: [item.path],
      queryParams: item.queryParams
    }));

    const articleItems: PaletteResult[] = this.articles.map((art) => ({
      id: `art:${art.id}`,
      title: art.title,
      subtitle: `${art.status} · ${art.slug}`,
      group: "Articles",
      path: ["/articles", art.id]
    }));

    const all = [...actions, ...pages, ...articleItems];
    if (!q) {
      return [...actions, ...pages.slice(0, 8), ...articleItems.slice(0, 6)];
    }

    return all
      .filter((item) => `${item.title} ${item.subtitle}`.toLowerCase().includes(q))
      .slice(0, 14);
  }

  get groupedResults(): { label: string; items: PaletteResult[] }[] {
    const results = this.filteredResults;
    const order: PaletteResult["group"][] = ["Quick Actions", "Pages", "Articles"];
    return order.map((group) => ({
      label: group,
      items: results.filter((r) => r.group === group)
    }));
  }

  isActive(item: PaletteResult): boolean {
    return this.filteredResults[this.activeIndex]?.id === item.id;
  }

  selectItem(item: PaletteResult): void {
    const idx = this.filteredResults.findIndex((r) => r.id === item.id);
    if (idx !== -1) this.activeIndex = idx;
  }

  execute(item: PaletteResult): void {
    this.close();
    void this.router.navigate(item.path, item.queryParams ? { queryParams: item.queryParams } : undefined);
  }
}
