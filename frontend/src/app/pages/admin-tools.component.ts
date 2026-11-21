import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { catchError, forkJoin, map, of } from "rxjs";
import { ApiService } from "../core/api.service";
import { ArticleVersion } from "../models";
import { ConfirmButtonComponent } from "../shared/ui";

interface ActionLogEntry {
  at: Date;
  message: string;
}

const LAST_REINDEX_KEY = "rk_last_reindex_at";

function toDateInputValue(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");

  return `${year}-${month}-${day}`;
}

@Component({
  selector: "rk-admin-tools",
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmButtonComponent],
  template: `
    <div>
      <p class="text-sm font-semibold text-slate-500">Administration</p>
      <h1 class="mt-1 text-3xl font-bold tracking-tight">Admin tools</h1>
      <p class="mt-2 text-sm text-slate-500">System maintenance actions. These affect the whole knowledge base, so use them deliberately.</p>

      <div
        *ngIf="lastReindexAt && latestArticleAt"
        class="mt-7 flex flex-wrap items-start justify-between gap-4 rounded-2xl border p-5"
        [ngClass]="changedArticleCount ? 'border-amber-300 bg-amber-50' : 'border-slate-200 bg-white'"
      >
        <div class="min-w-0">
          <p class="text-sm font-bold" [ngClass]="changedArticleCount ? 'text-amber-900' : 'text-slate-900'">
            <ng-container *ngIf="changedArticleCount; else indexFresh">
              {{ changedArticleCount }} article{{ changedArticleCount === 1 ? " has" : "s have" }} changed since your
              last reindex, you may want to run it again.
            </ng-container>
            <ng-template #indexFresh>Your search index matches the latest article change.</ng-template>
          </p>

          <p class="mt-1 text-xs text-slate-500">
            Last reindex in this browser: {{ lastReindexAt | date:"medium" }} ·
            Most recent article change: {{ latestArticleAt | date:"medium" }}
          </p>
        </div>

        <button
          *ngIf="changedArticleCount && !advancedOpen"
          type="button"
          class="rk-btn rk-btn-caution"
          (click)="advancedOpen = true"
        >
          Go to Reindex
        </button>
      </div>

      <section class="mt-7 overflow-hidden rounded-2xl border border-dashed border-slate-300 bg-white">
        <button
          type="button"
          class="flex w-full items-center justify-between gap-4 px-5 py-4 text-left"
          [attr.aria-expanded]="advancedOpen"
          (click)="advancedOpen = !advancedOpen"
        >
          <span class="min-w-0">
            <span class="block text-sm font-bold">Advanced</span>
            <span class="mt-0.5 block text-xs text-slate-500">
              Reindex and analytics aggregation. These rebuild shared system state for every user.
            </span>
          </span>

          <span class="rk-badge shrink-0 bg-slate-900 text-white">
            {{ advancedOpen ? "Hide" : "Expand" }}
          </span>
        </button>

        <div *ngIf="advancedOpen" class="grid gap-6 border-t border-dashed border-slate-300 p-5 xl:grid-cols-2">
          <section class="rounded-xl border border-slate-200 p-5">
            <h2 class="text-lg font-bold">Search index</h2>
            <p class="mt-2 text-sm leading-6 text-slate-500">
              Rebuild the current tenant's OpenSearch index.
            </p>

            <div class="mt-5">
              <rk-confirm-button
                label="Reindex"
                confirmLabel="Yes, rebuild the index"
                [disabled]="busy"
                (confirmed)="reindex()"
              />
            </div>

            <p *ngIf="busy" class="mt-4 text-sm text-slate-500">Rebuilding…</p>

            <p *ngIf="reindexMessage" class="mt-4 text-sm text-emerald-700">{{ reindexMessage }}</p>
          </section>

          <section class="rounded-xl border border-slate-200 p-5">
            <h2 class="text-lg font-bold">Analytics aggregation</h2>
            <p class="mt-2 text-sm leading-6 text-slate-500">
              Trigger aggregation for an optional date. The backend defaults to yesterday when no date is provided.
            </p>

            <div class="mt-5 flex flex-wrap items-center gap-2">
              <button
                *ngFor="let chip of dateChips"
                type="button"
                class="rounded-full border px-3 py-1.5 text-xs font-semibold transition"
                [ngClass]="date === chip.value
                  ? 'border-slate-900 bg-slate-900 text-white'
                  : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50'"
                (click)="date = chip.value"
              >
                {{ chip.label }}
              </button>
            </div>

            <div class="mt-4 flex flex-wrap gap-2">
              <input class="rk-input max-w-xs" type="date" [(ngModel)]="date" aria-label="Aggregation date" />
              <rk-confirm-button
                label="Run aggregation"
                confirmLabel="Yes, run aggregation"
                (confirmed)="aggregate()"
              />
            </div>

            <p *ngIf="aggregationMessage" class="mt-4 text-sm text-emerald-700">{{ aggregationMessage }}</p>
          </section>
        </div>
      </section>

      <section class="rk-card mt-6 p-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="font-bold">Session log</h2>
          <button
            *ngIf="log.length"
            type="button"
            class="text-xs font-semibold text-slate-500 hover:underline"
            (click)="log = []"
          >
            Clear
          </button>
        </div>

        <p *ngIf="!log.length" class="mt-3 text-sm text-slate-500">
          Nothing has run yet in this browser session. Actions appear here as you use them.
        </p>

        <ul *ngIf="log.length" class="mt-3 space-y-1.5">
          <li *ngFor="let entry of log" class="flex flex-wrap items-baseline gap-2 text-sm">
            <span class="font-mono text-xs text-slate-400">{{ entry.at | date:"HH:mm:ss" }}</span>
            <span class="text-slate-700">{{ entry.message }}</span>
          </li>
        </ul>
      </section>
    </div>
  `
})
export class AdminToolsComponent implements OnInit {
  date = "";
  busy = false;
  reindexMessage = "";
  aggregationMessage = "";

