import { CommonModule } from "@angular/common";
import { Component } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";
import { PortalArticleDetail, SearchHit } from "../models";

@Component({
  selector: "rk-agent",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <p class="text-sm font-semibold text-slate-500">Integration</p>
      <h1 class="mt-1 text-3xl font-bold tracking-tight">Agent access</h1>
      <p class="mt-2 text-sm text-slate-500">
        Read-only search and article retrieval using the authenticated agent API.
      </p>

      <section class="rk-card mt-7 p-6">
        <div class="flex flex-col gap-3 sm:flex-row">
          <input
            class="rk-input"
            [(ngModel)]="query"
            (keyup.enter)="search()"
            placeholder="Search published knowledge"
            aria-label="Agent knowledge search"
          />

          <button
            class="rk-btn rk-btn-primary"
            (click)="search()"
          >
            Search
          </button>
        </div>

        <div *ngIf="recentSearches.length" class="mt-4">
          <p class="text-[11px] font-bold uppercase tracking-wide text-slate-400">Recent searches</p>
          <div class="mt-2 flex flex-wrap gap-2">
            <button
              *ngFor="let recent of recentSearches"
              type="button"
              class="rounded-full border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50"
              (click)="runSearch(recent)"
            >
              {{ recent }}
            </button>
          </div>
        </div>

        <div class="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <p class="text-[11px] font-bold uppercase tracking-wide text-slate-400">
              Call this endpoint from another system
            </p>

            <button type="button" class="text-xs font-semibold text-slate-600 hover:underline" (click)="copyExample()">
              {{ exampleCopied ? "Copied" : "Copy" }}
            </button>
          </div>

          <pre class="mt-2 overflow-x-auto rounded-lg bg-slate-950 p-3 text-xs leading-5 text-slate-100"><code>{{ curlExample }}</code></pre>

          <p class="mt-2 text-xs text-slate-500">
            Uses the same base address this app is configured with, and the same bearer token as your session.
          </p>
        </div>

        <p *ngIf="searched && durationMs !== null" class="mt-6 text-sm font-semibold text-slate-600">
          {{ results.length }} result{{ results.length === 1 ? "" : "s" }} found in {{ durationMs }} ms
        </p>

        <div class="mt-6 divide-y divide-slate-100">
          <div
            *ngFor="let result of results"
            class="py-5"
          >
            <div class="flex items-start justify-between gap-4">
              <div>
                <p class="font-bold">{{ result.title }}</p>

                <p
                  class="mt-2 text-sm leading-6 text-slate-500"
                  [innerHTML]="result.excerpt"
                ></p>
              </div>

              <span class="text-xs text-slate-400">
                {{ result.score | number:"1.2-2" }}
              </span>
            </div>

            <button
              class="mt-3 text-sm font-semibold underline underline-offset-4"
              (click)="open(result.articleId!)"
            >
              Fetch authoritative article
            </button>
          </div>
        </div>

        <div
          *ngIf="searched && !results.length"
          class="mt-8 rounded-xl bg-slate-50 p-6 text-center"
        >
          <p class="font-semibold">No results</p>
          <p class="mt-1 text-sm text-slate-500">
            Try a different question or phrase.
          </p>
        </div>
      </section>

      <section
        *ngIf="article"
        class="rk-card mt-6 p-6"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <p class="text-xs font-bold uppercase tracking-widest text-slate-400">
              Article
            </p>

            <h2 class="mt-2 text-2xl font-bold">
              {{ article.title }}
            </h2>
          </div>

          <button
            class="rk-btn rk-btn-secondary"
            (click)="article = undefined"
          >
            Close
          </button>
        </div>

        <!-- Rich text article content -->
        <div class="mt-8 space-y-6">
          @for (block of article.content.blocks; track $index) {
            @switch (block["type"]) {

              @case ("heading") {
                @switch (block["level"]) {

                  @case (1) {
                    <h1 class="text-3xl font-bold text-slate-950">
                      {{ block["text"] }}
                    </h1>
                  }

                  @case (2) {
                    <h2 class="text-2xl font-bold text-slate-950">
                      {{ block["text"] }}
                    </h2>
                  }

                  @case (3) {
                    <h3 class="text-xl font-semibold text-slate-950">
                      {{ block["text"] }}
                    </h3>
                  }

                  @default {
                    <h4 class="text-lg font-semibold text-slate-950">
                      {{ block["text"] }}
                    </h4>
                  }

                }
              }

              @case ("paragraph") {
                <p class="text-base leading-8 text-slate-700">
                  {{ block["text"] }}
                </p>
              }

              @case ("list") {
                <ul class="list-disc space-y-2 pl-6 text-base leading-7 text-slate-700">
                  @for (item of block["items"]; track $index) {
                    <li>{{ item }}</li>
                  }
                </ul>
              }

              @case ("code") {
                <pre
                  class="overflow-x-auto rounded-xl bg-slate-950 p-4 text-sm text-slate-100"
                ><code>{{ block["text"] || block["code"] }}</code></pre>
              }

              @case ("image") {
                <figure>
                  <img
                    [src]="block['url']"
                    [alt]="block['alt'] || ''"
                    class="max-w-full rounded-xl border border-slate-200"
                  />

                  @if (block["caption"]) {
                    <figcaption class="mt-2 text-sm text-slate-500">
                      {{ block["caption"] }}
                    </figcaption>
                  }
                </figure>
              }

            }
          }
        </div>
      </section>
    </div>
  `
})
export class AgentComponent {
  query = "";
  searched = false;
  results: SearchHit[] = [];
  article?: PortalArticleDetail;

  durationMs: number | null = null;
  recentSearches: string[] = [];
  exampleCopied = false;

  private readonly baseUrl =
    localStorage.getItem("rk_api_url")?.replace(/\/$/, "") || "http://localhost:8080";

  constructor(private readonly api: ApiService) {}

  get curlExample(): string {
    const sample = this.query.trim() || "how do I reset my password";

    return [
      `curl -G "${this.baseUrl}/api/v1/agent/search" \\`,
      `  --data-urlencode "q=${sample}" \\`,
      `  -H "Authorization: Bearer <your-access-token>" \\`,
      `  -H "Accept: application/json"`
    ].join("\n");
  }

  copyExample(): void {
    navigator.clipboard
      ?.writeText(this.curlExample)
      .then(() => {
        this.exampleCopied = true;
        setTimeout(() => (this.exampleCopied = false), 1800);
      })
      .catch(() => (this.exampleCopied = false));
  }

  search(): void {
    const term = this.query.trim();

    if (!term) {
      return;
    }

    this.runSearch(term);
  }

  runSearch(term: string): void {
    const startedAt = performance.now();

    this.query = term;
    this.rememberSearch(term);

    this.api.agentSearch(term).subscribe({
      next: (result) => {
        this.durationMs = Math.round(performance.now() - startedAt);
        this.results = result.results;
        this.searched = true;
      },
      error: () => {
        this.durationMs = Math.round(performance.now() - startedAt);
        this.results = [];
        this.searched = true;
      }
    });
  }

  private rememberSearch(term: string): void {
    this.recentSearches = [term, ...this.recentSearches.filter((item) => item !== term)].slice(0, 5);
  }

  open(id: string): void {
    this.api.agentArticle(id).subscribe((article) => {
      this.article = article;
    });
  }
}