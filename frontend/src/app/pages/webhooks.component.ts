import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";
import { WebhookDelivery, WebhookEventType, WebhookSubscription } from "../models";

@Component({
  selector: "rk-webhooks", standalone: true, imports: [CommonModule, FormsModule],
  template: `
    <div>
      <p class="text-sm font-semibold text-slate-500">Administration</p>
      <h1 class="mt-1 text-3xl font-bold tracking-tight">Webhooks</h1>
      <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">Configure outbound article events for external systems. Secrets are shown only once when a subscription is created.</p>

      <div *ngIf="createdSecret" class="mt-6 rounded-2xl border border-amber-200 bg-amber-50 p-5">
        <div class="flex items-start justify-between gap-4"><div><h2 class="font-bold text-amber-950">Copy your signing secret now</h2><p class="mt-1 text-sm text-amber-800">The backend will not return this secret again. Store it securely in the receiving application.</p></div><button class="rk-btn rk-btn-secondary" type="button" (click)="createdSecret = null">Done</button></div>
        <div class="mt-4 flex flex-col gap-2 sm:flex-row"><input class="rk-input flex-1 font-mono text-sm" [value]="createdSecret.secret" readonly /><button class="rk-btn rk-btn-primary" type="button" (click)="copySecret()">Copy secret</button></div>
      </div>

      <div class="mt-7 grid gap-6 xl:grid-cols-[390px_1fr]">
        <form class="rk-card p-6" (ngSubmit)="create()">
          <h2 class="font-bold">Add endpoint</h2><p class="mt-1 text-sm text-slate-500">Only HTTPS endpoints are accepted by the backend.</p>
          <div class="mt-5 space-y-4">
            <div><label class="rk-label" for="webhook-url">Endpoint URL</label><input id="webhook-url" class="rk-input" [(ngModel)]="url" name="url" type="url" placeholder="https://example.com/webhooks/ricozknow" required /></div>
            <div><p class="rk-label">Events</p><label *ngFor="let type of eventTypes" class="mt-2 flex cursor-pointer items-start gap-3 rounded-xl border border-slate-200 p-3 hover:bg-slate-50"><input class="mt-1" type="checkbox" [checked]="selectedEvents.has(type)" (change)="toggleEvent(type)" /><span><span class="block text-sm font-semibold">{{ eventLabel(type) }}</span><span class="block text-xs text-slate-500">{{ eventDescription(type) }}</span></span></label></div>
            <div *ngIf="selectedEventPreviews.length" class="rounded-xl border border-slate-200 bg-slate-50 p-4"><p class="text-[11px] font-bold uppercase tracking-wide text-slate-400">Example payload</p><p class="mt-1 text-xs text-slate-500">This is the shape your endpoint receives for each selected event.</p><div *ngFor="let preview of selectedEventPreviews" class="mt-3"><p class="text-xs font-semibold text-slate-700">{{ preview.label }}</p><pre class="mt-1 overflow-x-auto rounded-lg bg-slate-950 p-3 text-xs leading-5 text-slate-100"><code>{{ preview.payload }}</code></pre></div></div>
            <p *ngIf="error" class="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{{ error }}</p>
            <button class="rk-btn rk-btn-primary w-full" [disabled]="saving || !url.trim() || selectedEvents.size === 0">{{ saving ? "Creating…" : "Create webhook" }}</button>
          </div>
        </form>

        <section class="space-y-6">
          <div class="rk-card overflow-hidden">
            <div class="border-b border-slate-200 px-5 py-4"><div class="flex items-center justify-between gap-3"><div><h2 class="font-bold">Subscriptions</h2><p class="mt-1 text-xs text-slate-500">{{ subscriptions.length }} configured endpoint{{ subscriptions.length === 1 ? '' : 's' }}</p></div><button class="rk-btn rk-btn-secondary" type="button" (click)="load()">Refresh</button></div></div>
            <div *ngIf="subscriptions.length === 0" class="px-6 py-12 text-center text-sm text-slate-500">No webhook subscriptions yet.</div>
            <div *ngFor="let subscription of subscriptions" class="border-b border-slate-100 p-5 last:border-b-0">
              <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between"><div class="min-w-0"><div class="flex flex-wrap items-center gap-2"><span class="rk-badge" [ngClass]="subscription.active ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'">{{ subscription.active ? 'ACTIVE' : 'INACTIVE' }}</span><span *ngFor="let event of subscription.eventTypes" class="rk-badge bg-slate-100 text-slate-600">{{ eventLabel(event) }}</span></div><p class="mt-3 break-all font-mono text-sm text-slate-700">{{ subscription.url }}</p></div><div class="flex flex-wrap gap-2 lg:justify-end"><button class="rk-btn rk-btn-secondary" type="button" (click)="showDeliveries(subscription)">{{ selectedSubscription?.id === subscription.id ? 'Hide deliveries' : 'Delivery history' }}</button><button *ngIf="subscription.active" class="rk-btn rk-btn-secondary" type="button" (click)="deactivate(subscription)">Deactivate</button><button class="rk-btn rk-btn-danger" type="button" (click)="remove(subscription)">Delete</button></div></div>
              <div *ngIf="selectedSubscription?.id === subscription.id" class="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4"><div class="flex items-center justify-between gap-3"><h3 class="text-sm font-bold">Recent deliveries</h3><button class="text-xs font-semibold text-slate-500 hover:text-slate-900" type="button" (click)="loadDeliveries(subscription)">Refresh</button></div><div *ngIf="deliveries.length === 0" class="py-6 text-center text-sm text-slate-500">No deliveries recorded yet.</div><div *ngFor="let delivery of deliveries" class="mt-3 rounded-xl border border-slate-200 bg-white p-4"><div class="flex flex-wrap items-center justify-between gap-2"><div class="flex items-center gap-2"><span class="rk-badge" [ngClass]="deliveryBadge(delivery.status)">{{ delivery.status }}</span><span class="text-sm font-semibold">{{ eventLabel(delivery.eventType) }}</span></div><span class="text-xs text-slate-400">{{ delivery.createdAt | date:'medium' }}</span></div><div class="mt-2 text-xs text-slate-500">Attempts: {{ delivery.attemptCount }}<span *ngIf="delivery.deliveredAt"> · Delivered {{ delivery.deliveredAt | date:'medium' }}</span></div><p *ngIf="delivery.lastError" class="mt-2 break-words rounded-lg bg-rose-50 p-2 text-xs text-rose-700">{{ delivery.lastError }}</p></div></div>
            </div>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-50 p-5"><h2 class="font-bold">Integration notes</h2><ul class="mt-3 space-y-2 text-sm leading-6 text-slate-600"><li>• Payloads contain <code>eventType</code>, <code>occurredAt</code>, and <code>data</code>.</li><li>• Requests include an HMAC-SHA256 signature in <code>X-RicozKnow-Signature</code>, prefixed with <code>sha256=</code>.</li><li>• Delivery attempts are capped at 3 with a short retry delay; each result is recorded here.</li><li>• Use delivery history to diagnose HTTP failures, blocked targets, and endpoint errors.</li></ul></div>
        </section>
      </div>
    </div>
  `
})
export class WebhooksComponent implements OnInit {
  readonly eventTypes: WebhookEventType[] = ["ARTICLE_PUBLISHED", "ARTICLE_ARCHIVED"];
  readonly selectedEvents = new Set<WebhookEventType>();
  subscriptions: WebhookSubscription[] = []; deliveries: WebhookDelivery[] = []; selectedSubscription: WebhookSubscription | null = null; createdSecret: WebhookSubscription | null = null;
  url = ""; saving = false; error = "";
  constructor(private readonly api: ApiService) {}
  ngOnInit(): void { this.selectedEvents.add("ARTICLE_PUBLISHED"); this.load(); }
  load(): void { this.api.webhooks().subscribe({ next: (items) => this.subscriptions = items, error: () => this.error = "Could not load webhook subscriptions." }); }
  toggleEvent(type: WebhookEventType): void { this.selectedEvents.has(type) ? this.selectedEvents.delete(type) : this.selectedEvents.add(type); }
  create(): void { this.error = ""; this.saving = true; this.api.createWebhook({ url: this.url.trim(), eventTypes: [...this.selectedEvents] }).subscribe({ next: (created) => { this.saving = false; this.createdSecret = created; this.url = ""; this.selectedEvents.clear(); this.selectedEvents.add("ARTICLE_PUBLISHED"); this.load(); }, error: (err) => { this.saving = false; this.error = err?.error?.message || "Could not create the webhook."; } }); }
  deactivate(subscription: WebhookSubscription): void { if (!confirm("Deactivate this webhook? It will stop receiving new events.")) return; this.api.deactivateWebhook(subscription.id).subscribe(() => this.load()); }
  remove(subscription: WebhookSubscription): void { if (!confirm("Delete this webhook and its delivery history?")) return; this.api.deleteWebhook(subscription.id).subscribe(() => { if (this.selectedSubscription?.id === subscription.id) { this.selectedSubscription = null; this.deliveries = []; } this.load(); }); }
  showDeliveries(subscription: WebhookSubscription): void { if (this.selectedSubscription?.id === subscription.id) { this.selectedSubscription = null; this.deliveries = []; return; } this.selectedSubscription = subscription; this.loadDeliveries(subscription); }
  loadDeliveries(subscription: WebhookSubscription): void { this.api.webhookDeliveries(subscription.id).subscribe({ next: (items) => this.deliveries = items }); }
  
