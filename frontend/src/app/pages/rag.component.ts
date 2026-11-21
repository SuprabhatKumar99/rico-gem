import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";
import { Category, CreatedRagApiKey, RagApiKey, RagSearchResult } from "../models";

@Component({
  selector: "rk-rag",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <p class="text-sm font-semibold text-slate-500">Integration</p>
      <h1 class="mt-1 text-3xl font-bold tracking-tight">RAG</h1>
      <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
        Manage RAG API keys and test semantic knowledge search against your published knowledge base.
      </p>

      <div
        *ngIf="createdKey"
        class="mt-6 rounded-2xl border border-amber-200 bg-amber-50 p-5"
      >
        <div class="flex items-start justify-between gap-4">
          <div>
            <h2 class="font-bold text-amber-950">Copy your RAG API key now</h2>
            <p class="mt-1 text-sm text-amber-800">
              This raw key is shown only once. Store it securely in the application that will call the RAG API.
            </p>
          </div>
          <button
            class="rk-btn rk-btn-secondary"
            type="button"
            (click)="createdKey = null"
          >
            Done
          </button>
        </div>

        <div class="mt-4 flex flex-col gap-2 sm:flex-row">
          <input
            class="rk-input flex-1 font-mono text-sm"
            [value]="createdKey.key"
            readonly
          />
          <button
            class="rk-btn rk-btn-primary"
            type="button"
            (click)="copyKey()"
          >
            Copy key
          </button>
        </div>
      </div>

      <p
        *ngIf="error"
        class="mt-5 rounded-xl bg-rose-50 p-3 text-sm text-rose-700"
      >
        {{ error }}
      </p>

      <div class="mt-7 grid gap-6 xl:grid-cols-[390px_1fr]">
        <section class="space-y-6">
          <form class="rk-card p-6" (ngSubmit)="createKey()">
            <h2 class="font-bold">Create RAG API key</h2>
            <p class="mt-1 text-sm text-slate-500">
              Keys are tenant-scoped and grant the <code>RAG_SEARCH</code> scope.
            </p>

            <div class="mt-5 space-y-4">
              <div>
                <label class="rk-label" for="rag-key-name">Key name</label>
                <input
                  id="rag-key-name"
                  class="rk-input"
                  [(ngModel)]="keyName"
                  name="keyName"
                  maxlength="150"
                  placeholder="Customer support bot"
                  required
                />
              </div>

              <div>
                <label class="rk-label" for="rag-key-expiry">Expires at</label>
                <input
                  id="rag-key-expiry"
                  class="rk-input"
                  [(ngModel)]="expiresAt"
                  name="expiresAt"
                  type="datetime-local"
                />
                <p class="mt-1 text-xs text-slate-400">Optional.</p>
              </div>

              <button
                class="rk-btn rk-btn-primary w-full"
                [disabled]="savingKey || !keyName.trim()"
              >
                {{ savingKey ? "Creating…" : "Create API key" }}
              </button>
            </div>
          </form>

          <section class="rk-card p-6">
            <div class="flex items-center justify-between gap-3">
              <div>
                <h2 class="font-bold">RAG API keys</h2>
                <p class="mt-1 text-xs text-slate-500">
                  {{ keys.length }} configured key{{ keys.length === 1 ? "" : "s" }}
                </p>
              </div>
              <button
                class="rk-btn rk-btn-secondary"
                type="button"
                (click)="loadKeys()"
              >
                Refresh
              </button>
            </div>

            <div
              *ngIf="keys.length === 0"
              class="mt-6 rounded-xl bg-slate-50 p-5 text-center text-sm text-slate-500"
            >
              No RAG API keys yet.
            </div>

            <div *ngFor="let key of keys" class="mt-4 rounded-xl border border-slate-200 p-4">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="font-semibold">{{ key.name }}</p>
                  <p class="mt-1 font-mono text-xs text-slate-500">{{ key.prefix }}…</p>
                </div>
                <span
                  class="rk-badge"
                  [ngClass]="key.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'"
                >
                  {{ key.status }}
                </span>
              </div>

              <div class="mt-3 space-y-1 text-xs text-slate-500">
                <p>Created: {{ key.createdAt | date:'medium' }}</p>
                <p *ngIf="key.expiresAt">Expires: {{ key.expiresAt | date:'medium' }}</p>
                <p *ngIf="key.lastUsedAt">Last used: {{ key.lastUsedAt | date:'medium' }}</p>
              </div>

              <input *ngIf="key.status === 'ACTIVE'" class="rk-input mt-3" [(ngModel)]="key.ipAllowlist" placeholder="IP allowlist, e.g. 10.0.0.0/8,203.0.113.10">
              <div class="mt-3 flex flex-wrap gap-2" *ngIf="key.status === 'ACTIVE'">
                <button class="rk-btn rk-btn-secondary" type="button" (click)="saveIpAllowlist(key)">Save IP allowlist</button>
                <button class="rk-btn rk-btn-secondary" type="button" (click)="rotateKey(key)">Rotate key</button>
                <button class="rk-btn rk-btn-danger" type="button" (click)="revokeKey(key)">Revoke</button>
              </div>
            </div>
          </section>
        </section>

        <section class="space-y-6">
          <section class="rk-card p-6">
            <div>
              <h2 class="font-bold">RAG search playground</h2>
              <p class="mt-1 text-sm text-slate-500">
                Test the same API-key-authenticated semantic search endpoint used by external integrations.
              </p>
            </div>

            <div class="mt-5 grid gap-4 md:grid-cols-[1fr_130px]">
              <div>
                <label class="rk-label" for="rag-search-key">RAG API key</label>
                <input
                  id="rag-search-key"
                  class="rk-input font-mono"
                  [(ngModel)]="searchApiKey"
                  name="searchApiKey"
                  type="password"
                  placeholder="rk_live_…"
                />
              </div>

              <div>
                <label class="rk-label" for="rag-top-k">Top K</label>
                <input
                  id="rag-top-k"
                  class="rk-input"
                  [(ngModel)]="topK"
                  name="topK"
                  type="number"
                  min="1"
                  max="20"
                />
              </div>
            </div>

            <div class="mt-4">
              <label class="rk-label" for="rag-category">Category filter</label>
              <select
                id="rag-category"
                class="rk-input"
                [(ngModel)]="categoryId"
                name="categoryId"
              >
                <option value="">All categories</option>
                <option *ngFor="let category of categories" [value]="category.id">
                  {{ category.name }}
                </option>
              </select>
            </div>

            <div class="mt-4">
              <label class="rk-label" for="rag-query">Question</label>
              <textarea
                id="rag-query"
                class="rk-input min-h-28 resize-y"
                [(ngModel)]="query"
                name="query"
                placeholder="How do I reset my password?"
              ></textarea>
            </div>

            <button
              class="rk-btn rk-btn-primary mt-4"
              type="button"
              [disabled]="searching || !searchApiKey.trim() || !query.trim()"
              (click)="search()"
            >
              {{ searching ? "Searching…" : "Semantic search" }}
            </button>

            <p *ngIf="searchError" class="mt-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">
              {{ searchError }}
            </p>
          </section>

          <section *ngIf="searched" class="rk-card overflow-hidden">
            <div class="border-b border-slate-200 px-5 py-4">
              <h2 class="font-bold">Results</h2>
              <p class="mt-1 text-xs text-slate-500">
                {{ results.length }} result{{ results.length === 1 ? "" : "s" }} for “{{ searchedQuery }}”
              </p>
            </div>

            <div
              *ngIf="results.length === 0"
              class="px-6 py-12 text-center text-sm text-slate-500"
            >
              No semantically similar published content was found.
            </div>

            <article
              *ngFor="let result of results"
              class="border-b border-slate-100 p-5 last:border-b-0"
            >
              <div class="flex items-start justify-between gap-4">
                <div class="min-w-0">
                  <h3 class="font-bold">{{ result.title }}</h3>
                  <p class="mt-1 text-xs text-slate-500">
                    {{ result.categoryName || "Uncategorized" }} · {{ result.visibility }}
                  </p>
                </div>
                <span class="rk-badge bg-slate-100 text-slate-600">
                  Score {{ result.score | number:'1.3-3' }}
                </span>
              </div>

              <p class="mt-4 whitespace-pre-wrap text-sm leading-6 text-slate-700">
                {{ result.content }}
              </p>

              <p class="mt-3 font-mono text-xs text-slate-400">
                {{ result.slug }} · chunk {{ result.chunkId }}
              </p>
            </article>
          </section>
        </section>
      </div>
    </div>
  `
})
export class RagComponent implements OnInit {
  keys: RagApiKey[] = [];
  categories: Category[] = [];
  createdKey: CreatedRagApiKey | null = null;

  keyName = "";
  expiresAt = "";
  savingKey = false;
  error = "";

  searchApiKey = "";
  query = "";
  topK = 5;
  categoryId = "";
  searching = false;
  searched = false;
  searchedQuery = "";
  searchError = "";
  results: RagSearchResult[] = [];

  constructor(private readonly api: ApiService) {}

  ngOnInit(): void {
    this.loadKeys();
    this.api.categories().subscribe({
      next: (items) => (this.categories = items),
      error: () => (this.categories = [])
    });
  }

  loadKeys(): void {
    this.api.ragApiKeys().subscribe({
      next: (items) => (this.keys = items),
      error: (err) => (this.error = err?.error?.message || "Could not load RAG API keys.")
    });
  }

  createKey(): void {
    this.error = "";
    this.savingKey = true;

    const expiresAt = this.expiresAt
      ? new Date(this.expiresAt).toISOString()
      : undefined;

    this.api.createRagApiKey({
      name: this.keyName.trim(),
      expiresAt
    }).subscribe({
      next: (created) => {
        this.createdKey = created;
        this.keyName = "";
        this.expiresAt = "";
        this.savingKey = false;
        this.loadKeys();
      },
      error: (err) => {
        this.savingKey = false;
        this.error = err?.error?.message || "Could not create the RAG API key.";
      }
    });
  }

  saveIpAllowlist(key: RagApiKey): void { this.api.setRagApiKeyIpAllowlist(key.id, key.ipAllowlist || "").subscribe({ next:()=>this.loadKeys(), error:e=>this.error=e?.error?.message||"Could not save IP allowlist." }); }
  rotateKey(key: RagApiKey): void { if(!confirm(`Rotate ${key.name}? The current key will stop working.`)) return; this.api.rotateRagApiKey(key.id).subscribe({ next:x=>{this.createdKey=x;this.loadKeys();}, error:e=>this.error=e?.error?.message||"Could not rotate key." }); }
  revokeKey(key: RagApiKey): void {
    if (!confirm(`Revoke the RAG API key “${key.name}”?`)) {
      return;
    }

    this.api.revokeRagApiKey(key.id).subscribe({
      next: () => this.loadKeys(),
      error: (err) => (this.error = err?.error?.message || "Could not revoke the RAG API key.")
    });
  }

  async copyKey(): Promise<void> {
    const key = this.createdKey?.key;
    if (!key) {
      return;
    }

    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(key);
        return;
      }

      const textarea = document.createElement("textarea");
      textarea.value = key;
      textarea.style.position = "fixed";
      textarea.style.left = "-9999px";
      textarea.style.top = "0";
      textarea.setAttribute("readonly", "");
      document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();

      const copied = document.execCommand("copy");
      document.body.removeChild(textarea);

      if (!copied) {
        throw new Error("Clipboard copy failed");
      }
    } catch (err) {
      console.error("Unable to copy RAG API key:", err);
      this.error = "Could not copy the API key automatically. Please select and copy it manually.";
    }
  }

  search(): void {
    if (!this.searchApiKey.trim() || !this.query.trim()) {
      return;
    }

    this.searching = true;
    this.searched = false;
    this.searchError = "";
    this.results = [];

    const resolvedTopK = Math.min(20, Math.max(1, Number(this.topK) || 5));

    this.api.ragSearch(
      this.searchApiKey.trim(),
      this.query.trim(),
      resolvedTopK,
      this.categoryId || undefined
    ).subscribe({
      next: (response) => {
        this.results = response.results;
        this.searchedQuery = response.query;
        this.searched = true;
        this.searching = false;
      },
      error: (err) => {
        this.searching = false;
        this.searched = true;
        this.searchError = err?.error?.message || "RAG search failed.";
      }
    });
  }
}
