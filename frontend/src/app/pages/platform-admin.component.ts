import { CommonModule } from '@angular/common';
import { Component,OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
@Component({selector:'rk-platform-admin',standalone:true,imports:[CommonModule,FormsModule],template:`
<div class="space-y-6"><header class="flex items-center justify-between"><div><p class="text-sm text-slate-500">Restricted platform operations</p><h1 class="text-3xl font-bold">SaaS operator overview</h1></div><button class="rk-btn" (click)="load()">Refresh</button></header>
<p *ngIf="error" role="alert" class="text-rose-700">{{error}}</p><p *ngIf="loading">Loading…</p>
<section *ngIf="result" class="rk-card p-5"><h2 class="text-xl font-bold mb-3">Tenants</h2><p class="text-sm text-slate-500 mb-4">At most 200 tenants. No impersonation or billing-state overrides are exposed.</p><div class="overflow-x-auto"><table class="w-full text-left text-sm"><thead><tr><th>Tenant</th><th>Plan</th><th>Billing</th><th>Seats</th></tr></thead><tbody><tr class="border-t" *ngFor="let t of result.tenants"><td class="py-2">{{t.name}} · {{t.slug}}</td><td>{{t.plan_code}}</td><td>{{t.billing_status}}</td><td>{{t.seats}}</td></tr></tbody></table></div>
<h2 class="text-xl font-bold mt-6 mb-2">Stripe price configuration</h2><p class="text-xs text-slate-500 mb-3">The Stripe Dashboard controls actual pricing. Configure matching Price IDs here; do not paste secret API keys.</p>
<div class="flex flex-wrap items-end gap-3"><label class="text-sm">Plan<select class="rk-select block" [(ngModel)]="plan"><option value="GROWTH">Growth</option><option value="BUSINESS">Business</option></select></label>
<label class="text-sm">Stripe Price ID<input class="rk-input block" [(ngModel)]="stripePrice" placeholder="price_..." /></label><button class="rk-btn" type="button" (click)="configure()">Save Price ID</button></div>
<p class="text-xs text-slate-500 mt-2" *ngFor="let p of result.plans">{{p.name}}: {{p.stripe_configured ? 'Configured' : 'Not configured'}}</p>
<h2 class="text-xl font-bold mt-6">Received invoice totals</h2><p class="text-xs text-slate-500">Grouped by currency; not an ARR estimate.</p><p *ngFor="let item of result.paidInvoiceTotals">{{item.currency | uppercase}}: {{item.received_minor}} minor units across {{item.paid_invoices}} invoices</p></section>
</div>`})
export class PlatformAdminComponent implements OnInit {
 plan='GROWTH';stripePrice='';result:any=null;error='';loading=false;constructor(private api:ApiService){}ngOnInit(){this.load();}
 configure(){this.api.platformConfigurePrice(this.plan,this.stripePrice.trim()).subscribe({next:()=>{this.stripePrice='';this.load();},error:()=>this.error='Price configuration failed. Check the value and operator permissions.'});}
 load(){this.loading=true;this.api.platformOverview().subscribe({next:r=>{this.result=r;this.loading=false;},error:()=>{this.error='Access denied or platform view unavailable. Platform operators need a server-side allowlist entry.';this.loading=false;}});}
}
