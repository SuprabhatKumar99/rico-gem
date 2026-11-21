import { CommonModule } from "@angular/common";
import { Component, ElementRef, Input, ViewChild } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { RouterLink } from "@angular/router";
import { ApiService } from "../core/api.service";

interface ChatTurn {
  role: "user" | "bot";
  text: string;
  citations?: string[];
  needsHandoff?: boolean;
}

@Component({
  selector: "rk-portal-assistant",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="fixed bottom-4 right-3 z-40 sm:right-4">
      <section
        *ngIf="open"
        class="mb-3 flex h-[min(480px,70vh)] w-[min(350px,calc(100vw-1.5rem))] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl"
        role="dialog"
        aria-label="Help Center Assistant"
      >
        <header
          class="flex items-center justify-between border-b border-slate-800 bg-slate-900 px-4 py-3.5 text-white"
          [style.background]="customAccentColor"
        >
          <div>
            <p class="text-sm font-bold">Knowledge Assistant</p>
            <p class="text-[11px] text-white/75">Grounded answers from published articles</p>
          </div>
          <button
            type="button"
            class="grid h-8 w-8 place-items-center rounded-lg text-white/80 hover:bg-white/10 hover:text-white"
            aria-label="Close assistant"
            (click)="open = false"
          >
            ✕
          </button>
        </header>

        <div #chatBody class="flex-1 space-y-3 overflow-y-auto bg-slate-50/60 p-4">
          <div *ngFor="let turn of turns" [class.text-right]="turn.role === 'user'">
            <div
              class="inline-block max-w-[90%] rounded-2xl px-3.5 py-2.5 text-left text-sm leading-6"
              [class.bg-slate-900]="turn.role === 'user'"
              [class.text-white]="turn.role === 'user'"
              [class.bg-white]="turn.role === 'bot'"
              [class.border]="turn.role === 'bot'"
              [class.border-slate-200]="turn.role === 'bot'"
              [class.text-slate-700]="turn.role === 'bot'"
            >
              {{ turn.text }}
              <div *ngIf="turn.citations?.length" class="mt-2.5 border-t border-slate-100 pt-2">
                <p class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Sources</p>
                <div class="mt-1 flex flex-wrap gap-1.5">
                  <a
                    *ngFor="let slug of turn.citations"
                    [routerLink]="['/portal/article', slug]"
                    [queryParams]="{ tenant: tenantSlug }"
                    class="rounded-lg border border-slate-200 bg-slate-50 px-2 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-100"
                    (click)="open = false"
                  >
                    {{ slug }}
                  </a>
                </div>
              </div>
            </div>
          </div>

          <div *ngIf="turns.length === 1" class="pt-1">
            <p class="text-[11px] font-semibold uppercase tracking-wider text-slate-400">Try asking</p>
            <div class="mt-2 flex flex-wrap gap-1.5">
              <button
                *ngFor="let suggestion of suggestions"
                type="button"
                class="rounded-xl border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100"
                (click)="askQuestion(suggestion)"
              >
                {{ suggestion }}
              </button>
            </div>
          </div>
        </div>

        <form class="flex gap-2 border-t border-slate-200 bg-white p-3" (ngSubmit)="submit()">
          <input
            class="rk-input !mt-0 min-w-0 flex-1 text-sm"
            [(ngModel)]="draft"
            name="assistantQuery"
            placeholder="Ask a question…"
            aria-label="Ask the help center assistant"
            [disabled]="busy"
          />
          <button
            type="submit"
            class="rk-btn rk-btn-primary"
            [style.background]="customAccentColor"
            [class.text-white]="!!customAccentColor"
            [disabled]="busy || !draft.trim()"
          >
            {{ busy ? "…" : "Ask" }}
          </button>
        </form>
      </section>

      <button
        type="button"
        class="rk-btn rk-btn-primary ml-auto flex items-center gap-2 rounded-xl px-3.5 py-2.5 text-xs font-semibold shadow-lg transition hover:opacity-95 sm:rounded-2xl sm:px-4 sm:py-3 sm:text-sm"
        [style.background]="customAccentColor"
        [class.text-white]="!!customAccentColor"
        [attr.aria-expanded]="open"
        (click)="open = !open"
      >
        <span>{{ open ? "Hide assistant" : "Ask Assistant" }}</span>
      </button>
    </div>
  `
})
export class PortalAssistantComponent {
  @Input() tenantSlug = "demo";
  @Input() primaryColor = "#0f172a";
  @ViewChild("chatBody") chatBody?: ElementRef<HTMLDivElement>;

  get customAccentColor(): string | null {
    const color = this.primaryColor?.trim().toLowerCase();
    if (!color || color === "#0f172a" || color === "#000000" || color === "#111827") {
      return null;
    }
    return this.primaryColor;
  }

  open = false;
  draft = "";
  busy = false;
  conversationId: string | null = null;
  private readonly sessionId = `portal-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  readonly suggestions = [
    "How do I reset my password?",
    "How do I configure SAML SSO?",
    "How do I connect an AI agent?"
  ];

  turns: ChatTurn[] = [
    {
      role: "bot",
      text: "Hi! Ask a question and I'll find an answer grounded in our published help articles."
    }
  ];

  constructor(private readonly api: ApiService) {}

  submit(): void {
    const q = this.draft.trim();
    if (!q || this.busy) return;
    this.draft = "";
    this.askQuestion(q);
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = this.chatBody?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, 0);
  }

  askQuestion(q: string): void {
    if (!q.trim() || this.busy) return;
    this.turns.push({ role: "user", text: q });
    this.scrollToBottom();
    this.busy = true;
    const tenant = this.tenantSlug || "demo";
    this.api.widgetAnswer(tenant, q, this.sessionId, "en", this.conversationId).subscribe({
      next: (res) => {
        this.conversationId = res.conversationId ?? this.conversationId;
        this.turns.push({
          role: "bot",
          text: res.answer,
          citations: res.articleSlugs || [],
          needsHandoff: res.needsHandoff
        });
        this.busy = false;
        this.scrollToBottom();
      },
      error: () => {
        this.turns.push({
          role: "bot",
          text: "I couldn't reach the assistant right now. Try searching the portal or contacting support."
        });
        this.busy = false;
        this.scrollToBottom();
      }
    });
  }
}
