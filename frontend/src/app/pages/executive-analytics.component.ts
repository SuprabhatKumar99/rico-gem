import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';

@Component({selector:'rk-executive-analytics',standalone:true,imports:[CommonModule,FormsModule],template:`
<div class="space-y-6">
<header class="flex flex-wrap gap-4 items-end justify-between"><div><p class="text-sm font-semibold text-slate-500">Analytics / Phase 7</p><h1 class="text-3xl font-bold">Business intelligence</h1><p class="text-sm text-slate-500 mt-2">Tenant-isolated operational metrics; figures are not financial forecasts.</p></div>
<div class="flex gap-2"><select class="rk-select" [(ngModel)]="days" aria-label="Analytics period"><option [ngValue]="7">7 days</option><option [ngValue]="30">30 days</option><option [ngValue]="90">90 days</option><option [ngValue]="365">365 days</option></select><button class="rk-btn rk-btn-primary" (click)="load()">Refresh</button></div></header>
<p *ngIf="loading" role="status">Loading metrics…</p><p *ngIf="error" role="alert" class="text-rose-700">{{error}}</p>
<div *ngIf="data" class="space-y-6">
<div class="grid grid-cols-2 xl:grid-cols-4 gap-4">
 <div *ngFor="let m of metrics" class="rk-card p-4"><p class="text-xs text-slate-500">{{m.label}}</p><p class="text-2xl font-bold mt-2">{{m.value}}</p></div>
</div>
<div class="grid lg:grid-cols-2 gap-6">
<section *ngFor="let group of groups" class="rk-card p-5"><h2 class="text-lg font-bold mb-4">{{group.title}}</h2><dl class="space-y-3"><div *ngFor="let entry of group.entries" class="flex items-center justify-between gap-3 border-b border-slate-100 pb-2"><dt class="text-sm text-slate-600">{{entry[0]}}</dt><dd class="font-bold">{{entry[1]}}</dd></div></dl></section>
</div>
<section class="rk-card p-5"><h2 class="text-lg font-bold mb-4">AI provider performance and cost</h2><p class="text-xs text-slate-500 mb-3">USD amounts are estimates only when model pricing is configured; blanks mean unknown, not zero.</p>
<p *ngIf="!data.providerCosts?.length" class="text-slate-500 text-sm">No AI calls in this period.</p><div *ngIf="data.providerCosts?.length" class="overflow-x-auto"><table class="w-full text-sm text-left"><thead><tr><th>Provider</th><th>Model</th><th>Calls</th><th>Tokens</th><th>Latency</th><th>Est. USD</th></tr></thead><tbody><tr *ngFor="let p of data.providerCosts" class="border-t border-slate-100"><td class="py-2">{{p.provider}}</td><td>{{p.model}}</td><td>{{p.requests}}</td><td>{{(+p.input_tokens)+(+p.output_tokens)}}</td><td>{{p.latency_ms}} ms</td><td>{{p.estimated_cost_usd ?? 'Not configured'}}</td></tr></tbody></table></div></section>
<section class="rk-card p-5"><h2 class="text-lg font-bold mb-2">Daily article engagement</h2><p class="text-xs text-slate-500 mb-4">Nightly rollups; today may be incomplete.</p><p *ngIf="!daily.length" class="text-sm text-slate-500">No daily statistics yet.</p><div *ngFor="let row of daily" class="flex items-center gap-3 py-1 text-xs"><span class="w-24">{{row.day}}</span><div class="h-3 flex-1 bg-slate-100 rounded"><div class="h-full bg-blue-600 rounded" [style.width.%]="(+row.article_views)/maxViews*100"></div></div><span>{{row.article_views}}</span></div></section>
<p class="text-xs text-slate-500">Search effectiveness and deflection are behavior-derived proxies; evaluation hits do not establish factual answer correctness. Paid invoice totals are received invoice amounts, not recognized revenue or ARR.</p>
</div></div>
`})
export class ExecutiveAnalyticsComponent implements OnInit {
 days=30;loading=false;error='';data:any=null;daily:any[]=[];
 constructor(private api:ApiService){}
 ngOnInit(){this.load();}
 load(){this.loading=true;this.error='';this.api.executiveAnalytics(this.days).subscribe({next:v=>{this.data=v;this.loading=false;},error:()=>{this.loading=false;this.error='Analytics could not be loaded';}});this.api.executiveDaily(this.days).subscribe({next:v=>this.daily=v,error:()=>this.daily=[]});}
 get maxViews(){return Math.max(1,...this.daily.map(d=>+d.article_views));}
 get metrics(){if(!this.data)return [];let d=this.data;return [
 {label:'Article views',value:d.knowledge.articleViews},{label:'Search queries',value:d.search.queries},
 {label:'AI requests',value:d.ai.requests},{label:'Support tickets',value:d.support.tickets},
 {label:'Published articles',value:d.knowledge.published},{label:'AI evaluation cases',value:d.ai.evaluated},
 {label:'Successful connector syncs',value:d.connectors.completed},{label:'Active staff (tracked)',value:d.productivity.activeStaff}
 ];}
 get groups(){if(!this.data)return [];const d=this.data;return [
 {title:'Content quality and usage',entries:[['Helpful feedback',d.knowledge.helpful],['Unhelpful feedback',d.knowledge.unhelpful],['Unverified published',d.knowledge.unverified],['New articles',d.productivity.newArticles]]},
 {title:'Search effectiveness',entries:[['Zero-result searches',d.search.zeroResults],['Unsuccessful searches',d.search.unsuccessful],['Searches',d.search.queries]]},
 {title:'AI quality proxies',entries:[['Evaluation hits',d.ai.evaluationHits],['Evaluation cases',d.ai.evaluated],['AI failures',d.ai.failures]]},
 {title:'Support and connected sources',entries:[['Resolved tickets',d.support.resolved],['Estimated deflections',d.support.estimatedDeflection],['Connector failures',d.connectors.failures],['Active connector sources',d.connectors.sources]]},
 {title:'SaaS activity',entries:[['Staff seats',d.productivity.staffSeats],['Plan',d.subscription?.[0]?.plan_code||'—'],['Billing status',d.subscription?.[0]?.status||'—'],['Paid USD invoice cents',d.paidInvoicesMinor]]}
 ];}
}
