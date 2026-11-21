import { CommonModule } from "@angular/common";
import { Component, Input, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";
import { AuthService } from "../core/auth.service";
import { Category } from "../models";

const RING_RADIUS = 15.5;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

@Component({
  selector: "rk-coverage-ring",
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="group/coverage relative inline-flex shrink-0 items-center gap-2">
      <svg class="h-7 w-7 -rotate-90" viewBox="0 0 36 36" aria-hidden="true">
        <circle
          cx="18"
          cy="18"
          [attr.r]="radius"
          fill="none"
          stroke="#e2e8f0"
          stroke-width="4"
          [attr.stroke-dasharray]="hasValue ? null : '2 4'"
        />
        <circle
          *ngIf="hasValue"
          cx="18"
          cy="18"
          [attr.r]="radius"
          fill="none"
          stroke="#10b981"
          stroke-width="4"
          stroke-linecap="round"
          [attr.stroke-dasharray]="circumference"
          [attr.stroke-dashoffset]="dashOffset"
        />
      </svg>

      <span *ngIf="hasValue" class="text-xs font-bold text-slate-600">{{ roundedPercent }}%</span>
      <span *ngIf="!hasValue" class="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
        Coming soon
      </span>

      <span
        role="tooltip"
        class="pointer-events-none absolute bottom-full left-0 z-30 mb-2 w-56 rounded-lg bg-slate-900 px-2.5 py-1.5 text-left text-[11px] font-medium leading-4 text-white opacity-0 shadow-lg transition group-hover/coverage:opacity-100"
      >
        <span class="mb-0.5 block font-bold text-white/70">Topic coverage</span>
        {{ tooltip }}
      </span>
    </span>
  `
})
export class CoverageRingComponent {
  @Input() percent: number | null = null;

  readonly radius = RING_RADIUS;
  readonly circumference = RING_CIRCUMFERENCE;

  get hasValue(): boolean {
    return this.percent !== null && this.percent !== undefined && !Number.isNaN(this.percent);
  }

  get clampedPercent(): number {
    const value = this.percent ?? 0;
    return Math.min(100, Math.max(0, value));
  }

  get roundedPercent(): number {
    return Math.round(this.clampedPercent);
  }

  get dashOffset(): number {
    return RING_CIRCUMFERENCE * (1 - this.clampedPercent / 100);
  }

  get tooltip(): string {
    return this.hasValue
      ? `${this.roundedPercent}% of expected topics have an article in this category.`
      : "Coverage data coming soon - the backend does not report expected topics per category yet.";
  }
}

@Component({
  selector: "rk-categories",
  standalone: true,
  imports: [CommonModule, FormsModule, CoverageRingComponent],
  template: `
    <div>
      <div>
        <p class="text-sm font-semibold text-slate-500">Knowledge Studio</p>
        <h1 class="mt-1 text-3xl font-bold tracking-tight">Categories</h1>
        <p class="mt-2 text-sm text-slate-500">Organize your articles into topics so people can browse, not just search.</p>
      </div>

      <div class="mt-7 grid gap-6 xl:grid-cols-[360px_1fr]">
        <form *ngIf="canManage" class="rk-card p-6" (ngSubmit)="save()">
          <h2 class="font-bold">{{ editingId ? "Edit category" : "New category" }}</h2>

          <div class="mt-5 space-y-4">
            <div>
              <label class="rk-label" for="name">Name</label>
              <input id="name" class="rk-input" [(ngModel)]="form.name" name="name" required />
            </div>

            <div>
              <label class="rk-label" for="slug">Slug</label>
              <input id="slug" class="rk-input" [(ngModel)]="form.slug" name="slug" required />
            </div>

            <div>
              <label class="rk-label" for="parent">Parent</label>
              <select id="parent" class="rk-select" [(ngModel)]="form.parentId" name="parent">
                <option value="">No parent</option>
                <option *ngFor="let category of list" [value]="category.id">
                  {{ category.name }}
                </option>
              </select>
            </div>

            <div>
              <label class="rk-label" for="sortOrder">Sort order</label>
              <input id="sortOrder" class="rk-input" type="number" [(ngModel)]="form.sortOrder" name="sortOrder" />
            </div>

            <button class="rk-btn rk-btn-primary w-full">
              {{ editingId ? "Update category" : "Create category" }}
            </button>

            <button
              *ngIf="editingId"
              type="button"
              class="rk-btn rk-btn-secondary w-full"
              (click)="reset()"
            >
              Cancel
            </button>
          </div>
        </form>

        <section class="rk-card overflow-hidden">
          <div class="overflow-x-auto">
            <table class="w-full min-w-[620px] text-left text-sm">
              <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th class="px-5 py-3">Name</th>
                  <th class="px-5 py-3">Slug</th>
                  <th class="px-5 py-3">Tags</th>
                  <th class="px-5 py-3">Parent</th>
                  <th class="px-5 py-3">Order</th>
                  <th class="px-5 py-3"></th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr *ngFor="let category of list">
                  <td class="px-5 py-4">
                    <div class="flex items-center gap-3">
                      <span class="font-semibold">{{ category.name }}</span>
                      <rk-coverage-ring [percent]="coverageFor(category)" />
                    </div>
                  </td>
                  <td class="px-5 py-4 text-slate-500">{{ primarySlug(category.slug) }}</td>
                  <td class="px-5 py-4">
                    <span
                      *ngFor="let tag of extraTags(category.slug)"
                      class="mr-1.5 inline-flex rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 last:mr-0"
                    >
                      {{ tag }}
                    </span>
                  </td>
                  <td class="px-5 py-4 text-slate-500">{{ parentName(category.parentId) }}</td>
                  <td class="px-5 py-4">{{ category.sortOrder }}</td>
                  <td class="px-5 py-4 text-right">
                    <button *ngIf="canManage" class="rk-btn rk-btn-secondary mr-2" (click)="edit(category)">Edit</button>
                    <button *ngIf="isAdmin" class="rk-btn rk-btn-danger" (click)="remove(category)">Delete</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  `
})
export class CategoriesComponent implements OnInit {
  list: Category[] = [];
  editingId = "";

  // Expected topic coverage is not returned by the API yet, so this stays empty and every
  // ring renders in its disabled state. Once the backend reports coverage per category,
  // fill this map (keyed by category id) and the rings switch to real percentages.
  private readonly coverageByCategory = new Map<string, number>();

  form = {
    name: "",
    slug: "",
    parentId: "",
    sortOrder: 0
  };

  constructor(private readonly api: ApiService, readonly auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.hasAnyRole(["ADMIN", "EDITOR"]);
  }

  get isAdmin(): boolean {
    return this.auth.hasRole("ADMIN");
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.categories().subscribe((items) => {
      this.list = items;
    });
  }

  edit(category: Category): void {
    this.editingId = category.id;
    this.form = {
      name: category.name,
      slug: category.slug,
      parentId: category.parentId ?? "",
      sortOrder: category.sortOrder
    };
  }

  reset(): void {
    this.editingId = "";
    this.form = {
      name: "",
      slug: "",
      parentId: "",
      sortOrder: 0
    };
  }

  save(): void {
    const request = {
      name: this.form.name,
      slug: this.form.slug,
      parentId: this.form.parentId || null,
      sortOrder: this.form.sortOrder
    };

    const operation = this.editingId
      ? this.api.updateCategory(this.editingId, request)
      : this.api.createCategory(request);

    operation.subscribe(() => {
      this.reset();
      this.load();
    });
  }

  remove(category: Category): void {
    if (!confirm(`Delete ${category.name}?`)) {
      return;
    }

    this.api.deleteCategory(category.id).subscribe(() => this.load());
  }

  parentName(id: string | null): string {
    return this.list.find((category) => category.id === id)?.name ?? "—";
  }

  coverageFor(category: Category): number | null {
    const stored = this.coverageByCategory.get(category.id);

    return stored === undefined ? null : stored;
  }

  primarySlug(raw: string): string {
    return raw.split(",")[0]?.trim() ?? raw;
  }

  extraTags(raw: string): string[] {
    return raw
      .split(",")
      .slice(1)
      .map((tag) => tag.trim())
      .filter(Boolean);
  }
}
