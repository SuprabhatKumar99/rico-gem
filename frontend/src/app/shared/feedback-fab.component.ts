import { CommonModule } from "@angular/common";
import { Component, HostListener, OnDestroy, OnInit, inject } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { NavigationEnd, Router, RouterLink } from "@angular/router";
import { Subscription } from "rxjs";
import { filter } from "rxjs/operators";
import { ApiService } from "../core/api.service";
import { AuthService } from "../core/auth.service";
import { ThemeService } from "../core/theme.service";
import {
  ProductFeedback,
  ProductFeedbackSeverity,
  ProductFeedbackType
} from "../models";

interface CapturedDiagnostics {
  pageUrl: string;
  routePath: string;
  browser: string;
  os: string;
  userAgent: string;
  viewport: string;
  theme: "light" | "dark";
  language: string;
}

interface RecentFeedbackEntry {
  id: string;
  type: ProductFeedbackType;
  severity: ProductFeedbackSeverity;
  title: string;
  pageUrl: string | null;
  createdAt: string;
}

const RECENT_STORAGE_KEY = "rk_recent_product_feedback";

function detectClientBrowser(ua: string): { browser: string; os: string } {
  let browser = "Unknown browser";
  const edge = ua.match(/Edg(?:A|iOS)?\/(\d+(?:\.\d+)?)/);
  const opera = ua.match(/OPR\/(\d+(?:\.\d+)?)/);
  const firefox = ua.match(/Firefox\/(\d+(?:\.\d+)?)/);
  const chrome = ua.match(/(?:Chrome|CriOS|HeadlessChrome)\/(\d+(?:\.\d+)?)/);
  const safari = ua.match(/Version\/(\d+(?:\.\d+)?).*Safari\//);

  if (edge) browser = `Edge ${edge[1]}`;
  else if (opera) browser = `Opera ${opera[1]}`;
  else if (firefox) browser = `Firefox ${firefox[1]}`;
  else if (chrome) browser = `Chrome ${chrome[1]}`;
  else if (safari) browser = `Safari ${safari[1]}`;

  let os = "Unknown OS";
  if (/iPhone|iPad|iPod/i.test(ua)) os = "iOS";
  else if (/Android/i.test(ua)) os = "Android";
  else if (/Windows NT/i.test(ua)) os = "Windows";
  else if (/Mac OS X|Macintosh/i.test(ua)) os = "macOS";
  else if (/CrOS/i.test(ua)) os = "ChromeOS";
  else if (/Linux/i.test(ua)) os = "Linux";

  return { browser, os };
}

@Component({
  selector: "rk-feedback-fab",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div
      class="fixed z-40 transition-all"
      [ngClass]="isPortalRoute ? 'bottom-[4.5rem] right-3 sm:bottom-[5rem] sm:right-4' : 'bottom-4 right-4 sm:bottom-5 sm:right-5'"
    >
      <button
        type="button"
        class="rk-feedback-fab group inline-flex h-11 items-center gap-2 rounded-full px-4 text-xs font-semibold transition hover:-translate-y-0.5 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-500 focus-visible:ring-offset-2"
        aria-label="Send bug report or feature request"
        [attr.aria-expanded]="open"
        data-testid="feedback-fab-button"
        (click)="openPanel()"
      >
        <svg class="h-4 w-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M7.5 8.25h9m-9 3H12m-9.75 1.51c0 1.6 1.123 2.994 2.707 3.227 1.129.166 2.27.293 3.423.379.35.026.67.21.865.501L12 21l2.755-4.133a1.14 1.14 0 0 1 .865-.501 48.172 48.172 0 0 0 3.423-.379c1.584-.233 2.707-1.626 2.707-3.228V6.741c0-1.602-1.123-2.995-2.707-3.228A48.394 48.394 0 0 0 12 3c-2.392 0-4.744.175-7.043.513C3.373 3.746 2.25 5.14 2.25 6.741v6.018Z" />
        </svg>
        <span>Feedback</span>
      </button>
    </div>

    <div
      *ngIf="open"
      class="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/45 p-3 backdrop-blur-[2px] sm:items-center sm:p-5"
      role="dialog"
      aria-modal="true"
      aria-labelledby="rk-feedback-title"
      data-testid="feedback-modal"
      (click)="closePanel()"
    >
      <div
        class="rk-card flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden shadow-2xl"
        (click)="$event.stopPropagation()"
      >
        <div class="flex items-start justify-between gap-3 border-b border-slate-200 px-5 py-4">
          <div class="min-w-0">
            <h2 id="rk-feedback-title" class="text-base font-bold tracking-tight text-slate-950">
              Send feedback to engineering
            </h2>
            <p class="mt-0.5 text-xs text-slate-500">
              Report a bug or request a feature. Current page URL &amp; browser info are attached automatically.
            </p>
          </div>
          <button
            type="button"
            class="grid h-8 w-8 shrink-0 place-items-center rounded-lg border border-slate-200 bg-white text-xs text-slate-500 hover:bg-slate-50 hover:text-slate-800"
            aria-label="Close feedback dialog"
            (click)="closePanel()"
          >
            ✕
          </button>
        </div>

        <div *ngIf="recentEntries.length > 0 && !submittedRecord" class="flex items-center gap-1 border-b border-slate-200 bg-slate-50 px-5 py-2">
          <button
            type="button"
            class="rounded-lg px-3 py-1.5 text-xs font-semibold transition"
            [ngClass]="activeTab === 'form' ? 'bg-white text-slate-950 shadow-xs border border-slate-200' : 'text-slate-500 hover:text-slate-800'"
            (click)="activeTab = 'form'"
          >
            New report
          </button>
          <button
            type="button"
            class="rounded-lg px-3 py-1.5 text-xs font-semibold transition"
            [ngClass]="activeTab === 'recent' ? 'bg-white text-slate-950 shadow-xs border border-slate-200' : 'text-slate-500 hover:text-slate-800'"
            (click)="activeTab = 'recent'"
          >
            Recent sent ({{ recentEntries.length }})
          </button>
        </div>

        <div class="flex-1 overflow-y-auto px-5 py-4">
          <!-- Confirmation state -->
          <div *ngIf="submittedRecord" class="space-y-4 py-2 text-center" data-testid="feedback-success">
            <div class="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-emerald-100 text-emerald-700">
              <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="m4.5 12.75 6 6 9-13.5" />
              </svg>
            </div>
            <div>
              <p class="text-base font-bold text-slate-950">Sent to the development team</p>
              <p class="mt-1 text-xs text-slate-500">
                Reference <span class="font-mono font-semibold text-slate-800">{{ submittedRecord.id }}</span> has been logged for triage.
              </p>
            </div>

            <div class="rounded-xl border border-slate-200 bg-slate-50 p-3.5 text-left text-xs">
              <div class="flex flex-wrap items-center gap-2">
                <span
                  class="rk-badge"
                  [ngClass]="submittedRecord.type === 'BUG' ? 'bg-rose-100 text-rose-700' : 'bg-sky-100 text-sky-700'"
                >
                  {{ submittedRecord.type === 'BUG' ? 'Bug report' : 'Feature request' }}
                </span>
                <span class="rk-badge bg-slate-200 text-slate-700">{{ submittedRecord.severity }}</span>
              </div>
              <p class="mt-2 font-semibold text-slate-900">{{ submittedRecord.title }}</p>
              <p *ngIf="submittedRecord.pageUrl" class="mt-1.5 truncate font-mono text-[11px] text-slate-500">
                URL: {{ submittedRecord.pageUrl }}
              </p>
              <p *ngIf="submittedRecord.browser" class="mt-0.5 text-[11px] text-slate-500">
                Env: {{ submittedRecord.browser }} · {{ submittedRecord.os }} · {{ submittedRecord.viewport }}
              </p>
            </div>

            <div class="flex flex-wrap items-center justify-center gap-2 pt-1">
              <a
                *ngIf="canViewInbox"
                routerLink="/product-feedback"
                class="rk-btn rk-btn-secondary text-xs"
                (click)="closePanel()"
              >
                Open feedback inbox
              </a>
              <button type="button" class="rk-btn rk-btn-secondary text-xs" (click)="resetForm()">
                Submit another
              </button>
              <button type="button" class="rk-btn rk-btn-primary text-xs" (click)="closePanel()">
                Done
              </button>
            </div>
          </div>

          <!-- Recent submissions tab -->
          <div *ngIf="!submittedRecord && activeTab === 'recent'" class="space-y-2.5">
            <div
              *ngFor="let item of recentEntries"
              class="rounded-xl border border-slate-200 bg-slate-50 p-3.5"
            >
              <div class="flex items-center justify-between gap-2">
                <div class="flex items-center gap-2">
                  <span
                    class="rk-badge"
                    [ngClass]="item.type === 'BUG' ? 'bg-rose-100 text-rose-700' : 'bg-sky-100 text-sky-700'"
                  >
                    {{ item.type === 'BUG' ? 'Bug' : 'Feature' }}
                  </span>
                  <span class="font-mono text-[11px] text-slate-500">{{ item.id }}</span>
                </div>
                <span class="text-[11px] text-slate-400">{{ item.createdAt | date: "short" }}</span>
              </div>
              <p class="mt-1.5 text-sm font-semibold text-slate-900">{{ item.title }}</p>
              <p *ngIf="item.pageUrl" class="mt-1 truncate font-mono text-[11px] text-slate-500">{{ item.pageUrl }}</p>
            </div>
          </div>

          <!-- Submission form -->
          <form
            *ngIf="!submittedRecord && activeTab === 'form'"
            class="space-y-4"
            (ngSubmit)="submit()"
          >
            <div class="grid grid-cols-2 gap-2.5">
              <button
                type="button"
                class="flex items-center gap-2.5 rounded-xl border p-3 text-left transition"
                [ngClass]="feedbackType === 'BUG'
                  ? 'border-rose-300 bg-rose-50 text-rose-900'
                  : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'"
                data-testid="feedback-type-bug"
                (click)="feedbackType = 'BUG'"
              >
                <span class="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-rose-100 text-rose-700">
                  <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z" />
                  </svg>
                </span>
                <span class="min-w-0">
                  <span class="block text-xs font-bold">Bug report</span>
                  <span class="block text-[11px] opacity-75">Something is broken</span>
                </span>
              </button>

              <button
                type="button"
                class="flex items-center gap-2.5 rounded-xl border p-3 text-left transition"
                [ngClass]="feedbackType === 'FEATURE'
                  ? 'border-sky-300 bg-sky-50 text-sky-900'
                  : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'"
                data-testid="feedback-type-feature"
                (click)="feedbackType = 'FEATURE'"
              >
                <span class="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-sky-100 text-sky-700">
                  <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 18v-5.25m0 0a6.01 6.01 0 0 0 1.5-.189m-1.5.189a6.01 6.01 0 0 1-1.5-.189m3.75 7.478a12.06 12.06 0 0 1-4.5 0m3.75 2.383a14.406 14.406 0 0 1-3 0M14.25 18v-.192c0-.983.658-1.823 1.508-2.316a7.5 7.5 0 1 0-7.517 0c.85.493 1.509 1.333 1.509 2.316V18" />
                  </svg>
                </span>
                <span class="min-w-0">
                  <span class="block text-xs font-bold">Feature request</span>
                  <span class="block text-[11px] opacity-75">Suggest an improvement</span>
                </span>
              </button>
            </div>

            <div>
              <label for="rk-fb-summary" class="mb-1 block text-xs font-semibold text-slate-700">
                Summary
              </label>
              <input
                id="rk-fb-summary"
                name="title"
                type="text"
                required
                maxlength="160"
                [(ngModel)]="title"
                class="rk-input"
                [placeholder]="feedbackType === 'BUG'
                  ? 'e.g., Filter dropdown resets when navigating to page 2'
                  : 'e.g., Export verification sign-offs as signed PDF'"
              />
            </div>

            <div>
              <label for="rk-fb-desc" class="mb-1 block text-xs font-semibold text-slate-700">
                {{ feedbackType === 'BUG' ? 'Steps to reproduce & expected behavior' : 'Use case & desired outcome' }}
              </label>
              <textarea
                id="rk-fb-desc"
                name="description"
                rows="3"
                required
                maxlength="4000"
                [(ngModel)]="description"
                class="rk-input min-h-[5.5rem] resize-y"
                [placeholder]="feedbackType === 'BUG'
                  ? 'Describe what you clicked, what happened, and what you expected instead...'
                  : 'Explain the workflow problem and how this feature would help your team...'"
              ></textarea>
            </div>

            <div class="space-y-3">
              <div>
                <span class="mb-1.5 block text-xs font-semibold text-slate-700">
                  {{ feedbackType === 'BUG' ? 'Severity' : 'Priority' }}
                </span>
                <div class="grid grid-cols-4 gap-1.5">
                  <button
                    *ngFor="let level of severityLevels"
                    type="button"
                    class="rounded-lg border px-2 py-2 text-xs font-semibold transition"
                    [ngClass]="severity === level
                      ? 'rk-seg-btn-active'
                      : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'"
                    (click)="severity = level"
                  >
                    {{ level | titlecase }}
                  </button>
                </div>
              </div>

              <div>
                <label for="rk-fb-email" class="mb-1 block text-xs font-semibold text-slate-700">
                  Email <span class="font-normal text-slate-400">(optional, for follow-up)</span>
                </label>
                <input
                  id="rk-fb-email"
                  name="reporterEmail"
                  type="email"
                  maxlength="160"
                  [(ngModel)]="reporterEmail"
                  class="rk-input"
                  placeholder="you@company.com"
                />
              </div>
            </div>

            <div class="rounded-xl border border-slate-200 bg-slate-50 p-3.5" data-testid="feedback-diagnostics-card">
              <div class="flex items-center justify-between gap-2">
                <label class="flex cursor-pointer items-center gap-2 text-xs font-semibold text-slate-800">
                  <input
                    type="checkbox"
                    name="includeDiagnostics"
                    [(ngModel)]="includeDiagnostics"
                    class="h-4 w-4 rounded border-slate-300 accent-slate-900"
                  />
                  <span>Include page URL &amp; browser diagnostics</span>
                </label>
                <span class="text-[10px] font-semibold uppercase tracking-wide text-emerald-700">
                  {{ includeDiagnostics ? 'Auto-captured' : 'Excluded' }}
                </span>
              </div>

              <div *ngIf="includeDiagnostics" class="mt-2.5 grid gap-2 text-[11px] sm:grid-cols-2">
                <div class="min-w-0 rounded-lg border border-slate-200 bg-white px-2.5 py-2 sm:col-span-2">
                  <span class="block text-[10px] font-semibold uppercase tracking-wide text-slate-400">Current Page URL</span>
                  <span class="mt-0.5 block truncate font-mono text-slate-800" [title]="diagnostics.pageUrl" data-testid="feedback-captured-url">
                    {{ diagnostics.pageUrl }}
                  </span>
                </div>
                <div class="min-w-0 rounded-lg border border-slate-200 bg-white px-2.5 py-2">
                  <span class="block text-[10px] font-semibold uppercase tracking-wide text-slate-400">Browser &amp; OS</span>
                  <span class="mt-0.5 block truncate font-medium text-slate-800" data-testid="feedback-captured-browser">
                    {{ diagnostics.browser }} · {{ diagnostics.os }}
                  </span>
                </div>
                <div class="min-w-0 rounded-lg border border-slate-200 bg-white px-2.5 py-2">
                  <span class="block text-[10px] font-semibold uppercase tracking-wide text-slate-400">Viewport &amp; Theme</span>
                  <span class="mt-0.5 block truncate font-medium text-slate-800" data-testid="feedback-captured-viewport">
                    {{ diagnostics.viewport }} · {{ diagnostics.theme | titlecase }} · {{ diagnostics.language }}
                  </span>
                </div>
              </div>

              <details *ngIf="includeDiagnostics" class="mt-2">
                <summary class="cursor-pointer text-[11px] font-medium text-slate-500 hover:text-slate-700">
                  View raw User-Agent string
                </summary>
                <p class="mt-1.5 break-all rounded-lg border border-slate-200 bg-white p-2 font-mono text-[10px] text-slate-600">
                  {{ diagnostics.userAgent }}
                </p>
              </details>
            </div>

            <div
              *ngIf="errorMessage"
              class="rounded-xl border border-rose-200 bg-rose-50 px-3.5 py-2.5 text-xs font-medium text-rose-700"
              role="alert"
            >
              {{ errorMessage }}
            </div>

            <div class="flex items-center justify-end gap-2 pt-1">
              <button type="button" class="rk-btn rk-btn-secondary text-xs" (click)="closePanel()">
                Cancel
              </button>
              <button
                type="submit"
                class="rk-btn rk-btn-primary text-xs"
                [disabled]="submitting"
                data-testid="feedback-submit-button"
              >
                {{ submitting ? 'Sending...' : (feedbackType === 'BUG' ? 'Submit bug report' : 'Submit feature request') }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `
})
export class FeedbackFabComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private navSub?: Subscription;

  open = false;
  isPortalRoute = false;
  activeTab: "form" | "recent" = "form";

  feedbackType: ProductFeedbackType = "BUG";
  severity: ProductFeedbackSeverity = "MEDIUM";
  readonly severityLevels: ProductFeedbackSeverity[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

  title = "";
  description = "";
  reporterEmail = "";
  includeDiagnostics = true;

  submitting = false;
  errorMessage = "";
  submittedRecord: ProductFeedback | null = null;
  recentEntries: RecentFeedbackEntry[] = [];

  diagnostics: CapturedDiagnostics = {
    pageUrl: "",
    routePath: "/",
    browser: "Unknown browser",
    os: "Unknown OS",
    userAgent: "",
    viewport: "0x0",
    theme: "light",
    language: "en-US"
  };

  get canViewInbox(): boolean {
    return this.auth.hasAnyRole(["ADMIN", "EDITOR", "REVIEWER"]);
  }

  ngOnInit(): void {
    this.syncRouteState(this.router.url);
    this.recentEntries = this.loadRecent();
    this.navSub = this.router.events
      .pipe(filter((ev): ev is NavigationEnd => ev instanceof NavigationEnd))
      .subscribe((ev) => {
        this.syncRouteState(ev.urlAfterRedirects);
        if (this.open) {
          this.captureDiagnostics();
        }
      });
  }

  ngOnDestroy(): void {
    this.navSub?.unsubscribe();
  }

  @HostListener("document:keydown.escape")
  onEscape(): void {
    if (this.open) {
      this.closePanel();
    }
  }

  private syncRouteState(url: string): void {
    const clean = (url || "").split("?")[0];
    this.isPortalRoute = clean === "/portal" || clean.startsWith("/portal/");
  }

  openPanel(): void {
    this.captureDiagnostics();
    if (!this.reporterEmail && this.auth.user?.email) {
      this.reporterEmail = this.auth.user.email;
    }
    this.errorMessage = "";
    this.open = true;
  }

  closePanel(): void {
    this.open = false;
  }

  resetForm(): void {
    this.submittedRecord = null;
    this.activeTab = "form";
    this.title = "";
    this.description = "";
    this.severity = "MEDIUM";
    this.errorMessage = "";
    this.captureDiagnostics();
  }

  captureDiagnostics(): void {
    const ua = typeof navigator !== "undefined" ? navigator.userAgent : "";
    const parsed = detectClientBrowser(ua);
    const pageUrl = typeof window !== "undefined" ? window.location.href : this.router.url;
    const routePath = typeof window !== "undefined"
      ? `${window.location.pathname}${window.location.search}`
      : this.router.url;
    const viewport = typeof window !== "undefined"
      ? `${window.innerWidth}x${window.innerHeight}`
      : "unknown";
    const language = typeof navigator !== "undefined" ? navigator.language || "en-US" : "en-US";

    this.diagnostics = {
      pageUrl,
      routePath,
      browser: parsed.browser,
      os: parsed.os,
      userAgent: ua,
      viewport,
      theme: this.theme.resolved,
      language
    };
  }

  submit(): void {
    this.errorMessage = "";
    const trimmedTitle = this.title.trim();
    const trimmedDesc = this.description.trim();

    if (trimmedTitle.length < 3) {
      this.errorMessage = "Please enter a short summary (at least 3 characters).";
      return;
    }
    if (trimmedDesc.length < 5) {
      this.errorMessage = "Please add a few more details so the dev team can act on it.";
      return;
    }

    this.captureDiagnostics();
    this.submitting = true;

    this.api
      .submitProductFeedback({
        type: this.feedbackType,
        severity: this.severity,
        title: trimmedTitle,
        description: trimmedDesc,
        reporterEmail: this.reporterEmail.trim() || null,
        includeDiagnostics: this.includeDiagnostics,
        pageUrl: this.includeDiagnostics ? this.diagnostics.pageUrl : null,
        routePath: this.includeDiagnostics ? this.diagnostics.routePath : null,
        userAgent: this.includeDiagnostics ? this.diagnostics.userAgent : null,
        viewport: this.includeDiagnostics ? this.diagnostics.viewport : null,
        theme: this.includeDiagnostics ? this.diagnostics.theme : null,
        language: this.includeDiagnostics ? this.diagnostics.language : null
      })
      .subscribe({
        next: (created) => {
          this.submitting = false;
          this.submittedRecord = created;
          this.saveRecent({
            id: created.id,
            type: created.type,
            severity: created.severity,
            title: created.title,
            pageUrl: created.pageUrl,
            createdAt: created.createdAt
          });
        },
        error: (err) => {
          this.submitting = false;
          this.errorMessage = err?.error?.message || "Could not submit feedback right now. Please try again.";
        }
      });
  }

  private loadRecent(): RecentFeedbackEntry[] {
    try {
      const raw = localStorage.getItem(RECENT_STORAGE_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed.slice(0, 12) : [];
    } catch {
      return [];
    }
  }

  private saveRecent(entry: RecentFeedbackEntry): void {
    this.recentEntries = [entry, ...this.recentEntries.filter((e) => e.id !== entry.id)].slice(0, 12);
    try {
      localStorage.setItem(RECENT_STORAGE_KEY, JSON.stringify(this.recentEntries));
    } catch {
      // Ignore storage errors
    }
  }
}
