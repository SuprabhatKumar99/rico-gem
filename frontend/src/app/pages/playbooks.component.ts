import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { ApiService } from "../core/api.service";
import { AuthService } from "../core/auth.service";
import { Playbook, PlaybookStep } from "../models";

@Component({
  selector: "rk-playbooks",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="space-y-6">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p class="text-xs font-bold uppercase tracking-widest text-slate-400">Interactive resolution</p>
          <h1 class="mt-1 text-3xl font-bold tracking-tight">Guided playbooks</h1>
          <p class="mt-2 text-sm text-slate-500">
            Build and run branching troubleshooting workflows that guide agents and customers directly to verified resolution articles.
          </p>
        </div>

        <button
          *ngIf="canEdit"
          type="button"
          class="rk-btn rk-btn-primary"
          (click)="showBuilder = !showBuilder"
        >
          {{ showBuilder ? "Close builder" : "New playbook" }}
        </button>
      </div>

      <div *ngIf="message" role="status" class="rounded-xl border border-emerald-200 bg-emerald-50 p-3.5 text-sm text-emerald-800">
        {{ message }}
      </div>
      <div *ngIf="error" role="alert" class="rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-sm text-rose-700">
        {{ error }}
      </div>

      <section *ngIf="showBuilder" class="rk-card p-6">
        <h2 class="text-lg font-bold">Create a 2-branch troubleshooting playbook</h2>
        <p class="mt-1 text-xs text-slate-500">Define a starting diagnostic question and two immediate resolution paths.</p>

        <form class="mt-5 grid gap-4 md:grid-cols-2" (ngSubmit)="createPlaybook()">
          <div>
            <label class="rk-label" for="pb-title">Playbook title</label>
            <input id="pb-title" name="pbTitle" class="rk-input" [(ngModel)]="builder.title" placeholder="e.g. Troubleshoot API 401 Errors" required />
          </div>
          <div>
            <label class="rk-label" for="pb-cat">Category</label>
            <input id="pb-cat" name="pbCat" class="rk-input" [(ngModel)]="builder.category" placeholder="API & Integrations" />
          </div>
          <div class="md:col-span-2">
            <label class="rk-label" for="pb-summary">Summary</label>
            <input id="pb-summary" name="pbSummary" class="rk-input" [(ngModel)]="builder.summary" placeholder="Isolate expired tokens, wrong scopes, and IP allowlist rejections." />
          </div>
          <div class="md:col-span-2">
            <label class="rk-label" for="pb-q">Initial diagnostic question</label>
            <input id="pb-q" name="pbQuestion" class="rk-input" [(ngModel)]="builder.question" placeholder="Does the request include a valid Bearer or X-API-Key header?" required />
          </div>

          <div class="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
            <p class="text-xs font-bold uppercase tracking-wider text-slate-500">Branch A</p>
            <input class="rk-input !mt-0 bg-white" name="optALabel" [(ngModel)]="builder.optionALabel" placeholder="Option label (e.g. Yes, header is present)" required />
            <input class="rk-input !mt-0 bg-white" name="optATitle" [(ngModel)]="builder.optionAResTitle" placeholder="Resolution title" required />
            <textarea class="rk-textarea !mt-0 bg-white min-h-[76px]" name="optASummary" [(ngModel)]="builder.optionAResSummary" placeholder="Resolution instructions…" required></textarea>
          </div>

          <div class="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
            <p class="text-xs font-bold uppercase tracking-wider text-slate-500">Branch B</p>
            <input class="rk-input !mt-0 bg-white" name="optBLabel" [(ngModel)]="builder.optionBLabel" placeholder="Option label (e.g. No / header missing)" required />
            <input class="rk-input !mt-0 bg-white" name="optBTitle" [(ngModel)]="builder.optionBResTitle" placeholder="Resolution title" required />
            <textarea class="rk-textarea !mt-0 bg-white min-h-[76px]" name="optBSummary" [(ngModel)]="builder.optionBResSummary" placeholder="Resolution instructions…" required></textarea>
          </div>

          <div class="md:col-span-2 flex justify-end gap-2">
            <button type="button" class="rk-btn rk-btn-secondary" (click)="showBuilder = false">Cancel</button>
            <button type="submit" class="rk-btn rk-btn-primary" [disabled]="saving">
              {{ saving ? "Creating…" : "Publish playbook" }}
            </button>
          </div>
        </form>
      </section>

      <div class="grid gap-6 lg:grid-cols-[360px_minmax(0,1fr)]">
        <aside class="space-y-3">
          <p class="text-xs font-bold uppercase tracking-widest text-slate-400">Available playbooks ({{ playbooks.length }})</p>
          <button
            *ngFor="let pb of playbooks"
            type="button"
            class="block w-full rounded-2xl border p-5 text-left transition"
            [class.border-slate-900]="selected?.id === pb.id"
            [class.bg-slate-900]="selected?.id === pb.id"
            [class.text-white]="selected?.id === pb.id"
            [class.border-slate-200]="selected?.id !== pb.id"
            [class.bg-white]="selected?.id !== pb.id"
            [class.hover:border-slate-300]="selected?.id !== pb.id"
            (click)="selectPlaybook(pb)"
          >
            <div class="flex items-center justify-between gap-2">
              <span
                class="text-[11px] font-bold uppercase tracking-wider"
                [class.text-slate-300]="selected?.id === pb.id"
                [class.text-slate-400]="selected?.id !== pb.id"
              >
                {{ pb.category }}
              </span>
              <span
                class="text-xs font-semibold"
                [class.text-emerald-300]="selected?.id === pb.id"
                [class.text-emerald-700]="selected?.id !== pb.id"
              >
                {{ resolutionRate(pb) }}% resolved
              </span>
            </div>
            <p class="mt-2 text-base font-bold">{{ pb.title }}</p>
            <p
              class="mt-1.5 text-xs leading-5"
              [class.text-slate-300]="selected?.id === pb.id"
              [class.text-slate-500]="selected?.id !== pb.id"
            >
              {{ pb.summary }}
            </p>
            <p
              class="mt-3 text-[11px]"
              [class.text-slate-400]="selected?.id === pb.id"
              [class.text-slate-400]="selected?.id !== pb.id"
            >
              {{ pb.runsCount }} runs · {{ pb.steps.length }} nodes
            </p>
          </button>
        </aside>

        <section *ngIf="selected && currentStep" class="rk-card p-6 sm:p-8">
          <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-5">
            <div>
              <p class="text-xs font-bold uppercase tracking-widest text-slate-400">
                {{ selected.category }} · Step {{ history.length + 1 }}
              </p>
              <h2 class="mt-1 text-2xl font-bold tracking-tight">{{ selected.title }}</h2>
            </div>

            <div class="flex items-center gap-2">
              <button
                *ngIf="history.length"
                type="button"
                class="rk-btn rk-btn-secondary text-xs"
                (click)="goBack()"
              >
                ← Back
              </button>
              <button
                type="button"
                class="rk-btn rk-btn-secondary text-xs"
                (click)="restart()"
              >
                Restart
              </button>
              <button
                *ngIf="canEdit && playbooks.length > 1"
                type="button"
                class="rk-btn rk-btn-secondary text-xs text-rose-600"
                (click)="deleteSelected()"
              >
                Delete
              </button>
            </div>
          </div>

          <div *ngIf="trailLabels.length" class="mt-4 flex flex-wrap items-center gap-2 text-xs text-slate-500">
            <span class="font-semibold text-slate-400">Path:</span>
            <span *ngFor="let crumb of trailLabels; let last = last" class="inline-flex items-center gap-1.5">
              <span class="rounded-lg bg-slate-100 px-2.5 py-1 font-medium text-slate-700">{{ crumb }}</span>
              <span *ngIf="!last" class="text-slate-400">→</span>
            </span>
          </div>

          <div class="mt-6">
            <ng-container *ngIf="!currentStep.resolution; else resolutionView">
              <h3 class="text-xl font-bold text-slate-950">{{ currentStep.prompt }}</h3>
              <p *ngIf="currentStep.guidance" class="mt-2 text-sm leading-6 text-slate-500">
                {{ currentStep.guidance }}
              </p>

              <div class="mt-6 space-y-3">
                <button
                  *ngFor="let opt of currentStep.options"
                  type="button"
                  class="flex w-full items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-white px-5 py-4 text-left text-sm font-semibold text-slate-800 transition hover:border-slate-900 hover:bg-slate-50"
                  (click)="chooseOption(opt.label, opt.nextStepId)"
                >
                  <span>{{ opt.label }}</span>
                  <span class="text-slate-400">→</span>
                </button>
              </div>
            </ng-container>

            <ng-template #resolutionView>
              <div class="rounded-2xl border border-emerald-200 bg-emerald-50/70 p-6">
                <p class="text-xs font-bold uppercase tracking-widest text-emerald-700">Recommended resolution</p>
                <h3 class="mt-2 text-xl font-bold text-slate-950">{{ currentStep.resolution!.title }}</h3>
                <p class="mt-3 text-sm leading-7 text-slate-700">{{ currentStep.resolution!.summary }}</p>

                <div class="mt-5 flex flex-wrap items-center gap-3">
                  <a
                    *ngIf="currentStep.resolution!.articleSlug"
                    [routerLink]="['/portal/article', currentStep.resolution!.articleSlug]"
                    [queryParams]="{ tenant: 'demo' }"
                    class="rk-btn rk-btn-primary text-xs"
                  >
                    {{ currentStep.resolution!.actionLabel || "Open linked article" }} →
                  </a>

                  <button
                    type="button"
                    class="rk-btn rk-btn-secondary text-xs"
                    [disabled]="outcomeRecorded"
                    (click)="recordOutcome('RESOLVED')"
                  >
                    {{ outcomeRecorded ? "Outcome recorded ✓" : "Mark resolved" }}
                  </button>

                  <button
                    *ngIf="!outcomeRecorded"
                    type="button"
                    class="rk-btn rk-btn-secondary text-xs"
                    (click)="recordOutcome('ESCALATED')"
                  >
                    Escalate to support
                  </button>
                </div>
              </div>
            </ng-template>
          </div>
        </section>
      </div>
    </div>
  `
})
export class PlaybooksComponent implements OnInit {
  playbooks: Playbook[] = [];
  selected?: Playbook;
  currentStep?: PlaybookStep;
  history: PlaybookStep[] = [];
  trailLabels: string[] = [];
  outcomeRecorded = false;

  showBuilder = false;
  saving = false;
  message = "";
  error = "";

  builder = {
    title: "",
    category: "API & Integrations",
    summary: "",
    question: "",
    optionALabel: "",
    optionAResTitle: "",
    optionAResSummary: "",
    optionBLabel: "",
    optionBResTitle: "",
    optionBResSummary: ""
  };

  constructor(
    private readonly api: ApiService,
    private readonly auth: AuthService
  ) {}

  get canEdit(): boolean {
    return this.auth.hasAnyRole(["ADMIN", "EDITOR"]);
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.playbooks().subscribe({
      next: (items) => {
        this.playbooks = items;
        if (!this.selected && items.length) {
          this.selectPlaybook(items[0]);
        } else if (this.selected) {
          const refreshed = items.find((p) => p.id === this.selected!.id);
          if (refreshed) this.selected = refreshed;
        }
      },
      error: () => (this.error = "Unable to load playbooks.")
    });
  }

  selectPlaybook(pb: Playbook): void {
    this.selected = pb;
    this.history = [];
    this.trailLabels = [];
    this.outcomeRecorded = false;
    this.currentStep = pb.steps[0];
  }

  chooseOption(label: string, nextStepId: string): void {
    if (!this.selected || !this.currentStep) return;
    const next = this.selected.steps.find((s) => s.id === nextStepId);
    if (!next) return;
    this.history.push(this.currentStep);
    this.trailLabels.push(label);
    this.currentStep = next;
    this.outcomeRecorded = false;
  }

  goBack(): void {
    const prev = this.history.pop();
    this.trailLabels.pop();
    if (prev) {
      this.currentStep = prev;
      this.outcomeRecorded = false;
    }
  }

  restart(): void {
    if (this.selected) this.selectPlaybook(this.selected);
  }

  recordOutcome(outcome: "RESOLVED" | "ESCALATED"): void {
    if (!this.selected || this.outcomeRecorded) return;
    this.api.recordPlaybookRun(this.selected.id, outcome).subscribe({
      next: (updated) => {
        this.outcomeRecorded = true;
        if (this.selected) {
          this.selected.runsCount = updated.runsCount;
          this.selected.resolvedCount = updated.resolvedCount;
        }
        this.message = outcome === "RESOLVED"
          ? "Playbook run marked as resolved."
          : "Playbook run logged for support escalation.";
      },
      error: () => (this.error = "Unable to record playbook run.")
    });
  }

  createPlaybook(): void {
    this.error = "";
    this.message = "";
    if (!this.builder.title.trim() || !this.builder.question.trim()) {
      this.error = "Title and starting question are required.";
      return;
    }
    this.saving = true;
    const steps: PlaybookStep[] = [
      {
        id: "start",
        prompt: this.builder.question.trim(),
        options: [
          { label: this.builder.optionALabel.trim() || "Path A", nextStepId: "res-a" },
          { label: this.builder.optionBLabel.trim() || "Path B", nextStepId: "res-b" }
        ]
      },
      {
        id: "res-a",
        prompt: "Resolution A",
        resolution: {
          title: this.builder.optionAResTitle.trim() || "Apply Path A fix",
          summary: this.builder.optionAResSummary.trim() || "Verify configuration and retry."
        }
      },
      {
        id: "res-b",
        prompt: "Resolution B",
        resolution: {
          title: this.builder.optionBResTitle.trim() || "Apply Path B fix",
          summary: this.builder.optionBResSummary.trim() || "Check workspace settings and retry."
        }
      }
    ];
    this.api.createPlaybook({
      title: this.builder.title.trim(),
      summary: this.builder.summary.trim() || "Interactive guided troubleshooting flow.",
      category: this.builder.category.trim() || "General",
      published: true,
      steps
    }).subscribe({
      next: (created) => {
        this.saving = false;
        this.showBuilder = false;
        this.builder = {
          title: "",
          category: "API & Integrations",
          summary: "",
          question: "",
          optionALabel: "",
          optionAResTitle: "",
          optionAResSummary: "",
          optionBLabel: "",
          optionBResTitle: "",
          optionBResSummary: ""
        };
        this.message = `Playbook "${created.title}" published.`;
        this.playbooks = [created, ...this.playbooks];
        this.selectPlaybook(created);
      },
      error: (e) => {
        this.saving = false;
        this.error = e?.error?.message || "Unable to create playbook.";
      }
    });
  }

  deleteSelected(): void {
    if (!this.selected) return;
    const id = this.selected.id;
    this.api.deletePlaybook(id).subscribe({
      next: () => {
        this.playbooks = this.playbooks.filter((p) => p.id !== id);
        this.selected = this.playbooks[0];
        if (this.selected) this.selectPlaybook(this.selected);
        this.message = "Playbook deleted.";
      },
      error: () => (this.error = "Unable to delete playbook.")
    });
  }

  resolutionRate(pb: Playbook): number {
    if (!pb.runsCount) return 100;
    return Math.round((pb.resolvedCount / pb.runsCount) * 100);
  }
}
