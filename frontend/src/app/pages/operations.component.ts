import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ApiService } from "../core/api.service";
import { AuditLog, AiCostEvent, IndexStatus, TenantSettings, UsageMeter, WebhookDeadLetter } from "../models";

@Component({
  selector: "rk-operations",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <div class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p class="text-sm font-semibold text-slate-500">Administration</p>
          <h1 class="mt-1 text-3xl font-bold tracking-tight">Operations</h1>
          <p class="mt-2 max-w-3xl text-sm leading-6 text-slate-500">Production-facing health, indexing, usage, audit, dead-letter, and tenant configuration controls.</p>
        </div>
        <button class="rk-btn rk-btn-secondary" (click)="refreshAll()">Refresh</button>
      </div>

      <p *ngIf="message" class="mt-5 rounded-xl bg-emerald-50 p-3 text-sm text-emerald-700">{{ message }}</p>
      <p *ngIf="error" class="mt-5 rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{{ error }}</p>

      <div class="mt-7 grid gap-6 xl:grid-cols-2">
        <section class="rk-card p-6">
          <div class="flex items-center justify-between"><div><h2 class="font-bold">System health</h2><p class="mt-1 text-xs text-slate-500">Live backend health snapshot.</p></div><button class="rk-btn rk-btn-secondary" (click)="loadHealth()">Refresh</button></div>
          <pre class="mt-4 max-h-72 overflow-auto rounded-xl bg-slate-950 p-4 text-xs leading-5 text-white">{{ health | json }}</pre>
        </section>

        <section class="rk-card p-6">
          <div class="flex items-center justify-between"><div><h2 class="font-bold">Search index</h2><p class="mt-1 text-xs text-slate-500">Reconcile index state and inspect jobs.</p></div><div class="flex gap-2"><button class="rk-btn rk-btn-secondary" (click)="reconcile()">Reconcile</button><button class="rk-btn rk-btn-primary" (click)="reindex()">Reindex</button></div></div>
          <div class="mt-4 grid grid-cols-2 gap-3">
            <div class="rounded-xl bg-slate-50 p-4"><p class="text-xs text-slate-500">Pending jobs</p><p class="mt-1 text-xl font-bold">{{ pendingJobs | json }}</p></div>
            <div class="rounded-xl bg-slate-50 p-4"><p class="text-xs text-slate-500">Tracked jobs</p><p class="mt-1 text-xl font-bold">{{ jobs.length }}</p></div>
          </div>
          <div class="mt-4 max-h-56 overflow-auto">
            <div *ngFor="let item of indexStatus" class="border-b border-slate-100 py-3 text-xs">
              <div class="flex justify-between gap-3"><span class="font-mono">{{ item.articleId }}</span><span>{{ item.searchStatus }} / {{ item.ragStatus }}</span></div>
              <p *ngIf="item.searchError || item.ragError" class="mt-1 text-rose-600">{{ item.searchError || item.ragError }}</p>
            </div>
          </div>
        </section>

        <section class="rk-card p-6">
          <h2 class="font-bold">Usage</h2>
          <div class="mt-4 overflow-x-auto"><table class="w-full text-left text-sm"><thead class="text-xs uppercase text-slate-400"><tr><th class="py-2">Metric</th><th class="py-2">Period</th><th class="py-2">Quantity</th></tr></thead><tbody class="divide-y divide-slate-100"><tr *ngFor="let u of usage"><td class="py-2 font-semibold">{{ u.metric }}</td><td class="py-2">{{ u.periodStart }}</td><td class="py-2">{{ u.quantity }}</td></tr></tbody></table></div>
          <div class="mt-5 flex flex-wrap gap-2"><input class="rk-input" type="datetime-local" [(ngModel)]="from"><input class="rk-input" type="datetime-local" [(ngModel)]="to"><button class="rk-btn rk-btn-secondary" (click)="loadCosts()">AI costs</button></div>
          <div class="mt-4 max-h-48 overflow-auto"><div *ngFor="let c of costs" class="border-b border-slate-100 py-2 text-xs">{{ c.provider }}/{{ c.model }} · {{ c.operation }} · {{ c.costMicros }} micros · {{ c.createdAt | date:"short" }}</div></div>
        </section>

        <section class="rk-card p-6">
          <h2 class="font-bold">Webhook dead-letter queue</h2>
          <div *ngIf="!dlq.length" class="mt-4 text-sm text-slate-500">No dead-letter deliveries.</div>
          <div *ngFor="let item of dlq" class="mt-3 rounded-xl border border-slate-200 p-4">
            <div class="flex items-start justify-between gap-3"><div><p class="font-semibold">{{ item.eventType }}</p><p class="mt-1 text-xs text-slate-500">{{ item.reason }} · attempts {{ item.attemptCount }}</p></div><button class="rk-btn rk-btn-secondary" (click)="replay(item)">Replay</button></div>
          </div>
        </section>

        <section class="rk-card p-6 xl:col-span-2">
          <div class="flex items-center justify-between"><div><h2 class="font-bold">Audit log</h2><p class="mt-1 text-xs text-slate-500">Latest tenant-scoped administrative activity.</p></div><button class="rk-btn rk-btn-secondary" (click)="loadAudit()">Refresh</button></div>
          <div class="mt-4 overflow-x-auto"><table class="w-full min-w-[800px] text-left text-sm"><thead class="text-xs uppercase text-slate-400"><tr><th class="py-2">Time</th><th class="py-2">Actor</th><th class="py-2">Action</th><th class="py-2">Entity</th><th class="py-2">Source</th></tr></thead><tbody class="divide-y divide-slate-100"><tr *ngFor="let a of audit.content"><td class="py-3">{{ a.createdAt | date:"medium" }}</td><td class="py-3 font-mono text-xs">{{ a.actorUserId }}</td><td class="py-3 font-semibold">{{ a.action }}</td><td class="py-3">{{ a.entityType }} {{ a.entityId }}</td><td class="py-3">{{ a.source }}</td></tr></tbody></table></div>
        </section>

        <section class="rk-card p-6 xl:col-span-2">
          <div class="flex items-center justify-between"><div><h2 class="font-bold">Tenant limits & defaults</h2><p class="mt-1 text-xs text-slate-500">Enterprise settings enforced by the backend.</p></div><button class="rk-btn rk-btn-primary" (click)="saveSettings()">Save settings</button></div>
          <div class="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <label class="text-xs font-semibold text-slate-500">Timezone<input class="rk-input mt-1" [(ngModel)]="settings.timezone" /></label>
            <label class="text-xs font-semibold text-slate-500">Default language<input class="rk-input mt-1" [(ngModel)]="settings.defaultLanguage" /></label>
            <label class="text-xs font-semibold text-slate-500">Max users<input class="rk-input mt-1" type="number" [(ngModel)]="settings.maxUsers" /></label>
            <label class="text-xs font-semibold text-slate-500">Monthly API requests<input class="rk-input mt-1" type="number" [(ngModel)]="settings.maxApiRequestsMonthly" /></label>
            <label class="text-xs font-semibold text-slate-500">Monthly RAG queries<input class="rk-input mt-1" type="number" [(ngModel)]="settings.maxRagQueriesMonthly" /></label>
            <label class="text-xs font-semibold text-slate-500">Monthly AI cost (cents)<input class="rk-input mt-1" type="number" [(ngModel)]="settings.maxAiCostCentsMonthly" /></label>
            <label class="text-xs font-semibold text-slate-500">Max storage (bytes)<input class="rk-input mt-1" type="number" [(ngModel)]="settings.maxStorageBytes" /></label>
            <label class="text-xs font-semibold text-slate-500">Settings JSON<input class="rk-input mt-1" [(ngModel)]="settings.settingsJson" /></label>
          </div>
        </section>
      </div>
    </div>
  `
})
export class OperationsComponent implements OnInit {
  health: Record<string, unknown> = {};
  usage: UsageMeter[] = [];
  costs: AiCostEvent[] = [];
  dlq: WebhookDeadLetter[] = [];
  indexStatus: IndexStatus[] = [];
  jobs: Record<string, unknown>[] = [];
  pendingJobs: unknown = "—";
  audit: { content: AuditLog[]; totalElements: number } = { content: [], totalElements: 0 };
  settings: TenantSettings = { tenantId: "", timezone: "UTC", defaultLanguage: "en", maxUsers: 100, maxStorageBytes: 10737418240, maxApiRequestsMonthly: 100000, maxRagQueriesMonthly: 25000, maxAiCostCentsMonthly: 100000, settingsJson: "{}", createdAt: "", updatedAt: "" };
  from = ""; to = "";
  error = ""; message = "";

  constructor(private readonly api: ApiService) {}
  ngOnInit(): void { this.refreshAll(); }

  refreshAll(): void {
    this.loadHealth(); this.loadUsage(); this.loadDlq(); this.loadIndex(); this.loadAudit(); this.loadSettings(); this.loadCosts();
  }
  loadHealth(): void { this.api.adminHealth().subscribe({ next: x => this.health = x, error: e => this.error = e?.error?.message || "Health check failed." }); }
  loadUsage(): void { this.api.usage().subscribe({ next: x => this.usage = x, error: e => this.error = e?.error?.message || "Could not load usage." }); }
  loadDlq(): void { this.api.webhookDlq().subscribe({ next: x => this.dlq = x, error: e => this.error = e?.error?.message || "Could not load webhook DLQ." }); }
  loadIndex(): void {
    this.api.indexStatus().subscribe({ next: x => this.indexStatus = x, error: e => this.error = e?.error?.message || "Could not load index status." });
    this.api.pendingIndexJobs().subscribe({ next: x => this.pendingJobs = x });
    this.api.indexJobs().subscribe({ next: x => this.jobs = x });
  }
  loadAudit(): void { this.api.auditLogs().subscribe({ next: x => this.audit = x, error: e => this.error = e?.error?.message || "Could not load audit logs." }); }
  loadSettings(): void { this.api.tenantSettings().subscribe({ next: x => this.settings = x, error: e => this.error = e?.error?.message || "Could not load tenant settings." }); }
  loadCosts(): void {
    const end = this.to || new Date().toISOString();
    const start = this.from || new Date(Date.now() - 30 * 86400000).toISOString();
    this.api.aiCosts(start, end).subscribe({ next: x => this.costs = x, error: e => this.error = e?.error?.message || "Could not load AI costs." });
  }
  reconcile(): void { this.api.reconcileSearch().subscribe({ next: () => { this.message = "Search reconciliation requested."; this.loadIndex(); }, error: e => this.error = e?.error?.message || "Reconciliation failed." }); }
  reindex(): void { this.api.reindex().subscribe({ next: x => this.message = `Reindex completed: ${x.indexed ?? 0} indexed.`, error: e => this.error = e?.error?.message || "Reindex failed." }); }
  replay(item: WebhookDeadLetter): void { this.api.replayWebhookDlq(item.id).subscribe({ next: () => this.loadDlq(), error: e => this.error = e?.error?.message || "Replay failed." }); }
  saveSettings(): void { this.api.saveTenantSettings(this.settings).subscribe({ next: x => { this.settings = x; this.message = "Tenant settings saved."; }, error: e => this.error = e?.error?.message || "Could not save settings." }); }
}
