import { CommonModule } from "@angular/common";
import { Component } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { AuthService } from "../core/auth.service";
import { ApiService } from "../core/api.service";

@Component({
  selector: "rk-login",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="grid min-h-screen lg:grid-cols-[1.05fr_0.95fr]">
      <section class="hidden bg-slate-950 p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div class="flex items-center gap-3">
          <div class="grid h-10 w-10 place-items-center rounded-xl bg-white font-black text-slate-950">R</div>
          <span class="text-xl font-bold">RicozKnow</span>
        </div>

        <div class="max-w-xl">
          <p class="text-sm font-semibold uppercase tracking-widest text-slate-500">Knowledge platform</p>
          <h1 class="mt-5 text-5xl font-bold leading-tight tracking-tight">
            Put trusted answers closer to every question.
          </h1>
          <p class="mt-6 text-lg leading-8 text-slate-400">
            Search, author, review and govern your organization's published knowledge.
          </p>
        </div>

        <p class="text-sm text-slate-500">Search first. Content second. Administration third.</p>
      </section>

      <section class="flex items-center justify-center bg-white p-6 sm:p-10">
        <div class="w-full max-w-md">
          <div class="lg:hidden">
            <div class="grid h-10 w-10 place-items-center rounded-xl bg-slate-900 font-black text-white">R</div>
            <p class="mt-4 text-xl font-bold">RicozKnow</p>
          </div>

          <div class="mt-10 lg:mt-0">
            <p class="text-sm font-semibold text-slate-500">Welcome back</p>
            <h2 class="mt-2 text-3xl font-bold tracking-tight">Sign in to your workspace</h2>
            <p class="mt-2 text-sm leading-6 text-slate-500">
              The backend requires a tenant slug, email and password.
            </p>
          </div>

          <form class="mt-8 space-y-5" (ngSubmit)="submit()">
            <div>
              <label class="rk-label" for="tenantSlug">Tenant slug</label>
              <input id="tenantSlug" class="rk-input" [(ngModel)]="form.tenantSlug" name="tenantSlug" required />
            </div>

            <div>
              <label class="rk-label" for="email">Email</label>
              <input id="email" class="rk-input" type="email" [(ngModel)]="form.email" name="email" required />
            </div>

            <div>
              <label class="rk-label" for="password">Password</label>
              <input id="password" class="rk-input" type="password" [(ngModel)]="form.password" name="password" required />
            </div>

            <div *ngIf="mfaRequired" class="rounded-xl border border-amber-200 bg-amber-50 p-4">
              <label class="rk-label" for="otp">Authenticator code</label>
              <input
                id="otp"
                class="rk-input mt-2 text-center tracking-[0.35em]"
                type="text"
                inputmode="numeric"
                autocomplete="one-time-code"
                maxlength="6"
                pattern="[0-9]{6}"
                [(ngModel)]="form.otp"
                name="otp"
                placeholder="000000"
                required
                autofocus
              />
              <p class="mt-2 text-xs leading-5 text-amber-800">
                MFA is enabled for this account. Open your authenticator app and enter the current 6-digit code.
              </p>
            </div>

            <div
              *ngIf="error"
              role="alert"
              class="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm leading-5 text-rose-700"
            >
              {{ error }}
            </div>

            <button class="rk-btn rk-btn-primary w-full" [disabled]="busy">
              {{ busy ? "Signing in…" : "Sign in" }}
            </button>
          </form>

          <div class="mt-6 space-y-3">
            <div class="flex items-center gap-3 text-xs text-slate-500"><span class="h-px flex-1 bg-slate-200"></span>Or continue with<span class="h-px flex-1 bg-slate-200"></span></div>
            <div class="grid grid-cols-2 gap-3">
              <button type="button" class="rk-btn rk-btn-secondary" [disabled]="busy" (click)="social('google')">Google</button>
              <button type="button" class="rk-btn rk-btn-secondary" [disabled]="busy" (click)="social('microsoft')">Microsoft</button>
            </div>
            <p class="text-xs text-slate-500">Enter your workspace tenant slug first. To use social login with an existing password account, link it from Account Security.</p>
          </div>

          <div class="mt-5 flex flex-wrap gap-x-4 gap-y-2 text-sm">
            <a routerLink="/account/forgot" class="font-semibold text-slate-700 underline underline-offset-4">Forgot password?</a>
            <a routerLink="/account/resend" class="font-semibold text-slate-700 underline underline-offset-4">Verify email / resend</a>
          </div>

          <p class="mt-5 text-sm text-slate-500">
            New organization?
            <a routerLink="/register" class="font-semibold text-slate-950 underline underline-offset-4">
              Register
            </a>
          </p>
        </div>
      </section>
    </div>
  `
})
export class LoginComponent {
  form = {
    tenantSlug: "",
    email: "",
    password: "",
    otp: ""
  };

  busy = false;
  error = "";
  mfaRequired = false;

  constructor(
    private readonly auth: AuthService,
    private readonly api: ApiService,
    private readonly router: Router
  ) {}

  social(provider: "google" | "microsoft"): void {
    this.error = "";
    if (!/^[a-zA-Z0-9-]{3,100}$/.test(this.form.tenantSlug.trim())) {
      this.error = "Enter your tenant slug before using social sign-in.";
      return;
    }
    this.busy = true;
    this.authSocialStart(provider);
  }

  private authSocialStart(provider: "google" | "microsoft"): void {
    this.api.socialStart(provider, this.form.tenantSlug).subscribe({
      next: res => {
        sessionStorage.setItem("rk_social_state", res.state);
        window.location.assign(res.authorizationUrl);
      },
      error: err => { this.busy = false; this.error = err?.error?.message || "Social sign-in is unavailable for this workspace."; }
    });
  }

  submit(): void {
    this.error = "";

    if (this.mfaRequired && !/^\d{6}$/.test(this.form.otp.trim())) {
      this.error = "Enter the 6-digit code from your authenticator app.";
      return;
    }

    this.busy = true;

    this.auth.login({
      tenantSlug: this.form.tenantSlug,
      email: this.form.email,
      password: this.form.password,
      ...(this.form.otp.trim() ? { otp: this.form.otp.trim() } : {})
    }).subscribe({
      next: () => {
        this.mfaRequired = false;
        void this.router.navigateByUrl("/dashboard");
      },
      error: (error) => {
        const message = error?.error?.message || "Sign in failed. Check your tenant, email and password.";
        const normalized = String(message).toLowerCase();

        if (normalized.includes("mfa verification required") || normalized.includes("mfa")) {
          this.mfaRequired = true;
          this.error = "MFA verification is required. Enter the current code from your authenticator app.";
        } else {
          this.error =
            error?.status === 401
              ? `${message} The tenant slug must match the one used at registration, in lowercase and without spaces.`
              : message;
        }

        this.busy = false;
      },
      complete: () => {
        this.busy = false;
      }
    });
  }
}
