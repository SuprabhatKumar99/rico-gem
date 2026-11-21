import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";
import { ArticlePerformance, DeflectionSummary, KnowledgeGap, StaleContent } from "../models";
import { MetricCardComponent } from "../shared/ui";

function maxOf(values: number[]): number {
  return Math.max(1, ...values);
}

@Component({
  selector: "rk-analytics",
  standalone: true,
  imports: [CommonModule, FormsModule, MetricCardComponent],
  template: `
    <div>
      <div class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="text-sm font-semibold text-slate-500">Administration</p>
          <h1 class="mt-1 text-3xl font-bold tracking-tight">Knowledge performance</h1>
            <p class="mt-2 text-sm text-slate-500">
              See how your content is performing and where people are still getting stuck.
            </p>
        </div>

        <div class="flex gap-2">
          <select
            class="rk-select w-40"
            [(ngModel)]="days"
            aria-label="Analytics window in days"
          >
            <option [ngValue]="7">Last 7 days</option>
            <option [ngValue]="30">Last 30 days</option>
            <option [ngValue]="90">Last 90 days</option>
          </select>
          <button class="rk-btn rk-btn-primary" (click)="load()">Refresh</button>
        </div>
      </div>

      <div class="mt-7 grid gap-4 md:grid-cols-3">
        <rk-metric-card
          label="Estimated deflection"
          [value]="deflection?.estimatedDeflectionCount ?? '—'"
          explain="People who likely found their answer in an article and did not need to contact support, based on view and search patterns."
        />
        <rk-metric-card
          label="Confirmed deflection"
          [value]="deflection?.confirmedDeflectionCount ?? '—'"
          explain="People who explicitly marked an article as helpful instead of contacting support."
        />
        <rk-metric-card
          label="Support contacts"
          [value]="deflection?.supportContactCount ?? '—'"
          explain="Number of people who still reached out to support during this period."
        />
      </div>

      <div class="mt-7 grid gap-6 xl:grid-cols-2">
        <section class="rk-card overflow-hidden">
          <div class="border-b border-slate-200 px-5 py-4">
            <h2 class="font-bold">Article performance</h2>
            <p class="mt-1 text-xs text-slate-500">Views and feedback counts for the selected window.</p>
          </div>

          <div class="border-b border-slate-100 px-5 py-4">
            <div class="flex flex-wrap items-center gap-4 text-xs text-slate-500">
              <span class="text-xs font-bold uppercase tracking-wide text-slate-400">Chart</span>
              <span class="flex items-center gap-1.5">
                <span class="h-2 w-2 rounded-full bg-blue-600"></span>
                Views
              </span>
              <span class="flex items-center gap-1.5">
                <span class="h-2 w-2 rounded-full bg-emerald-500"></span>
                Helpful
              </span>
              <span class="flex items-center gap-1.5">
                <span class="h-2 w-2 rounded-full bg-rose-500"></span>
                Unhelpful
              </span>
            </div>

            <div *ngIf="!performance.length" class="mt-3 text-sm text-slate-500">
              No article performance returned for the selected window.
            </div>

            <div *ngFor="let item of performance" class="mt-3">
              <p class="line-clamp-2 text-xs font-semibold text-slate-700">{{ item.title }}</p>

              <svg
                class="mt-1 h-20 w-full"
                viewBox="0 0 300 80"
                preserveAspectRatio="none"
                role="img"
                [attr.aria-label]="item.title + ' views, helpful and unhelpful counts'"
              >
                <rect x="0" [attr.y]="80 - (item.views / performanceMax) * 72" width="60" [attr.height]="(item.views / performanceMax) * 72" fill="#2563eb" />
                <rect x="100" [attr.y]="80 - (item.helpfulCount / performanceMax) * 72" width="60" [attr.height]="(item.helpfulCount / performanceMax) * 72" fill="#10b981" />
                <rect x="200" [attr.y]="80 - (item.unhelpfulCount / performanceMax) * 72" width="60" [attr.height]="(item.unhelpfulCount / performanceMax) * 72" fill="#f43f5e" />
              </svg>
            </div>
          </div>

          <div class="divide-y divide-slate-100 md:hidden">
            <div *ngFor="let item of performance" class="px-5 py-4">
              <p class="break-words font-semibold leading-snug">{{ item.title }}</p>

              <dl class="mt-2 grid grid-cols-3 gap-2 text-xs">
                <div>
                  <dt class="text-slate-400">Views</dt>
                  <dd class="text-lg font-bold text-slate-800">{{ item.views }}</dd>
                </div>
                <div>
                  <dt class="text-slate-400">Helpful</dt>
                  <dd class="text-lg font-bold text-emerald-600">{{ item.helpfulCount }}</dd>
                </div>
                <div>
                  <dt class="text-slate-400">Unhelpful</dt>
                  <dd class="text-lg font-bold text-rose-600">{{ item.unhelpfulCount }}</dd>
                </div>
              </dl>
            </div>
          </div>

          <div class="hidden overflow-x-auto md:block">
            <table class="w-full min-w-[600px] text-left text-sm">
              <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th class="px-5 py-3">Article</th>
                  <th class="px-5 py-3">Views</th>
                  <th class="px-5 py-3">Helpful</th>
                  <th class="px-5 py-3">Unhelpful</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr *ngFor="let item of performance">
                  <td class="px-5 py-4 break-words font-semibold">{{ item.title }}</td>
                  <td class="px-5 py-4">{{ item.views }}</td>
                  <td class="px-5 py-4">{{ item.helpfulCount }}</td>
                  <td class="px-5 py-4">{{ item.unhelpfulCount }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="rk-card">
          <div class="border-b border-slate-200 px-5 py-4">
            <h2 class="font-bold">Knowledge gaps</h2>
            <p class="mt-1 text-xs text-slate-500">Returned by the knowledge-gaps endpoint.</p>
          </div>

          <div class="border-b border-slate-100 px-5 py-4">
            <p class="text-xs font-bold uppercase tracking-wide text-slate-400">
              Chart · gap score by search
            </p>

            <div *ngIf="!gaps.length" class="mt-3 text-sm text-slate-500">
              No knowledge gaps returned for the selected window.
            </div>

            <div *ngFor="let item of gaps" class="mt-3">
              <div class="flex items-center justify-between gap-3 text-xs">
                <span class="truncate font-semibold text-slate-700">{{ item.query }}</span>
                <span class="font-bold text-amber-700">{{ item.gapScore }}</span>
              </div>

              <svg
                class="mt-1 h-2.5 w-full"
                viewBox="0 0 100 4"
                preserveAspectRatio="none"
                role="img"
                [attr.aria-label]="item.query + ' gap score ' + item.gapScore"
              >
                <rect x="0" y="0" width="100" height="4" fill="#f1f5f9" />
                <rect x="0" y="0" [attr.width]="(item.gapScore / gapsMax) * 100" height="4" fill="#f59e0b" />
              </svg>
            </div>
          </div>

          <div class="divide-y divide-slate-100">
            <div *ngFor="let item of gaps" class="px-5 py-4">
              <div class="flex items-center justify-between gap-4">
                <p class="font-semibold">{{ item.query }}</p>
                <span class="rk-badge bg-amber-100 text-amber-700">{{ item.gapScore }}</span>
              </div>
              <p class="mt-1 text-xs text-slate-500">
                {{ item.volumeCount }} searches · {{ item.zeroResultCount }} zero results · {{ item.unsuccessfulCount }} unsuccessful
              </p>
            </div>
          </div>
        </section>
      </div>

      <section class="rk-card mt-6 overflow-hidden">
        <div class="border-b border-slate-200 px-5 py-4">
          <h2 class="font-bold">Stale content</h2>
          <p class="mt-1 text-xs text-slate-500">Content within the requested expiry horizon.</p>
        </div>

        <div class="divide-y divide-slate-100 md:hidden">
          <div *ngFor="let item of stale" class="px-5 py-4">
            <div class="flex items-start justify-between gap-3">
              <p class="break-words font-semibold leading-snug">{{ item.title }}</p>
              <span
                class="rk-badge shrink-0"
                [ngClass]="item.alreadyExpired ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-700'"
              >
                {{ item.alreadyExpired ? "Expired" : "Upcoming" }}
              </span>
            </div>
            <p class="mt-1 text-xs text-slate-500">Expires {{ item.expiresAt | date:"medium" }}</p>
          </div>
        </div>

        <div class="hidden overflow-x-auto md:block">
          <table class="w-full min-w-[620px] text-left text-sm">
            <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th class="px-5 py-3">Article</th>
                <th class="px-5 py-3">Expires</th>
                <th class="px-5 py-3">State</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr *ngFor="let item of stale">
                <td class="px-5 py-4 break-words font-semibold">{{ item.title }}</td>
                <td class="px-5 py-4">{{ item.expiresAt | date:"medium" }}</td>
                <td class="px-5 py-4">{{ item.alreadyExpired ? "Expired" : "Upcoming" }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `
})
export class AnalyticsComponent implements OnInit {
  days = 30;
  performance: ArticlePerformance[] = [];
  gaps: KnowledgeGap[] = [];
  stale: StaleContent[] = [];
  deflection?: DeflectionSummary;

  constructor(private readonly api: ApiService) {}

  get performanceMax(): number {
    const values = this.performance.flatMap((item) => [item.views, item.helpfulCount, item.unhelpfulCount]);
    return maxOf(values);
  }

  get gapsMax(): number {
    return maxOf(this.gaps.map((item) => item.gapScore));
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.performance(this.days).subscribe((items) => {
      this.performance = items;
    });

    this.api.knowledgeGaps(this.days, 20).subscribe((items) => {
      this.gaps = items;
    });

    this.api.deflection(this.days).subscribe((summary) => {
      this.deflection = summary;
    });

    this.api.staleContent(this.days).subscribe((items) => {
      this.stale = items;
    });
  }
}
