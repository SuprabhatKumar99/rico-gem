import { CommonModule } from "@angular/common";
import { Component, OnDestroy, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { catchError, forkJoin, map, of } from "rxjs";
import { ApiService } from "../core/api.service";
import { SearchHit, Synonym } from "../models";

interface SynonymConflict {
  word: string;
  existingTerms: string[];
}

interface ImportSummary {
  imported: number;
  failed: number;
}

function splitCsvLine(line: string): string[] {
  const cells: string[] = [];
  let current = "";
  let inQuotes = false;

  for (let i = 0; i < line.length; i++) {
    const char = line[i];

    if (inQuotes) {
      if (char === '"' && line[i + 1] === '"') {
        current += '"';
        i++;
      } else if (char === '"') {
        inQuotes = false;
      } else {
        current += char;
      }
      continue;
    }

    if (char === '"') {
      inQuotes = true;
    } else if (char === ",") {
      cells.push(current);
      current = "";
    } else {
      current += char;
    }
  }

  cells.push(current);
  return cells;
}

function parseSynonyms(raw: string): string[] {
  return raw
    .split(/[;|]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

@Component({
  selector: "rk-synonyms",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      <p class="text-sm font-semibold text-slate-500">Administration</p>
      <h1 class="mt-1 text-3xl font-bold tracking-tight">Search synonyms</h1>
        <p class="mt-2 text-sm text-slate-500">Add alternate search terms so people find articles even when they search using different words.</p>

      <div class="mt-7 grid gap-6 xl:grid-cols-[380px_1fr]">
        <div class="space-y-6">
          <form class="rk-card p-6" (ngSubmit)="save()">
            <h2 class="font-bold">Add or update</h2>

            <div class="mt-5 space-y-4">
              <div>
                <label class="rk-label" for="term">Term</label>
                <input id="term" class="rk-input" [(ngModel)]="term" name="term" required />
              </div>

              <div>
                <label class="rk-label" for="synonymInput">Alternatives</label>

                <div
                  class="flex flex-wrap items-center gap-2 rounded-xl border border-slate-200 bg-white p-2 focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-[#2563eb]"
                  (click)="synonymInput.focus()"
                >
                  <span
                    *ngFor="let chip of synonymChips"
                    class="inline-flex items-center gap-1.5 rounded-full bg-slate-900 px-2.5 py-1 text-xs font-semibold text-white"
                  >
                    {{ chip }}
                    <button
                      type="button"
                      class="text-slate-400 hover:text-white"
                      [attr.aria-label]="'Remove ' + chip"
                      (click)="removeChip(chip)"
                    >
                      ✕
                    </button>
                  </span>

                  <input
                    #synonymInput
                    id="synonymInput"
                    class="min-w-[8rem] flex-1 border-0 bg-transparent px-1 py-1 text-sm outline-none"
                    [(ngModel)]="synonymDraft"
                    (keydown)="onSynonymKeydown($event)"
                    (blur)="addChipFromDraft()"
                    name="synonymDraft"
                    placeholder="Type a word, press Enter"
                  />
                </div>

                <p class="mt-1.5 text-xs text-slate-500">
                  Press Enter or comma to add each alternative. Click the ✕ on a chip to remove it.
                </p>
              </div>

              <div *ngIf="conflicts.length" class="rounded-xl border border-amber-300 bg-amber-50 p-3">
                <p class="text-xs font-bold text-amber-800">Heads up, these words already belong to other terms</p>
                <ul class="mt-2 space-y-1 text-xs text-amber-800">
                  <li *ngFor="let conflict of conflicts">
                    <span class="font-bold">{{ conflict.word }}</span> is used by
                    {{ conflict.existingTerms.join(", ") }}
                  </li>
                </ul>
              </div>

              <button class="rk-btn rk-btn-primary w-full">{{ saveLabel }}</button>

              <div *ngIf="saveWarningOpen" class="rounded-xl border border-slate-200 bg-slate-50 p-3">
                <p class="text-sm font-semibold">Save anyway?</p>
                <p class="mt-1 text-xs leading-5 text-slate-500">
                  Some alternatives already belong to another term. Saving may change what those searches return.
                </p>
                <div class="mt-3 flex gap-2">
                  <button type="button" class="rk-btn rk-btn-primary" (click)="confirmSave()">Save anyway</button>
                  <button type="button" class="rk-btn rk-btn-secondary" (click)="saveWarningOpen = false">Cancel</button>
                </div>
              </div>
            </div>
          </form>

          <section class="rk-card p-6">
            <h2 class="font-bold">Test my synonyms</h2>
            <p class="mt-1 text-xs text-slate-500">
              Type a word to see which existing articles would be found for it.
            </p>

            <input
              class="rk-input mt-4"
              placeholder="e.g. refund policy"
              [(ngModel)]="testWord"
              (ngModelChange)="onTestWordChange()"
              [ngModelOptions]="{ standalone: true }"
              aria-label="Test word"
            />

            <p *ngIf="testing" class="mt-3 text-xs text-slate-500">Searching...</p>

            <div *ngIf="!testing && testRan" class="mt-3">
              <p *ngIf="!testResults.length" class="text-sm text-slate-500">
                No articles would be found for "{{ lastTestWord }}".
              </p>

              <p *ngIf="testResults.length" class="text-xs font-semibold text-slate-500">
                {{ testResults.length }} article{{ testResults.length === 1 ? "" : "s" }} would be found
              </p>

              <div class="mt-2 space-y-2">
                <div *ngFor="let hit of testResults" class="rounded-xl border border-slate-200 p-3">
                  <p class="text-sm font-semibold">{{ hit.title }}</p>
                  <p class="mt-1 text-xs text-slate-500">{{ hit.excerpt }}</p>
                </div>
              </div>
            </div>
          </section>
        </div>

        <section class="rk-card overflow-hidden">
          <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 p-4">
            <p class="text-sm font-bold">{{ list.length }} synonym groups</p>

            <div class="flex flex-wrap items-center gap-2">
              <button type="button" class="rk-btn rk-btn-secondary" (click)="exportCsv()">Export CSV</button>
              <button type="button" class="rk-btn rk-btn-secondary" (click)="csvInput.click()">Import CSV</button>
              <input
                #csvInput
                type="file"
                accept=".csv,text/csv"
                class="hidden"
                (change)="importCsv($event)"
              />
            </div>
          </div>

          <p *ngIf="importSummary" class="border-b border-slate-200 bg-slate-50 px-5 py-3 text-xs font-semibold text-slate-600">
            Imported {{ importSummary.imported }} synonym group{{ importSummary.imported === 1 ? "" : "s" }}
            <span *ngIf="importSummary.failed">, {{ importSummary.failed }} failed</span>.
          </p>

          <div class="overflow-x-auto">
            <table class="w-full min-w-[620px] text-left text-sm">
              <thead class="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th class="px-5 py-3">Term</th>
                  <th class="px-5 py-3">Alternatives</th>
                  <th class="px-5 py-3"></th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr *ngFor="let item of list">
                  <td class="px-5 py-4 font-semibold">{{ item.term }}</td>
                  <td class="px-5 py-4 text-slate-600">{{ item.synonyms.join(", ") }}</td>
                  <td class="px-5 py-4 text-right">
                    <button class="rk-btn rk-btn-danger" (click)="remove(item)">Delete</button>
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
export class SynonymsComponent implements OnInit, OnDestroy {
  list: Synonym[] = [];
  term = "";

  synonymChips: string[] = [];
  synonymDraft = "";
  saveWarningOpen = false;

  testWord = "";
  lastTestWord = "";
  testResults: SearchHit[] = [];
  testing = false;
  testRan = false;

  importSummary: ImportSummary | null = null;

  private testTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly api: ApiService) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    if (this.testTimer) {
      clearTimeout(this.testTimer);
    }
  }

  load(): void {
    this.api.synonyms().subscribe((items) => {
      this.list = items;
    });
  }

  get saveLabel(): string {
    return this.saveWarningOpen ? "Check conflicts before saving" : "Save synonym";
  }

  onSynonymKeydown(event: KeyboardEvent): void {
    if (event.key === "Enter" || event.key === ",") {
      event.preventDefault();
      this.addChipFromDraft();
      return;
    }

    if (event.key === "Backspace" && !this.synonymDraft && this.synonymChips.length) {
      this.synonymChips = this.synonymChips.slice(0, -1);
    }
  }

  addChipFromDraft(): void {
    const word = this.synonymDraft.trim().replace(/,$/, "").trim();

    if (!word) {
      return;
    }

    this.synonymDraft = "";

    if (this.synonymChips.some((chip) => chip.toLowerCase() === word.toLowerCase())) {
      return;
    }

    this.synonymChips = [...this.synonymChips, word];
  }

  removeChip(chip: string): void {
    this.synonymChips = this.synonymChips.filter((item) => item !== chip);
  }

  get conflicts(): SynonymConflict[] {
    const currentTerm = this.term.trim().toLowerCase();

    return this.synonymChips
      .map((word) => {
        const needle = word.toLowerCase();
        const existingTerms = this.list
          .filter((item) => item.term.trim().toLowerCase() !== currentTerm)
          .filter(
            (item) =>
              item.term.trim().toLowerCase() === needle ||
              item.synonyms.some((synonym) => synonym.trim().toLowerCase() === needle)
          )
          .map((item) => item.term);

        return { word, existingTerms: [...new Set(existingTerms)] };
      })
      .filter((conflict) => conflict.existingTerms.length > 0);
  }

  onTestWordChange(): void {
    if (this.testTimer) {
      clearTimeout(this.testTimer);
    }

    const word = this.testWord.trim();

    if (!word) {
      this.testResults = [];
      this.testRan = false;
      this.lastTestWord = "";
      return;
    }

    this.testing = true;

    this.testTimer = setTimeout(() => {
      this.api.search(word).subscribe({
        next: (result) => {
          this.testResults = result.results;
          this.lastTestWord = word;
          this.testing = false;
          this.testRan = true;
        },
        error: () => {
          this.testResults = [];
          this.testing = false;
          this.testRan = true;
          this.lastTestWord = word;
        }
      });
    }, 350);
  }

  save(): void {
    this.addChipFromDraft();

    const term = this.term.trim();

    if (!term || !this.synonymChips.length) {
      return;
    }

    if (this.conflicts.length && !this.saveWarningOpen) {
      this.saveWarningOpen = true;
      return;
    }

    this.persist(term, [...this.synonymChips]);
  }

  confirmSave(): void {
    const term = this.term.trim();

    if (!term) {
      return;
    }

    this.persist(term, [...this.synonymChips]);
  }

  private persist(term: string, synonyms: string[]): void {
    this.api.upsertSynonym(term, synonyms).subscribe({
      next: () => {
        this.term = "";
        this.synonymChips = [];
        this.synonymDraft = "";
        this.saveWarningOpen = false;
        this.load();
      },
      error: () => {
        this.saveWarningOpen = false;
      }
    });
  }

  exportCsv(): void {
    const lines = ["term,synonyms"];

    for (const item of this.list) {
      lines.push(`"${item.term.replace(/"/g, '""')}","${item.synonyms.join(";").replace(/"/g, '""')}"`);
    }

    const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");

    link.href = url;
    link.download = "synonyms.csv";
    link.click();
    URL.revokeObjectURL(url);
  }

  importCsv(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];

    if (!file) {
      return;
    }

    const reader = new FileReader();

    reader.onload = () => {
      const rows = String(reader.result ?? "")
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter(Boolean)
        .map(splitCsvLine)
        .filter((cells) => cells[0] && !/^term$/i.test(cells[0].trim()));

      if (!rows.length) {
        this.importSummary = { imported: 0, failed: 0 };
        input.value = "";
        return;
      }

      forkJoin(
        rows.map((cells) => {
          const term = (cells[0] ?? "").trim();
          const synonyms = parseSynonyms(cells.slice(1).join(","));

          if (!term || !synonyms.length) {
            return of({ ok: false });
          }

          return this.api.upsertSynonym(term, synonyms).pipe(
            map(() => ({ ok: true })),
            catchError(() => of({ ok: false }))
          );
        })
      ).subscribe((results: { ok: boolean }[]) => {
        this.importSummary = {
          imported: results.filter((result) => result.ok).length,
          failed: results.filter((result) => !result.ok).length
        };
        input.value = "";
        this.load();
      });
    };

    reader.readAsText(file);
  }

  remove(item: Synonym): void {
    this.api.deleteSynonym(item.id).subscribe(() => this.load());
  }
}
