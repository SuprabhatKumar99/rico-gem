import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
interface Ticket { id:string;email:string;subject:string;status:string;provider:string;created_at:string; }
@Component({standalone:true,selector:'rk-support-operations',imports:[CommonModule,FormsModule],template:`
<main class="space-y-5">
 <h1 class="text-3xl font-bold">Customer support operations</h1>
 <p class="text-sm text-slate-600">Manage escalated support requests and review an estimated ticket deflection metric.</p>
 <p *ngIf="error" role="alert" class="rounded bg-rose-50 p-3 text-rose-700">{{error}}</p>
 <p *ngIf="message" role="status" class="rounded bg-blue-50 p-3">{{message}}</p>
 <section class="rk-card p-5"><h2 class="font-semibold">Support overview</h2>
  <div *ngIf="insights" class="grid grid-cols-2 lg:grid-cols-4 gap-4 mt-3">
   <div><strong>{{insights.tickets}}</strong><div class="text-xs">Total tickets</div></div>
   <div><strong>{{insights.resolvedTickets}}</strong><div class="text-xs">Resolved</div></div>
   <div><strong>{{insights.conversations}}</strong><div class="text-xs">Signed-in conversations</div></div>
   <div><strong>{{insights.estimatedDeflectionRate | percent:'1.0-1'}}</strong><div class="text-xs">Estimated deflection</div></div>
  </div><p class="mt-3 text-xs text-slate-500">Estimate uses answered conversations and linked escalations; it does not capture phone/email contacts or prove avoided tickets.</p>
 </section>
 <section class="rk-card p-5 space-y-3"><div class="flex justify-between items-center"><h2 class="font-semibold">Recent tickets</h2><button class="rk-btn" (click)="load()">Refresh</button></div>
  <p *ngIf="!tickets.length">No tickets found.</p>
  <article *ngFor="let t of tickets" class="rounded border p-3 flex flex-wrap gap-3 items-center justify-between">
   <div><strong>{{t.subject}}</strong><p class="text-sm">{{t.email}} · {{t.provider}} · {{t.created_at | date:'short'}}</p></div>
   <label>Ticket status <select class="rk-input" [ngModel]="t.status" (ngModelChange)="change(t,$event)" [disabled]="saving">
    <option value="OPEN">Open</option><option value="IN_PROGRESS">In progress</option><option value="RESOLVED">Resolved</option><option value="CLOSED">Closed</option>
   </select></label>
  </article>
 </section>
</main>`})
export class SupportOperationsComponent implements OnInit {
 private readonly http=inject(HttpClient);
 private readonly base=(localStorage.getItem('rk_api_url')?.replace(/\/$/,'')||'')+'/api/v1/admin/support';
 tickets:Ticket[]=[];insights:any=null;error='';message='';saving=false;
 ngOnInit(){this.load();}
 load(){this.http.get<Ticket[]>(this.base+'/tickets').subscribe({next:v=>this.tickets=v,error:()=>this.error='Unable to load tickets.'});this.http.get<any>(this.base+'/insights').subscribe({next:v=>this.insights=v,error:()=>this.error='Unable to load metrics.'});}
 change(t:Ticket,status:string){this.saving=true;this.http.patch(this.base+'/tickets/'+t.id,{status}).subscribe({next:()=>{this.message='Ticket status updated.';this.saving=false;this.load();},error:()=>{this.saving=false;this.error='Unable to update ticket.';this.load();}});}
}
