import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface Rule { id:string; name:string; triggerType:string; actionType:string; enabled:boolean;articleIdFilter?:string|null;cooldownMinutes:number;maxAttempts:number; }
interface Run {id:string;event_key:string;state:string;attempts:number;last_error:string|null;created_at:string;}
@Component({standalone:true, selector:'rk-automation', imports:[CommonModule, FormsModule], template:`
<main class="space-y-5">
  <header><h1 class="text-3xl font-bold">Knowledge automation</h1>
  <p class="text-slate-600">Define tenant-scoped rules, safely preview actions, and enable approved rules. Enabled rules execute asynchronously and are recorded in the execution history.</p></header>
  <p *ngIf="error" role="alert" class="rounded bg-rose-50 p-3 text-rose-700">{{error}}</p>
  <p *ngIf="message" role="status" class="rounded bg-blue-50 p-3 text-blue-800">{{message}}</p>
  <form class="rk-card p-5 space-y-3" (ngSubmit)="create()">
    <h2 class="font-bold">New automation rule</h2>
    <label class="block">Rule name<input name="name" class="rk-input w-full" [(ngModel)]="name" maxlength="120" required /></label>
    <label class="block">Trigger<select name="trigger" class="rk-input w-full" [(ngModel)]="trigger">
      <option *ngFor="let value of triggers" [value]="value">{{value}}</option></select></label>
    <label class="block">Action<select name="action" class="rk-input w-full" [(ngModel)]="action">
      <option *ngFor="let value of actions" [value]="value">{{value}}</option></select></label>
    <label class="block">Only for article ID (optional)<input name="filter" class="rk-input w-full" [(ngModel)]="articleIdFilter" placeholder="Article UUID"></label>
    <label class="block">Cooldown (minutes)<input type="number" name="cooldown" min="0" max="10080" class="rk-input" [(ngModel)]="cooldownMinutes"></label>
    <button type="submit" class="rk-btn rk-btn-primary" [disabled]="saving||!name.trim()">Create disabled rule</button>
  </form>
  <section class="rk-card p-5 space-y-3"><h2 class="font-bold">Sensitive content preview</h2>
    <p class="text-sm text-slate-600">Local server-side heuristic scan, not a complete DLP policy. Nothing is saved.</p>
    <textarea class="rk-input w-full" rows="3" maxlength="200000" [(ngModel)]="scanText" aria-label="Text to scan"></textarea>
    <button class="rk-btn" (click)="scan()" [disabled]="!scanText.trim()">Scan text</button>
    <ul><li *ngFor="let finding of findings">{{finding.severity}}: {{finding.detail}}</li></ul>
    <p *ngIf="scanComplete && !findings.length">No matching patterns found (not a guarantee of safety).</p>
  </section>
  <section class="rk-card p-5 space-y-3"><h2 class="font-bold">Configured rules</h2>
    <p *ngIf="!rules.length" class="text-slate-500">No rules configured.</p>
    <div *ngFor="let rule of rules" class="rounded border p-3 flex flex-wrap gap-3 justify-between items-center">
      <div><p class="font-semibold">{{rule.name}}</p><p class="text-sm">{{rule.triggerType}} → {{rule.actionType}}</p>
        <p class="text-xs text-slate-500">{{rule.enabled?'Enabled':'Disabled'}}</p></div>
      <div class="flex gap-2"><button class="rk-btn" (click)="preview(rule)">Preview</button>
        <button class="rk-btn" (click)="toggle(rule)">{{rule.enabled?'Disable':'Enable'}}</button>
        <button class="rk-btn" (click)="history(rule)">Executions</button>
        <button class="rk-btn" (click)="remove(rule)">Delete</button></div>
    </div>
  </section>
  <section class="rk-card p-5 space-y-2" *ngIf="activeRule"><h2 class="font-bold">Execution history · {{activeRule.name}}</h2>
   <p *ngIf="!runs.length">No executions yet.</p>
   <div *ngFor="let run of runs" class="border rounded p-3 flex justify-between gap-3"><div><p>{{run.event_key}}</p><p class="text-sm">{{run.state}} · Attempts: {{run.attempts}} · {{run.created_at | date:'short'}}</p><p class="text-rose-600" *ngIf="run.last_error">{{run.last_error}}</p></div><button *ngIf="run.state==='DEAD'" class="rk-btn" (click)="retry(run)">Retry</button></div>
  </section>
</main>`})
export class AutomationComponent implements OnInit {
  private http=inject(HttpClient); private readonly url=(localStorage.getItem('rk_api_url')?.replace(/\/$/, '') || '')+'/api/v1/automation/rules';
  scanText='';findings:{severity:string;detail:string}[]=[];scanComplete=false;
  scan(){this.http.post<{severity:string;detail:string}[]>((localStorage.getItem('rk_api_url')?.replace(/\/$/, '') || '')+'/api/v1/knowledge-security/scan',{content:this.scanText}).subscribe({next:v=>{this.findings=v;this.scanComplete=true;},error:()=>this.error='Scan request failed.'});}
  activeRule:Rule|null=null;runs:Run[]=[];
  history(rule:Rule){this.activeRule=rule;this.http.get<Run[]>(this.url+'/'+rule.id+'/executions').subscribe({next:v=>this.runs=v,error:()=>this.error='Execution history failed.'});}
  retry(run:Run){if(!this.activeRule)return;this.http.post(this.url+'/'+this.activeRule.id+'/executions/'+run.id+'/retry',{}).subscribe({next:()=>{this.message='Job queued for retry.';this.history(this.activeRule!);},error:()=>this.error='Retry failed.'});}
  rules:Rule[]=[];articleIdFilter='';cooldownMinutes=0;name='';trigger='ARTICLE_UPDATED';action='FLAG_FOR_REVIEW';saving=false;error='';message='';
  triggers=['ARTICLE_CREATED','ARTICLE_UPDATED','REVIEW_OVERDUE','CONNECTOR_FAILED'];
  actions=['FLAG_FOR_REVIEW','QUEUE_NOTIFICATION','AUDIT_ONLY'];
  ngOnInit(){this.load();}
  load(){this.http.get<Rule[]>(this.url).subscribe({next:v=>this.rules=v,error:()=>this.error='Could not load automation rules.'});}
  create(){if(!this.name.trim())return;this.saving=true;this.http.post<Rule>(this.url,{name:this.name,triggerType:this.trigger,actionType:this.action,enabled:false,articleIdFilter:this.articleIdFilter||null,cooldownMinutes:this.cooldownMinutes,maxAttempts:5}).subscribe({next:()=>{this.saving=false;this.name='';this.articleIdFilter='';this.message='Rule saved in disabled state.';this.load();},error:()=>{this.saving=false;this.error='Could not create rule.';}});}
  preview(rule:Rule){this.http.post<{explanation:string;wouldExecute:boolean}>(this.url+'/'+rule.id+'/preview',{}).subscribe({next:v=>this.message=v.explanation,error:()=>this.error='Preview failed.'});}
  toggle(rule:Rule){this.http.put(this.url+'/'+rule.id,{name:rule.name,triggerType:rule.triggerType,actionType:rule.actionType,enabled:!rule.enabled,articleIdFilter:rule.articleIdFilter||null,cooldownMinutes:rule.cooldownMinutes,maxAttempts:rule.maxAttempts}).subscribe({next:()=>{this.message='Rule updated. Enabled rules execute when trusted triggers occur.';this.load();},error:()=>this.error='Unable to update rule.'});}
  remove(rule:Rule){if(!confirm('Delete this rule?'))return;this.http.delete(this.url+'/'+rule.id).subscribe({next:()=>this.load(),error:()=>this.error='Unable to delete rule.'});}
}
