import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { ApiService } from "../core/api.service";
import { AuthService } from "../core/auth.service";

@Component({
  selector: "rk-social-callback",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="mx-auto max-w-md space-y-4 p-10 text-center">
      <h1 class="text-2xl font-bold">Complete social sign-in</h1>
      <p *ngIf="error" role="alert" class="text-red-700">{{error}}</p>
      <p *ngIf="busy">Validating your account…</p>
      <div *ngIf="mfaRequired" class="space-y-3">
        <label class="block text-sm" for="socialOtp">Authenticator or recovery code</label>
        <input id="socialOtp" class="rk-input" [(ngModel)]="otp" autocomplete="one-time-code" />
        <button class="rk-btn rk-btn-primary" (click)="complete()" [disabled]="busy || otp.length < 6">Verify & sign in</button>
        <p class="text-xs text-slate-500">If the code is rejected, start social sign-in again.</p>
      </div>
      <a href="/login" class="text-sm underline">Return to login</a>
    </section>
  `
})
export class SocialCallbackComponent implements OnInit {
  error = "";
  busy = false;
  mfaRequired = false;
  otp = "";
  private ticket = "";
  constructor(private readonly route: ActivatedRoute, private readonly api: ApiService,
    private readonly auth: AuthService, private readonly router: Router) {}
  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const expected = sessionStorage.getItem("rk_social_state");
    sessionStorage.removeItem("rk_social_state");
    if (params.has("error")) {
      const errorCode = params.get("error");
      this.error = errorCode === "account_link_required"
        ? "This email already has an account. Sign in with your password, then link the social provider from Security settings."
        : errorCode === "unverified_email"
        ? "Your provider has not supplied a verified email. Verify your email or link the provider to an existing account."
        : "Social sign-in was canceled or could not be completed.";
      return;
    }
    if (!expected || expected !== params.get("state") || !params.get("ticket")) {
      this.error = "Social sign-in could not be verified or was not initiated in this browser.";
      return;
    }
    this.ticket = params.get("ticket")!;
    // Erase the one-time ticket from browser history before any further navigation.
    window.history.replaceState(null, "", "/social/callback");
    this.complete();
  }
  complete(): void {
    if (!this.ticket) return;
    this.busy = true;
    this.error = "";
    this.api.socialFinish(this.ticket, this.otp).subscribe({
      next: reply => {
        this.busy = false;
        if (reply.mfaRequired) { this.mfaRequired = true; return; }
        if (!reply.session) { this.error = "Social sign-in returned no session."; return; }
        this.auth.setSession(reply.session);
        void this.router.navigateByUrl("/dashboard", {replaceUrl:true});
      },
      error: err => {
        this.busy = false;
        this.error = err?.error?.message || "Could not complete sign-in. Please restart from login.";
      }
    });
  }
}
