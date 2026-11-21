import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
interface Proposal {id:string;source_article_id:string;target_language:string;translated_title:string;translated_content:string;state:string;created_at:string;}
@Component({standalone:true,selector:'rk-translation-proposals',imports:[CommonModule,FormsModule],template:`
<main class="space-y-5">
 <h1 class="text-3xl font-bold">Translation approvals</h1>
 <p>Generate a translation suggestion for a published article. A different reviewer must approve it; approval creates a private draft for editing and publication through the usual workflow.</p>
 <p *ngIf="error" role="alert" class="rounded bg-rose-50 p-3 text-rose-700">{{error}}</p><p *ngIf="message" role="status" class="rounded bg-blue-50 p-3">{{message}}</p>
 <form class="rk-card p-5 space-y-3" (ngSubmit)="generate()"><h2 class="font-semibold">Generate proposal</h2>
  <label class="block">Published article ID<input class="rk-input w-full" name="articleId" [(ngModel)]="articleId" required></label>
  <label class="block">Target language<input class="rk-input w-full" name="language" [(ngModel)]="language" maxlength="16" required placeholder="fr"></label>
  <button class="rk-btn rk-btn-primary" [disabled]="busy||!articleId||!language">{{busy?'Generating…':'Generate proposal'}}</button>
 </form>
 <section class="rk-card p-5 space-y-4"><div class="flex justify-between"><h2 class="font-semibold">Translation proposals</h2><button class="rk-btn" (click)="load()">Refresh</button></div>
  <article *ngFor="let p of proposals" class="border rounded p-4 space-y-2">
   <h3 class="font-semibold">{{p.translated_title}} · {{p.target_language}}</h3><p class="text-xs">{{p.state}} · Source {{p.source_article_id}}</p>
   <details><summary>Review translated text</summary><p class="whitespace-pre-wrap max-h-64 overflow-y-auto p-3 text-sm bg-slate-50">{{p.translated_content}}</p></details>
   <div *ngIf="p.state==='PENDING'" class="flex gap-2"><button class="rk-btn rk-btn-primary" (click)="review(p,'APPROVED')" [disabled]="busy">Approve draft</button><button class="rk-btn" (click)="review(p,'REJECTED')" [disabled]="busy">Reject</button></div>
  </article><p *ngIf="!proposals.length">No proposals found.</p>
 </section>
</main>`})
export class TranslationProposalsComponent implements OnInit {
 private readonly http=inject(HttpClient);private readonly base=(localStorage.getItem('rk_api_url')?.replace(/\/$/,'')||'')+'/api/v1/translation-proposals';
 proposals:Proposal[]=[];articleId='';language='fr';busy=false;error='';message='';
 ngOnInit(){this.load();}
 load(){this.http.get<Proposal[]>(this.base).subscribe({next:v=>this.proposals=v,error:()=>this.error='Unable to load translations.'});}
 generate(){this.busy=true;this.http.post(this.base,{articleId:this.articleId,language:this.language}).subscribe({next:()=>{this.busy=false;this.message='Translation proposal saved for review.';this.load();},error:()=>{this.busy=false;this.error='Translation generation failed or an open proposal already exists.';}});}
 review(p:Proposal,decision:'APPROVED'|'REJECTED'){this.busy=true;this.http.post<{draftArticleId:string|null}>(this.base+'/'+p.id+'/review',{decision}).subscribe({next:v=>{this.busy=false;this.message=v.draftArticleId?'Private draft created: '+v.draftArticleId:'Proposal rejected.';this.load();},error:()=>{this.busy=false;this.error='Review failed. A different reviewer is required.';}});}
}
