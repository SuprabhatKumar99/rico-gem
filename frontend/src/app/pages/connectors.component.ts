import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';

/** Administrative connector marketplace. Every control calls a persisted backend operation. */
@Component({standalone:true,selector:'rk-connectors',imports:[CommonModule,FormsModule],template:`
<main class="space-y-6">
  <header class="space-y-1"><h1 class="text-3xl font-bold">Connector marketplace</h1>
  <p class="text-slate-600">Authorize accounts, choose sources, and review ingestion health. Connector content is restricted to admins until source ACL synchronization is verified.</p></header>
  <div *ngIf="error" role="alert" class="rounded-xl border border-rose-300 bg-rose-50 p-3 text-rose-800">{{error}} <button class="ml-3 underline" (click)="error=''">Dismiss</button></div>
  <div *ngIf="notice" role="status" class="rounded-xl border border-slate-200 bg-slate-50 p-3">{{notice}}</div>
  <section class="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-label="Providers">
    <div *ngFor="let c of catalog" class="rk-card p-5 space-y-3">
      <div class="flex items-center justify-between gap-2"><h2 class="font-bold text-lg">{{c.name}}</h2><span class="text-xs rounded-full bg-slate-100 px-2 py-1">{{isConnected(c.id)?'Connected':c.configured?'Ready':'Not configured'}}</span></div>
      <p class="text-sm text-slate-500">{{c.webhookSupported?'Signed webhook + scheduled sync':'Scheduled synchronization'}}</p>
      <div class="flex flex-wrap gap-2">
        <button *ngIf="!isConnected(c.id)" type="button" class="rk-btn rk-btn-primary" [disabled]="busy||!c.configured" (click)="connect(c.id)">Authorize</button>
        <button *ngIf="isConnected(c.id)" type="button" class="rk-btn rk-btn-primary" [disabled]="busy" (click)="browse(c.id)">Browse sources</button>
        <button *ngIf="isConnected(c.id)" type="button" class="rk-btn" [disabled]="busy" (click)="disconnect(c.id)">Disconnect</button>
      </div>
      <p *ngIf="!c.configured" class="text-xs text-amber-800">Configure OAuth credentials, callback URL and encryption key on the server.</p>
    </div>
  </section>
  <section *ngIf="selectedProvider" class="rk-card p-5 space-y-4" aria-label="Browse remote sources">
    <div class="flex flex-wrap justify-between gap-2"><h2 class="font-bold text-xl">Select {{selectedProvider}} sources</h2><button type="button" class="rk-btn" (click)="selectedProvider='';remoteItems=[]">Close</button></div>
    <label class="block text-sm">Automatic sync interval
      <select class="rk-input ml-2" [(ngModel)]="syncInterval"><option [ngValue]="15">Every 15 minutes</option><option [ngValue]="60">Every hour</option><option [ngValue]="360">Every 6 hours</option><option [ngValue]="1440">Every day</option></select></label>
    <p *ngIf="loadingRemote" class="text-sm text-slate-500">Loading remote resources…</p>
    <p *ngIf="!loadingRemote&&!remoteItems.length" class="text-sm text-slate-500">No supported resources returned. Check your provider permissions.</p>
    <ul class="divide-y"><li *ngFor="let item of remoteItems" class="py-3 flex items-center justify-between gap-3"><span class="min-w-0 break-all text-sm">{{item.name}} <small class="text-slate-500">{{item.mimeType}}</small></span>
    <button type="button" class="rk-btn" [disabled]="busy" (click)="item.mimeType === 'container' ? enter(item.id) : select(item)">{{ item.mimeType === 'container' ? 'Open' : 'Import & sync' }}</button></li></ul>
    <button type="button" *ngIf="selectedProvider==='sharepoint'" class="rk-btn" (click)="enter(undefined)">Back to sites</button>
    <button type="button" *ngIf="nextCursor" class="rk-btn" [disabled]="loadingRemote" (click)="loadMore()">Load more</button>
  </section>
  <section class="rk-card p-5 space-y-3" aria-label="Connected knowledge sources">
    <div class="flex items-center justify-between gap-3"><h2 class="font-bold text-xl">Connected sources</h2><button type="button" class="rk-btn" (click)="reload()">Refresh</button></div>
    <p *ngIf="!sources.length" class="text-slate-500">No selected connector sources yet.</p>
    <ul class="divide-y"><li *ngFor="let s of sources" class="py-4 space-y-2">
      <div class="flex flex-wrap justify-between items-center gap-2"><div><strong>{{s.name}}</strong> <span class="text-xs text-slate-500">{{s.provider}} · {{s.documents}} documents</span>
      <p class="text-xs text-slate-500">{{s.status}} · Last sync: {{s.lastSyncedAt ? (s.lastSyncedAt | date:'medium') : 'Never'}} · Next: {{s.nextSyncAt | date:'medium'}}</p></div>
      <div class="flex flex-wrap gap-2"><button class="rk-btn" [disabled]="busy" (click)="queue(s.id)">Sync now</button><button class="rk-btn" [disabled]="busy" (click)="queue(s.id,true)">Reindex</button><button class="rk-btn" (click)="showRuns(s.id)">History</button><button class="rk-btn" [disabled]="busy" (click)="disable(s.id)">Disable</button><button class="rk-btn" [disabled]="busy" (click)="remove(s.id)">Delete</button></div></div>
      <p *ngIf="s.lastError" role="alert" class="text-sm text-rose-700">{{s.lastError}}</p>
      <div *ngIf="expandedSource===s.id" class="rounded-lg bg-slate-50 p-3"><h3 class="font-semibold">Recent synchronization runs</h3>
        <p *ngIf="!runs.length" class="text-sm text-slate-500">No runs have executed yet.</p>
        <ul><li *ngFor="let r of runs" class="text-xs py-2 border-b">{{r.startedAt | date:'medium'}} — {{r.state}} · added {{r.inserted}}, updated {{r.updated}}, removed {{r.deleted}} <span *ngIf="r.error">· {{r.error}}</span></li></ul></div>
    </li></ul>
  </section>
  <section class="rk-card p-5 space-y-3"><h2 class="font-bold text-xl">Restricted connector search</h2>
    <p class="text-sm text-slate-500">Search is admin-only and scoped to your tenant. Imported content is not exposed to public RAG or knowledge portal pages.</p>
    <form class="flex gap-2" (ngSubmit)="search()"><input name="searchQuery" class="rk-input flex-1" [(ngModel)]="searchQuery" maxlength="200" placeholder="Search connected documents" aria-label="Search connector documents"/><button type="submit" class="rk-btn rk-btn-primary">Search</button></form>
    <ul class="divide-y"><li *ngFor="let result of results" class="py-2"><strong>{{result.title}}</strong> <span class="text-xs text-slate-500">{{result.provider}}</span><p class="text-sm">{{result.excerpt}}</p><a *ngIf="result.url" class="text-sm text-blue-700 underline" [href]="result.url" target="_blank" rel="noopener noreferrer">Open original</a></li></ul>
  </section>
</main>`})
export class ConnectorsComponent implements OnInit {
  private api=inject(ApiService);catalog:any[]=[];connections:any[]=[];sources:any[]=[];
  selectedProvider='';remoteItems:any[]=[];nextCursor:string|null=null;loadingRemote=false;busy=false;
  syncInterval=60;expandedSource='';runs:any[]=[];results:any[]=[];searchQuery='';error='';notice='';
  ngOnInit(){this.reload();}
  isConnected(id:string){return this.connections.some(c=>c.provider===id&&c.connected);}
  reload(){this.api.connectorCatalog().subscribe({next:v=>this.catalog=v,error:()=>this.error='Could not load connector catalog. Admin role is required.'});
    this.api.oauthConnections().subscribe({next:v=>this.connections=v,error:()=>this.error='Could not load OAuth connections.'});
    this.api.connectorSources().subscribe({next:v=>this.sources=v,error:()=>this.error='Could not load sources.'});}
  connect(id:string){this.busy=true;this.error='';this.api.oauthAuthorize(id).subscribe({next:v=>window.location.assign(v.url),error:()=>{this.busy=false;this.error='Authorization failed; verify OAuth callback and provider configuration.';}});}
  disconnect(id:string){if(!confirm('Disconnect '+id+' and permanently remove its imported source documents?'))return;this.busy=true;this.api.oauthDisconnect(id).subscribe({next:()=>{this.busy=false;this.notice='Account disconnected and imported sources removed.';this.reload();},error:()=>{this.busy=false;this.error='Could not disconnect.';}});}
  browse(id:string){this.selectedProvider=id;this.remoteItems=[];this.nextCursor=null;this.fetch(undefined);}
  private fetch(cursor?:string){this.loadingRemote=true;this.error='';this.api.connectorBrowse(this.selectedProvider,cursor).subscribe({next:v=>{this.remoteItems=[...this.remoteItems,...v.items];this.nextCursor=v.nextCursor;this.loadingRemote=false;},error:()=>{this.loadingRemote=false;this.error='Browse failed. Check permissions and reconnect the provider if the token expired.';}});}
  enter(cursor?:string){this.remoteItems=[];this.nextCursor=null;this.fetch(cursor);}
  loadMore(){if(this.nextCursor)this.fetch(this.nextCursor);}
  select(item:any){this.busy=true;this.api.connectorSelect(this.selectedProvider,item.id,item.name,this.syncInterval).subscribe({next:()=>{this.busy=false;this.notice='Source selected; its first synchronization is queued.';this.reload();},error:()=>{this.busy=false;this.error='Unable to add source.';}});}
  queue(id:string,reindex=false){this.busy=true;(reindex?this.api.connectorReindex(id):this.api.connectorSync(id)).subscribe({next:()=>{this.busy=false;this.notice='Synchronization queued.';this.reload();},error:()=>{this.busy=false;this.error='Unable to queue sync.';}});}
  showRuns(id:string){if(this.expandedSource===id){this.expandedSource='';return;}this.expandedSource=id;this.api.connectorRuns(id).subscribe({next:v=>this.runs=v,error:()=>this.error='Failed to load sync history.'});}
  disable(id:string){if(!confirm('Disable this source and stop scheduled sync?'))return;this.busy=true;this.api.connectorDisable(id).subscribe({next:()=>{this.busy=false;this.reload();},error:()=>{this.busy=false;this.error='Unable to disable source.';}});}
  remove(id:string){if(!confirm('Permanently delete the selected source and its indexed documents?'))return;this.busy=true;this.api.connectorRemove(id).subscribe({next:()=>{this.busy=false;this.reload();},error:()=>{this.busy=false;this.error='Unable to delete source.';}});}
  search(){this.api.connectorSearch(this.searchQuery).subscribe({next:v=>this.results=v,error:()=>this.error='Search failed.'});}
}
