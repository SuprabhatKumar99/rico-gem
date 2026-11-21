import { CommonModule } from "@angular/common";
import { Component, OnInit, inject } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";
import { ProductFeedback, ProductFeedbackStatus } from "../models";

@Component({
  selector: "rk-product-feedback-page",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p class="text-xs font-bold uppercase tracking-widest text-slate-400">Engineering Triage</p>
          <h1 class="mt-1 text-2xl font-bold tracking-tight text-slate-950">Product feedback inbox</h1>
          <p class="mt-1 text-sm text-slate-500">
            Bug reports and feature requests submitted via the persistent feedback button, with captured page URL and browser diagnostics.
          </p>
        </div>
        <button type="button" class="rk-btn rk-btn-secondary" (click)="load()" [disabled]="loading">
          {{ loading ? "Refreshing..." : "Refresh inbox" }}
        </button>
      </div>

      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div class="rk-card p-5">
          <p class="text-xs font-semibold uppercase tracking-wide text-slate-400">Total reports</p>
          <p class="mt-2 text-2xl font-bold text-slate-950">{{ items.length }}</p>
        </div>
        <div class="rk-card p-5">
          <p class="text-xs font-semibold uppercase tracking-wide text-rose-600">Open bugs</p>
          <p class="mt-2 text-2xl font-bold text-slate-950">{{ openBugsCount }}</p>
        </div>
        <div class="rk-card p-5">
          <p class="text-xs font-semibold uppercase tracking-wide text-sky-600">Feature requests</p>
          <p class="mt-2 text-2xl font-bold text-slate-950">{{ featureRequestsCount }}</p>
        </div>
        <div class="rk-card p-5">
          <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600">Resolved</p>
          <p class="mt-2 text-2xl font-bold text-slate-950">{{ resolvedCount }}</p>
        </div>
      </div>

      <div class="rk-card flex flex-wrap items-center gap-3 p-4">
        <input
          type="search"
          [(ngModel)]="searchTerm"
          class="rk-input min-w-[200px] flex-1"
          placeholder="Search title, description, URL, or browser..."
        />
        <select [(ngModel)]="typeFilter" class="rk-input w-full sm:w-44" (ngModelChange)="load()">
          <option value="">All types</option>
          <option value="BUG">Bug reports</option>
          <option value="FEATURE">Feature requests</option>
        </select>
        <select [(ngModel)]="statusFilter" class="rk-input w-full sm:w-44" (ngModelChange)="load()">
          <option value="">All statuses</option>
          <option value="NEW">New</option>
          <option value="TRIAGED">Triaged</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="RESOLVED">Resolved</option>
          <option value="WONT_FIX">Won't fix</option>
        </select>
      </div>

      <div *ngIf="errorMessage" class="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
        {{ errorMessage }}
      </div>

      <div *ngIf="!loading && filteredItems.length === 0" class="rk-card p-10 text-center">
        <p class="text-base font-semibold text-slate-900">No feedback matches your filter</p>
        <p class="mt-1 text-sm text-slate-500">Use the floating Feedback button in the bottom-right corner to submit a new report.</p>
      </div>

      <div class="space-y-4">
        <article
          *ngFor="let item of filteredItems"
          class="rk-card p-5"
          [attr.data-testid]="'feedback-row-' + item.id"
        >
          <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div class="min-w-0 flex-1 space-y-2.5">
              <div class="flex flex-wrap items-center gap-2">
                <span
                  class="rk-badge"
                  [ngClass]="item.type === 'BUG' ? 'bg-rose-100 text-rose-700' : 'bg-sky-100 text-sky-700'"
                >
                  {{ item.type === 'BUG' ? 'Bug report' : 'Feature request' }}
                </span>
                <span class="rk-badge" [ngClass]="severityClass(item.severity)">
                  {{ item.severity }}
                </span>
                <span class="font-mono text-xs text-slate-400">{{ item.id }}</span>
                <span class="text-xs text-slate-400">·</span>
                <span class="text-xs text-slate-500">{{ item.reporterEmail || 'Anonymous visitor' }}</span>
                <span class="text-xs text-slate-400">·</span>
                <span class="text-xs text-slate-400">{{ item.createdAt | date: "medium" }}</span>
              </div>

              <h2 class="text-base font-bold text-slate-950">{{ item.title }}</h2>
              <p class="whitespace-pre-line text-sm text-slate-600">{{ item.description }}</p>

              <div
                *ngIf="item.includeDiagnostics"
                class="mt-3 rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs"
              >
                <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
                  <div *ngIf="item.pageUrl" class="min-w-0 max-w-full">
                    <span class="font-semibold text-slate-500">Page URL:</span>
                    <span class="ml-1.5 break-all font-mono text-slate-800">{{ item.pageUrl }}</span>
                  </div>
                  <div *ngIf="item.browser || item.os">
                    <span class="font-semibold text-slate-500">Browser:</span>
                    <span class="ml-1.5 text-slate-800">{{ item.browser || 'Unknown' }} · {{ item.os || 'Unknown' }}</span>
                  </div>
                  <div *ngIf="item.viewport">
                    <span class="font-semibold text-slate-500">Viewport:</span>
                    <span class="ml-1.5 font-mono text-slate-800">{{ item.viewport }}</span>
                  </div>
                  <div *ngIf="item.theme">
                    <span class="font-semibold text-slate-500">Theme:</span>
                    <span class="ml-1.5 text-slate-800">{{ item.theme | titlecase }}</span>
                  </div>
                  <div *ngIf="item.language">
                    <span class="font-semibold text-slate-500">Locale:</span>
                    <span class="ml-1.5 text-slate-800">{{ item.language }}</span>
                  </div>
                </div>
                <details *ngIf="item.userAgent" class="mt-2">
                  <summary class="cursor-pointer text-[11px] font-medium text-slate-500 hover:text-slate-700">
                    Raw User-Agent
                  </summary>
                  <p class="mt-1 break-all font-mono text-[11px] text-slate-600">{{ item.userAgent }}</p>
                </details>
              </div>
              <p *ngIf="!item.includeDiagnostics" class="text-xs italic text-slate-400">
                Reporter opted out of sharing page URL and browser diagnostics.
              </p>
            </div>

            <div class="flex shrink-0 items-center gap-2 lg:flex-col lg:items-end">
              <label class="text-[11px] font-semibold uppercase tracking-wide text-slate-400">Status</label>
              <select
                class="rk-input w-40 py-2 text-xs font-semibold"
                [ngModel]="item.status"
                (ngModelChange)="changeStatus(item, $event)"
              >
                <option value="NEW">New</option>
                <option value="TRIAGED">Triaged</option>
                <option value="IN_PROGRESS">In progress</option>
                <option value="RESOLVED">Resolved</option>
                <option value="WONT_FIX">Won't fix</option>
              </select>
            </div>
          </div>
        </article>
      </div>
    </div>
  `
})
export class ProductFeedbackComponent implements OnInit {
  private readonly api = inject(ApiService);

  items: ProductFeedback[] = [];
  loading = false;
  errorMessage = "";
  typeFilter = "";
  statusFilter = "";
  searchTerm = "";

  ngOnInit(): void {
    this.load();
  }

  get openBugsCount(): number {
    return this.items.filter((i) => i.type === "BUG" && i.status !== "RESOLVED" && i.status !== "WONT_FIX").length;
  }

  get featureRequestsCount(): number {
    return this.items.filter((i) => i.type === "FEATURE").length;
  }

  get resolvedCount(): number {
    return this.items.filter((i) => i.status === "RESOLVED").length;
  }

  get filteredItems(): ProductFeedback[] {
    const q = this.searchTerm.trim().toLowerCase();
    if (!q) return this.items;
    return this.items.filter((item) =>
      [item.id, item.title, item.description, item.pageUrl, item.browser, item.os, item.reporterEmail]
        .filter(Boolean)
        .some((field) => String(field).toLowerCase().includes(q))
    );
  }

  load(): void {
    this.loading = true;
    this.errorMessage = "";
    this.api
      .productFeedback({
        type: this.typeFilter || undefined,
        status: this.statusFilter || undefined
      })
      .subscribe({
        next: (rows) => {
          this.items = rows;
          this.loading = false;
        },
        error: () => {
          this.errorMessage = "Could not load product feedback inbox.";
          this.loading = false;
        }
      });
  }

  changeStatus(item: ProductFeedback, nextStatus: ProductFeedbackStatus): void {
    const prev = item.status;
    item.status = nextStatus;
    this.api.updateProductFeedbackStatus(item.id, nextStatus).subscribe({
      next: (updated) => {
        item.status = updated.status;
        item.updatedAt = updated.updatedAt;
      },
      error: () => {
        item.status = prev;
        this.errorMessage = "Could not update status.";
      }
    });
  }

  severityClass(severity: string): string {
    switch (severity) {
      case "CRITICAL":
        return "bg-rose-600 text-white";
      case "HIGH":
        return "bg-amber-100 text-amber-800";
      case "MEDIUM":
        return "bg-slate-100 text-slate-700";
      default:
        return "bg-emerald-100 text-emerald-700";
    }
  }
}
