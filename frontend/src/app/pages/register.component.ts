import { CommonModule } from "@angular/common";
import { Component } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { ApiService } from "../core/api.service";

@Component({
  selector: "rk-register",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-slate-950 px-4 py-10 sm:py-16">
      <div class="mx-auto max-w-3xl rounded-3xl bg-white p-6 sm:p-10">
        <div class="flex items-center gap-3">
          <div class="grid h-10 w-10 place-items-center rounded-xl bg-slate-900 font-black text-white">R</div>
          <span class="text-xl font-bold">RicozKnow</span>
        </div>

        <div class="mt-10">
          <p class="text-sm font-semibold text-slate-500">Tenant setup</p>
          <h1 class="mt-2 text-3xl font-bold tracking-tight">Create your organization</h1>
          <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
            These fields match the backend registration request exactly.
          </p>
        </div>

        <form class="mt-8 grid gap-5 sm:grid-cols-2" (ngSubmit)="submit()">
          <div class="sm:col-span-2">
            <label class="rk-label" for="organizationName">Organization name</label>
            <input id="organizationName" class="rk-input" [(ngModel)]="form.organizationName" name="organizationName" required />
          </div>

          <div>
            <label class="rk-label" for="tenantSlug">Tenant slug</label>
            <input id="tenantSlug" class="rk-input" [(ngModel)]="form.tenantSlug" name="tenantSlug" minlength="3" required />
          </div>

          <div>
            <label class="rk-label" for="subdomain">Subdomain</label>
            <input id="subdomain" class="rk-input" [(ngModel)]="form.subdomain" name="subdomain" minlength="3" required />
          </div>

          <div>
            <label class="rk-label" for="name">Admin name</label>
            <input id="name" class="rk-input" [(ngModel)]="form.name" name="name" required />
          </div>

          <div>
            <label class="rk-label" for="email">Email</label>
            <input id="email" class="rk-input" type="email" [(ngModel)]="form.email" name="email" required />
          </div>

          <div class="sm:col-span-2">
            <label class="rk-label" for="password">Password</label>
            <input id="password" class="rk-input" type="password" [(ngModel)]="form.password" name="password" minlength="8" required />
          </div>

          <div class="sm:col-span-2">
            <div *ngIf="error" role="alert" class="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">
              {{ error }}
            </div>

            <div *ngIf="success" role="status" class="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-700">
              <p class="font-semibold">{{ success }}</p>
              <p class="mt-1">Open the verification link sent to <strong>{{ form.email }}</strong>. You must verify your email before signing in.</p>
              <div class="mt-3 flex flex-wrap gap-3">
                <a routerLink="/account/resend" [queryParams]="{ tenant: form.tenantSlug, email: form.email }" class="font-semibold underline underline-offset-4">Resend verification email</a>
                <a routerLink="/login" class="font-semibold underline underline-offset-4">Go to sign in</a>
              </div>
            </div>

            <button *ngIf="!success" class="rk-btn rk-btn-primary mt-2 w-full" [disabled]="busy">{{ busy ? "Creating…" : "Create tenant" }}</button>
          </div>
        </form>

        <p class="mt-7 text-sm text-slate-500">
          Already registered?
          <a routerLink="/login" class="font-semibold text-slate-950 underline underline-offset-4">Sign in</a>
        </p>
      </div>
    </div>
  `
})
export class RegisterComponent {
  form = {
    organizationName: "",
    tenantSlug: "",
    subdomain: "",
    email: "",
    password: "",
    name: ""
  };

  error = "";
  success = "";
  busy = false;

  constructor(private readonly api: ApiService) {}

  submit(): void {
    this.error = "";
    this.success = "";
    this.busy = true;

    this.api.register(this.form).subscribe({
      next: (response) => {
        this.busy = false;
        this.success = response.message || "Registration successful. Check your email to verify your account.";
      },
      error: (error) => {
        this.busy = false;
        this.error = error?.error?.message || "Registration failed.";
      }
    });
  }
}
