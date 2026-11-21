import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { ApiService } from "../core/api.service";
import { AuthService } from "../core/auth.service";
import { Category, KnowledgeRequest } from "../models";

@Component({
  selector: "rk-knowledge-requests",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="space-y-6">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p class="text-xs font-bold uppercase tracking-widest text-slate-400">Editorial intake</p>
          <h1 class="mt-1 text-3xl font-bold tracking-tight">Knowledge requests</h1>
          <p class="mt-2 text-sm text-slate-500">
            Capture documentation demand from support, customers, and internal teams, prioritize by upvotes, and convert directly into governed article drafts.
          </p>
        </div>

        <button
          type="button"
          class="rk-btn rk-btn-primary"
          (click)="showCreate = !showCreate"
        >
          {{ showCreate ? "Close form" : "New request" }}
        </button>
      </div>

      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div class="rk-card p-5">
          <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">Open queue</p>
          <p class="mt-2 text-3xl font-bold">{{ openCount }}</p>
        </div>
        <div class="rk-card p-5">
          <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">Urgent &amp; High</p>
          <p class="mt-2 text-3xl font-bold text-rose-600">{{ urgentHighCount }}</p>
        </div>
        <div class="rk-card p-5">
          <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">Drafts created</p>
          <p class="mt-2 text-3xl font-bold text-indigo-600">{{ draftCreatedCount }}</p>
        </div>
        <div class="rk-card p-5">
          <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">Total demand votes</p>
          <p class="mt-2 text-3xl font-bold">{{ totalUpvotes }}</p>
        </div>
      </div>

      <div *ngIf="message" role="status" class="rounded-xl border border-emerald-200 bg-emerald-50 p-3.5 text-sm text-emerald-800">
        {{ message }}
      </div>
      <div *ngIf="error" role="alert" class="rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-sm text-rose-700">
        {{ error }}
      </div>

      <section *ngIf="showCreate" class="rk-card p-6">
        <h2 class="text-lg font-bold">Submit a knowledge request</h2>
        <p class="mt-1 text-xs text-slate-500">Describe the missing guide or outdated topic so editors can triage it.</p>

        <form class="mt-5 grid gap-4 md:grid-cols-2" (ngSubmit)="createRequest()">
          <div class="md:col-span-2">
            <label class="rk-label" for="req-title">Title</label>
            <input
              id="req-title"
              name="title"
              class="rk-input"
              [(ngModel)]="form.title"
              placeholder="e.g. Configure Okta SCIM Role Mapping"
              required
            />
          </div>

          <div class="md:col-span-2">
            <label class="rk-label" for="req-desc">Context &amp; customer questions</label>
            <textarea
              id="req-desc"
              name="description"
              class="rk-textarea min-h-[100px]"
              [(ngModel)]="form.description"
              placeholder="Share recurring ticket patterns, edge cases, or exact steps readers need."
            ></textarea>
          </div>

          <div>
            <label class="rk-label" for="req-priority">Priority</label>
            <select id="req-priority" name="priority" class="rk-select" [(ngModel)]="form.priority">
              <option value="URGENT">Urgent</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
          </div>

          <div>
            <label class="rk-label" for="req-source">Source</label>
            <select id="req-source" name="source" class="rk-select" [(ngModel)]="form.source">
              <option value="SUPPORT">Support escalation</option>
              <option value="CUSTOMER">Customer request</option>
              <option value="INTERNAL">Internal team</option>
            </select>
          </div>

          <div>
            <label class="rk-label" for="req-category">Target category</label>
            <select id="req-category" name="categoryId" class="rk-select" [(ngModel)]="form.categoryId">
              <option value="">Uncategorized</option>
              <option *ngFor="let cat of categories" [value]="cat.id">{{ cat.name }}</option>
            </select>
          </div>

          <div class="flex items-end justify-end gap-2">
            <button type="button" class="rk-btn rk-btn-secondary" (click)="showCreate = false">Cancel</button>
            <button type="submit" class="rk-btn rk-btn-primary" [disabled]="saving">
              {{ saving ? "Submitting…" : "Submit request" }}
            </button>
          </div>
        </form>
      </section>

      <section class="rk-card p-5">
        <div class="grid gap-3 md:grid-cols-[minmax(0,1fr)_180px_180px]">
          <input
            class="rk-input !mt-0"
            [(ngModel)]="search"
            name="searchRequests"
            placeholder="Filter requests by title or requester…"
            aria-label="Filter knowledge requests"
          />

          <select
            class="rk-select !mt-0"
            [(ngModel)]="statusFilter"
            name="statusFilter"
            aria-label="Filter by status"
            (ngModelChange)="load()"
          >
            <option value="">All statuses</option>
            <option value="OPEN">Open</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="DRAFT_CREATED">Draft created</option>
            <option value="COMPLETED">Completed</option>
            <option value="DECLINED">Declined</option>
          </select>

          <select
            class="rk-select !mt-0"
            [(ngModel)]="priorityFilter"
            name="priorityFilter"
            aria-label="Filter by priority"
            (ngModelChange)="load()"
          >
            <option value="">All priorities</option>
            <option value="URGENT">Urgent</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
        </div>

        <div *ngIf="!visibleRequests.length" class="mt-6 rounded-xl border border-dashed border-slate-200 p-10 text-center">
          <p class="font-semibold text-slate-800">No knowledge requests match your filters</p>
          <p class="mt-1 text-sm text-slate-500">Clear filters or create a new request for your editorial queue.</p>
        </div>

        <div class="mt-5 space-y-3">
          <article
            *ngFor="let item of visibleRequests"
            class="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 transition hover:border-slate-300 lg:flex-row lg:items-center"
          >
            <button
              type="button"
              class="flex shrink-0 flex-row items-center justify-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2 text-sm font-bold text-slate-800 hover:border-slate-900 hover:bg-slate-900 hover:text-white lg:w-16 lg:flex-col lg:gap-0.5"
              [attr.aria-label]="'Upvote ' + item.title"
              (click)="upvote(item)"
            >
              <span class="text-xs leading-none">▲</span>
              <span>{{ item.upvotes }}</span>
            </button>

            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <span
                  class="rounded-md px-2 py-0.5 text-[11px] font-bold uppercase tracking-wider"
                  [ngClass]="priorityClass(item.priority)"
                >
                  {{ item.priority }}
                </span>
                <span class="rounded-md bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-600">
                  {{ item.source }}
                </span>
                <span *ngIf="categoryName(item.categoryId)" class="text-xs font-medium text-slate-500">
                  {{ categoryName(item.categoryId) }}
                </span>
              </div>

              <h3 class="mt-2 text-base font-bold text-slate-950">{{ item.title }}</h3>
              <p class="mt-1 text-sm leading-6 text-slate-600">{{ item.description }}</p>
              <p class="mt-2 text-xs text-slate-400">
                Requested by {{ item.requesterEmail }} · Updated {{ item.updatedAt | date: "mediumDate" }}
              </p>
            </div>

            <div class="flex flex-wrap items-center gap-2 lg:shrink-0">
              <select
                class="rk-select !mt-0 !w-36 py-2 text-xs font-semibold"
                [ngModel]="item.status"
                [name]="'status-' + item.id"
                [attr.aria-label]="'Status for ' + item.title"
                (ngModelChange)="changeStatus(item, $event)"
              >
                <option value="OPEN">Open</option>
                <option value="IN_PROGRESS">In progress</option>
                <option value="DRAFT_CREATED">Draft created</option>
                <option value="COMPLETED">Completed</option>
                <option value="DECLINED">Declined</option>
              </select>

              <a
                *ngIf="item.linkedArticleId"
                [routerLink]="['/articles', item.linkedArticleId]"
                class="rk-btn rk-btn-secondary text-xs"
              >
                Open draft →
              </a>

              <button
                *ngIf="!item.linkedArticleId && canEdit"
                type="button"
                class="rk-btn rk-btn-primary text-xs"
                [disabled]="convertingId === item.id"
                (click)="convertToDraft(item)"
              >
                {{ convertingId === item.id ? "Creating…" : "Create draft article" }}
              </button>
            </div>
          </article>
        </div>
      </section>
    </div>
  `
})
export class KnowledgeRequestsComponent implements OnInit {
  requests: KnowledgeRequest[] = [];
  categories: Category[] = [];
  statusFilter = "";
  priorityFilter = "";
  search = "";
  showCreate = false;
  saving = false;
  convertingId: string | null = null;
  message = "";
  error = "";

  form = {
    title: "",
    description: "",
    categoryId: "",
    priority: "HIGH" as "LOW" | "MEDIUM" | "HIGH" | "URGENT",
    source: "SUPPORT" as "INTERNAL" | "SUPPORT" | "CUSTOMER"
  };

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService
  ) {}

  get canEdit(): boolean {
    return this.auth.hasAnyRole(["ADMIN", "EDITOR"]);
  }

  ngOnInit(): void {
    this.api.categories().subscribe({ next: (cats) => (this.categories = cats), error: () => undefined });
    this.load();
  }

  load(): void {
    this.api.knowledgeRequests({
      status: this.statusFilter || undefined,
      priority: this.priorityFilter || undefined
    }).subscribe({
      next: (items) => (this.requests = items),
      error: () => (this.error = "Unable to load knowledge requests.")
    });
  }

  get visibleRequests(): KnowledgeRequest[] {
    const q = this.search.trim().toLowerCase();
    if (!q) return this.requests;
    return this.requests.filter((r) =>
      `${r.title} ${r.description} ${r.requesterEmail}`.toLowerCase().includes(q)
    );
  }

  get openCount(): number {
    return this.requests.filter((r) => r.status === "OPEN" || r.status === "IN_PROGRESS").length;
  }

  get urgentHighCount(): number {
    return this.requests.filter(
      (r) => (r.priority === "URGENT" || r.priority === "HIGH") && r.status !== "COMPLETED" && r.status !== "DECLINED"
    ).length;
  }

  get draftCreatedCount(): number {
    return this.requests.filter((r) => !!r.linkedArticleId).length;
  }

  get totalUpvotes(): number {
    return this.requests.reduce((acc, r) => acc + r.upvotes, 0);
  }

  createRequest(): void {
    this.error = "";
    this.message = "";
    if (!this.form.title.trim()) {
      this.error = "Please provide a request title.";
      return;
    }
    this.saving = true;
    this.api.createKnowledgeRequest({
      title: this.form.title.trim(),
      description: this.form.description.trim(),
      categoryId: this.form.categoryId || null,
      priority: this.form.priority,
      source: this.form.source
    }).subscribe({
      next: () => {
        this.saving = false;
        this.showCreate = false;
        this.form = { title: "", description: "", categoryId: "", priority: "HIGH", source: "SUPPORT" };
        this.message = "Knowledge request added to the triage queue.";
        this.load();
      },
      error: (e) => {
        this.saving = false;
        this.error = e?.error?.message || "Could not create request.";
      }
    });
  }

  upvote(item: KnowledgeRequest): void {
    this.api.upvoteKnowledgeRequest(item.id).subscribe({
      next: (updated) => {
        item.upvotes = updated.upvotes;
        this.load();
      },
      error: () => (this.error = "Could not record upvote.")
    });
  }

  changeStatus(item: KnowledgeRequest, status: KnowledgeRequest["status"]): void {
    this.api.updateKnowledgeRequest(item.id, { status }).subscribe({
      next: (updated) => {
        item.status = updated.status;
        this.message = `Updated "${item.title}" to ${status.replace("_", " ")}.`;
      },
      error: () => (this.error = "Could not update status.")
    });
  }

  convertToDraft(item: KnowledgeRequest): void {
    this.convertingId = item.id;
    this.error = "";
    this.api.convertKnowledgeRequestToDraft(item.id).subscribe({
      next: (res) => {
        this.convertingId = null;
        item.linkedArticleId = res.article.id;
        item.status = "DRAFT_CREATED";
        this.message = `Draft article "${res.article.title}" created and linked.`;
      },
      error: (e) => {
        this.convertingId = null;
        this.error = e?.error?.message || "Could not create draft article.";
      }
    });
  }

  categoryName(categoryId: string | null): string {
    if (!categoryId) return "";
    return this.categories.find((c) => c.id === categoryId)?.name ?? "";
  }

  priorityClass(priority: KnowledgeRequest["priority"]): string {
    switch (priority) {
      case "URGENT":
        return "bg-rose-100 text-rose-700";
      case "HIGH":
        return "bg-amber-100 text-amber-800";
      case "MEDIUM":
        return "bg-indigo-50 text-indigo-700";
      default:
        return "bg-slate-100 text-slate-600";
    }
  }
}
