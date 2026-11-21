import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { Observable } from "rxjs";
import { ApiService } from "../core/api.service";

@Component({
  selector: "rk-auth-actions",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <main class="min-h-screen bg-slate-50 px-4 py-10">
      <div class="mx-auto max-w-lg">
        <a routerLink="/login" class="text-sm font-semibold text-slate-500">← Back to sign in</a>
        <section class="rk-card mt-5 p-7">
          <div class="mb-6 flex items-center gap-3">
            <div class="grid h-10 w-10 place-items-center rounded-xl bg-slate-900 font-black text-white">R</div>
            <div><p class="font-bold">RicozKnow</p><p class="text-xs text-slate-500">Account security</p></div>
          </div>

          <h1 class="text-2xl font-bold">{{ title }}</h1>
          <p class="mt-2 text-sm leading-6 text-slate-500">{{ description }}</p>

          <form *ngIf="!(mode === 'verify' && token)" class="mt-6 space-y-4" (ngSubmit)="submit()">
            <ng-container [ngSwitch]="mode">
              <ng-container *ngSwitchCase="'verify'">
                <label class="rk-label" for="verifyToken">Verification token</label>
                <input id="verifyToken" name="verifyToken" class="rk-input" [(ngModel)]="token" placeholder="Paste the token from your email" required />
              </ng-container>

              <ng-container *ngSwitchCase="'resend'">
                <label class="rk-label" for="resendTenant">Tenant slug</label>
                <input id="resendTenant" name="resendTenant" class="rk-input" [(ngModel)]="tenantSlug" placeholder="your-tenant" required />
                <label class="rk-label" for="resendEmail">Email</label>
                <input id="resendEmail" name="resendEmail" class="rk-input" [(ngModel)]="email" type="email" autocomplete="email" placeholder="you@example.com" required />
              </ng-container>

              <ng-container *ngSwitchCase="'forgot'">
                <label class="rk-label" for="forgotTenant">Tenant slug</label>
                <input id="forgotTenant" name="forgotTenant" class="rk-input" [(ngModel)]="tenantSlug" placeholder="your-tenant" required />
                <label class="rk-label" for="forgotEmail">Email</label>
                <input id="forgotEmail" name="forgotEmail" class="rk-input" [(ngModel)]="email" type="email" autocomplete="email" placeholder="you@example.com" required />
              </ng-container>

              <ng-container *ngSwitchCase="'reset'">
                <label class="rk-label" for="resetToken">Reset token</label>
                <input id="resetToken" name="resetToken" class="rk-input" [(ngModel)]="token" placeholder="Paste the token from your email" required />
                <label class="rk-label" for="resetPassword">New password</label>
                <input id="resetPassword" name="resetPassword" class="rk-input" [(ngModel)]="password" type="password" autocomplete="new-password" placeholder="Minimum 12 characters" minlength="12" required />
                <p class="text-xs text-slate-500">Use at least 12 characters and avoid reusing an old password.</p>
              </ng-container>

              <ng-container *ngSwitchCase="'invite'">
                <div *ngIf="token; else manualInviteToken" class="rounded-xl border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
                  Your invitation link is ready. Complete the form below to create your account.
                </div>
                <ng-template #manualInviteToken>
                  <label class="rk-label" for="inviteToken">Invitation token</label>
                  <input id="inviteToken" name="inviteToken" class="rk-input" [(ngModel)]="token" placeholder="Paste the invitation token" required />
                </ng-template>
                <label class="rk-label" for="inviteName">Name</label>
                <input id="inviteName" name="inviteName" class="rk-input" [(ngModel)]="name" autocomplete="name" placeholder="Your name" required />
                <label class="rk-label" for="invitePassword">Password</label>
                <input id="invitePassword" name="invitePassword" class="rk-input" [(ngModel)]="password" type="password" autocomplete="new-password" placeholder="Minimum 12 characters" minlength="12" required />
              </ng-container>
            </ng-container>

            <button class="rk-btn rk-btn-primary w-full" type="submit" [disabled]="busy">
              {{ busy ? "Working…" : actionLabel }}
            </button>
          </form>

          <p *ngIf="message" class="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm leading-5 text-emerald-700">{{ message }}</p>
          <p *ngIf="error" class="mt-5 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm leading-5 text-rose-700">{{ error }}</p>

          <a *ngIf="mode === 'verify' && message" routerLink="/login" class="mt-5 inline-flex rk-btn rk-btn-primary">Continue to sign in</a>

          <div class="mt-6 flex flex-wrap gap-x-4 gap-y-2 text-sm">
            <a *ngIf="mode !== 'forgot' && mode !== 'reset'" routerLink="/account/forgot" class="font-semibold text-slate-700 underline underline-offset-4">Forgot password?</a>
            <a *ngIf="mode !== 'resend'" routerLink="/account/resend" class="font-semibold text-slate-700 underline underline-offset-4">Resend verification</a>
            <a routerLink="/login" class="font-semibold text-slate-700 underline underline-offset-4">Sign in</a>
          </div>
        </section>
      </div>
    </main>
  `
})
export class AuthActionsComponent implements OnInit {
  mode: "verify" | "resend" | "forgot" | "reset" | "invite" = "verify";
  token = "";
  tenantSlug = "";
  email = "";
  password = "";
  name = "";
  busy = false;
  message = "";
  error = "";

  constructor(private readonly route: ActivatedRoute, private readonly router: Router, private readonly api: ApiService) {}

  ngOnInit(): void {
    const raw = this.route.snapshot.paramMap.get("action");
    const routeAction = this.route.snapshot.data["action"];

    if (raw && ["verify", "resend", "forgot", "reset", "invite"].includes(raw)) {
      this.mode = raw as typeof this.mode;
    } else if (typeof routeAction === "string" && ["verify", "resend", "forgot", "reset", "invite"].includes(routeAction)) {
      this.mode = routeAction as typeof this.mode;
    }

    this.token = this.route.snapshot.queryParamMap.get("token") || "";
    this.tenantSlug = this.route.snapshot.queryParamMap.get("tenant") || "";
    this.email = this.route.snapshot.queryParamMap.get("email") || "";

    // The backend sends users directly to /verify-email?token=... .
    // Verify immediately when that token is present so the link itself completes the flow.
    if (this.mode === "verify" && this.token.trim()) {
      this.verifyFromLink();
    }
  }

  private verifyFromLink(): void {
    this.busy = true;
    this.error = "";
    this.message = "Verifying your email…";

    this.api.verifyEmail(this.token.trim()).subscribe({
      next: () => {
        this.busy = false;
        this.message = "Your email has been verified successfully. You can now sign in.";
      },
      error: (e: any) => {
        this.busy = false;
        this.message = "";
        this.error = e?.error?.message || e?.error?.detail || e?.message || "This verification link is invalid or expired.";
      }
    });
  }

  get title(): string {
    return ({ verify: "Verify your email", resend: "Resend verification email", forgot: "Forgot password?", reset: "Reset your password", invite: "Accept invitation" } as Record<string, string>)[this.mode];
  }

  get description(): string {
    return ({
      verify: "Use the token from your verification email to confirm your account.",
      resend: "Enter your tenant and email address to request a fresh verification email.",
      forgot: "Enter your tenant and account email. If the account exists, we’ll send password-reset instructions.",
      reset: "Choose a new password using the reset token from your email.",
      invite: "You were invited to RicozKnow. Create your account by confirming your name and choosing a password."
    } as Record<string, string>)[this.mode];
  }

  get actionLabel(): string {
    return ({ verify: "Verify email", resend: "Send verification email", forgot: "Send reset link", reset: "Reset password", invite: "Accept invitation" } as Record<string, string>)[this.mode];
  }

  submit(): void {
    if (this.mode === "reset" || this.mode === "invite") {
      if (this.password.length < 12) {
        this.error = "Password must be at least 12 characters.";
        return;
      }
    }
    this.busy = true;
    this.error = "";
    this.message = "";

    let request: Observable<unknown>;
    switch (this.mode) {
      case "verify": request = this.api.verifyEmail(this.token); break;
      case "resend": request = this.api.resendVerification(this.tenantSlug, this.email); break;
      case "forgot": request = this.api.forgotPassword(this.tenantSlug, this.email); break;
      case "reset": request = this.api.resetPassword(this.token, this.password); break;
      case "invite": request = this.api.acceptInvitation(this.token, this.password, this.name); break;
    }

    request.subscribe({
      next: () => {
        this.busy = false;
        this.message = this.successMessage;
        if (this.mode === "reset" || this.mode === "invite" || this.mode === "verify") {
          setTimeout(() => void this.router.navigateByUrl("/login"), 1200);
        }
      },
      error: (e: any) => {
        this.busy = false;
        this.error = e?.error?.message || e?.error?.detail || e?.message || "Request failed. Please check the details and try again.";
      }
    });
  }

  private get successMessage(): string {
    return ({
      verify: "Email verified successfully. Redirecting to sign in…",
      resend: "If the account is eligible, a new verification email has been sent.",
      forgot: "If the account exists, password-reset instructions have been sent.",
      reset: "Password reset successfully. Redirecting to sign in…",
      invite: "Your invited account has been created successfully. Redirecting to sign in…"
    } as Record<string, string>)[this.mode];
  }
}
