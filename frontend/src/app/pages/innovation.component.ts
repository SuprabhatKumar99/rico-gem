import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';

@Component({
  standalone: true, selector: 'rk-innovation', imports: [CommonModule, FormsModule, RouterLink],
  template: `
  <main class="space-y-6">
    <header><p class="text-sm font-semibold text-slate-500">RicozKnow workspace</p><h1 class="text-3xl font-bold">Knowledge innovation</h1>
      <p class="text-slate-500">Collaboration, grounded AI answers, content-quality signals and ingestion connectors.</p></header>
    <div class="flex flex-wrap gap-2" role="tablist" aria-label="Knowledge tools">
      <button *ngFor="let section of tabs" class="rk-btn" [class.rk-btn-primary]="tab === section" (click)="tab=section" [attr.aria-selected]="tab === section" role="tab">{{ section }}</button>
    </div>
    <div *ngIf="error" role="alert" class="rounded-lg bg-rose-50 p-4 text-rose-700">{{ error }}</div>
    <section *ngIf="tab === 'Collaborative editor'" class="rk-card p-6 space-y-4">
      <h2 class="text-xl font-bold">Collaborative editorial workflow</h2>
      <p>Use article revisions and block-level review comments to coordinate edits, compare versions and approve publication.</p>
      <div class="flex flex-wrap gap-3"><a routerLink="/articles" class="rk-btn rk-btn-primary">Open articles</a><a routerLink="/review" class="rk-btn">Review queue</a></div>
      <div class="flex flex-wrap gap-2 items-end"><label>Article <select class="rk-input" [(ngModel)]="selectedArticle" (ngModelChange)="loadVersions()"><option value="">Choose article</option><option *ngFor="let a of collaborationArticles" [value]="a.id">{{a.title}}</option></select></label>
      <label>Draft version <select class="rk-input" [(ngModel)]="selectedVersion"><option value="">Choose version</option><option *ngFor="let v of collaborationVersions" [value]="v.id">#{{v.versionNumber}} · {{v.status}}</option></select></label>
      <a *ngIf="selectedArticle && selectedVersion" [routerLink]="['/collaborate',selectedArticle,selectedVersion]" class="rk-btn rk-btn-primary">Open shared editor</a></div>
      <p class="text-sm text-slate-500">Shared draft editing and live collaborator presence are available. Choose an article and editable version below. Concurrent changes are protected by revision checks; automatic keystroke merging is not yet supported.</p>
    </section>
    <section *ngIf="tab === 'AI copilot'" class="rk-card p-6 space-y-4">
      <h2 class="text-xl font-bold">AI knowledge copilot</h2><p class="text-sm text-slate-500">Answers use indexed tenant knowledge. Verify cited sources before relying on generated content.</p>
      <form (ngSubmit)="ask()" class="space-y-3"><label for="copilot-question" class="font-semibold">Your question</label>
        <textarea id="copilot-question" rows="3" name="question" class="rk-input w-full" [(ngModel)]="question" maxlength="4000" required></textarea>
        <button class="rk-btn rk-btn-primary" type="submit" [disabled]="asking || !question.trim()">{{ asking ? 'Searching…' : 'Ask copilot' }}</button></form>
      <div *ngFor="let turn of answers" class="rounded-lg border p-4 space-y-3">
        <p class="font-semibold">{{ turn.question }}</p><p class="whitespace-pre-wrap">{{ turn.answer }}</p>
        <p class="text-xs text-slate-500">{{ turn.grounded ? 'Grounded in indexed sources' : 'No sufficiently grounded answer' }} · Confidence {{ turn.confidence | number:'1.2-2' }}</p>
        <ul class="space-y-2"><li *ngFor="let c of turn.citations" class="text-sm"><a *ngIf="c.slug" [routerLink]="['/portal/article',c.slug]" class="text-blue-700 underline">[{{ c.rank }}] {{ c.title }}</a><span *ngIf="!c.slug">[{{ c.rank }}] {{ c.title }}</span></li></ul>
      </div>
    </section>
    <section *ngIf="tab === 'Quality intelligence'" class="rk-card p-6 space-y-4">
      <div class="flex justify-between"><h2 class="text-xl font-bold">Content quality insights</h2><button class="rk-btn" (click)="loadQuality()">Refresh</button></div>
      <p *ngIf="quality">{{ quality.findingsCount }} findings across {{ quality.totalArticles }} tenant articles. Title matches are candidate duplicates, not confirmed duplicates.</p>
      <p *ngIf="quality && !quality.findingsCount">No issues detected by these rules.</p>
      <ul class="divide-y"><li *ngFor="let f of quality?.findings" class="py-3"><a [routerLink]="['/articles',f.articleId]" class="font-semibold text-blue-700">{{ f.title }}</a> <span class="text-xs">({{ f.severity }})</span><p class="text-sm">{{ f.rule }} — {{ f.recommendation }}</p></li></ul>
      <a routerLink="/knowledge-health" class="rk-btn">Open indexing & publishing health</a>
    </section>
    <section *ngIf="tab === 'Connector marketplace'" class="rk-card p-6 space-y-4">
      <h2 class="text-xl font-bold">Connector catalog</h2><p>Available connectors use the existing ingestion pipeline. OAuth connections require provider credentials configured on the backend. Select and synchronize external sources using the dedicated connector marketplace. Restricted external content stays admin-only until permission-aware retrieval is enabled.</p>
      <div class="grid gap-3 md:grid-cols-2"><div *ngFor="let connector of catalog" class="rounded-lg border p-4">
        <div class="flex justify-between gap-2"><h3 class="font-bold">{{ connector.name }}</h3><span class="text-xs">{{ connector.status }}</span></div>
        <p class="text-sm text-slate-500">{{ connector.description }}</p>
        <a *ngIf="connector.status === 'AVAILABLE'" routerLink="/knowledge-sources" class="text-sm text-blue-700 underline">Configure source</a>
        <div *ngIf="connector.kind === 'OAUTH'" class="flex gap-2 mt-2 items-center">
          <span class="text-sm">{{ oauthStatus(connector.id) }}</span>
          <button *ngIf="!isConnected(connector.id)" class="rk-btn" [disabled]="!isConfigured(connector.id) || oauthBusy" (click)="connect(connector.id)">Connect with OAuth</button>
          <button *ngIf="isConnected(connector.id)" class="rk-btn" [disabled]="oauthBusy" (click)="disconnect(connector.id)">Disconnect</button>
        </div>
      </div></div>
      <h3 class="font-semibold">Configured sources ({{ sources.length }})</h3>
      <ul class="divide-y"><li *ngFor="let source of sources" class="py-2 text-sm">{{ source.name || source.url || source.id }} — {{ source.status }}</li></ul>
      <a routerLink="/connectors" class="rk-btn rk-btn-primary">Open connector marketplace</a>
    </section>
  </main>`
})
export class InnovationComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly tabs = ['Collaborative editor','AI copilot','Quality intelligence','Connector marketplace'];
  tab = this.tabs[0]; error = ''; question = ''; asking = false; conversationId: string | null = null;
  selectedArticle = ''; selectedVersion = ''; collaborationArticles: any[] = []; collaborationVersions: any[] = [];
  loadVersions() { this.selectedVersion=''; this.collaborationVersions=[];
    if(this.selectedArticle) this.api.getVersions(this.selectedArticle).subscribe({next:v=>this.collaborationVersions=v.filter((x:any)=>['DRAFT','REJECTED'].includes(x.status)),error:()=>this.error='Could not load versions.'}); }
  connections: any[] = []; oauthBusy=false;
  isConnected(id:string){return this.connections.some(c=>c.provider===id&&c.connected);}
  isConfigured(id:string){return this.connections.some(c=>c.provider===id&&c.configured);}
  oauthStatus(id:string){const c=this.connections.find(c=>c.provider===id);return !c?'Status unavailable':c.connected?'Authorized':c.configured?'Ready to authorize':'Server configuration required';}
  loadConnections(){this.api.oauthConnections().subscribe({next:v=>this.connections=v,error:()=>{this.error='OAuth connections require administrator access.';}});}
  connect(id:string){this.oauthBusy=true;this.api.oauthAuthorize(id).subscribe({next:v=>{window.location.assign(v.url);},error:()=>{this.error='OAuth could not start. Verify provider credentials and callback URLs.';this.oauthBusy=false;}});}
  disconnect(id:string){if(!confirm('Disconnect '+id+'?'))return;this.oauthBusy=true;this.api.oauthDisconnect(id).subscribe({next:()=>{this.oauthBusy=false;this.loadConnections();},error:()=>{this.oauthBusy=false;this.error='Disconnect failed.';}});}
  answers: any[] = []; quality: any = null; catalog: any[] = []; sources: any[] = [];
  ngOnInit() { this.loadConnections(); this.api.listArticles(undefined,0,100).subscribe({next:v=>this.collaborationArticles=v.content,error:()=>this.error='Could not load articles.'}); this.loadQuality(); this.api.innovationCatalog().subscribe({next: v=>this.catalog=v,error:()=>this.error='Connector catalog unavailable.'});
    this.api.innovationSources().subscribe({next:v=>this.sources=v,error:()=>this.error='Could not load connected sources.'}); }
  loadQuality() { this.api.innovationQuality().subscribe({next:v=>this.quality=v,error:()=>this.error='Quality report unavailable.'}); }
  ask() { const q=this.question.trim(); if(!q || this.asking) return; this.asking=true; this.error='';
    this.api.innovationAsk(q,this.conversationId).subscribe({next: v=> { this.conversationId=v.conversationId; this.answers.push({...v,question:q}); this.question=''; this.asking=false; },
      error:()=>{this.error='Unable to generate an answer. Check RAG indexing, model settings and your access permissions.';this.asking=false;} }); }
}
