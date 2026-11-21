import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";
import { SessionView } from "../models";

@Component({
  selector: "rk-security",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <p class="text-sm font-semibold text-slate-500">Account security</p>
      <h1 class="mt-1 text-3xl font-bold tracking-tight">Security</h1>
      <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">Manage multi-factor authentication and active sessions for your account.</p>

      <p *ngIf="message" class="mt-5 rounded-xl bg-emerald-50 p-3 text-sm text-emerald-700">{{ message }}</p>
      <p *ngIf="error" class="mt-5 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{{ error }}</p>

      <div class="mt-7 grid gap-6 xl:grid-cols-[420px_1fr]">
        <section class="rk-card p-6">
          <div class="flex items-center justify-between gap-3">
            <div>
              <h2 class="font-bold">Two-factor authentication</h2>
              <p class="mt-1 text-xs text-slate-500">Use any TOTP authenticator app.</p>
            </div>
            <span class="rk-badge" [ngClass]="mfaEnabled ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'">
              {{ mfaEnabled ? "ENABLED" : "DISABLED" }}
            </span>
          </div>

          <div *ngIf="!mfaEnabled && !enrollment" class="mt-5">
            <button class="rk-btn rk-btn-primary" (click)="enroll()">Set up MFA</button>
          </div>

          <div *ngIf="enrollment" class="mt-5 space-y-4">
            <p class="text-sm leading-6 text-slate-600">Add this secret to your authenticator app, then enter the six-digit code to confirm.</p>
            <div class="rounded-xl bg-slate-50 p-4">
              <p class="text-[11px] font-bold uppercase tracking-wide text-slate-400">Secret</p>
              <p class="mt-1 break-all font-mono text-sm">{{ enrollment.secret }}</p>
              <p class="mt-3 break-all text-[11px] text-slate-500">{{ enrollment.otpauthUri }}</p>
            </div>
            <input class="rk-input" [(ngModel)]="code" inputmode="numeric" maxlength="6" placeholder="123456" />
            <button class="rk-btn rk-btn-primary w-full" [disabled]="code.length !== 6" (click)="enable()">Enable MFA</button>
          </div>

          <div *ngIf="mfaEnabled" class="mt-5 space-y-3">
            <label for="mfaDisableCode" class="block text-sm text-slate-600">Enter a current authenticator code or an unused recovery code to disable MFA.</label>
            <input id="mfaDisableCode" class="rk-input" [(ngModel)]="disableCode" autocomplete="one-time-code" placeholder="Authenticator or recovery code" />
            <button class="rk-btn rk-btn-danger" [disabled]="disableCode.trim().length < 6" (click)="disable()">Disable MFA</button>
            <div class="border-t border-slate-200 pt-4">
              <label for="rotateRecoveryCode" class="block text-sm text-slate-600">Generate a new set of recovery codes (invalidates old codes).</label>
              <input id="rotateRecoveryCode" class="rk-input mt-2" [(ngModel)]="rotationProof"
                     autocomplete="one-time-code" placeholder="Authenticator or recovery code" />
              <button class="rk-btn rk-btn-secondary mt-2" [disabled]="rotationProof.trim().length < 6"
                      (click)="regenerateRecoveryCodes()">Regenerate recovery codes</button>
            </div>
          </div>
          <div *ngIf="recoveryCodes.length" class="mt-5 space-y-3 rounded-lg border border-amber-200 bg-amber-50 p-4">
            <p class="font-semibold text-amber-900">Save your one-time recovery codes now</p>
            <p class="text-sm text-amber-900">Each code works once. These codes will not be displayed again. Store them in a secure password manager.</p>
            <ul class="grid grid-cols-2 gap-2 font-mono text-xs"><li *ngFor="let item of recoveryCodes">{{ item }}</li></ul>
            <button class="rk-btn rk-btn-secondary" (click)="copyRecoveryCodes()">Copy recovery codes</button>
            <button class="rk-btn rk-btn-secondary" (click)="recoveryCodes = []">I have saved these codes</button>
          </div>
        </section>

        <section class="rk-card p-6">
          <h2 class="font-bold">Connected sign-in providers</h2>
          <p class="mt-2 text-sm text-slate-500">Link your existing account before using Google or Microsoft on the sign-in screen.</p>
          <label class="mt-4 block text-sm" for="socialProof">{{ mfaEnabled ? 'Current authenticator or recovery code' : 'Current password' }}</label>
          <input id="socialProof" class="rk-input mt-2" [type]="mfaEnabled ? 'text' : 'password'" [(ngModel)]="socialProof" autocomplete="off" />
          <div class="mt-4 flex flex-wrap gap-2">
            <button class="rk-btn rk-btn-secondary" [disabled]="socialProof.length < 6" (click)="linkSocial('google')">Link Google</button>
            <button class="rk-btn rk-btn-secondary" [disabled]="socialProof.length < 6" (click)="linkSocial('microsoft')">Link Microsoft</button>
          </div>
        </section>
        <section class="rk-card overflow-hidden">
          <div class="border-b border-slate-200 px-5 py-4">
            <div class="flex items-center justify-between gap-3">
              <div><h2 class="font-bold">Active sessions</h2><p class="mt-1 text-xs text-slate-500">{{ sessions.length }} active session{{ sessions.length === 1 ? "" : "s" }}</p></div>
              <button class="rk-btn rk-btn-secondary" (click)="loadSessions()">Refresh</button>
            </div>
          </div>
          <div *ngIf="!sessions.length" class="p-10 text-center text-sm text-slate-500">No active sessions returned.</div>
          <div *ngFor="let session of sessions" class="border-b border-slate-100 p-5 last:border-b-0">
            <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <p class="font-semibold">{{ session.deviceName || session.deviceId || "Unknown device" }}</p>
                <p class="mt-1 text-xs text-slate-500">{{ session.userAgent || "Unknown user agent" }}</p>
                <p class="mt-1 text-xs text-slate-400">{{ session.ipAddress || "IP unavailable" }} · last used {{ session.lastUsedAt | date:"medium" }}</p>
              </div>
              <button class="rk-btn rk-btn-danger" (click)="revoke(session)">Revoke</button>
            </div>
          </div>
        </section>
      </div>
    </div>
  `
})
export class SecurityComponent implements OnInit {
  sessions: SessionView[] = [];
  enrollment: { secret: string; otpauthUri: string } | null = null;
  mfaEnabled = false;
  socialProof = "";
  code = "";
  disableCode = "";
  rotationProof = "";
  recoveryCodes: string[] = [];
  message = "";
  error = "";

  constructor(private readonly api: ApiService) {}
  ngOnInit(): void {
    this.loadSessions();
    this.loadMfaStatus();
  }

  linkSocial(provider: "google" | "microsoft"): void {
    this.error = "";
    this.api.socialLinkStart(provider, this.socialProof).subscribe({
      next: res => {
        sessionStorage.setItem("rk_social_state", res.state);
        window.location.assign(res.authorizationUrl);
      },
      error: err => { this.error = err?.error?.message || "Could not start account linking."; }
    });
  }

  loadMfaStatus(): void {
    this.api.mfaStatus().subscribe({
      next: status => { this.mfaEnabled = status.enabled === true; },
      error: e => { this.error = e?.error?.message || "Could not load MFA status."; }
    });
  }

  loadSessions(): void {
    this.api.sessions().subscribe({ next: s => this.sessions = s, error: e => this.error = e?.error?.message || "Could not load sessions." });
  }
  enroll(): void {
    this.clear();
    this.api.enrollMfa().subscribe({ next: e => this.enrollment = e, error: err => this.error = err?.error?.message || "Could not start MFA setup." });
  }
  enable(): void {
    this.api.enableMfa(this.code).subscribe({
      next: result => {
        this.recoveryCodes = result.recoveryCodes;
        this.mfaEnabled = true;
        this.enrollment = null;
        this.code = "";
        this.message = "MFA enabled.";
        this.error = "";
      },
      error: e => this.error = e?.error?.message || "Invalid MFA code."
    });
  }
  disable(): void {
    if (!this.disableCode.trim() || !confirm("Disable MFA for this account and revoke other sessions?")) return;
    this.api.disableMfa(this.disableCode.trim()).subscribe({
      next: () => {
        this.mfaEnabled = false;
        this.recoveryCodes = [];
        this.disableCode = "";
        this.enrollment = null;
        this.code = "";
        this.message = "MFA disabled.";
        this.error = "";
      },
      error: e => this.error = e?.error?.message || "Could not disable MFA."
    });
  }
  regenerateRecoveryCodes(): void {
    if (!this.rotationProof.trim() || !confirm("Replace all existing MFA recovery codes? Older codes will stop working.")) return;
    this.clear();
    this.api.regenerateMfaRecoveryCodes(this.rotationProof.trim()).subscribe({
      next: value => {
        this.recoveryCodes = value.recoveryCodes;
        this.rotationProof = "";
        this.message = "Recovery codes replaced. Save this new set securely; it will only be shown once.";
      },
      error: e => this.error = e?.error?.message || "Could not regenerate recovery codes."
    });
  }
  async copyRecoveryCodes(): Promise<void> {
    if (!this.recoveryCodes.length) return;
    try {
      await navigator.clipboard.writeText(this.recoveryCodes.join("\n"));
      this.message = "Recovery codes copied. Store them securely.";
    } catch {
      this.error = "Copy unavailable. Save the codes manually before leaving this page.";
    }
  }
  revoke(s: SessionView): void {
    if (!confirm("Revoke this session?")) return;
    this.api.revokeSession(s.id).subscribe({ next: () => this.loadSessions(), error: e => this.error = e?.error?.message || "Could not revoke session." });
  }
  private clear(): void { this.error = ""; this.message = ""; }
}
