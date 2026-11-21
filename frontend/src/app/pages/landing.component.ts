import { CommonModule } from "@angular/common";
import { Component } from "@angular/core";
import { RouterLink } from "@angular/router";
import { ThemeToggleComponent } from "../shared/theme-toggle.component";

@Component({
  selector: "rk-landing",
  standalone: true,
  imports: [CommonModule, RouterLink, ThemeToggleComponent],
  template: `
    <div class="min-h-screen overflow-x-clip bg-white text-slate-950">
      <header class="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div class="mx-auto flex h-16 max-w-7xl items-center justify-between gap-3 px-4 sm:px-6 lg:px-8">
          <a routerLink="/" class="flex items-center gap-3">
            <span class="grid h-9 w-9 place-items-center rounded-xl bg-slate-950 font-black text-white">R</span>
            <div>
              <p class="font-bold tracking-tight">RicozKnow</p>
              <p class="hidden text-[10px] uppercase tracking-widest text-slate-400 sm:block">Knowledge platform</p>
            </div>
          </a>

          <nav class="hidden gap-7 md:flex">
            <a href="#product" class="text-sm font-medium text-slate-600 hover:text-slate-950">Product</a>
            <a href="#features" class="text-sm font-medium text-slate-600 hover:text-slate-950">Features</a>
            <a href="#agent" class="text-sm font-medium text-slate-600 hover:text-slate-950">AI Agent</a>
            <a href="#governance" class="text-sm font-medium text-slate-600 hover:text-slate-950">Governance</a>
          </nav>

          <div class="flex items-center gap-2">
            <rk-theme-toggle [compact]="true" />
            <a routerLink="/login" class="hidden rounded-xl px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 sm:inline-flex">Sign in</a>
            <a routerLink="/register" class="rk-btn rk-btn-primary px-3.5 text-xs sm:px-4 sm:text-sm">Get started</a>
            <button
              type="button"
              class="grid h-10 w-10 place-items-center rounded-xl border border-slate-200 bg-white text-sm text-slate-700 hover:bg-slate-50 md:hidden"
              [attr.aria-expanded]="mobileMenuOpen"
              aria-label="Toggle menu"
              (click)="mobileMenuOpen = !mobileMenuOpen"
            >
              {{ mobileMenuOpen ? "✕" : "☰" }}
            </button>
          </div>
        </div>

        <nav
          *ngIf="mobileMenuOpen"
          class="border-t border-slate-200 bg-white px-4 py-4 md:hidden"
        >
          <div class="flex flex-col gap-1">
            <a href="#product" class="rounded-xl px-3 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50" (click)="mobileMenuOpen = false">Product</a>
            <a href="#features" class="rounded-xl px-3 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50" (click)="mobileMenuOpen = false">Features</a>
            <a href="#agent" class="rounded-xl px-3 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50" (click)="mobileMenuOpen = false">AI Agent</a>
            <a href="#governance" class="rounded-xl px-3 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50" (click)="mobileMenuOpen = false">Governance</a>
            <a routerLink="/explore" [queryParams]="{ tenant: 'demo' }" class="rounded-xl px-3 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50" (click)="mobileMenuOpen = false">Explore portal</a>
            <a routerLink="/login" class="mt-2 rk-btn rk-btn-secondary w-full justify-center" (click)="mobileMenuOpen = false">Sign in</a>
          </div>
        </nav>
      </header>

      <main>
        <section class="overflow-x-clip border-b border-slate-200">
          <div class="mx-auto grid max-w-7xl gap-12 px-4 py-14 sm:px-6 lg:grid-cols-[1.05fr_.95fr] lg:px-8 lg:py-24">
            <div class="flex flex-col justify-center">
              <span class="w-fit rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-bold uppercase tracking-widest text-slate-500">Knowledge built for people and AI</span>
              <h1 class="mt-6 max-w-3xl text-4xl font-bold leading-[1.08] tracking-[-0.04em] sm:text-6xl lg:text-7xl">Your organization's knowledge, <span class="text-slate-500">structured for AI.</span></h1>
              <p class="mt-6 max-w-2xl text-base leading-7 text-slate-600 sm:text-xl sm:leading-8">Create, govern, publish and connect trusted organizational knowledge with the people, customers and AI agents that need it.</p>
              <div class="mt-8 flex flex-col gap-3 sm:flex-row">
                <a routerLink="/register" class="rk-btn rk-btn-primary justify-center px-6 py-3">Create your workspace</a>
                <a routerLink="/explore" [queryParams]="{ tenant: 'demo' }" class="rk-btn rk-btn-secondary justify-center px-6 py-3">Explore the platform</a>
              </div>
              <div class="mt-10 flex flex-wrap gap-x-6 gap-y-2.5 text-sm text-slate-500 sm:gap-x-8">
                <span>Rich content</span><span>Review workflow</span><span>Published knowledge</span><span>AI-ready APIs</span>
              </div>
            </div>

            <div class="relative">
              <div class="pointer-events-none absolute -inset-4 rounded-[3rem] bg-slate-100 blur-3xl sm:-inset-8"></div>
              <div class="relative rounded-3xl border border-slate-200 bg-white p-3 shadow-2xl shadow-slate-200/70">
                <div class="overflow-hidden rounded-2xl border border-slate-200 bg-slate-50">
                  <div class="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3.5 sm:px-5 sm:py-4"><div class="flex items-center gap-3"><div class="grid h-8 w-8 place-items-center rounded-lg bg-slate-950 text-xs font-black text-white">R</div><div><p class="text-sm font-bold">Knowledge Studio</p><p class="text-[11px] text-slate-400">Governed knowledge</p></div></div><span class="rounded-full bg-emerald-50 px-2.5 py-1 text-[10px] font-bold text-emerald-700">Published</span></div>
                  <div class="grid gap-4 p-4 sm:grid-cols-[150px_1fr] sm:p-5"><div class="hidden space-y-2 sm:block"><div class="rounded-lg bg-slate-900 px-3 py-2 text-xs font-semibold text-white">Dashboard</div>
                  <div class="px-3 py-2 text-xs font-semibold text-slate-500">Articles</div>
                  <div class="px-3 py-2 text-xs font-semibold text-slate-500">Review queue</div>
                  <div class="px-3 py-2 text-xs font-semibold text-slate-500">Categories</div></div>
                  <div><div class="rounded-2xl border border-slate-200 bg-white p-4 sm:p-5"><p class="text-xs font-bold uppercase tracking-widest text-slate-400">Article</p><h2 class="mt-2 text-lg font-bold sm:text-xl">Getting started with your knowledge base</h2><div class="mt-5 space-y-3"><div class="h-3 w-11/12 rounded-full bg-slate-100"></div><div class="h-3 w-full rounded-full bg-slate-100"></div><div class="h-3 w-8/12 rounded-full bg-slate-100"></div></div><div class="mt-6 flex gap-2"><span class="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-semibold text-slate-600">Version 4</span><span class="rounded-full bg-blue-50 px-2.5 py-1 text-[10px] font-semibold text-blue-700">Approved</span></div></div><div class="mt-4 grid grid-cols-2 gap-3"><div class="rounded-xl border border-slate-200 bg-white p-4"><p class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Articles</p><p class="mt-1 text-2xl font-bold">248</p></div><div class="rounded-xl border border-slate-200 bg-white p-4"><p class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Published</p><p class="mt-1 text-2xl font-bold">214</p></div></div></div></div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section id="product" class="border-b border-slate-200 bg-slate-50"><div class="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8 lg:py-24"><p class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">One knowledge layer</p><h2 class="mt-3 max-w-2xl text-3xl font-bold tracking-tight sm:text-4xl">From internal knowledge to trusted answers.</h2><p class="mt-4 max-w-2xl text-base leading-7 text-slate-600">Create and maintain authoritative content, then make approved knowledge available through search and agent integrations.</p><div id="features" class="mt-12 grid gap-4 md:grid-cols-3"><article class="rounded-2xl border border-slate-200 bg-white p-6"><div class="grid h-11 w-11 place-items-center rounded-xl bg-slate-950 text-sm font-black text-white">01</div><h3 class="mt-5 text-xl font-bold">Create</h3><p class="mt-2 text-sm leading-6 text-slate-600">Build structured articles with rich content, categories and reusable knowledge.</p></article><article class="rounded-2xl border border-slate-200 bg-white p-6"><div class="grid h-11 w-11 place-items-center rounded-xl bg-slate-950 text-sm font-black text-white">02</div><h3 class="mt-5 text-xl font-bold">Govern</h3><p class="mt-2 text-sm leading-6 text-slate-600">Review changes, manage versions and control what becomes published knowledge.</p></article><article class="rounded-2xl border border-slate-200 bg-white p-6"><div class="grid h-11 w-11 place-items-center rounded-xl bg-slate-950 text-sm font-black text-white">03</div><h3 class="mt-5 text-xl font-bold">Connect</h3><p class="mt-2 text-sm leading-6 text-slate-600">Let teams, customers and authenticated AI agents find the knowledge they need.</p></article></div></div></section>

        <section id="governance" class="border-b border-slate-200"><div class="mx-auto grid max-w-7xl gap-12 px-4 py-16 sm:px-6 lg:grid-cols-2 lg:gap-14 lg:px-8 lg:py-24"><div><p class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">Built for governance</p><h2 class="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">Knowledge should be controlled before it is consumed.</h2><p class="mt-5 max-w-xl text-base leading-7 text-slate-600">Create drafts, submit changes for review, manage versions and publish the content that is ready to be trusted.</p><div class="mt-8 space-y-4"><div class="flex gap-4"><span class="mt-1 grid h-6 w-6 shrink-0 place-items-center rounded-full bg-slate-950 text-xs font-bold text-white">✓</span><div><p class="font-semibold">Versioned content</p><p class="mt-1 text-sm text-slate-500">Keep changes traceable across article versions.</p></div></div><div class="flex gap-4"><span class="mt-1 grid h-6 w-6 shrink-0 place-items-center rounded-full bg-slate-950 text-xs font-bold text-white">✓</span><div><p class="font-semibold">Review workflow</p><p class="mt-1 text-sm text-slate-500">Separate creation from approval and publishing.</p></div></div><div class="flex gap-4"><span class="mt-1 grid h-6 w-6 shrink-0 place-items-center rounded-full bg-slate-950 text-xs font-bold text-white">✓</span><div><p class="font-semibold">Role-based access</p><p class="mt-1 text-sm text-slate-500">Give each team member access appropriate to their responsibility.</p></div></div></div></div><div class="rounded-3xl border border-slate-200 bg-slate-50 p-4 sm:p-5"><div class="rounded-2xl border border-slate-200 bg-white p-5 sm:p-6"><div class="flex items-center justify-between"><div><p class="text-xs font-bold uppercase tracking-widest text-slate-400">Review queue</p><p class="mt-1 font-bold">Content requiring attention</p></div><span class="rounded-full bg-amber-50 px-2.5 py-1 text-xs font-bold text-amber-700">12</span></div><div class="mt-6 space-y-3"><div class="rounded-xl border border-slate-200 p-4"><div class="flex justify-between"><p class="text-sm font-semibold">Customer onboarding</p><span class="text-xs text-slate-400">v3</span></div><p class="mt-1 text-xs text-slate-500">Updated by Content team</p></div><div class="rounded-xl border border-slate-200 p-4"><div class="flex justify-between"><p class="text-sm font-semibold">Security guidelines</p><span class="text-xs text-slate-400">v7</span></div><p class="mt-1 text-xs text-slate-500">Awaiting reviewer approval</p></div><div class="rounded-xl border border-slate-200 p-4"><div class="flex justify-between"><p class="text-sm font-semibold">Billing FAQ</p><span class="text-xs text-slate-400">v2</span></div><p class="mt-1 text-xs text-slate-500">Submitted for review</p></div></div></div></div></div></section>

        <section id="agent" class="border-b border-slate-200 bg-slate-950 text-white"><div class="mx-auto grid max-w-7xl gap-12 px-4 py-16 sm:px-6 lg:grid-cols-[.9fr_1.1fr] lg:px-8 lg:py-24"><div><p class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">AI Agent Access</p><h2 class="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">Give AI access to the knowledge you actually trust.</h2><p class="mt-5 max-w-xl text-base leading-7 text-slate-300">Authenticated agents can search published knowledge and retrieve authoritative articles without permissions to create, edit or publish content.</p><a routerLink="/login" class="mt-8 inline-flex rounded-xl bg-white px-5 py-3 text-sm font-bold text-slate-950 hover:bg-slate-100">Open Agent Access</a></div><div class="rounded-3xl border border-slate-800 bg-slate-900 p-4 sm:p-5"><div class="rounded-2xl border border-slate-800 bg-slate-950 p-5 sm:p-6"><div class="flex items-center gap-3"><div class="grid h-10 w-10 place-items-center rounded-xl bg-white text-sm font-black text-slate-950">AI</div><div><p class="font-bold">Knowledge retrieval</p><p class="text-xs text-slate-500">Authenticated agent</p></div></div><div class="mt-7 space-y-3"><div class="rounded-xl border border-slate-800 p-4"><p class="text-xs text-slate-500">Question</p><p class="mt-1 text-sm font-medium">How do I reset my company password?</p></div><div class="text-center text-slate-600">↓</div><div class="rounded-xl border border-slate-800 p-4"><p class="text-xs text-slate-500">RicozKnow</p><p class="mt-1 text-sm font-medium">Search published knowledge</p></div><div class="text-center text-slate-600">↓</div><div class="rounded-xl border border-emerald-900 bg-emerald-950/30 p-4"><p class="text-xs text-emerald-500">Authoritative article</p><p class="mt-1 text-sm font-medium">Password Reset Procedure</p></div></div></div></div></div></section>

        <section class="border-b border-slate-200"><div class="mx-auto max-w-4xl px-4 py-16 text-center sm:px-6 lg:py-24"><p class="text-xs font-bold uppercase tracking-[0.2em] text-slate-400">Start with your knowledge</p><h2 class="mt-3 text-3xl font-bold tracking-tight sm:text-5xl">Build a knowledge base your team and AI can rely on.</h2><p class="mx-auto mt-5 max-w-2xl text-base leading-7 text-slate-600">Bring your organization's content into one governed workspace and make it easier to discover, maintain and use.</p><div class="mt-8 flex flex-col justify-center gap-3 sm:flex-row"><a routerLink="/register" class="rk-btn rk-btn-primary justify-center px-6 py-3">Create your workspace</a><a routerLink="/login" class="rk-btn rk-btn-secondary justify-center px-6 py-3">Sign in</a></div></div></section>
      </main>

      <footer><div class="mx-auto flex max-w-7xl flex-col gap-5 px-4 py-8 sm:px-6 md:flex-row md:items-center md:justify-between lg:px-8"><div><p class="font-bold">RicozKnow</p><p class="mt-1 text-xs text-slate-400">Governed knowledge for people and AI.</p></div><div class="flex flex-wrap gap-5 text-sm text-slate-500"><a href="#product">Product</a><a href="#features">Features</a><a href="#agent">AI Agent</a><a routerLink="/login">Sign in</a></div></div></footer>
    </div>
  `
})
export class LandingComponent {
  mobileMenuOpen = false;
}
