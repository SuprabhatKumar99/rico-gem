import { CommonModule } from "@angular/common";
import { Component, Input } from "@angular/core";
import { ThemeService } from "../core/theme.service";

@Component({
  selector: "rk-theme-toggle",
  standalone: true,
  imports: [CommonModule],
  template: `
    <button
      type="button"
      class="rk-theme-toggle inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white text-slate-700 transition hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      [class.h-10]="true"
      [class.w-10]="compact && !showLabel"
      [class.px-3]="!compact || showLabel"
      [attr.aria-label]="theme.isDark ? 'Switch to light mode' : 'Switch to dark mode'"
      [attr.aria-pressed]="theme.isDark"
      [attr.title]="theme.isDark ? 'Switch to light mode' : 'Switch to dark mode'"
      (click)="theme.toggle()"
    >
      <svg
        *ngIf="theme.isDark; else moonIcon"
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        class="h-4 w-4 shrink-0 text-amber-400"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2" />
        <path d="M12 20v2" />
        <path d="m4.93 4.93 1.41 1.41" />
        <path d="m17.66 17.66 1.41 1.41" />
        <path d="M2 12h2" />
        <path d="M20 12h2" />
        <path d="m6.34 17.66-1.41 1.41" />
        <path d="m19.07 4.93-1.41 1.41" />
      </svg>

      <ng-template #moonIcon>
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="h-4 w-4 shrink-0 text-slate-700"
          aria-hidden="true"
        >
          <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" />
        </svg>
      </ng-template>

      <span *ngIf="showLabel" class="text-xs font-semibold">
        {{ theme.isDark ? "Light" : "Dark" }}
      </span>
    </button>
  `
})
export class ThemeToggleComponent {
  @Input() compact = true;
  @Input() showLabel = false;

  constructor(readonly theme: ThemeService) {}
}
