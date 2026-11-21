import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";

@Component({
  selector: "rk-developer-portal",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <section class="space-y-5 max-w-6xl">
    <header><p class="text-sm font-semibold text-slate-500">Integrations · Phase 8</p>
      <h1 class="text-3xl font-bold">Developer Portal</h1>
      <p class="text-sm text-slate-600 mt-2">Issue tenant-bound credentials, review tool usage and manage human review requests. Only explicitly granted scopes are effective.</p>
    </header>
    <p *ngIf="error" class="rounded-lg bg-rose-50 p-3 text-rose-700" role="alert">{{error}}</p>
    <p *ngIf="info" class="rounded-lg bg-slate-100 p-3 text-slate-700" role="status">{{info}}</p>
    <section *ngIf="created" class="rk-card p-5 space-y-2 border-amber-300">
      <h2 class="font-bold">New key — displayed only once</h2>
      <p class="text-sm">Store it in your secret manager. Never embed keys in browser JavaScript or public code.</p>
      <div class="flex gap-2 flex-wrap"><input class="rk-input flex-1 font-mono" readonly [value]="created.key" aria-label="New API key"/>
      <button class="rk-btn rk-btn-primary" (click)="copy()">Copy</button>
      <button class="rk-btn rk-btn-secondary" (click)="created=null">Dismiss</button></div>
    </section>
    <div class="grid md:grid-cols-2 gap-5">
      <section class="rk-card p-5 space-y-3"><h2 class="text-lg font-bold">Create MCP credential</h2>
        <label class="block text-sm">Credential name<input class="rk-input mt-1" [(ngModel)]="name" maxlength="150" placeholder="Search integration"/></label>
        <label class="block text-sm">Expires on (optional)<input class="rk-input mt-1" type="datetime-local" [(ngModel)]="expiry"/></label>
        <label class="flex items-center gap-2"><input type="checkbox" [(ngModel)]="read"/> Read public articles</label>
        <label class="flex items-center gap-2"><input type="checkbox" [(ngModel)]="search"/> Search public articles</label>
        <label class="flex items-center gap-2"><input type="checkbox" [(ngModel)]="write"/> Request human review (requires server enablement)</label>
        <p class="text-xs text-slate-500">MCP_CONNECT is required and added automatically. Write access cannot publish content. MCP is disabled on the server by default.</p>
        <button class="rk-btn rk-btn-primary" [disabled]="busy || !name.trim()" (click)="create()">{{busy?'Creating…':'Create key'}}</button>
      </section>
      <section class="rk-card p-5"><h2 class="text-lg font-bold">Connection details</h2>
        <p class="text-sm text-slate-600 mt-2">Protocol: MCP 2026-07-28 · stateless HTTP · JSON-RPC</p>
        <p class="mt-2 font-mono text-xs break-all">POST /mcp</p>
        <p class="text-xs text-slate-600 mt-3">Use headers Authorization: Bearer &lt;key&gt;, MCP-Protocol-Version: 2026-07-28 and Mcp-Method. Set Mcp-Name for tools/call. Tool responses never include private documents or unverified connector data.</p>
        <pre class="text-xs bg-slate-50 p-3 overflow-auto mt-3">{{sample}}</pre>
      </section>
    </div>
    <section class="rk-card p-5"><div class="flex justify-between items-center"><h2 class="font-bold">Developer credentials</h2>
      <button class="rk-btn rk-btn-secondary" (click)="load()">Refresh</button></div>
      <div *ngIf="!keys.length" class="text-sm text-slate-500 mt-3">No API credentials.</div>
      <div *ngFor="let k of keys" class="flex gap-2 justify-between items-center border-t border-slate-100 py-3 text-sm">
        <div class="min-w-0"><strong>{{k.name}}</strong><p class="font-mono text-xs text-slate-500">{{k.prefix}} · {{k.status}} · {{k.scopes}}</p></div>
        <button class="text-rose-700 text-sm" *ngIf="k.status==='ACTIVE'" (click)="revoke(k)">Revoke</button>
      </div>
    </section>
    <section class="rk-card p-5"><h2 class="font-bold">API usage · last 30 days</h2>
      <div class="overflow-x-auto mt-3"><table class="w-full text-sm text-left"><thead><tr><th>Key</th><th>Method</th><th>Tool</th><th>Requests</th><th>Failures</th><th>Avg ms</th></tr></thead>
      <tbody><tr *ngFor="let u of usage" class="border-t border-slate-100"><td class="py-2 font-mono text-xs">{{u.api_key_id}}</td><td>{{u.method}}</td><td>{{u.tool_name}}</td><td>{{u.requests}}</td><td>{{u.failures}}</td><td>{{u.average_latency_ms}}</td></tr></tbody></table></div>
      <p *ngIf="!usage.length" class="text-sm text-slate-500 mt-2">No recorded requests yet.</p>
    </section>
    <section class="rk-card p-5"><h2 class="font-bold">Human review requests</h2>
      <p class="text-sm text-slate-500">Only administrators can resolve requests. A review request never publishes or modifies an article.</p>
      <div *ngFor="let item of requests" class="flex justify-between gap-3 py-3 border-t border-slate-100 text-sm">
        <div><strong>{{item.status}}</strong> · <span class="font-mono text-xs">{{item.article_id}}</span>
          <p class="text-slate-600 mt-1">{{item.reason}}</p></div>
        <button *ngIf="item.status==='OPEN'" class="rk-btn rk-btn-secondary" (click)="resolve(item.id)">Mark resolved</button>
      </div><p *ngIf="!requests.length" class="text-sm text-slate-500 mt-3">No review requests.</p>
    </section>
  </section>`
})
export class DeveloperPortalComponent implements OnInit {
  name=''; expiry=''; read=true;search=true;write=false;busy=false;error='';info='';created:any=null;
  keys:any[]=[];usage:any[]=[];requests:any[]=[];
  readonly sample=`curl -X POST https://YOUR-API/mcp \
  -H 'Authorization: Bearer rk_live_...' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: tools/list' \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'`;
  constructor(private api:ApiService){}
  ngOnInit(){this.load();}
  load(){this.api.developerKeys().subscribe({next:x=>this.keys=x,error:()=>this.error='Could not load keys'});
    this.api.developerUsage().subscribe({next:x=>this.usage=x,error:()=>this.error='Could not load usage'});
    this.api.developerReviewRequests().subscribe({next:x=>this.requests=x,error:()=>this.error='Could not load review requests'});
  }
  create(){const scopes=['MCP_CONNECT'];if(this.read)scopes.push('MCP_READ');if(this.search)scopes.push('MCP_SEARCH');if(this.write)scopes.push('MCP_WRITE');
    this.error='';this.busy=true;
    const expiresAt=this.expiry?new Date(this.expiry).toISOString():undefined;
    this.api.developerCreateKey(this.name.trim(),scopes,expiresAt).subscribe({next:v=>{this.created=v;this.busy=false;this.name='';this.load();},
      error:e=>{this.busy=false;this.error=e?.error?.message||'Key creation failed';}});
  }
  revoke(k:any){if(!confirm('Revoke this key immediately?'))return;
    this.api.developerRevokeKey(k.id).subscribe({next:()=>this.load(),error:()=>this.error='Revoke failed'});}
  resolve(id:string){this.api.developerResolveReview(id).subscribe({next:()=>this.load(),error:()=>this.error='Resolution failed'});}
  async copy(){if(!this.created)return;try{await navigator.clipboard.writeText(this.created.key);this.info='Key copied';}catch{this.error='Clipboard unavailable';}}
}
