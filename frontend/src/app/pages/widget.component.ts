import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";

@Component({selector:"rk-widget",standalone:true,imports:[CommonModule,FormsModule],template:`
<div class="h-full min-h-screen bg-transparent p-2">
 <section class="mx-auto flex h-[600px] max-w-[370px] flex-col overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-2xl">
  <header class="border-b border-slate-200 bg-slate-950 px-5 py-4 text-white" [style.background-color]="brandColor||'#0f172a'">
   <p class="font-bold">{{brandTitle||'Support'}}</p><p class="text-xs text-slate-300">Ask a question about our knowledge base</p>
  </header>
  <main class="flex-1 space-y-3 overflow-y-auto p-4">
   <p *ngIf="handoff" class="text-amber-700 text-sm">This answer may need human help.</p>
   <div *ngIf="citations.length" class="text-xs"><strong>Knowledge sources:</strong> <a *ngFor="let slug of citations" class="underline mr-2" [href]="'/portal/article/'+encodeURIComponent(slug)+'?tenant='+encodeURIComponent(tenant)" target="_blank" rel="noopener">{{slug}}</a></div>
   <div *ngFor="let m of messages" [class.ml-8]="m.role==='user'" [class.bg-slate-100]="m.role==='bot'" [class.bg-slate-950]="m.role==='user'" [class.text-white]="m.role==='user'" class="rounded-2xl p-3 text-sm leading-6">{{m.text}}</div>
   <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
    <p class="font-semibold">Need more help?</p><p class="mt-1 text-xs text-slate-500">Can't find what you're looking for? Contact our support team.</p>
    <input class="rk-input mt-3" [(ngModel)]="email" name="supportEmail" type="email" placeholder="you@example.com" autocomplete="email">
    <button type="button" class="rk-btn rk-btn-primary mt-2 w-full" (click)="sendTicket()" [disabled]="busy || !email.trim()">{{busy?'Sending…':'Contact support'}}</button>
    <p *ngIf="sent" class="mt-2 text-xs text-emerald-700">Your request has been sent.</p>
   </div>
  </main>
  <label class="px-4 text-xs">Answer language <select class="rk-input ml-2" [(ngModel)]="language" [ngModelOptions]="{standalone:true}"><option value="en">English</option><option value="hi">Hindi</option><option value="fr">French</option><option value="es">Spanish</option><option value="de">German</option></select></label>
  <form class="flex gap-2 border-t border-slate-200 p-3" (ngSubmit)="ask()">
   <input class="rk-input min-w-0" [(ngModel)]="query" name="q" placeholder="Ask anything…" [disabled]="busy">
   <button class="rk-btn rk-btn-primary" [disabled]="busy||!query.trim()">Send</button>
  </form>
 </section>
</div>`})
export class WidgetComponent implements OnInit {
 brandTitle='';brandColor='';
 ngOnInit(){if(this.tenant)this.api.tenantLookup(this.tenant).subscribe({next:t=>{this.brandTitle=t.portalTitle||t.name;this.brandColor=t.primaryColor||'';},error:()=>undefined});}
 query="";language="en";
 email=""; 
 busy=false; 
 handoff=false; 
 sent=false;
 citations:string[]=[];conversationId:string|null=null;
 encodeURIComponent=encodeURIComponent; 
 
 sessionId = this.generateSessionId();

 messages:{role:"user"|"bot";text:string}[]=[{role:"bot",text:"Hi! How can I help?"}];
 constructor(private readonly api:ApiService){}
 get tenant(){return new URLSearchParams(window.location.search).get("tenant")||"";}
 ask(){const q=this.query.trim();if(!q||!this.tenant)return;this.messages.push({role:"user",text:q});this.query="";this.busy=true;this.api.widgetAnswer(this.tenant,q,this.sessionId,this.language,this.conversationId).subscribe({next:r=>{this.messages.push({role:"bot",text:r.answer});this.handoff=r.needsHandoff;this.citations=r.articleSlugs||[];this.conversationId=r.conversationId||null;this.busy=false;},error:()=>{this.messages.push({role:"bot",text:"I’m unable to answer right now. Please contact support."});this.handoff=true;this.busy=false;}})}
 sendTicket(){const email=this.email.trim();if(!email)return;const conversation=this.messages.map(m=>`${m.role==="user"?"Customer":"RicozKnow"}: ${m.text}`).join("\n\n");const q=this.messages.filter(x=>x.role==="user").slice(-1)[0]?.text||"Support request";this.busy=true;this.api.supportTicket({email,subject:q.slice(0,120),description:conversation,conversationId:this.conversationId}).subscribe({next:()=>{this.sent=true;this.busy=false;},error:()=>this.busy=false});}


    private generateSessionId(): string {
        // Use crypto.randomUUID when available
        if (
            typeof crypto !== 'undefined' &&
            typeof crypto.randomUUID === 'function'
        ) {
            return crypto.randomUUID();
        }

        // Fallback for browsers / non-secure contexts
        if (
            typeof crypto !== 'undefined' &&
            typeof crypto.getRandomValues === 'function'
        ) {
            const bytes = new Uint8Array(16);
            crypto.getRandomValues(bytes);

            // RFC 4122 version 4
            bytes[6] = (bytes[6] & 0x0f) | 0x40;
            bytes[8] = (bytes[8] & 0x3f) | 0x80;

            return [
            this.bytesToHex(bytes.slice(0, 4)),
            this.bytesToHex(bytes.slice(4, 6)),
            this.bytesToHex(bytes.slice(6, 8)),
            this.bytesToHex(bytes.slice(8, 10)),
            this.bytesToHex(bytes.slice(10, 16))
            ].join('-');
        }

        // Last-resort fallback
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(
            /[xy]/g,
            (c) => {
            const r = Math.random() * 16 | 0;
            const v = c === 'x'
                ? r
                : (r & 0x3) | 0x8;

            return v.toString(16);
            }
        );
        }

        private bytesToHex(bytes: Uint8Array): string {
        return Array.from(bytes)
            .map((byte) => byte.toString(16).padStart(2, '0'))
            .join('');
        }
}
