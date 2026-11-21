import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";
import { AuthService } from "../core/auth.service";
import { KnowledgeSource } from "../models";

@Component({
  selector: "rk-knowledge-sources",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <div class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="text-sm font-semibold text-slate-500">Knowledge ingestion</p>
          <h1 class="mt-1 text-3xl font-bold tracking-tight">Knowledge sources</h1>
          <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">
            Import documents, individual URLs, or whole websites and monitor their processing lifecycle.
          </p>
        </div>
        <button class="rk-btn rk-btn-secondary" (click)="load()">Refresh</button>
      </div>

      <p *ngIf="message" class="mt-5 rounded-xl bg-emerald-50 p-3 text-sm text-emerald-700">{{ message }}</p>
      <p *ngIf="error" class="mt-5 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{{ error }}</p>

      <div *ngIf="canManage" class="mt-7 grid gap-6 xl:grid-cols-3">
        <section class="rk-card p-6">
          <h2 class="font-bold">Upload documents</h2>
          <p class="mt-1 text-xs text-slate-500">Supports single and bulk ingestion.</p>
          <div class="mt-5 space-y-4">
            <input class="rk-input" type="file" multiple (change)="files = $any($event.target).files" />
            <input class="rk-input" [(ngModel)]="language" placeholder="Language (en)" />
            <input class="rk-input" [(ngModel)]="syncInterval" type="number" min="1" placeholder="Sync interval (minutes, optional)" />
            <button class="rk-btn rk-btn-primary w-full" [disabled]="busy || !files?.length" (click)="upload()">
              {{ busy ? "Uploading…" : "Upload documents" }}
            </button>
          </div>
        </section>

        <section class="rk-card p-6">
          <h2 class="font-bold">Add URL</h2>
          <p class="mt-1 text-xs text-slate-500">Process one URL as a knowledge source.</p>
          <div class="mt-5 space-y-4">
            <input class="rk-input" [(ngModel)]="url" placeholder="https://docs.example.com/article" />
            <input class="rk-input" [(ngModel)]="name" placeholder="Source name (optional)" />
            <input class="rk-input" [(ngModel)]="maxPages" type="number" min="1" placeholder="Max pages" />
            <button class="rk-btn rk-btn-primary w-full" [disabled]="busy || !url.trim()" (click)="addUrl(false)">Add URL</button>
          </div>
        </section>

        <section class="rk-card p-6">
          <h2 class="font-bold">Crawl website</h2>
          <p class="mt-1 text-xs text-slate-500">Discover and ingest linked pages from a site.</p>
          <div class="mt-5 space-y-4">
            <input class="rk-input" [(ngModel)]="website" placeholder="https://docs.example.com" />
            <input class="rk-input" [(ngModel)]="websiteName" placeholder="Website name (optional)" />
            <input class="rk-input" [(ngModel)]="websiteMaxPages" type="number" min="1" placeholder="Max pages" />
            <button class="rk-btn rk-btn-primary w-full" [disabled]="busy || !website.trim()" (click)="addUrl(true)">Start crawl</button>
          </div>
        </section>
      </div>

      <section class="rk-card mt-6 overflow-hidden">
        <div class="border-b border-slate-200 px-5 py-4">
          <h2 class="font-bold">Sources <span class="font-normal text-slate-400">({{ sources.length }})</span></h2>
        </div>
        <div *ngIf="!sources.length" class="p-10 text-center text-sm text-slate-500">No knowledge sources yet.</div>
        <div *ngFor="let source of sources" class="border-b border-slate-100 p-5 last:border-b-0">
          <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <span class="rk-badge bg-slate-100 text-slate-700">{{ source.type }}</span>
                <span class="rk-badge" [ngClass]="statusClass(source.status)">{{ source.status }}</span>
                <span *ngIf="source.processingPhase" class="text-xs text-slate-400">{{ source.processingPhase }}</span>
              </div>
              <p class="mt-2 font-semibold">{{ source.name }}</p>
              <p *ngIf="source.url" class="mt-1 break-all text-xs text-slate-500">{{ source.url }}</p>
              <p class="mt-2 text-xs text-slate-500">
                {{ source.discoveredPages }} pages · {{ source.language }} · retries {{ source.retryCount }}/{{ source.maxRetries }}
                <span *ngIf="source.lastSyncedAt"> · synced {{ source.lastSyncedAt | date:"medium" }}</span>
              </p>
              <p *ngIf="source.lastError" class="mt-2 rounded-lg bg-rose-50 p-2 text-xs text-rose-700">{{ source.lastError }}</p>
            </div>
            <div *ngIf="canManage" class="flex flex-wrap gap-2">
              <button class="rk-btn rk-btn-secondary" (click)="sync(source)">Sync</button>
              <button class="rk-btn rk-btn-secondary" (click)="reindex(source)">Reindex</button>
              <button class="rk-btn rk-btn-secondary" (click)="retry(source)">Retry</button>
              <button class="rk-btn rk-btn-danger" (click)="remove(source)">Delete</button>
            </div>
            <span *ngIf="!canManage" class="text-xs font-medium text-slate-400">Read-only access</span>
          </div>
        </div>
      </section>
    </div>
  `
})
export class KnowledgeSourcesComponent implements OnInit {
  sources: KnowledgeSource[] = [];
  files: FileList | null = null;
  language = "en";
  syncInterval: number | null = null;
  url = "";
  name = "";
  maxPages = 25;
  website = "";
  websiteName = "";
  websiteMaxPages = 25;
  busy = false;
  message = "";
  error = "";

  constructor(private readonly api: ApiService, readonly auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.hasAnyRole(["ADMIN", "EDITOR"]);
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.knowledgeSources().subscribe({
      next: s => this.sources = s,
      error: e => this.error = e?.error?.message || "Could not load knowledge sources."
    });
  }

  upload(): void {
    if (!this.files?.length) return;
    this.resetNotice(); this.busy = true;
    const files = Array.from(this.files);
    const onSuccess = (): void => {
      this.busy = false;
      this.message = `${files.length} document${files.length === 1 ? "" : "s"} submitted.`;
      this.files = null;
      this.load();
    };
    const onError = (e: any): void => {
      this.busy = false;
      this.error = e?.error?.message || "Document upload failed.";
    };
    if (files.length === 1) {
      this.api.uploadKnowledgeDocument(files[0], this.language, this.syncInterval || undefined).subscribe({ next: onSuccess, error: onError });
    } else {
      this.api.uploadKnowledgeDocuments(files, this.language, this.syncInterval || undefined).subscribe({ next: onSuccess, error: onError });
    }
  }

  addUrl(website: boolean): void {
    this.resetNotice(); this.busy = true;
    const onSuccess = (): void => {
      this.busy = false;
      this.message = website ? "Website crawl submitted." : "URL submitted.";
      this.load();
    };
    const onError = (e: any): void => {
      this.busy = false;
      this.error = e?.error?.message || "Could not add the source.";
    };
    if (website) {
      this.api.addKnowledgeWebsite(this.website.trim(), this.websiteName.trim() || undefined, this.language, this.syncInterval || undefined, this.websiteMaxPages).subscribe({ next: onSuccess, error: onError });
    } else {
      this.api.addKnowledgeUrl(this.url.trim(), this.name.trim() || undefined, this.language, this.syncInterval || undefined, this.maxPages).subscribe({ next: onSuccess, error: onError });
    }
  }

  sync(s: KnowledgeSource): void { this.api.syncKnowledgeSource(s.id).subscribe({ next: () => this.load(), error: e => this.error = e?.error?.message || "Sync failed." }); }
  reindex(s: KnowledgeSource): void { this.api.reindexKnowledgeSource(s.id).subscribe({ next: () => this.load(), error: e => this.error = e?.error?.message || "Reindex failed." }); }
  retry(s: KnowledgeSource): void { this.api.retryKnowledgeSource(s.id).subscribe({ next: () => this.load(), error: e => this.error = e?.error?.message || "Retry failed." }); }
  remove(s: KnowledgeSource): void {
    if (!confirm(`Delete "${s.name}"?`)) return;
    this.api.deleteKnowledgeSource(s.id).subscribe({ next: () => this.load(), error: e => this.error = e?.error?.message || "Delete failed." });
  }

  statusClass(status: string): string {
    return ["READY", "COMPLETED", "SYNCED"].includes(status) ? "bg-emerald-100 text-emerald-700"
      : ["FAILED", "ERROR"].includes(status) ? "bg-rose-100 text-rose-700"
      : "bg-amber-100 text-amber-700";
  }
  private resetNotice(): void { this.error = ""; this.message = ""; }
}
