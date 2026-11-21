import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ApiService } from '../core/api.service';

type Plan = {code:string;name:string;monthlyPriceMinor:number;currency:string;maxSeats:number;maxArticles:number;maxConnectors:number;maxAiRequests:number;contactSales:boolean};
type Subscription = {plan:string;status:string;periodEnd:string|null;trialEnd:string|null;cancelAtPeriodEnd:boolean};
type Overview = {subscription:Subscription;effectivePlan:string;limits:Record<string,number>;usage:Record<string,number>};
type Invoice = {id:string;status:string;currency:string;due:number;paid:number;url:string|null;paidAt:string|null};

@Component({
 selector:'rk-billing',standalone:true,imports:[CommonModule],
 template:`
 <div class="space-y-6">
   <header class="flex flex-wrap items-center justify-between gap-4">
    <div><p class="text-sm font-semibold text-slate-500">Administration / Billing</p>
      <h1 class="text-3xl font-bold">Subscription &amp; usage</h1><p class="text-sm text-slate-500 mt-2">Plans and invoices are enforced and reconciled on the server.</p></div>
    <button type="button" class="rk-btn" (click)="load()">Refresh</button>
   </header>
   <p *ngIf="loading" role="status">Loading billing data…</p>
   <div *ngIf="error" role="alert" class="rounded-lg border border-rose-300 p-4 text-rose-700">{{error}}</div>
   <section *ngIf="overview as data" class="rk-card p-5 space-y-3">
    <h2 class="font-semibold text-xl">Current subscription</h2>
    <p><strong>{{data.effectivePlan}}</strong> · {{data.subscription.status}} <span *ngIf="data.subscription.cancelAtPeriodEnd">· Cancels at period end</span></p>
    <p *ngIf="data.subscription.trialEnd">Trial ends {{data.subscription.trialEnd | date:'mediumDate'}}</p>
    <p *ngIf="data.subscription.periodEnd">Current paid period ends {{data.subscription.periodEnd | date:'mediumDate'}}</p>
    <div class="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
      <div class="rounded-lg bg-slate-50 p-3">Seats <strong class="block">{{data.usage['seats']}} / {{formatLimit(data.limits['max_seats'])}}</strong></div>
      <div class="rounded-lg bg-slate-50 p-3">Articles <strong class="block">{{data.usage['articles']}} / {{formatLimit(data.limits['max_articles'])}}</strong></div>
      <div class="rounded-lg bg-slate-50 p-3">Connectors <strong class="block">{{data.usage['connectors']}} / {{formatLimit(data.limits['max_connectors'])}}</strong></div>
      <div class="rounded-lg bg-slate-50 p-3">AI requests/month <strong class="block">{{data.usage['aiRequestsThisMonth']}} / {{formatLimit(data.limits['max_ai_requests'])}}</strong></div>
    </div>
    <div class="flex flex-wrap gap-2">
      <button type="button" class="rk-btn rk-btn-primary" (click)="openPortal()" [disabled]="busy || !data.subscription">Manage payments and plan</button>
      <button *ngIf="data.subscription.status==='ACTIVE' && !data.subscription.cancelAtPeriodEnd" type="button" class="rk-btn" (click)="cancel()" [disabled]="busy">Cancel at period end</button>
      <button *ngIf="data.subscription.cancelAtPeriodEnd" type="button" class="rk-btn" (click)="resume()" [disabled]="busy">Request cancellation reversal</button>
    </div>
    <p class="text-xs text-slate-500">Subscription status changes only after verified payment-provider webhooks, not after a checkout redirect.</p>
   </section>
   <section class="space-y-3"><h2 class="text-xl font-bold">Available plans</h2>
    <div class="grid md:grid-cols-3 gap-4">
      <div *ngFor="let p of plans" class="rk-card p-5 space-y-3">
       <h3 class="text-xl font-bold">{{p.name}}</h3><p class="text-2xl font-semibold">{{p.contactSales ? 'Contact sales' : (p.monthlyPriceMinor===0 ? 'Free' : (p.monthlyPriceMinor/100 | currency:p.currency.toUpperCase()) + '/mo')}}</p>
       <p class="text-sm text-slate-600">{{formatLimit(p.maxSeats)}} seats · {{formatLimit(p.maxArticles)}} articles · {{formatLimit(p.maxConnectors)}} connectors</p>
       <p class="text-sm text-slate-600">{{formatLimit(p.maxAiRequests)}} AI requests/month</p>
       <button type="button" class="rk-btn rk-btn-primary" [disabled]="busy || p.contactSales || p.monthlyPriceMinor===0 || overview?.effectivePlan===p.code" (click)="checkout(p.code)">Choose plan</button>
      </div>
    </div><p class="text-xs text-slate-500">Listed amounts are deployment-configured plan examples until Stripe Price IDs are provisioned and verified.</p>
   </section>
   <section class="rk-card p-5"><h2 class="text-xl font-bold mb-3">Invoices</h2>
    <p *ngIf="!invoices.length" class="text-sm text-slate-500">No reconciled provider invoices.</p>
    <div class="overflow-x-auto" *ngIf="invoices.length"><table class="w-full text-sm text-left"><thead><tr><th class="py-2">Invoice ID</th><th>Status</th><th>Amount paid</th><th>Paid</th><th>Details</th></tr></thead><tbody>
      <tr *ngFor="let invoice of invoices" class="border-t border-slate-100"><td class="py-3">{{invoice.id}}</td><td>{{invoice.status}}</td><td>{{invoice.paid / 100 | currency:invoice.currency.toUpperCase()}}</td><td>{{invoice.paidAt | date:'mediumDate'}}</td><td><a *ngIf="invoice.url" [href]="invoice.url" target="_blank" rel="noopener noreferrer" class="underline">View invoice</a></td></tr>
    </tbody></table></div>
   </section>
 </div>`
})
export class BillingComponent implements OnInit {
 loading=false; busy=false; error=''; overview:Overview|null=null; plans:Plan[]=[]; invoices:Invoice[]=[];
 constructor(private api:ApiService){}
 ngOnInit(){this.load();}
 formatLimit(value?:number){return value===0?'Unlimited':String(value??'—');}
 load(){this.loading=true;this.error='';this.api.billingOverview().subscribe({next:d=>{this.overview=d;this.loading=false;},error:e=>{this.error=e.error?.message||'Billing overview unavailable';this.loading=false;}});
   this.api.billingPlans().subscribe({next:r=>this.plans=r,error:()=>{this.error='Cannot load billing plans';}});
   this.api.billingInvoices().subscribe({next:r=>this.invoices=r,error:()=>{this.invoices=[];}});
 }
 private redirect(url:string){try{const u=new URL(url);if(u.protocol==='https:' && (u.hostname==='checkout.stripe.com'||u.hostname==='billing.stripe.com'||u.hostname.endsWith('.stripe.com')))window.location.assign(url);else throw Error('Invalid billing URL');}catch{this.error='Untrusted billing redirect URL returned';}}
 checkout(plan:string){this.busy=true;this.error='';this.api.billingCheckout(plan).subscribe({next:r=>{this.busy=false;this.redirect(r.url);},error:e=>{this.busy=false;this.error=e.error?.message||'Checkout is unavailable. Confirm Stripe Price IDs and credentials.';}});}
 openPortal(){this.busy=true;this.api.billingPortal().subscribe({next:r=>{this.busy=false;this.redirect(r.url);},error:e=>{this.busy=false;this.error=e.error?.message||'Billing portal unavailable';}});}
 cancel(){if(!window.confirm('Cancel this subscription at the end of its paid period?'))return;this.busy=true;this.api.billingCancel(true).subscribe({next:()=>{this.busy=false;this.load();},error:()=>{this.busy=false;this.error='Unable to submit cancellation';}});}
 resume(){this.busy=true;this.api.billingCancel(false).subscribe({next:()=>{this.busy=false;this.load();},error:()=>{this.busy=false;this.error='Unable to change cancellation';}});}
}
