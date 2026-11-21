import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
@Component({standalone:true,selector:'rk-portal-history',imports:[CommonModule,RouterLink],template:`
<main class="max-w-4xl mx-auto p-5 space-y-5"><h1 class="text-3xl font-bold">Your support history</h1>
 <a [routerLink]="['/portal']" [queryParams]="{tenant:tenant}" class="underline">Back to knowledge base</a>
 <p>Only support conversations and tickets associated with your signed-in customer account appear here.</p>
 <p role="alert" *ngIf="error" class="text-rose-700">{{error}}</p>
 <section class="rk-card p-5 space-y-2"><h2 class="font-bold">Conversations</h2><p *ngIf="!conversations.length">No saved conversations. Sign in before chatting to save history.</p>
  <button *ngFor="let item of conversations" class="rk-btn block" (click)="loadMessages(item.id)">{{item.created_at | date:'short'}} · {{item.language}}</button>
  <div *ngIf="messages.length" class="space-y-2 border-t pt-3"><div *ngFor="let m of messages" class="rounded bg-slate-50 p-3"><strong>{{m.role}}</strong><p class="whitespace-pre-wrap">{{m.content}}</p></div></div>
 </section>
 <section class="rk-card p-5 space-y-2"><h2 class="font-bold">Your support tickets</h2>
  <p *ngIf="!tickets.length">No support tickets found.</p><div *ngFor="let t of tickets" class="border rounded p-3"><strong>{{t.subject}}</strong> · {{t.status}} · {{t.created_at | date:'short'}}</div>
 </section></main>`})
export class PortalHistoryComponent implements OnInit {
 private readonly http=inject(HttpClient);private readonly route=inject(ActivatedRoute);
 private readonly base=(localStorage.getItem('rk_api_url')?.replace(/\/$/,'')||'')+'/api/v1/portal';
 conversations:any[]=[];messages:any[]=[];tickets:any[]=[];error='';
 get tenant(){return this.route.snapshot.queryParamMap.get('tenant')||'';}
 ngOnInit(){const params={tenant:this.tenant};this.http.get<any[]>(this.base+'/conversations',{params,withCredentials:true}).subscribe({next:v=>this.conversations=v,error:()=>this.error='Sign in to view your conversations.'});
 this.http.get<any[]>(this.base+'/support/my-tickets',{params,withCredentials:true}).subscribe({next:v=>this.tickets=v,error:()=>this.error='Sign in to view support tickets.'});}
 loadMessages(id:string){this.http.get<any[]>(this.base+'/conversations/'+id+'/messages',{params:{tenant:this.tenant},withCredentials:true}).subscribe({next:v=>this.messages=v,error:()=>this.error='Unable to read conversation.'});}
}
