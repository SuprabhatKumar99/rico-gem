import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';

interface HealthIssue { articleId: string; title: string; code: string; severity: string; detail: string; }
interface HealthReport { total: number; published: number; drafts: number; inReview: number;
  searchFailures: number; ragFailures: number; issueCount: number; generatedAt: string; issues: HealthIssue[]; }

@Component({
  standalone: true,
  selector: 'rk-knowledge-health',
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="space-y-6">
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div><p class="text-sm font-semibold text-slate-500">Knowledge intelligence</p>
          <h1 class="text-3xl font-bold">Knowledge health</h1>
          <p class="mt-2 text-sm text-slate-500">Tenant-specific publishing and indexing diagnostics.</p></div>
        <div class="flex gap-2"><button class="rk-btn" (click)="downloadCsv()" [disabled]="!report">Export CSV</button><button class="rk-btn rk-btn-primary" (click)="load()" [disabled]="loading">{{ loading ? 'Refreshing…' : 'Refresh' }}</button></div>
      </header>
      <div *ngIf="error" role="alert" class="rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800">{{ error }}</div>
      <ng-container *ngIf="report as r">
        <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <div *ngFor="let stat of stats(r)" class="rk-card p-5">
            <p class="text-sm text-slate-500">{{ stat.label }}</p>
            <p class="mt-2 text-3xl font-bold">{{ stat.value }}</p>
          </div>
        </div>
        <section class="rk-card overflow-hidden">
          <div class="border-b border-slate-200 p-5"><h2 class="text-lg font-bold">Issues requiring attention</h2>
            <p class="text-sm text-slate-500">{{ r.issueCount }} issue(s) across {{ r.total }} articles · {{ r.generatedAt | date:'medium' }}</p></div>
          <p *ngIf="!r.issues.length" class="p-5 text-emerald-700">No issues detected by the current rules.</p>
          <div class="p-4"><label class="text-sm font-semibold" for="health-severity">Filter by severity</label>
            <select id="health-severity" class="rk-select ml-3" [(ngModel)]="severity"><option value="ALL">All</option><option value="HIGH">High</option><option value="MEDIUM">Medium</option></select></div>
          <ul class="divide-y divide-slate-100">
            <li *ngFor="let issue of filteredIssues(r)" class="flex flex-wrap items-center justify-between gap-3 p-5">
              <div><span class="mr-2 rounded px-2 py-1 text-xs font-bold" [ngClass]="issue.severity === 'HIGH' ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-800'">{{ issue.severity }}</span>
                <span class="font-semibold">{{ issue.title }}</span><p class="mt-2 text-sm text-slate-500">{{ issue.detail }}</p></div>
              <a [routerLink]="['/articles', issue.articleId]" class="rk-btn">Open article</a>
            </li>
          </ul>
        </section>
      </ng-container>
    </div>`
})
export class KnowledgeHealthComponent implements OnInit {
  private readonly api = inject(ApiService);
  report: HealthReport | null = null;
  error = '';
  severity = 'ALL';
  loading = false;
  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading = true; this.error = '';
    this.api.knowledgeDiagnostics().subscribe({
      next: report => { this.report = report; this.loading = false; },
      error: () => { this.loading = false; this.error = 'Unable to load health report. Check your permissions and API connection.'; }
    });
  }
  filteredIssues(r: HealthReport): HealthIssue[] {
    return this.severity === 'ALL' ? r.issues : r.issues.filter(i => i.severity === this.severity);
  }
  downloadCsv(): void {
    if (!this.report) return;
    const escapeCell = (value: string): string => '"' + value.replace(/^[=+@\-]/, "'").replace(/"/g, '""') + '"';
    const rows = [['Article ID', 'Title', 'Severity', 'Code', 'Details'],
      ...this.filteredIssues(this.report).map(i => [i.articleId, i.title, i.severity, i.code, i.detail])];
    const csv = '\uFEFF' + rows.map(row => row.map(escapeCell).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url; anchor.download = 'ricozknow-knowledge-health.csv'; anchor.click();
    URL.revokeObjectURL(url);
  }
  stats(r: HealthReport) { return [
    {label: 'Total articles', value: r.total}, {label: 'Published', value: r.published},
    {label: 'In review', value: r.inReview}, {label: 'Issues detected', value: r.issueCount}
  ]; }
}
