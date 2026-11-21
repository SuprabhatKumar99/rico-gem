import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";
import { AuthService } from "../core/auth.service";
import { Category } from "../models";

@Component({
  selector: "rk-ai-studio",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <p class="text-sm font-semibold text-slate-500">AI & knowledge intelligence</p>
      <h1 class="mt-1 text-3xl font-bold tracking-tight">AI Studio</h1>
      <p class="mt-2 text-sm text-slate-500">Generate drafts, improve content, analyze article intelligence, and turn knowledge gaps into draft work.</p>

      <p *ngIf="message" class="mt-5 rounded-xl bg-emerald-50 p-3 text-sm text-emerald-700">{{ message }}</p>
      <p *ngIf="error" class="mt-5 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{{ error }}</p>

      <div class="mt-6 grid gap-6 xl:grid-cols-2">
        <section class="rk-card p-6">
          <h2 class="font-bold">Generate knowledge article</h2>
          <div class="mt-5 space-y-4">
            <div><label class="rk-label">Topic</label><input class="rk-input" [(ngModel)]="topic" placeholder="How to configure SSO" /></div>
            <div><label class="rk-label">Source material</label><textarea class="rk-input min-h-28" [(ngModel)]="sourceMaterial" placeholder="Paste trusted source material or notes..."></textarea></div>
            <div class="grid gap-4 md:grid-cols-3">
              <div><label class="rk-label">Category</label><select class="rk-select" [(ngModel)]="categoryId"><option value="">None</option><option *ngFor="let c of categories" [value]="c.id">{{ c.name }}</option></select></div>
              <div><label class="rk-label">Audience</label><input class="rk-input" [(ngModel)]="audience" placeholder="Customers" /></div>
              <div><label class="rk-label">Language</label><input class="rk-input" [(ngModel)]="language" placeholder="en" /></div>
            </div>
            <div class="flex flex-wrap gap-2">
              <button class="rk-btn rk-btn-primary" [disabled]="busy || !topic.trim()" (click)="generate()">{{ busy ? "Working…" : "Generate" }}</button>
              <button class="rk-btn rk-btn-secondary" [disabled]="busy || !topic.trim()" (click)="generateDraft()">Generate + create draft</button>
            </div>
          </div>
        </section>

        <section class="rk-card p-6">
          <h2 class="font-bold">Improve existing content</h2>
          <p class="mt-1 text-xs text-slate-500">Use the backend AI improvement endpoint without leaving the studio.</p>
          <div class="mt-5 space-y-4">
            <textarea class="rk-input min-h-48" [(ngModel)]="content" placeholder="Paste article content here..."></textarea>
            <input class="rk-input" [(ngModel)]="instruction" placeholder="Instruction, e.g. make it clearer and more concise" />
            <button class="rk-btn rk-btn-primary" [disabled]="busy || !content.trim()" (click)="improve()">Improve content</button>
          </div>
        </section>
      </div>

      <section class="rk-card mt-6 p-6">
        <div class="flex flex-wrap items-end justify-between gap-4">
          <div><h2 class="font-bold">Article intelligence</h2><p class="mt-1 text-xs text-slate-500">Analyze an article and inspect dependencies/dependents.</p></div>
          <div class="flex gap-2"><input class="rk-input w-72" [(ngModel)]="articleId" placeholder="Article ID" /><button class="rk-btn rk-btn-secondary" [disabled]="!articleId.trim()" (click)="analyzeArticle()">Analyze</button><button class="rk-btn rk-btn-secondary" [disabled]="!articleId.trim()" (click)="loadIntelligence()">Load</button></div>
        </div>
        <div *ngIf="intelligence" class="mt-5 grid gap-5 lg:grid-cols-3">
          <div class="rounded-xl bg-slate-50 p-4"><p class="text-xs font-bold uppercase tracking-wide text-slate-400">Intelligence</p><pre class="mt-3 max-h-80 overflow-auto whitespace-pre-wrap text-xs">{{ intelligence | json }}</pre></div>
          <div class="rounded-xl bg-slate-50 p-4"><div class="flex justify-between"><p class="text-xs font-bold uppercase tracking-wide text-slate-400">Dependencies</p><button class="text-xs font-semibold" (click)="detectDependencies()">Detect</button></div><pre class="mt-3 max-h-80 overflow-auto whitespace-pre-wrap text-xs">{{ dependencies | json }}</pre></div>
          <div class="rounded-xl bg-slate-50 p-4"><p class="text-xs font-bold uppercase tracking-wide text-slate-400">Dependents</p><pre class="mt-3 max-h-80 overflow-auto whitespace-pre-wrap text-xs">{{ dependents | json }}</pre></div>
        </div>
      </section>

      <section *ngIf="auth.hasRole('ADMIN')" class="rk-card mt-6 overflow-hidden">
        <div class="flex items-center justify-between border-b border-slate-200 p-5"><div><h2 class="font-bold">Knowledge-gap tickets</h2><p class="mt-1 text-xs text-slate-500">Admin intelligence endpoint for unresolved search demand.</p></div><button class="rk-btn rk-btn-secondary" (click)="loadGaps()">Refresh</button></div>
        <div *ngIf="!gaps.length" class="p-8 text-center text-sm text-slate-500">No knowledge gaps returned.</div>
        <div *ngFor="let gap of gaps" class="border-b border-slate-100 p-5 last:border-b-0">
          <div class="flex flex-wrap items-start justify-between gap-3"><div><p class="font-semibold">{{ gap['query'] || gap['question'] || "Untitled gap" }}</p><p class="mt-1 text-xs text-slate-500">Score: {{ gap['gapScore'] ?? gap['score'] ?? "—" }}</p></div><div class="flex gap-2"><button class="rk-btn rk-btn-secondary" (click)="suggest(gap)">Generate suggestion</button><button class="rk-btn rk-btn-primary" (click)="createGapDraft(gap)">Create draft</button></div></div>
        </div>
      </section>

      <section *ngIf="result" class="rk-card mt-6 p-6"><h2 class="font-bold">Latest AI response</h2><pre class="mt-4 max-h-[32rem] overflow-auto whitespace-pre-wrap rounded-xl bg-slate-950 p-4 text-xs text-white">{{ result | json }}</pre></section>
    </div>
  `
})
export class AiStudioComponent implements OnInit {
  categories: Category[] = [];
  topic = ""; sourceMaterial = ""; categoryId = ""; audience = ""; language = "en";
  content = ""; instruction = ""; articleId = "";
  busy = false; message = ""; error = "";
  result: unknown = null; intelligence: unknown = null; dependencies: unknown[] = []; dependents: unknown[] = []; gaps: Record<string, any>[] = [];

  constructor(private readonly api: ApiService, readonly auth: AuthService) {}
  ngOnInit(): void {
    this.api.categories().subscribe({ next: x => this.categories = x, error: () => this.categories = [] });
    if (this.auth.hasRole("ADMIN")) {
      this.loadGaps();
    }
  }
  private clear(): void { this.error = ""; this.message = ""; }
  generate(): void { this.clear(); this.busy = true; this.api.aiGenerate(this.request()).subscribe({ next: x => { this.result = x; this.busy = false; }, error: e => { this.error = e?.error?.message || "Generation failed."; this.busy = false; } }); }
  generateDraft(): void { this.clear(); this.busy = true; this.api.aiGenerateAndCreateDraft(this.request()).subscribe({ next: x => { this.result = x; this.message = "Draft created by AI."; this.busy = false; }, error: e => { this.error = e?.error?.message || "Could not create AI draft."; this.busy = false; } }); }
  improve(): void { this.clear(); this.busy = true; this.api.aiImprove(this.content, this.instruction.trim() || undefined).subscribe({ next: x => { this.result = x; this.busy = false; }, error: e => { this.error = e?.error?.message || "Improvement failed."; this.busy = false; } }); }
  private request() { return { topic: this.topic.trim(), sourceMaterial: this.sourceMaterial.trim() || undefined, categoryId: this.categoryId || undefined, audience: this.audience.trim() || undefined, language: this.language.trim() || undefined }; }
  analyzeArticle(): void { this.clear(); this.api.analyzeArticleIntelligence(this.articleId.trim()).subscribe({ next: x => { this.intelligence = x; this.loadDependencies(); }, error: e => this.error = e?.error?.message || "Analysis failed." }); }
  loadIntelligence(): void { this.clear(); this.api.articleIntelligence(this.articleId.trim()).subscribe({ next: x => this.intelligence = x, error: e => this.error = e?.error?.message || "Could not load intelligence." }); this.loadDependencies(); }
  loadDependencies(): void { const id = this.articleId.trim(); if (!id) return; this.api.articleDependencies(id).subscribe({ next: x => this.dependencies = x, error: () => this.dependencies = [] }); this.api.articleDependents(id).subscribe({ next: x => this.dependents = x, error: () => this.dependents = [] }); }
  detectDependencies(): void { const id = this.articleId.trim(); if (!id) return; this.api.detectArticleDependencies(id).subscribe({ next: x => { this.dependencies = x; this.message = "Dependency detection completed."; }, error: e => this.error = e?.error?.message || "Dependency detection failed." }); }
  loadGaps(): void {
    if (!this.auth.hasRole("ADMIN")) return;
    this.api.knowledgeGapTickets().subscribe({ next: x => this.gaps = x as Record<string, any>[], error: e => this.error = e?.error?.message || "Could not load knowledge gaps." }); }
  suggest(gap: Record<string, any>): void { if (!this.auth.hasRole("ADMIN") || !gap['id']) return; this.api.generateGapSuggestion(String(gap['id'])).subscribe({ next: x => this.result = x, error: e => this.error = e?.error?.message || "Could not generate suggestion." }); }
  createGapDraft(gap: Record<string, any>): void { if (!this.auth.hasRole("ADMIN") || !gap['id']) return; this.api.createGapDraft(String(gap['id'])).subscribe({ next: x => { this.result = x; this.message = "Draft created from knowledge gap."; }, error: e => this.error = e?.error?.message || "Could not create draft." }); }
}
