import { CommonModule } from "@angular/common";
import { Component, OnDestroy, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { ApiService } from "../core/api.service";
import { Category, SearchResult, Tenant } from "../models";

@Component({
  selector: "rk-portal-search",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
  <div class="min-h-screen bg-white">
    <header class="border-b border-slate-200">
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-5">
        <a routerLink="/portal" [queryParams]="{tenant:tenantSlug}" class="flex min-w-0 items-center gap-3">
          <img *ngIf="tenant?.logoUrl" [src]="tenant?.logoUrl" alt="" class="h-9 w-9 rounded-xl object-cover"/>
          <span *ngIf="!tenant?.logoUrl" class="grid h-9 w-9 shrink-0 place-items-center rounded-xl font-black text-white" [style.background]="tenant?.primaryColor || '#0f172a'">R</span>
          <span class="truncate font-bold">{{tenant?.portalTitle || tenant?.name || "Knowledge Portal"}}</span>
        </a>
        <a routerLink="/portal" [queryParams]="{tenant:tenantSlug}" class="rk-btn rk-btn-secondary">Knowledge base</a>
      </div>
    </header>

    <main class="mx-auto max-w-6xl px-4 py-8">
      <div class="relative">
        <form class="flex flex-col gap-2 sm:flex-row" (ngSubmit)="search()">
          <div class="relative flex-1">
            <input class="rk-input" [(ngModel)]="query" (ngModelChange)="onQueryChange()" name="query"
              placeholder="Ask a question or search the knowledge base…" autocomplete="off" />
            <div *ngIf="suggestionsOpen && suggestions.length"
              class="absolute left-0 right-0 top-full z-30 mt-2 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl">
              <button *ngFor="let item of suggestions" type="button"
                class="block w-full px-4 py-3 text-left text-sm hover:bg-slate-50"
                (mousedown)="query=item; suggestionsOpen=false; search()">{{item}}</button>
            </div>
          </div>
          <button class="rk-btn text-white" [style.background]="tenant?.primaryColor || '#0f172a'">Search</button>
        </form>
      </div>

      <div class="mt-6 grid gap-6 lg:grid-cols-[240px_1fr]">
        <aside class="rk-card h-fit p-4">
          <div class="flex items-center justify-between">
            <h2 class="font-bold">Filters</h2>
            <button type="button" class="text-xs font-semibold text-slate-500" (click)="resetFilters()">Reset</button>
          </div>
          <label class="rk-label mt-5">Category</label>
          <select class="rk-select" [(ngModel)]="filters.categoryId" (ngModelChange)="search()">
            <option value="">All categories</option><option *ngFor="let c of categories" [value]="c.id">{{c.name}}</option>
          </select>
          <label class="rk-label mt-4">Language</label>
          <select class="rk-select" [(ngModel)]="filters.language" (ngModelChange)="search()">
            <option value="">All languages</option>
            <option *ngFor="let f of result?.facets?.languages || []" [value]="f.value">{{f.value.toUpperCase()}}</option>
            <option *ngIf="!result?.facets?.languages?.length" value="en">EN</option>
          </select>
          <label class="rk-label mt-4">Article type</label>
          <select class="rk-select" [(ngModel)]="filters.articleType" (ngModelChange)="search()">
            <option value="">All types</option>
            <option *ngFor="let f of articleTypes" [value]="f">{{f}}</option>
          </select>
          <label class="rk-label mt-4">Tag</label>
          <select class="rk-select" [(ngModel)]="filters.tag" (ngModelChange)="search()">
            <option value="">All tags</option>
            <option *ngFor="let f of result?.facets?.tags || []" [value]="f.value">{{f.value}}</option>
          </select>
          <label class="rk-label mt-4">Published after</label>
          <input class="rk-input" type="date" [(ngModel)]="filters.dateFrom" (change)="search()"/>
          <label class="rk-label mt-4">Published before</label>
          <input class="rk-input" type="date" [(ngModel)]="filters.dateTo" (change)="search()"/>
          <label class="rk-label mt-4">Source</label>
          <select class="rk-select" [(ngModel)]="filters.sourceType" (ngModelChange)="search()">
            <option value="">All sources</option><option value="ARTICLE">Articles</option>
            <option value="PDF">PDF</option><option value="DOCX">DOCX</option><option value="URL">URL</option><option value="WEBSITE">Website</option>
          </select>
        </aside>

        <section>
          <div *ngIf="result" class="mb-5">
            <p class="text-sm text-slate-500"><strong>{{result.total}}</strong> results for “{{result.query}}”</p>
            <p *ngIf="result.rewrittenQuery && result.rewrittenQuery !== result.query" class="mt-2 rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-600">
              AI refined your query to <strong>{{result.rewrittenQuery}}</strong>
            </p>
          </div>

          <ng-container *ngIf="result as searchResult">
            <div *ngIf="searchResult.results?.length" class="space-y-3">
              <ng-container *ngFor="let item of searchResult.results; let i=index">
              <a *ngIf="item.articleId; else sourceResult"
                 [routerLink]="['/portal/article', item.slug]" [queryParams]="{tenant:tenantSlug}"
                 (click)="track(item.articleId!, i)"
                 class="block rounded-2xl border border-slate-200 p-5 transition hover:border-slate-300 hover:shadow-sm">
                <div class="flex flex-wrap gap-2 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  <span>Article</span><span *ngIf="item.articleType">{{item.articleType}}</span><span *ngIf="item.language">{{item.language}}</span>
                </div>
                <h2 class="mt-2 font-bold">{{item.title}}</h2>
                <p class="mt-2 text-sm leading-6 text-slate-600" [innerHTML]="item.excerpt"></p>
                <div class="mt-3 flex flex-wrap gap-2"><span *ngFor="let tag of item.tags || []" class="rounded-full bg-slate-100 px-2 py-1 text-xs">{{tag}}</span></div>
              </a>
              <ng-template #sourceResult>
                <a [href]="item.sourceUrl || '#'" [target]="item.sourceUrl ? '_blank' : null"
                   class="block rounded-2xl border border-slate-200 p-5 transition hover:border-slate-300 hover:shadow-sm">
                  <div class="text-[10px] font-bold uppercase tracking-wider text-slate-400">{{item.sourceType || "Knowledge source"}}</div>
                  <h2 class="mt-2 font-bold">{{item.title}}</h2>
                  <p class="mt-2 text-sm leading-6 text-slate-600" [innerHTML]="item.excerpt"></p>
                </a>
              </ng-template>
              </ng-container>
            </div>

            <div *ngIf="!searchResult.results?.length" class="rounded-2xl border border-slate-200 p-8 text-center">
              <p class="font-bold">No results</p>
              <p class="mt-2 text-sm text-slate-500">Try the suggested questions below or loosen a filter.</p>
            </div>
          </ng-container>

          <section *ngIf="result?.relatedQuestions?.length" class="mt-8 rounded-2xl border border-slate-200 p-5">
            <h2 class="font-bold">People also asked</h2>
            <div class="mt-3 flex flex-wrap gap-2">
              <button *ngFor="let q of result?.relatedQuestions" type="button" class="rounded-full border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50" (click)="query=q; search()">{{q}}</button>
            </div>
          </section>

          <section *ngIf="result && query.trim()" class="mt-8 rounded-2xl border border-slate-200 p-5">
            <div class="flex flex-col gap-4 sm:flex-row sm:items-end">
              <div class="flex-1"><label class="rk-label">Get alerts for this search</label><input class="rk-input" [(ngModel)]="alertEmail" placeholder="you@example.com" type="email"/></div>
              <button class="rk-btn rk-btn-primary" [disabled]="!alertEmail || savingAlert" (click)="saveAlert()">{{savingAlert ? "Saving…" : "Save search alert"}}</button>
            </div>
            <p *ngIf="alertMessage" class="mt-3 text-sm text-emerald-700">{{alertMessage}}</p>
          </section>
        </section>
      </div>
    </main>
  </div>
  `
})
export class PortalSearchComponent implements OnInit, OnDestroy {
  query = ""; result?: SearchResult; tenant?: Tenant; categories: Category[] = [];
  suggestions: string[] = []; suggestionsOpen = false; alertEmail = ""; savingAlert = false; alertMessage = "";
  articleTypes = ["GENERAL","GUIDE","FAQ","TUTORIAL","POLICY","REFERENCE","TROUBLESHOOTING"];
  filters = {categoryId:"",language:"",tag:"",articleType:"",dateFrom:"",dateTo:"",sourceType:""};
  private timer?: ReturnType<typeof setTimeout>;

  constructor(private readonly api: ApiService, private readonly route: ActivatedRoute) {}
  ngOnInit(): void {
    this.query = this.route.snapshot.queryParamMap.get("q") || "";
    this.loadTenant(); this.api.categories().subscribe({next:x=>this.categories=x});
    if (this.query) this.search();
  }
  ngOnDestroy(): void { if (this.timer) clearTimeout(this.timer); }
  get tenantSlug(): string | null { return this.route.snapshot.queryParamMap.get("tenant"); }

  private loadTenant(): void {
    const t = this.tenantSlug, host = window.location.hostname;
    const key = t || (host !== "localhost" && host !== "127.0.0.1" && host.split(".").length >= 3 ? (host.endsWith(".ricozknow.com") ? host.split(".")[0] : host) : null);
    if (key) this.api.tenantLookup(key).subscribe({next:x=>this.tenant=x,error:()=>undefined});
  }
  onQueryChange(): void {
    if (this.timer) clearTimeout(this.timer);
    const term=this.query.trim(); if(term.length<2){this.suggestions=[];this.suggestionsOpen=false;return;}
    this.timer=setTimeout(()=>this.api.searchSuggestions(term).subscribe({next:x=>{this.suggestions=x.slice(0,8);this.suggestionsOpen=!!x.length},error:()=>this.suggestionsOpen=false}),220);
  }
  resetFilters(): void { this.filters={categoryId:"",language:"",tag:"",articleType:"",dateFrom:"",dateTo:"",sourceType:""}; if(this.query) this.search(); }
  search(): void {
    const q=this.query.trim(); if(!q)return;
    this.suggestionsOpen=false; this.api.portalSearch(q,this.filters.categoryId||undefined,0,this.filters.language||undefined,{
      tag:this.filters.tag||undefined,articleType:this.filters.articleType||undefined,
      dateFrom:this.filters.dateFrom?new Date(this.filters.dateFrom+"T00:00:00Z").toISOString():undefined,
      dateTo:this.filters.dateTo?new Date(this.filters.dateTo+"T23:59:59Z").toISOString():undefined,
      sourceType:this.filters.sourceType||undefined
    }).subscribe({next:r=>this.result=r});
  }
  track(articleId:string, position:number): void { this.api.portalSearchClick(articleId,this.query.trim(),position).subscribe({error:()=>undefined}); }
  saveAlert(): void {
    if(!this.alertEmail.trim())return; this.savingAlert=true; this.alertMessage="";
    this.api.saveSearch({email:this.alertEmail.trim(),query:this.query.trim(),categoryId:this.filters.categoryId||undefined,language:this.filters.language||undefined,tag:this.filters.tag||undefined,articleType:this.filters.articleType||undefined}).subscribe({
      next:()=>{this.savingAlert=false;this.alertMessage="Saved. We’ll email you when new matching knowledge is published.";},
      error:(e)=>{this.savingAlert=false;this.alertMessage=e?.status===401?"Sign in to the customer portal before creating a saved-search alert.":e?.status===403?"Saved-search alerts must use your signed-in email address.":"Could not save this alert. Please try again.";}
    });
  }
}
