import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { ApiService } from "../core/api.service";
import { Category, PortalArticleSummary, SearchResult, Tenant } from "../models";

@Component({
  selector: "rk-portal",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <a *ngIf="!invitationToken" class="fixed bottom-3 left-3 z-30 rounded bg-slate-900 px-3 py-2 text-white text-xs" [routerLink]="['/portal/history']" [queryParams]="{tenant:tenant?.slug}">My support history</a>
    <div *ngIf="invitationToken; else knowledgePortal" class="min-h-screen bg-slate-50 px-4 py-10 sm:py-16">
      <div class="mx-auto max-w-lg">
        <section class="rk-card p-7 sm:p-9">
          <div class="mb-7 flex items-center gap-3">
            <img
              *ngIf="tenant?.logoUrl"
              [src]="tenant?.logoUrl"
              alt=""
              class="h-10 w-10 rounded-xl object-cover"
            />
            <div
              *ngIf="!tenant?.logoUrl"
              class="grid h-10 w-10 place-items-center rounded-xl font-black text-white"
              [style.background]="tenant?.primaryColor || '#0f172a'"
            >
              R
            </div>
            <div>
              <p class="font-bold">{{ tenant?.portalTitle || tenant?.name || "RicozKnow" }}</p>
              <p class="text-xs text-slate-500">Complete your invited account</p>
            </div>
          </div>

          <ng-container *ngIf="!invitationSuccess; else invitationComplete">
            <p class="text-sm font-semibold text-slate-500">Invitation</p>
            <h1 class="mt-2 text-3xl font-bold tracking-tight">Create your account</h1>
            <p class="mt-2 text-sm leading-6 text-slate-500">
              You have been invited to join {{ tenant?.name || "this organization" }}. Set your name and password to activate your account.
            </p>

            <form class="mt-7 space-y-5" (ngSubmit)="acceptInvitation()">
              <div>
                <label class="rk-label" for="invitedName">Name</label>
                <input
                  id="invitedName"
                  name="invitedName"
                  class="rk-input"
                  [(ngModel)]="invitedName"
                  autocomplete="name"
                  placeholder="Your name"
                  required
                />
              </div>

              <div>
                <label class="rk-label" for="invitedPassword">Password</label>
                <input
                  id="invitedPassword"
                  name="invitedPassword"
                  class="rk-input"
                  [(ngModel)]="invitedPassword"
                  type="password"
                  autocomplete="new-password"
                  minlength="12"
                  placeholder="Minimum 12 characters"
                  required
                />
                <p class="mt-1 text-xs text-slate-500">Use at least 12 characters.</p>
              </div>

              <div>
                <label class="rk-label" for="invitedPasswordConfirm">Confirm password</label>
                <input
                  id="invitedPasswordConfirm"
                  name="invitedPasswordConfirm"
                  class="rk-input"
                  [(ngModel)]="invitedPasswordConfirm"
                  type="password"
                  autocomplete="new-password"
                  minlength="12"
                  placeholder="Re-enter your password"
                  required
                />
              </div>

              <p *ngIf="invitationError" role="alert" class="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm leading-5 text-rose-700">
                {{ invitationError }}
              </p>

              <button
                class="rk-btn rk-btn-primary w-full"
                type="submit"
                [disabled]="invitationBusy"
              >
                {{ invitationBusy ? "Creating account…" : "Create account" }}
              </button>
            </form>

            <p class="mt-6 text-center text-sm text-slate-500">
              Already have an account?
              <a routerLink="/login" class="font-semibold text-slate-950 underline underline-offset-4">Sign in</a>
            </p>
          </ng-container>

          <ng-template #invitationComplete>
            <div class="text-center">
              <div class="mx-auto grid h-14 w-14 place-items-center rounded-full bg-emerald-100 text-2xl text-emerald-700">✓</div>
              <h1 class="mt-5 text-2xl font-bold">Your account is ready</h1>
              <p class="mt-2 text-sm leading-6 text-slate-500">
                Your invited account has been created successfully. You can now sign in with your email and password.
              </p>
              <a routerLink="/login" class="rk-btn rk-btn-primary mt-6 inline-flex">Continue to sign in</a>
            </div>
          </ng-template>
        </section>
      </div>
    </div>

    <ng-template #knowledgePortal>
      <div class="min-h-screen bg-white">
        <header class="border-b border-slate-200">
          <div class="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-5 sm:px-6">
            <div class="flex min-w-0 items-center gap-3">
              <img
                *ngIf="tenant?.logoUrl"
                [src]="tenant?.logoUrl"
                alt=""
                class="h-9 w-9 rounded-xl object-cover"
              />
              <div
                *ngIf="!tenant?.logoUrl"
                class="grid h-9 w-9 place-items-center rounded-xl font-black text-white"
                [style.background]="tenant?.primaryColor || '#0f172a'"
              >
                R
              </div>
              <div class="min-w-0">
                <p class="truncate font-bold">{{ tenant?.portalTitle || tenant?.name || "Knowledge Portal" }}</p>
                <p class="truncate text-xs text-slate-500">{{ tenant?.name }}</p>
              </div>
            </div>

            <div class="flex items-center gap-2">
              <button type="button" class="rk-btn rk-btn-secondary" (click)="openSupport()">Contact support</button>
              <button type="button" class="rk-btn rk-btn-secondary" (click)="showCustomerLogin=true">{{customerEmail ? "Account" : "Customer sign in"}}</button>
            </div>
          </div>
        </header>

        <main class="mx-auto max-w-6xl px-4 py-14 sm:px-6">
          <section class="mx-auto max-w-3xl text-center">
            <p class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">Knowledge base</p>
            <h1 class="mt-4 text-4xl font-bold tracking-tight sm:text-5xl">How can we help?</h1>
            <p class="mx-auto mt-4 max-w-xl text-sm leading-6 text-slate-500">
              Search the published knowledge for this tenant.
            </p>

            <form class="mt-7 flex flex-col gap-2 sm:flex-row" (ngSubmit)="search()">
              <input
                class="rk-input"
                [(ngModel)]="query"
                name="query"
                placeholder="Search for an answer…"
                aria-label="Search knowledge base"
              />
              <button class="rk-btn text-white sm:px-6" [style.background]="tenant?.primaryColor || '#0f172a'">Search</button>
            </form>
          </section>

          <section *ngIf="showCustomerLogin" class="mx-auto mt-8 max-w-xl rounded-2xl border border-slate-200 bg-slate-50 p-5">
              <div class="flex items-center justify-between"><h2 class="font-bold">Customer account</h2><button type="button" class="text-sm" (click)="showCustomerLogin=false">Close</button></div>
              <p class="mt-1 text-sm text-slate-500" *ngIf="!customerSignedIn">Use a magic link—no password required.</p>
              <div *ngIf="customerSignedIn" class="mt-4 flex items-center justify-between gap-3">
                <span class="text-sm text-slate-700">Signed in as {{ customerEmail }}</span>
                <button type="button" class="rk-btn rk-btn-secondary" (click)="signOutCustomer()" [disabled]="customerBusy">Sign out</button>
              </div>
              <div *ngIf="!customerSignedIn && !magicSent" class="mt-4 flex gap-2">
                <input class="rk-input" [(ngModel)]="customerEmail" type="email" name="customerEmail" placeholder="you@example.com">
                <button class="rk-btn rk-btn-primary" (click)="requestMagicLink()" [disabled]="customerBusy">Email me a link</button>
              </div>
              <p *ngIf="!customerSignedIn && magicSent" class="mt-3 text-sm text-emerald-700">Check your email for a secure sign-in link.</p>
            </section>

          <p *ngIf="curatedLoadError" role="alert" class="mt-5 rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">Featured or pinned articles could not be loaded. Please refresh the page or contact support if this continues.</p>
          <section *ngIf="featuredArticles.length" class="mt-12">
            <div class="flex items-center justify-between gap-4">
              <div>
                <h2 class="text-lg font-bold">Featured articles</h2>
                <p class="mt-1 text-sm text-slate-500">Recommended knowledge highlighted by your team.</p>
              </div>
            </div>
            <div class="mt-5 grid gap-4 md:grid-cols-2">
              <a *ngFor="let article of featuredArticles" [routerLink]="['/portal/article', article.slug]" [queryParams]="{ tenant: tenantSlug }" class="rounded-2xl border border-slate-200 p-6 transition hover:-translate-y-0.5 hover:shadow-sm">
                <div class="flex items-center gap-2"><span class="rounded-full bg-amber-50 px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-amber-700">Featured</span><span *ngIf="article.pinned" class="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-slate-600">Pinned</span></div>
                <h3 class="mt-3 text-lg font-bold">{{ article.title }}</h3>
                <p class="mt-2 text-sm text-slate-500">{{ article.slug }}</p>
              </a>
            </div>
          </section>

          <section *ngIf="pinnedArticles.length" class="mt-12">
            <div class="flex items-center justify-between gap-4">
              <div>
                <h2 class="text-lg font-bold">Pinned articles</h2>
                <p class="mt-1 text-sm text-slate-500">Important knowledge your team has pinned for quick access.</p>
              </div>
            </div>
            <div class="mt-5 grid gap-4 md:grid-cols-2">
              <a *ngFor="let article of pinnedArticles" [routerLink]="['/portal/article', article.slug]" [queryParams]="{ tenant: tenantSlug }" class="rounded-2xl border border-slate-200 p-6 transition hover:-translate-y-0.5 hover:shadow-sm">
                <span class="rounded-full bg-slate-100 px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-slate-600">Pinned</span>
                <h3 class="mt-3 text-lg font-bold">{{ article.title }}</h3>
                <p class="mt-2 text-sm text-slate-500">{{ article.slug }}</p>
              </a>
            </div>
          </section>

          <section class="mt-12">
            <div class="flex items-center justify-between gap-4">
              <div>
                <h2 class="text-lg font-bold">Popular topics</h2>
                <p class="mt-1 text-sm text-slate-500">Browse the available categories.</p>
              </div>
            </div>

            <div class="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <button
                *ngFor="let category of categories"
                type="button"
                class="flex items-center gap-3 rounded-2xl border border-slate-200 p-4 text-left transition hover:-translate-y-0.5 hover:shadow-sm"
                (click)="browse(category.id)"
              >
                <span
                  class="grid h-10 w-10 shrink-0 place-items-center rounded-xl text-sm font-black text-white"
                  [style.background]="tenant?.primaryColor || '#0f172a'"
                >
                  {{ categoryInitial(category.name) }}
                </span>

                <span class="min-w-0">
                  <span class="block truncate font-semibold">{{ category.name }}</span>
                  <span class="block truncate text-xs text-slate-500">Browse articles</span>
                </span>
              </button>
            </div>

            <p *ngIf="!categories.length" class="mt-4 text-sm text-slate-500">
              No topics have been set up yet.
            </p>
            <div class="mt-6" *ngIf="popularSearches.length">
              <p class="text-xs font-bold uppercase tracking-widest text-slate-400">Popular searches</p>
              <div class="mt-2 flex flex-wrap gap-2"><button *ngFor="let q of popularSearches" type="button" class="rounded-full border border-slate-200 px-3 py-1.5 text-xs font-semibold hover:bg-slate-50" (click)="query=q; search()">{{q}}</button></div>
            </div>
          </section>

          <section class="mt-12">
            <div class="flex items-center justify-between gap-4">
              <div>
                <h2 class="text-lg font-bold">{{ searchResult ? "Search results" : "Recommended articles" }}</h2>
                <p *ngIf="searchResult" class="mt-1 text-sm text-slate-500">
                  <span class="font-semibold" [style.color]="tenant?.primaryColor || '#0f172a'">
                    {{ searchResult.total }}
                  </span>
                  results for “{{ searchResult.query }}”
                </p>
              </div>
              <a *ngIf="searchResult" routerLink="/portal" class="text-sm font-semibold underline underline-offset-4">
                Clear
              </a>
            </div>

            <div class="mt-5 grid gap-4 md:grid-cols-2">
              <a
                *ngFor="let article of articles"
                [routerLink]="['/portal/article', article.slug]"
                [queryParams]="{ tenant: tenantSlug }"
                class="rounded-2xl border border-slate-200 p-6 transition hover:-translate-y-0.5 hover:shadow-sm"
              >
                <p class="text-xs font-bold uppercase tracking-widest text-slate-400">Article</p>
                <h3 class="mt-2 text-lg font-bold">{{ article.title }}</h3>
                <p class="mt-2 text-sm text-slate-500">{{ article.slug }}</p>
              </a>
            </div>

            <div *ngIf="searchResult && !articles.length" class="mt-5 rounded-2xl border border-slate-200 p-8 text-center">
              <p class="font-bold">No results for “{{ searchResult.query }}”</p>
              <p class="mx-auto mt-2 max-w-md text-sm leading-6 text-slate-500">
                Try different words or browse one of the available categories.
              </p>
            </div>
          </section>
        </main>
      </div>
    </ng-template>
  `
})
export class PortalComponent implements OnInit {
  tenant?: Tenant;
  categories: Category[] = [];
  articles: PortalArticleSummary[] = [];
  curatedLoadError = false;
  featuredArticles: PortalArticleSummary[] = [];
  pinnedArticles: PortalArticleSummary[] = [];
  searchResult?: SearchResult;
  query = "";
  popularSearches: string[] = [];

  invitationToken = "";
  invitedName = "";
  invitedPassword = "";
  invitedPasswordConfirm = "";
  invitationBusy = false;
  invitationSuccess = false;
  invitationError = "";
  showCustomerLogin = false;
  customerEmail = "";
  customerBusy = false;
  magicSent = false;
  customerSignedIn = false;

  constructor(
    private readonly api: ApiService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const params = new URLSearchParams(window.location.search);
    this.invitationToken = params.get("token")?.trim() || "";

    this.loadTenantFromHost();
    const magic = params.get("magic")?.trim();
    // Remove the sensitive one-time credential from the address bar immediately.
    if (magic) {
      const clean = new URL(window.location.href);
      clean.searchParams.delete("magic");
      window.history.replaceState({}, "", clean.toString());
      this.api.customerConsumeMagic(magic).subscribe({
        next: () => this.refreshCustomerSession(),
        error: () => { this.customerSignedIn = false; }
      });
    } else {
      this.refreshCustomerSession();
    }

    // The invitation email uses /portal?token=..., so do not load the public
    // knowledge portal while an invitation token is present.
    if (this.invitationToken) {
      return;
    }

    this.api.searchSuggestions().subscribe({next: x=>this.popularSearches=x.slice(0,8)});

    this.api.categories().subscribe({
      next: (items) => {
        this.categories = items;
      }
    });

    this.reloadPortalContent();
  }

  acceptInvitation(): void {
    this.invitationError = "";

    if (!this.invitationToken) {
      this.invitationError = "This invitation link is missing its invitation token.";
      return;
    }

    if (!this.invitedName.trim()) {
      this.invitationError = "Please enter your name.";
      return;
    }

    if (this.invitedPassword.length < 12) {
      this.invitationError = "Password must be at least 12 characters.";
      return;
    }

    if (this.invitedPassword !== this.invitedPasswordConfirm) {
      this.invitationError = "Passwords do not match.";
      return;
    }

    this.invitationBusy = true;

    this.api.acceptInvitation(
      this.invitationToken,
      this.invitedPassword,
      this.invitedName.trim()
    ).subscribe({
      next: () => {
        this.invitationBusy = false;
        this.invitationSuccess = true;
      },
      error: (error: unknown) => {
        this.invitationBusy = false;
        this.invitationError = this.invitationErrorMessage(error);
      }
    });
  }

  openSupport(): void {
    const tenant = this.tenantSlug || this.tenant?.slug || "";
    window.location.href = `/widget?tenant=${encodeURIComponent(tenant)}`;
  }

  private reloadPortalContent(): void {
    this.api.portalArticles(undefined, 0, undefined, { featured: true }).subscribe({
      next: (page) => { this.featuredArticles = page.content.slice(0, 6); },
      error: () => { this.featuredArticles = []; this.curatedLoadError = true; }
    });

    this.api.portalArticles(undefined, 0, undefined, { pinned: true }).subscribe({
      next: (page) => { this.pinnedArticles = page.content.slice(0, 6); },
      error: () => { this.pinnedArticles = []; this.curatedLoadError = true; }
    });

    this.api.portalArticles(undefined, 0).subscribe({
      next: (page) => { this.articles = page.content; }
    });
  }

  private refreshCustomerSession(): void {
    this.api.customerMe().subscribe({
      next: (user) => {
        this.customerEmail = user?.email ?? "";
        this.customerSignedIn = !!user;
        this.reloadPortalContent();
      },
      error: () => {
        this.customerEmail = "";
        this.customerSignedIn = false;
        this.reloadPortalContent();
      }
    });
  }

  signOutCustomer(): void {
    this.customerBusy = true;
    this.api.customerLogout().subscribe({
      next: () => {
        this.customerSignedIn = false;
        this.customerEmail = "";
        this.customerBusy = false;
        this.magicSent = false;
        this.reloadPortalContent();
      },
      error: () => { this.customerBusy = false; }
    });
  }

  requestMagicLink(): void {
    if (!this.customerEmail.trim()) return;
    const tenant = this.tenantSlug || this.tenant?.slug || "";
    this.customerBusy = true;
    this.api.customerMagicLink(tenant, this.customerEmail.trim()).subscribe({
      next:()=>{this.magicSent=true;this.customerBusy=false;},
      error:()=>{this.customerBusy=false;}
    });
  }

  browse(categoryId: string): void {
    this.searchResult = undefined;

    this.api.portalArticles(categoryId, 0).subscribe((page) => {
      this.articles = page.content;
    });
  }

  categoryInitial(name: string): string {
    return (name?.trim().charAt(0) || "?").toUpperCase();
  }

  search(): void {
    if (!this.query.trim()) {
      return;
    }

    this.api.portalSearch(this.query.trim(), undefined, 0).subscribe((result) => {
      this.searchResult = result;
      this.articles = result.results.filter(item => !!item.articleId).map((item) => ({
        id: item.articleId!,
        slug: item.slug,
        title: item.title,
        categoryId: item.categoryId
      }));
    });
  }

  private loadTenantFromHost(): void {
    const host = window.location.hostname;

    if (host !== "localhost" && host !== "127.0.0.1") {
      const parts = host.split(".");

      if (parts.length >= 3) {
        this.api.tenantLookup(host.endsWith(".ricozknow.com") ? parts[0] : host).subscribe({
          next: (tenant) => {
            this.tenant = tenant;
          }
        });
      }

      return;
    }

    const tenantSlug = new URLSearchParams(window.location.search).get("tenant");

    if (!tenantSlug) {
      return;
    }

    this.api.tenantLookup(tenantSlug).subscribe({
      next: (tenant) => {
        this.tenant = tenant;
      }
    });
  }

  get tenantSlug(): string | null {
    return new URLSearchParams(window.location.search).get("tenant");
  }

  private invitationErrorMessage(error: unknown): string {
    if (!error || typeof error !== "object") {
      return "We could not create your account. The invitation may be expired or invalid.";
    }

    const e = error as {
      error?: { message?: string; detail?: string; error?: string };
      message?: string;
    };

    return (
      e.error?.message ||
      e.error?.detail ||
      e.error?.error ||
      e.message ||
      "We could not create your account. The invitation may be expired or invalid."
    );
  }
}
