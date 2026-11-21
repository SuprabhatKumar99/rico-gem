import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { Category } from '../models';

interface EnterpriseHit {
  id: string; sourceType: string; title: string; excerpt: string; url: string | null;
  citation: string; accessLevel: string; trust: string; freshness: string;
  modifiedAt: string | null; score: number;
}

/** Staff-only workspace; content and citations are returned from the permission-gated API. */
@Component({
  standalone: true,
  selector: 'rk-enterprise-search',
  imports: [CommonModule, FormsModule],
  template: `
  <main class="space-y-6">
    <header class="space-y-2">
      <h1 class="text-3xl font-bold">Enterprise knowledge search</h1>
      <p class="text-sm text-slate-600">Search current published articles and tenant-scoped connector snapshots. External documents remain administrator-only until provider ACL mapping is verified.</p>
    </header>
    <div role="alert" *ngIf="error" class="rounded-lg border border-rose-300 bg-rose-50 p-3 text-rose-700">{{error}}</div>
    <section class="rk-card p-5 space-y-3">
      <form (ngSubmit)="search()" class="space-y-3">
        <label for="enterprise-query" class="block font-semibold">Question or keywords</label>
        <input id="enterprise-query" name="query" class="rk-input w-full" [(ngModel)]="query" minlength="2" maxlength="300" placeholder="Find your organization's knowledge" required />
        <div class="grid gap-3 sm:grid-cols-3">
          <label class="text-sm">Source
            <select name="provider" class="rk-input w-full" [(ngModel)]="provider">
              <option value="">All eligible sources</option><option value="article">Articles only</option>
              <option *ngFor="let x of providers" [value]="x">{{x}}</option>
            </select>
          </label>
          <label class="text-sm">Language (articles)
            <input name="language" class="rk-input w-full" [(ngModel)]="language" maxlength="16" placeholder="All languages" />
          </label>
          <label class="text-sm">Maximum results
            <select name="limit" class="rk-input w-full" [(ngModel)]="limit">
              <option [ngValue]="10">10</option><option [ngValue]="20">20</option><option [ngValue]="40">40</option>
            </select>
          </label>
        </div>
        <div class="grid gap-3 sm:grid-cols-3">
          <label class="text-sm">Article category
            <select name="categoryId" class="rk-input w-full" [(ngModel)]="categoryId">
              <option value="">All categories</option><option *ngFor="let c of categories" [value]="c.id">{{c.name}}</option>
            </select>
          </label>
          <label class="text-sm">Modified after
            <input name="dateFrom" type="date" class="rk-input w-full" [(ngModel)]="dateFrom" />
          </label>
          <label class="text-sm">Modified before
            <input name="dateTo" type="date" class="rk-input w-full" [(ngModel)]="dateTo" />
          </label>
        </div>
        <div class="flex flex-wrap gap-2">
          <button class="rk-btn rk-btn-primary" type="submit" [disabled]="busy||!query.trim()">{{busy?'Searching…':'Search'}}</button>
          <button class="rk-btn" type="button" [disabled]="busy||!query.trim()" (click)="answer()">Answer with citations</button>
          <button class="rk-btn" type="button" [disabled]="busy||!query.trim()" (click)="rewrite()">Suggest query rewrite</button>
        </div>
      </form>
      <div *ngIf="suggestion" class="text-sm bg-slate-50 p-3 rounded-lg" role="status">Suggested query: <strong>{{suggestion}}</strong>
        <button class="ml-2 underline" (click)="query=suggestion">Use suggestion</button>
      </div>
    </section>
    <section *ngIf="answerText" class="rk-card p-5 space-y-3" aria-label="Generated answer">
      <h2 class="text-xl font-semibold">Knowledge-grounded answer</h2>
      <p class="whitespace-pre-wrap">{{answerText}}</p>
      <p class="text-sm text-slate-500">Sources shown below are those supplied to the model; verify all generated claims against the originals.</p>
    </section>
    <section class="rk-card p-5 space-y-3" aria-label="Search results">
      <div class="flex items-center justify-between"><h2 class="text-xl font-bold">Results</h2>
        <span class="text-xs text-slate-500" *ngIf="searched">{{results.length}} returned · {{latencyMs}} ms</span></div>
      <p *ngIf="searched&&!results.length&&!busy" class="text-sm text-slate-500">No authorized results found. Try different keywords or review source sync status.</p>
      <div *ngFor="let hit of results" class="border-b py-4 space-y-2 last:border-0">
        <div class="flex flex-wrap gap-2 items-center"><strong>{{hit.citation}} {{hit.title}}</strong>
          <span class="rounded bg-slate-100 px-2 py-1 text-xs">{{hit.sourceType}}</span>
          <span class="text-xs text-slate-600">{{hit.freshness}} · {{hit.trust}}</span></div>
        <p class="text-sm whitespace-pre-wrap break-words">{{hit.excerpt}}</p>
        <div class="flex items-center gap-3 text-sm"><a *ngIf="hit.url" class="text-blue-700 underline" [href]="hit.url" (click)="clicked(hit)" target="_blank" rel="noopener noreferrer">Open source</a>
          <span *ngIf="hit.modifiedAt" class="text-slate-500">Updated {{hit.modifiedAt | date:'mediumDate'}}</span></div>
      </div>
    </section>
    <section class="rk-card p-5 space-y-3" aria-label="Search analytics">
      <div class="flex justify-between items-center"><h2 class="font-semibold text-xl">Retrieval health</h2><button class="rk-btn" (click)="insights()">Refresh</button></div>
      <p *ngIf="stats" class="text-sm">{{stats.searches}} searches · {{stats.unanswered}} unanswered · {{stats.averageLatencyMs | number:'1.0-0'}} ms average</p>
      <p class="text-xs text-slate-500">Query analytics store hashes, not customer question text.</p>
    </section>
    <section class="rk-card p-5 space-y-3" aria-label="Retrieval relevance evaluation">
      <h2 class="text-xl font-semibold">Relevance evaluation</h2>
      <p class="text-sm text-slate-600">Add known-good source IDs from results above, then run a ten-result retrieval evaluation.</p>
      <form (ngSubmit)="addCase()" class="grid gap-2 sm:grid-cols-2">
        <input name="evalQuestion" class="rk-input" [(ngModel)]="evalQuestion" placeholder="Test question" maxlength="300" required />
        <input name="expectedIds" class="rk-input" [(ngModel)]="expectedIds" placeholder="Expected source IDs, comma-separated" required />
        <button type="submit" class="rk-btn" [disabled]="!evalQuestion.trim()||!expectedIds.trim()">Save evaluation case</button>
        <button type="button" class="rk-btn rk-btn-primary" (click)="evaluate()">Run evaluation</button>
      </form>
      <p *ngIf="evaluation" class="text-sm" role="status">{{evaluation.cases}} cases · hit@10 {{evaluation.hitAt10 | percent:'1.1-1'}} · MRR {{evaluation.mrr | number:'1.2-2'}}</p>
    </section>
  </main>`
})
export class EnterpriseSearchComponent implements OnInit {
  private readonly api = inject(ApiService);
  providers = ['google-drive', 'notion', 'confluence', 'sharepoint', 'slack', 'github', 'jira'];
  query = ''; provider = ''; language = ''; limit = 20; busy = false; searched = false;
  categories: Category[] = []; categoryId = ''; dateFrom = ''; dateTo = '';
  error = ''; suggestion = ''; answerText = ''; results: EnterpriseHit[] = []; latencyMs = 0;
  stats: any = null; evaluation: any = null; evalQuestion = ''; expectedIds = '';
  ngOnInit(): void { this.insights(); this.api.categories().subscribe({next: c => this.categories = c, error: () => {}}); }
  search(): void {
    if (!this.query.trim()) return;
    this.error = ''; this.answerText = ''; this.busy = true;
    const from = this.dateFrom ? new Date(this.dateFrom + 'T00:00:00Z').toISOString() : undefined;
    const to = this.dateTo ? new Date(this.dateTo + 'T23:59:59Z').toISOString() : undefined;
    this.api.enterpriseSearch(this.query.trim(), this.provider, this.language, this.limit, this.categoryId, from, to).subscribe({
      next: r => { this.results = r.results || []; this.latencyMs = r.latencyMs || 0; this.searched = true; this.busy = false; this.insights(); },
      error: e => { this.error = e?.error?.message || 'Search is unavailable or access was denied.'; this.busy = false; }
    });
  }
  answer(): void {
    if (!this.query.trim()) return;
    this.error = ''; this.busy = true;
    this.api.enterpriseAnswer(this.query.trim(), this.provider).subscribe({
      next: r => { this.answerText = r.answer || ''; this.results = r.citations || []; this.searched = true; this.busy = false; this.insights(); },
      error: e => { this.error = e?.error?.message || 'Unable to generate an answer.'; this.busy = false; }
    });
  }
  clicked(hit:EnterpriseHit):void {
    this.api.enterpriseSearchFeedback(hit.id,hit.sourceType).subscribe({error:()=>{}});
  }
  rewrite(): void {
    if (!this.query.trim()) return;
    this.api.enterpriseSearchRewrite(this.query.trim()).subscribe({ next: r => this.suggestion = r.query || '',
      error: () => this.error = 'Query rewriting is unavailable.' });
  }
  insights(): void { this.api.enterpriseSearchInsights().subscribe({next: r => this.stats = r, error: () => {}}); }
  addCase(): void {
    const ids = this.expectedIds.split(',').map(x => x.trim()).filter(Boolean);
    if (!ids.length || !this.evalQuestion.trim()) return;
    this.api.enterpriseSearchEvalAdd(this.evalQuestion.trim(), ids).subscribe({
      next: () => { this.evalQuestion = ''; this.expectedIds = ''; this.error = ''; },
      error: e => this.error = e?.error?.message || 'Could not save evaluation case.'
    });
  }
  evaluate(): void {this.api.enterpriseSearchEvalRun().subscribe({next: r => this.evaluation = r,
    error: e => this.error = e?.error?.message || 'Evaluation could not run.'});}
}
