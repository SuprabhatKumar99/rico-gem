import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

interface KnowledgeGraphNode {
  id: string;
  title: string;
}

interface KnowledgeGraphEdge {
  from_article_id: string;
  to_article_id: string;
  relation: string;
}

interface KnowledgeGraphView {
  nodes: KnowledgeGraphNode[];
  edges: KnowledgeGraphEdge[];
  entities: unknown[];
}

@Component({
  standalone: true,
  selector: 'rk-ai-intelligence',
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div><p class="text-sm font-semibold text-slate-500">Human-governed AI</p>
        <h1 class="mt-1 text-3xl font-bold">AI Intelligence Platform</h1>
        <p class="mt-2 text-sm text-slate-500">Writing assistance, approved read-only agents, knowledge graph, evaluation and usage governance.</p>
      </div>
      <nav class="flex flex-wrap gap-2" aria-label="AI platform sections">
        <button *ngFor="let t of tabs" class="rk-btn" [ngClass]="tab === t ? 'rk-btn-primary' : 'rk-btn-secondary'"
          [disabled]="t !== 'Writing' && !auth.hasRole('ADMIN')" (click)="tab=t; loadTab()">{{t}}</button>
      </nav>
      <div *ngIf="error" role="alert" class="rounded-xl bg-red-50 p-4 text-sm text-red-800">{{error}}</div>
      <div *ngIf="notice" role="status" class="rounded-xl bg-slate-50 p-4 text-sm">{{notice}}</div>
      <section *ngIf="tab === 'Writing'" class="rk-card p-6 space-y-4">
        <h2 class="text-xl font-semibold">AI Writing Studio</h2>
        <p class="text-sm text-slate-500">Generated text is an editorial suggestion, never an automatically published article.</p>
        <div class="grid gap-4 md:grid-cols-3"><label>Action
          <select class="rk-select w-full" [(ngModel)]="operation"><option *ngFor="let o of operations" [value]="o">{{o}}</option></select>
          </label><label>Target language<input class="rk-input w-full" [(ngModel)]="language" maxlength="20" /></label>
          <label>Additional guidance<input class="rk-input w-full" [(ngModel)]="guidance" maxlength="500" /></label></div>
        <label class="block">Source material<textarea class="rk-input min-h-44 w-full" [(ngModel)]="source" maxlength="16000" placeholder="Insert verified text here"></textarea></label>
        <button class="rk-btn rk-btn-primary" [disabled]="busy || !source.trim()" (click)="write()">{{busy?'Generating…':'Generate suggestion'}}</button>
        <div *ngIf="writingResult" class="rounded-xl bg-slate-50 p-4">
          <p class="mb-2 text-sm font-semibold">Draft suggestion — human review required</p>
          <pre class="whitespace-pre-wrap text-sm">{{writingResult.text}}</pre>
          <button class="rk-btn rk-btn-secondary mt-3" (click)="copy(writingResult.text)">Copy text</button>
        </div>
      </section>
      <section *ngIf="tab === 'Agents' && auth.hasRole('ADMIN')" class="rk-card p-6 space-y-4">
        <h2 class="text-xl font-semibold">AI Agent Studio</h2>
        <p class="text-sm text-slate-500">Agents are read-only, use one authorized knowledge-search tool, and may require separate administrator approval.</p>
        <div class="grid gap-3 md:grid-cols-2"><label>Agent name<input class="rk-input w-full" [(ngModel)]="agentName" maxlength="120" /></label>
          <label>Instructions<textarea class="rk-input w-full" [(ngModel)]="agentInstructions" maxlength="4000"></textarea></label></div>
        <div class="flex gap-4"><label><input type="checkbox" [(ngModel)]="agentEnabled" /> Enabled</label>
          <label><input type="checkbox" [(ngModel)]="agentApproval" /> Require separate approval</label>
          <label><input type="checkbox" [(ngModel)]="agentConnectors" /> Include admin-restricted connector documents</label></div>
        <button class="rk-btn rk-btn-primary" [disabled]="busy || !agentName.trim() || !agentInstructions.trim()" (click)="createAgent()">Create agent</button>
        <div *ngFor="let a of agents" class="rounded-xl border border-slate-200 p-4 space-y-3">
          <div class="flex flex-wrap items-center gap-3"><strong>{{a.name}}</strong><span class="text-xs text-slate-500">{{a.enabled?'Enabled':'Disabled'}} · {{a.tool}} · {{a.sourceScope}} · {{a.requiresApproval?'Approval needed':'Direct run'}}</span>
            <button *ngIf="a.enabled" class="rk-btn rk-btn-secondary" (click)="disableAgent(a.id)">Disable</button></div>
          <div class="flex flex-wrap gap-2"><input class="rk-input flex-1" [(ngModel)]="agentQuestions[a.id]" placeholder="Ask a knowledge question" maxlength="2000" />
            <button class="rk-btn rk-btn-primary" [disabled]="busy || !a.enabled || !agentQuestions[a.id]" (click)="runAgent(a.id)">Run</button></div>
        </div>
        <div *ngIf="agentRun" class="rounded-xl bg-slate-50 p-4 space-y-3">
          <p><strong>Run:</strong> {{agentRun.status}}</p>
          <button *ngIf="agentRun.status==='WAITING_APPROVAL'" class="rk-btn rk-btn-secondary"
            [disabled]="busy" (click)="approveRun(agentRun.id)">Approve (different admin account required)</button>
          <p class="whitespace-pre-wrap text-sm">{{agentRun.answer}}</p>
          <pre class="whitespace-pre-wrap text-xs">{{agentRun.citations}}</pre>
        </div>
      </section>
      <section *ngIf="tab === 'Graph' && auth.hasRole('ADMIN')" class="rk-card p-6 space-y-4">
        <h2 class="text-xl font-semibold">Knowledge Graph & Quality</h2>
        <p class="text-sm text-slate-500">Metadata-derived links and similarity proposals. No automatic merge or publishing.</p>
        <div class="flex flex-wrap gap-2"><button class="rk-btn rk-btn-primary" [disabled]="busy" (click)="rebuildGraph()">Rebuild graph</button>
          <button class="rk-btn rk-btn-secondary" [disabled]="busy" (click)="scanQuality()">Scan quality</button>
          <button class="rk-btn rk-btn-secondary" (click)="loadGraph()">Refresh</button></div>
        <p *ngIf="graph">{{graph.nodes?.length || 0}} articles · {{graph.edges?.length || 0}} relationships · {{graph.entities?.length || 0}} entity references</p>
        <div *ngIf="graph?.nodes?.length" class="rounded-xl border border-slate-200 p-4">
          <p class="font-semibold mb-3">Explore connections — select an article</p>
          <div class="flex flex-wrap gap-2 max-h-40 overflow-auto">
            <button *ngFor="let n of graphNodes | slice:0:80" class="rk-btn rk-btn-secondary text-xs"
              [attr.aria-pressed]="selectedGraphNode === n.id" (click)="selectedGraphNode=n.id">{{n.title}}</button>
          </div>
          <div *ngIf="selectedGraphNode" class="mt-4 text-sm">
            <strong>Connected articles</strong>
            <div *ngFor="let e of selectedConnections()" class="mt-2">{{e.relation}} · {{e.from_article_id}} → {{e.to_article_id}}</div>
            <p *ngIf="!selectedConnections().length" class="text-slate-500">No detected relationships. Rebuild graph to refresh.</p>
          </div>
        </div>
        <div *ngFor="let p of proposals" class="rounded-xl border border-slate-200 p-3 text-sm">
          <strong>{{p.category}}</strong><span class="ml-2 text-slate-500">{{p.status}}</span><p>{{p.explanation}}</p>
          <p class="text-xs text-slate-500">Article {{p.article_id}} · related {{p.related_article_id || '—'}}</p>
          <div *ngIf="p.status==='OPEN'" class="mt-2 flex gap-2"><button class="rk-btn rk-btn-secondary" (click)="reviewProposal(p.id,'ACCEPTED')">Accept for manual review</button>
            <button class="rk-btn rk-btn-secondary" (click)="reviewProposal(p.id,'DISMISSED')">Dismiss</button></div>
        </div>
      </section>
      <section *ngIf="tab === 'Evaluation' && auth.hasRole('ADMIN')" class="rk-card p-6 space-y-4">
        <h2 class="text-xl font-semibold">RAG Evaluation & Guardrails</h2>
        <p class="text-sm text-slate-500">Uses permission-checked retrieval. The evidence-match metric is a heuristic, not a proof of groundedness.</p>
        <input class="rk-input w-full" [(ngModel)]="evalQuestion" placeholder="Evaluation question" maxlength="300" />
        <input class="rk-input w-full" [(ngModel)]="evalArticle" placeholder="Expected published article UUID" />
        <input class="rk-input w-full" [(ngModel)]="evalPhrase" placeholder="Optional expected excerpt phrase" maxlength="500" />
        <div class="flex gap-2"><button class="rk-btn rk-btn-secondary" [disabled]="busy" (click)="addCase()">Add case</button>
          <button class="rk-btn rk-btn-primary" [disabled]="busy || !cases.length" (click)="runEval()">Run evaluation</button></div>
        <p>{{cases.length}} test cases</p><pre *ngIf="evalResult" class="whitespace-pre-wrap rounded-xl bg-slate-50 p-4 text-sm">{{evalResult | json}}</pre>
      </section>
      <section *ngIf="tab === 'Governance' && auth.hasRole('ADMIN')" class="rk-card p-6 space-y-4">
        <h2 class="text-xl font-semibold">Model Usage Governance</h2>
        <p class="text-sm text-slate-500">Per-tenant quota, token counts, latency and provider failures. Costs appear only when pricing has been configured.</p>
        <button class="rk-btn rk-btn-secondary" (click)="loadUsage()">Refresh</button>
        <pre *ngIf="usage" class="overflow-auto whitespace-pre-wrap rounded-xl bg-slate-50 p-4 text-sm">{{usage | json}}</pre>
      </section>
    </div>
  `
})
export class AiIntelligenceComponent implements OnInit {
  tabs = ['Writing','Agents','Graph','Evaluation','Governance']; tab = 'Writing';
  operations=['REWRITE','SUMMARIZE','GRAMMAR','FAQ','TRANSLATE','TAGS']; operation='SUMMARIZE';
  language='en'; guidance=''; source=''; writingResult: any=null;
  busy=false;error='';notice=''; agents:any[]=[];agentName='Knowledge assistant';agentInstructions='Answer only from authorized evidence.';
  agentEnabled=false;agentApproval=true;agentConnectors=false;agentQuestions: Record<string,string>={};agentRun:any=null;
  graph:KnowledgeGraphView|null=null;selectedGraphNode="";proposals:any[]=[];cases:any[]=[];evalQuestion='';evalArticle='';evalPhrase='';evalResult:any=null;usage:any=null;
  constructor(private readonly api: ApiService, readonly auth: AuthService){}
  ngOnInit():void{this.loadTab();}
  private fail(err:any):void{this.error=err?.error?.message||'Operation failed. Verify your permissions and server logs.';this.busy=false;}
  private start():void{this.error='';this.notice='';this.busy=true;}
  loadTab():void{if(!this.auth.hasRole('ADMIN'))return;
    if(this.tab==='Agents')this.loadAgents();
    if(this.tab==='Graph')this.loadGraph();
    if(this.tab==='Evaluation')this.loadCases();
    if(this.tab==='Governance')this.loadUsage();}
  write():void{this.start();this.api.aiPlatformWrite({operation:this.operation,text:this.source,language:this.language,guidance:this.guidance}).subscribe({next:r=>{this.writingResult=r;this.busy=false;},error:e=>this.fail(e)});}
  copy(value:string):void{if(navigator.clipboard){navigator.clipboard.writeText(value).then(()=>this.notice='Copied. Review before publishing.').catch(()=>this.error='Clipboard permission denied.');}}
  loadAgents():void{this.api.aiPlatformAgents().subscribe({next:x=>this.agents=x,error:e=>this.fail(e)});}
  createAgent():void{this.start();this.api.aiPlatformAgentCreate({name:this.agentName,instructions:this.agentInstructions,enabled:this.agentEnabled,requiresApproval:this.agentApproval,maxToolCalls:1,sourceScope:this.agentConnectors?'ADMIN_CONNECTORS':'ARTICLES_ONLY'})
    .subscribe({next:()=>{this.busy=false;this.loadAgents();},error:e=>this.fail(e)});}
  disableAgent(id:string):void{this.start();this.api.aiPlatformAgentDisable(id).subscribe({next:()=>{this.busy=false;this.loadAgents();},error:e=>this.fail(e)});}
  runAgent(id:string):void{this.start();this.api.aiPlatformAgentRun(id,this.agentQuestions[id]).subscribe({next:r=>{this.agentRun=r;this.busy=false;},error:e=>this.fail(e)});}
  approveRun(id:string):void{this.start();this.api.aiPlatformAgentApprove(id).subscribe({next:r=>{this.agentRun=r;this.busy=false;},error:e=>this.fail(e)});}
  loadGraph():void{this.api.aiPlatformGraph().subscribe({next:r=>this.graph=r,error:e=>this.fail(e)});this.api.aiPlatformQualityProposals().subscribe({next:x=>this.proposals=x,error:e=>this.fail(e)});}
  get graphNodes():KnowledgeGraphNode[]{return this.graph?.nodes ?? [];}
  selectedConnections():KnowledgeGraphEdge[]{return (this.graph?.edges ?? []).filter(e=>e.from_article_id===this.selectedGraphNode||e.to_article_id===this.selectedGraphNode).slice(0,30);}
  rebuildGraph():void{this.start();this.api.aiPlatformGraphRebuild().subscribe({next:r=>{this.notice=JSON.stringify(r);this.busy=false;this.loadGraph();},error:e=>this.fail(e)});}
  scanQuality():void{this.start();this.api.aiPlatformQualityScan().subscribe({next:r=>{this.notice=JSON.stringify(r);this.busy=false;this.loadGraph();},error:e=>this.fail(e)});}
  reviewProposal(id:string,status:string):void{this.start();this.api.aiPlatformReviewProposal(id,status).subscribe({next:()=>{this.busy=false;this.loadGraph();},error:e=>this.fail(e)});}
  loadCases():void{this.api.aiPlatformEvalCases().subscribe({next:x=>this.cases=x,error:e=>this.fail(e)});}
  addCase():void{this.start();this.api.aiPlatformEvalAdd(this.evalQuestion,this.evalArticle,this.evalPhrase).subscribe({next:()=>{this.busy=false;this.loadCases();},error:e=>this.fail(e)});}
  runEval():void{this.start();this.api.aiPlatformEvalRun().subscribe({next:r=>{this.evalResult=r;this.busy=false;},error:e=>this.fail(e)});}
  loadUsage():void{this.api.aiPlatformUsage().subscribe({next:r=>this.usage=r,error:e=>this.fail(e)});}
}