  advancedOpen = false;

  lastReindexAt: string | null = null;
  latestArticleAt: string | null = null;
  changedArticleCount = 0;

  log: ActionLogEntry[] = [];

  constructor(private readonly api: ApiService) {}

  get dateChips(): { label: string; value: string }[] {
    const today = new Date();

    const yesterday = new Date(today);
    yesterday.setDate(today.getDate() - 1);

    const weekAgo = new Date(today);
    weekAgo.setDate(today.getDate() - 6);

    return [
      { label: "Yesterday", value: toDateInputValue(yesterday) },
      { label: "Today", value: toDateInputValue(today) },
      { label: "Last 7 days", value: toDateInputValue(weekAgo) }
    ];
  }

  ngOnInit(): void {
    this.lastReindexAt = localStorage.getItem(LAST_REINDEX_KEY);
    this.loadArticleActivity();
  }

  private loadArticleActivity(): void {
    this.api.listArticles(undefined, 0, 20).subscribe({
      next: (page) => {
        if (!page.content.length) {
          return;
        }

        forkJoin(
          page.content.map((article) =>
            this.api.getVersions(article.id).pipe(
              map((versions) => this.latestVersionDate(versions)),
              catchError(() => of(null))
            )
          )
        ).subscribe((dates) => {
          const known = dates
            .filter((value): value is string => !!value)
            .sort()
            .reverse();

          if (!known.length) {
            return;
          }

          this.latestArticleAt = known[0];

          const lastReindexAt = this.lastReindexAt;

          if (lastReindexAt) {
            this.changedArticleCount = known.filter((value) => value > lastReindexAt).length;
          }
        });
      },
      error: () => undefined
    });
  }

  private latestVersionDate(versions: ArticleVersion[]): string | null {
    const stamps = versions
      .flatMap((version) => [version.publishedAt, version.reviewedAt])
      .filter((value): value is string => !!value);

    if (!stamps.length) {
      return null;
    }

    return stamps.sort().reverse()[0];
  }

  reindex(): void {
    this.busy = true;
    this.reindexMessage = "";
    this.addLogEntry("Reindex started");

    this.api.reindex().subscribe({
      next: (result) => {
        this.reindexMessage = `${result.indexed} documents indexed.`;
        this.busy = false;
        this.lastReindexAt = new Date().toISOString();
        localStorage.setItem(LAST_REINDEX_KEY, this.lastReindexAt);
        this.changedArticleCount = 0;
        this.addLogEntry(`Reindex finished, ${result.indexed} documents indexed`);
      },
      error: (error) => {
        this.reindexMessage = error?.error?.message || "Reindex failed.";
        this.busy = false;
        this.addLogEntry("Reindex failed");
      }
    });
  }

  aggregate(): void {
    const chosen = this.date || "the backend default";
    this.addLogEntry(`Aggregation started for ${chosen}`);

    this.api.aggregate(this.date || undefined).subscribe({
      next: (result) => {
        this.aggregationMessage = `${result.status} · ${result.date}`;
        this.addLogEntry(`Aggregation finished for ${result.date}`);
      },
      error: (error) => {
        this.aggregationMessage = error?.error?.message || "Aggregation failed.";
        this.addLogEntry("Aggregation failed");
      }
    });
  }

  private addLogEntry(message: string): void {
    this.log = [{ at: new Date(), message }, ...this.log].slice(0, 25);
  }
}
