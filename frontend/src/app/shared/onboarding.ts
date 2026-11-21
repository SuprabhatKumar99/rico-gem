import { CommonModule } from "@angular/common";
import { Component, Input, Output, EventEmitter } from "@angular/core";

export interface OnboardingStep {
  title: string;
  body: string;
}

@Component({
  selector: "rk-onboarding",
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      *ngIf="visible"
      class="fixed inset-0 z-50 grid place-items-center bg-slate-950/40 p-4"
    >
      <div class="rk-card w-full max-w-md p-6">
        <p class="text-xs font-bold uppercase tracking-widest text-slate-400">
          Step {{ index + 1 }} of {{ steps.length }}
        </p>

        <h2 class="mt-2 text-xl font-bold tracking-tight">
          {{ steps[index].title }}
        </h2>

        <p class="mt-3 text-sm leading-6 text-slate-500">
          {{ steps[index].body }}
        </p>

        <div class="mt-4 flex gap-1.5">
          <span
            *ngFor="let step of steps; let i = index"
            class="h-1.5 flex-1 rounded-full"
            [class.bg-slate-900]="i <= index"
            [class.bg-slate-200]="i > index"
          ></span>
        </div>

        <div class="mt-6 flex items-center justify-between gap-3">
          <button
            *ngIf="index > 0"
            type="button"
            class="text-sm font-semibold text-slate-500 hover:text-slate-700"
            (click)="index = index - 1"
          >
            Back
          </button>

          <span *ngIf="index === 0" class="text-sm text-slate-400">Getting started</span>

          <button class="rk-btn rk-btn-primary ml-auto" type="button" (click)="next()">
            {{ index < steps.length - 1 ? "Next" : "Get started" }}
          </button>
        </div>

        <button
          type="button"
          class="mt-4 w-full text-xs font-medium text-slate-400 hover:text-slate-600"
          (click)="finish()"
        >
          Skip tour
        </button>
      </div>
    </div>
  `
})
export class OnboardingComponent {
  @Input() steps: OnboardingStep[] = [];
  @Input() visible = false;
  @Output() done = new EventEmitter();

  index = 0;

  next(): void {
    if (this.index < this.steps.length - 1) {
      this.index = this.index + 1;
      return;
    }

    this.finish();
  }

  finish(): void {
    this.done.emit();
  }
}