  // async copySecret(): Promise<void> { if (this.createdSecret?.secret) await navigator.clipboard.writeText(this.createdSecret.secret); }
  async copySecret(): Promise<void> {
    const secret = this.createdSecret?.secret;
    if (!secret) return;

    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(secret);
        return;
      }

      const textarea = document.createElement("textarea");
      textarea.value = secret;
      textarea.style.position = "fixed";
      textarea.style.left = "-9999px";
      textarea.style.top = "0";
      textarea.setAttribute("readonly", "");
      document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();

      const copied = document.execCommand("copy");
      document.body.removeChild(textarea);

      if (!copied) {
        throw new Error("Clipboard copy failed");
      }
    } catch (err) {
      console.error("Unable to copy webhook secret:", err);
      this.error = "Could not copy the secret automatically. Please select and copy it manually.";
    }
  }
  
  eventLabel(type: WebhookEventType): string { return type === "ARTICLE_PUBLISHED" ? "Article published" : "Article archived"; }
  eventDescription(type: WebhookEventType): string { return type === "ARTICLE_PUBLISHED" ? "Sent when an article is successfully published." : "Sent when an article is archived."; }
  deliveryBadge(status: string): string { if (status === "SUCCESS") return "bg-emerald-100 text-emerald-700"; if (status === "FAILED") return "bg-rose-100 text-rose-700"; return "bg-amber-100 text-amber-700"; }

  get selectedEventPreviews(): { type: WebhookEventType; label: string; payload: string }[] {
    return this.eventTypes
      .filter((type) => this.selectedEvents.has(type))
      .map((type) => ({ type, label: this.eventLabel(type), payload: JSON.stringify(this.samplePayload(type), null, 2) }));
  }

  private samplePayload(type: WebhookEventType): Record<string, unknown> {
    if (type === "ARTICLE_ARCHIVED") {
      return {
        eventType: "ARTICLE_ARCHIVED",
        occurredAt: "2026-03-14T09:41:22Z",
        data: {
          articleId: "8b1f6c40-2f6a-4d1e-9a3b-5c7d2e9f1a10",
          slug: "legacy-sso-login",
          title: "Signing in through the old SSO provider",
          status: "ARCHIVED",
          previousStatus: "PUBLISHED",
          archivedAt: "2026-03-14T09:41:22Z"
        }
      };
    }

    return {
      eventType: "ARTICLE_PUBLISHED",
      occurredAt: "2026-03-14T09:41:22Z",
      data: {
        articleId: "8b1f6c40-2f6a-4d1e-9a3b-5c7d2e9f1a10",
        slug: "how-to-reset-your-password",
        title: "How to reset your password",
        status: "PUBLISHED",
        visibility: "PUBLIC",
        categoryId: "3d9a5b21-77c4-4f18-8e60-2a4b6c8d0e55",
        versionId: "c07f5a90-51de-4c37-b6a8-9d21f0c8e742",
        versionNumber: 4,
        publishedAt: "2026-03-14T09:41:22Z",
        url: "https://help.example.com/portal/article/how-to-reset-your-password"
      }
    };
  }
}
