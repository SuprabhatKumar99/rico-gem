import { CommonModule } from "@angular/common";
import { Component, OnDestroy, OnInit, inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { ApiService } from "../core/api.service";
import { Tenant } from "../models";

interface PreviewDevice {
  key: string;
  label: string;
  icon: string;
  width: number;
}

type LogoState = "idle" | "loading" | "ok" | "broken";

const PREVIEW_DEVICES: PreviewDevice[] = [
  { key: "desktop", label: "Desktop", icon: "🖥", width: 0 },
  { key: "tablet", label: "Tablet", icon: "▭", width: 768 },
  { key: "phone", label: "Phone", icon: "▯", width: 390 }
];

@Component({
  selector: "rk-branding",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <p class="text-sm font-semibold text-slate-500">Administration</p>

      <h1 class="mt-1 text-3xl font-bold tracking-tight">
        Portal branding
      </h1>

      <p class="mt-2 text-sm text-slate-500">
        Customize how your public knowledge base looks to visitors.
      </p>

      <div class="mt-7 grid gap-6 xl:grid-cols-[1fr_420px]">
        <form class="rk-card p-6" (ngSubmit)="save()">
          <h2 class="font-bold">Branding settings</h2>

          <div class="mt-5 space-y-5">
            <div>
              <label class="rk-label" for="portalTitle">
                Portal title
              </label>

              <input
                id="portalTitle"
                class="rk-input"
                [(ngModel)]="form.portalTitle"
                name="portalTitle"
              />
            </div>

            <div>
              <label class="rk-label" for="logoUrl">
                Logo URL
              </label>

              <div class="flex items-center gap-2">
                <input
                  id="logoUrl"
                  class="rk-input"
                  [(ngModel)]="form.logoUrl"
                  (ngModelChange)="checkLogo()"
                  name="logoUrl"
                />

                <span
                  *ngIf="logoState === 'ok'"
                  class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-emerald-50 text-emerald-600"
                  title="Logo loaded successfully"
                  aria-label="Logo loaded successfully"
                >
                  ✓
                </span>

                <span
                  *ngIf="logoState === 'broken'"
                  class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-rose-50 text-rose-600"
                  [title]="'Broken link: ' + form.logoUrl"
                  aria-label="Broken logo link"
                >
                  !
                </span>

                <span
                  *ngIf="logoState === 'loading'"
                  class="shrink-0 text-xs font-medium text-slate-400"
                >
                  Checking...
                </span>
              </div>

              <p *ngIf="logoState === 'broken'" class="mt-1.5 text-xs font-medium text-rose-600">
                That image could not be loaded. Check the URL, including https:// and any file extension.
              </p>

              <div *ngIf="form.logoUrl && logoState === 'ok'" class="mt-3 grid grid-cols-2 gap-3">
                <div class="rounded-xl border border-slate-200 bg-white p-3">
                  <p class="text-[10px] font-bold uppercase tracking-wide text-slate-400">On light</p>
                  <div class="mt-2 flex h-12 items-center gap-2">
                    <img
                      [src]="form.logoUrl"
                      [alt]="form.portalTitle || 'Portal logo'"
                      class="h-10 w-10 rounded-lg object-contain"
                    />
                    <span class="truncate text-xs font-semibold text-slate-800">
                      {{ form.portalTitle || "Knowledge Portal" }}
                    </span>
                  </div>
                </div>

                <div class="rounded-xl bg-slate-900 p-3">
                  <p class="text-[10px] font-bold uppercase tracking-wide text-slate-500">On dark</p>
                  <div class="mt-2 flex h-12 items-center gap-2">
                    <img
                      [src]="form.logoUrl"
                      [alt]="form.portalTitle || 'Portal logo'"
                      class="h-10 w-10 rounded-lg object-contain"
                    />
                    <span class="truncate text-xs font-semibold text-white">
                      {{ form.portalTitle || "Knowledge Portal" }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <div>
              <label class="rk-label" for="primaryColor">
                Primary color
              </label>

              <div class="flex items-center gap-3">
                <input
                  id="primaryColor"
                  type="color"
                  class="h-10 w-12 shrink-0 cursor-pointer rounded-lg border border-slate-300 bg-white p-1"
                  [(ngModel)]="form.primaryColor"
                  name="primaryColorSwatch"
                  aria-label="Primary color swatch"
                />

                <input
                  id="primaryColorHex"
                  class="rk-input font-mono"
                  [(ngModel)]="form.primaryColor"
                  name="primaryColor"
                  placeholder="#2563eb"
                />
              </div>

              <p class="mt-1.5 text-xs text-slate-500">
                Move the swatch to try a color, or type a hex value. The preview updates as you go.
              </p>
            </div>

            <div class="grid gap-4 sm:grid-cols-2">
              <div><label class="rk-label" for="customDomain">Custom domain</label><input id="customDomain" class="rk-input" [(ngModel)]="form.customDomain" name="customDomain" placeholder="help.example.com"><p class="mt-1 text-xs text-slate-500">Point DNS to your RicozKnow edge; SSL is provisioned by the edge/reverse proxy.</p></div>
              <div><label class="rk-label" for="supportEmail">Support email</label><input id="supportEmail" class="rk-input" [(ngModel)]="form.supportEmail" name="supportEmail" type="email" placeholder="support@example.com"></div>
            </div>
            <div><label class="rk-label" for="supportedLanguages">Portal languages</label><input id="supportedLanguages" class="rk-input" [(ngModel)]="form.supportedLanguages" name="supportedLanguages" placeholder="en,fr,de"><p class="mt-1 text-xs text-slate-500">Comma-separated language codes. Link translated articles from the article editor.</p></div>
            <section *ngIf="form.customDomain" class="rounded-xl border p-4 space-y-3">
              <h3 class="font-semibold">Custom-domain ownership verification</h3>
              <p class="text-xs text-slate-600">Save the custom domain first, then request a TXT challenge. DNS proof does not provision an HTTPS certificate; the edge must separately activate TLS.</p>
              <button class="rk-btn" type="button" (click)="domainChallenge()">Generate DNS TXT challenge</button>
              <div *ngIf="challenge" class="text-xs space-y-2"><p>Record name: <code>{{challenge.recordName}}</code></p><p>TXT value: <code class="break-all">{{challenge.recordValue}}</code></p>
                <p>After publishing the record, enter the token from the TXT value below.</p>
              </div>
              <label class="block text-sm">Verification token<input name="domainToken" class="rk-input w-full" [(ngModel)]="domainToken" autocomplete="off" /></label>
              <button class="rk-btn" type="button" (click)="verifyDomain()" [disabled]="!domainToken.trim()">Verify DNS ownership</button>
              <p class="text-xs text-amber-700">After verification, configure HTTPS and mark the domain ACTIVE using your trusted edge provisioning process. It will not route until then.</p>
            </section>

            <p
              *ngIf="message"
              class="rounded-xl bg-emerald-50 p-3 text-sm text-emerald-700"
            >
              {{ message }}
            </p>

            <p
              *ngIf="error"
              class="rounded-xl bg-red-50 p-3 text-sm text-red-700"
            >
              {{ error }}
            </p>

            <div class="flex flex-wrap gap-2">
              <button
                type="submit"
                class="rk-btn rk-btn-primary"
                [disabled]="saving"
              >
                {{ saving ? "Saving..." : "Save branding" }}
              </button>

              <button
                type="button"
                class="rk-btn rk-btn-secondary"
                [disabled]="!isDirty()"
                (click)="reset()"
              >
                Reset
              </button>
            </div>

            <p *ngIf="isDirty()" class="text-xs font-medium text-amber-600">
              You have unsaved changes.
            </p>
          </div>
        </form>

        <section class="rk-card overflow-hidden">
          <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 p-5">
            <h2 class="font-bold">Portal preview</h2>

            <div class="flex items-center gap-1.5">
              <button
                *ngFor="let device of devices"
                type="button"
                class="grid h-9 w-9 place-items-center rounded-xl border text-sm transition"
                [class.border-slate-900]="activeDevice === device.key"
                [class.bg-slate-900]="activeDevice === device.key"
                [class.text-white]="activeDevice === device.key"
                [class.border-slate-200]="activeDevice !== device.key"
                [class.text-slate-500]="activeDevice !== device.key"
                [class.hover:bg-slate-50]="activeDevice !== device.key"
                [attr.aria-pressed]="activeDevice === device.key"
                [attr.aria-label]="device.label + ' preview width'"
                [title]="device.label"
                (click)="activeDevice = device.key"
              >
                {{ device.icon }}
              </button>
            </div>
          </div>

          <div class="p-5">
            <div
              class="mx-auto overflow-hidden rounded-2xl border border-slate-200 bg-white transition-all duration-200"
              [style.maxWidth.px]="activeDeviceWidth || null"
            >
              <div
                class="flex items-center gap-3 border-b border-slate-200 p-4"
              >
                <img
                  *ngIf="form.logoUrl"
                  [src]="form.logoUrl"
                  [alt]="form.portalTitle || 'Portal logo'"
                  class="h-8 w-8 rounded-lg object-cover"
                />

                <div
                  *ngIf="!form.logoUrl"
                  class="grid h-8 w-8 place-items-center rounded-lg text-sm font-black text-white"
                  [style.background-color]="form.primaryColor || '#0f172a'"
                >
                  R
                </div>

                <span class="font-bold">
                  {{ form.portalTitle || "Knowledge Portal" }}
                </span>
              </div>

              <div class="p-8 text-center">
                <p
                  class="text-xs font-bold uppercase tracking-widest text-slate-400"
                >
                  Knowledge base
                </p>

                <p class="mt-3 text-2xl font-bold">
                  How can we help?
                </p>

                <div
                  class="mt-5 h-11 rounded-xl border border-slate-300"
                  [style.border-color]="form.primaryColor || '#cbd5e1'"
                ></div>

                <button
                  type="button"
                  class="mt-5 rounded-xl px-4 py-2 text-sm font-semibold text-white"
                  [style.background-color]="form.primaryColor || '#0f172a'"
                >
                  Search
                </button>
              </div>
            </div>
          </div>
        </section>
      </div>

      <div
        *ngIf="leaveDialogOpen"
        class="fixed inset-0 z-50 grid place-items-center bg-slate-950/40 p-4"
      >
        <div class="rk-card w-full max-w-sm p-6">
          <h2 class="font-bold">Unsaved branding changes</h2>
          <p class="mt-2 text-sm leading-6 text-slate-500">
            You have changed the portal title, logo or color but have not saved. If you leave now,
            those changes are lost.
          </p>

          <div class="mt-5 flex flex-wrap justify-end gap-2">
            <button type="button" class="rk-btn rk-btn-secondary" (click)="stayOnPage()">
              Keep editing
            </button>
            <button type="button" class="rk-btn rk-btn-primary" (click)="discardAndLeave()">
              Discard and leave
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class BrandingComponent implements OnInit, OnDestroy {
  private readonly domainHttp=inject(HttpClient);
  challenge: {recordName:string;recordValue:string;domain:string}|null=null;
  domainToken='';
  private get domainBase(){return (localStorage.getItem('rk_api_url')?.replace(/\/$/,'')||'')+'/api/v1/admin/custom-domain';}
  domainChallenge(){this.apiRequest('challenge',{},(v:any)=>{this.challenge=v;this.domainToken=(v.recordValue||'').replace('ricozknow-verification=','');this.message='Add the TXT record, then verify.';});}
  verifyDomain(){this.apiRequest('verify',{token:this.domainToken},()=>{this.message='Ownership verified. Edge TLS provisioning is still required.';this.challenge=null;});}
  private apiRequest(endpoint:string,body:any,onSuccess:(v:any)=>void){
    this.domainHttp.post<any>(this.domainBase+'/'+endpoint,body).subscribe({next:onSuccess,error:()=>this.error='Domain verification failed. Ensure the domain is saved and DNS TXT record has propagated.'});
  }

  form = {
    portalTitle: "",
    logoUrl: "",
    primaryColor: "",
    customDomain: "",
    supportEmail: "",
    supportedLanguages: "en"
  };

  message = "";
  error = "";
  saving = false;

  readonly devices = PREVIEW_DEVICES;
  activeDevice = "desktop";

  logoState: LogoState = "idle";
  private logoTimer: ReturnType<typeof setTimeout> | null = null;

  private savedForm = { ...this.form };
  leaveDialogOpen = false;
  private pendingUrl: string | null = null;
  private initialized = false;

  private readonly linkClickHandler = (event: MouseEvent): void => {
    if (!this.initialized || !this.isDirty() || this.leaveDialogOpen) {
      return;
    }

    const target = event.target as HTMLElement | null;
    const anchor = target?.closest("a[href]");

    if (!anchor || anchor.hasAttribute("download") || (anchor as HTMLAnchorElement).target === "_blank") {
      return;
    }

    const href = anchor.getAttribute("href") ?? "";

    if (!href || href.startsWith("#") || href === this.router.url) {
      return;
    }

    event.preventDefault();
    event.stopPropagation();

    this.pendingUrl = href;
    this.leaveDialogOpen = true;
  };

  constructor(private readonly api: ApiService, private readonly router: Router) {}

  ngOnInit(): void {
    this.loadBranding();
    document.addEventListener("click", this.linkClickHandler, true);
    this.initialized = true;
  }

  ngOnDestroy(): void {
    document.removeEventListener("click", this.linkClickHandler, true);

    if (this.logoTimer) {
      clearTimeout(this.logoTimer);
    }
  }

  get activeDeviceWidth(): number {
    return this.devices.find((device) => device.key === this.activeDevice)?.width ?? 0;
  }

  isDirty(): boolean {
    return (
      this.form.portalTitle !== this.savedForm.portalTitle ||
      this.form.logoUrl !== this.savedForm.logoUrl ||
      this.form.primaryColor !== this.savedForm.primaryColor ||
      this.form.customDomain !== this.savedForm.customDomain || this.form.supportEmail !== this.savedForm.supportEmail || this.form.supportedLanguages !== this.savedForm.supportedLanguages
    );
  }

  checkLogo(): void {
    const url = this.form.logoUrl.trim();

    if (this.logoTimer) {
      clearTimeout(this.logoTimer);
    }

    if (!url) {
      this.logoState = "idle";
      return;
    }

    this.logoState = "loading";

    this.logoTimer = setTimeout(() => {
      const probe = new Image();
      probe.onload = () => {
        this.logoState = "ok";
      };
      probe.onerror = () => {
        this.logoState = "broken";
      };
      probe.src = url;
    }, 400);
  }

  reset(): void {
    this.form = { ...this.savedForm };
    this.message = "";
    this.error = "";
    this.checkLogo();
  }

  stayOnPage(): void {
    this.leaveDialogOpen = false;
    this.pendingUrl = null;
  }

  discardAndLeave(): void {
    this.leaveDialogOpen = false;
    const target = this.pendingUrl;
    this.pendingUrl = null;
    this.form = { ...this.savedForm };

    if (target) {
      this.router.navigateByUrl(target);
    }
  }

  save(): void {
    this.message = "";
    this.error = "";
    this.saving = true;

    this.api.updateBranding(this.form).subscribe({
      next: (tenant) => {
        this.setTenant(tenant);
        this.message = "Branding saved.";
        this.saving = false;
      },
      error: (error) => {
        this.error =
          error?.error?.message ||
          "Unable to save branding.";
        this.saving = false;
      }
    });
  }

  private loadBranding(): void {
    this.message = "";
    this.error = "";

    /*
     * This is an authenticated admin page.
     *
     * The backend resolves the tenant from the authenticated
     * user's JWT, so we must not depend on the browser hostname
     * or subdomain here.
     */
    this.api.getCurrentBranding().subscribe({
      next: (tenant) => {
        this.setTenant(tenant);
      },
      error: (error) => {
        this.error =
          error?.error?.message ||
          "Unable to load branding.";
      }
    });
  }

  private setTenant(tenant: Tenant): void {
    this.form = {
      portalTitle: tenant.portalTitle || "",
      logoUrl: tenant.logoUrl || "",
      primaryColor: tenant.primaryColor || "",
      customDomain: tenant.customDomain || "", supportEmail: tenant.supportEmail || "", supportedLanguages: tenant.supportedLanguages || "en"
    };
    this.savedForm = { ...this.form };
    this.checkLogo();
  }
}