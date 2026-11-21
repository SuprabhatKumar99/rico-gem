import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, Router, RouterLink } from "@angular/router";
import { ApiService } from "../core/api.service";
import { AuthService } from "../core/auth.service";
import { Article, ArticleVersion, Category, Visibility, ArticleMetadata, ArticleTemplate, VersionDiff, BlockComment, ArticleGovernance, ArticleQuality, ReviewHistory, ReusableBlock, SavedArticleTemplate, VerificationEvent } from "../models";
import { StatusComponent } from "../shared/ui";

type ContentBlockType = "heading" | "paragraph" | "image" | "list" | "code";

interface EditorBlock {
  id?: string;
  type: ContentBlockType;
  text?: string;
  level?: number;
  items?: string[];
  url?: string;
  alt?: string;
}

@Component({
  selector: "rk-article-editor",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StatusComponent],
  template: `
    <div>
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <a routerLink="/articles" class="text-sm font-semibold text-slate-500 hover:text-slate-950">← Articles</a>
          <div class="mt-3 flex flex-wrap items-center gap-3">
            <h1 class="text-3xl font-bold tracking-tight">{{ article?.title || "New article" }}</h1>
            <rk-status *ngIf="version" [status]="version.status" />
          </div>
          <p *ngIf="article" class="mt-2 text-sm text-slate-500">
            v{{ version?.versionNumber ?? article.activeVersionNumber ?? "—" }} · {{ article.visibility }}
          </p>
        </div>

        <div class="flex flex-wrap gap-2">
          <a *ngIf="article && version && canEditContent" [routerLink]="['/collaborate', article.id, version.id]" class="rk-btn rk-btn-primary">Open TipTap live editor</a>
          <a *ngIf="article" [routerLink]="['/articles', article.id, 'review']" class="rk-btn rk-btn-secondary">Review workspace</a>
          <button *ngIf="article && version" class="rk-btn rk-btn-secondary" (click)="preview = !preview">
            {{ preview ? "Edit" : "Preview" }}
          </button>
          <button *ngIf="article && canEdit" class="rk-btn rk-btn-secondary" (click)="newVersion()">
            New version
          </button>
          <button *ngIf="article && canEdit && version?.status === 'DRAFT'" class="rk-btn rk-btn-secondary" (click)="submit()">
            Submit for review
          </button>
          <button *ngIf="canEditContent && !article" class="rk-btn rk-btn-primary" (click)="save()">
            {{ saving ? "Saving…" : "Save draft" }}
          </button>
        </div>
      </div>

      <div *ngIf="message" role="status" class="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">
        {{ message }}
      </div>

      <div *ngIf="error" role="alert" class="mt-5 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">
        {{ error }}
      </div>

      <div class="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
        <section class="rk-card overflow-hidden">
          <div *ngIf="preview; else editor" class="p-6 sm:p-8">
            <div class="mx-auto max-w-3xl">
              <p class="text-xs font-bold uppercase tracking-widest text-slate-400">Preview</p>
              <h2 class="mt-3 text-3xl font-bold">{{ article?.title || form.title }}</h2>

              <div class="mt-8 space-y-6">
                <ng-container *ngFor="let block of blocks">
                  <h3
                    *ngIf="block.type === 'heading'"
                    class="font-bold text-slate-950"
                    [class.text-2xl]="block.level === 1"
                    [class.text-xl]="block.level === 2"
                    [class.text-lg]="block.level === 3 || block.level === 4"
                  >
                    {{ block.text }}
                  </h3>

                  <p *ngIf="block.type === 'paragraph'" class="whitespace-pre-wrap text-base leading-8 text-slate-700">
                    {{ block.text }}
                  </p>

                  <pre *ngIf="block.type === 'code'" class="overflow-auto rounded-xl bg-slate-950 p-5 font-mono text-sm leading-6 text-white">{{ block.text }}</pre>

                  <ul *ngIf="block.type === 'list'" class="list-disc space-y-2 pl-6 text-base leading-7 text-slate-700">
                    <li *ngFor="let item of block.items">{{ item }}</li>
                  </ul>

                  <figure *ngIf="block.type === 'image' && block.url" class="space-y-2">
                    <img [src]="block.url" [alt]="block.alt || ''" class="max-h-[520px] rounded-xl border border-slate-200 object-contain" />
                    <figcaption *ngIf="block.alt" class="text-sm text-slate-500">{{ block.alt }}</figcaption>
                  </figure>
                </ng-container>
              </div>
            </div>
          </div>

          <ng-template #editor>
            <div *ngIf="article && version && canEditContent" class="m-4 rounded-xl border border-indigo-200 bg-indigo-50 p-4 text-sm text-indigo-900">
              Rich editing, autosave and simultaneous collaboration use the TipTap workspace above. The structured blocks below are read-only for existing drafts to prevent conflicting writes.
            </div>
            <div class="border-b border-slate-200 px-6 py-4">
              <p class="text-sm font-bold">Article content</p>
              <p class="mt-1 text-xs text-slate-500">
                Use the structured editor below. It produces the backend's required <code>blocks</code> JSON object instead of storing the editor content as a JSON string.
              </p>
            </div>

            <div *ngIf="!article" class="border-b border-slate-200 bg-slate-50 px-4 py-3 sm:px-6">
              <div class="flex flex-wrap gap-2">
                <button class="rk-btn rk-btn-secondary" [disabled]="!canEditContent" (click)="addBlock('paragraph')">Paragraph</button>
                <button class="rk-btn rk-btn-secondary" [disabled]="!canEditContent" (click)="addBlock('heading')">Heading</button>
                <button class="rk-btn rk-btn-secondary" [disabled]="!canEditContent" (click)="addBlock('list')">List</button>
                <button class="rk-btn rk-btn-secondary" [disabled]="!canEditContent" (click)="addBlock('code')">Code</button>
                <button class="rk-btn rk-btn-secondary" [disabled]="!canEditContent" (click)="addBlock('image')">Image</button>
              </div>
            </div>

            <div *ngIf="!article" class="p-4 sm:p-6">
              <div *ngIf="blocks.length === 0" class="rounded-xl border border-dashed border-slate-300 p-8 text-center">
                <p class="font-semibold text-slate-800">Start writing your article</p>
                <p class="mt-1 text-sm text-slate-500">Add a paragraph or heading from the toolbar above.</p>
              </div>

              <div class="space-y-4">
                <article *ngFor="let block of blocks; let index = index" class="rounded-xl border border-slate-200 bg-white p-4" [class.border-indigo-500]="selectedBlockId === block.id">
                  <button *ngIf="article && version" class="float-right text-xs font-semibold text-indigo-600" type="button" (click)="selectReviewBlock(block.id!)">Review comments ({{ commentsForBlock(block.id!).length }})</button>
                  <div class="flex flex-wrap items-center justify-between gap-3">
                    <div class="flex items-center gap-2">
                      <span class="text-xs font-bold uppercase tracking-widest text-slate-400">{{ block.type }}</span>
                      <select
                        class="rk-select !mt-0 w-auto py-1.5 text-sm"
                        [(ngModel)]="block.type"
                        [name]="'block-type-' + index"
                        [disabled]="!canEditContent"
                        (ngModelChange)="normalizeBlock(block)"
                      >
                        <option value="paragraph">Paragraph</option>
                        <option value="heading">Heading</option>
                        <option value="list">List</option>
                        <option value="code">Code</option>
                        <option value="image">Image</option>
                      </select>
                    </div>

                    <div class="flex gap-1">
                      <button class="rounded-lg px-2 py-1 text-sm text-slate-500 hover:bg-slate-100" [disabled]="!canEdit || index === 0" (click)="moveBlock(index, -1)" aria-label="Move block up">↑</button>
                      <button class="rounded-lg px-2 py-1 text-sm text-slate-500 hover:bg-slate-100" [disabled]="!canEdit || index === blocks.length - 1" (click)="moveBlock(index, 1)" aria-label="Move block down">↓</button>
                      <button class="rounded-lg px-2 py-1 text-sm text-rose-600 hover:bg-rose-50" [disabled]="!canEditContent" (click)="removeBlock(index)">Remove</button>
                    </div>
                  </div>

                  <div *ngIf="block.type === 'heading'" class="mt-4 grid gap-4 sm:grid-cols-[120px_minmax(0,1fr)]">
                    <div>
                      <label class="rk-label" [for]="'heading-level-' + index">Level</label>
                      <select id="heading-level-{{ index }}" class="rk-select" [(ngModel)]="block.level" [name]="'heading-level-' + index" [disabled]="!canEditContent">
                        <option [ngValue]="1">H1</option>
                        <option [ngValue]="2">H2</option>
                        <option [ngValue]="3">H3</option>
                        <option [ngValue]="4">H4</option>
                      </select>
                    </div>
                    <div>
                      <label class="rk-label" [for]="'heading-text-' + index">Heading</label>
                      <input id="heading-text-{{ index }}" class="rk-input text-lg font-semibold" [(ngModel)]="block.text" [name]="'heading-text-' + index" [disabled]="!canEditContent" placeholder="Heading text" />
                    </div>
                  </div>

                  <div *ngIf="block.type === 'paragraph'" class="mt-4">
                    <label class="rk-label" [for]="'paragraph-text-' + index">Text</label>
                    <textarea id="paragraph-text-{{ index }}" class="rk-textarea min-h-[150px] resize-y text-base leading-7" [(ngModel)]="block.text" [name]="'paragraph-text-' + index" [disabled]="!canEditContent" placeholder="Write your article paragraph..."></textarea>
                  </div>

                  <div *ngIf="block.type === 'code'" class="mt-4">
                    <label class="rk-label" [for]="'code-text-' + index">Code</label>
                    <textarea id="code-text-{{ index }}" class="rk-textarea min-h-[180px] resize-y font-mono text-sm leading-6" [(ngModel)]="block.text" [name]="'code-text-' + index" [disabled]="!canEditContent" spellcheck="false" placeholder="Paste code here..."></textarea>
                  </div>

                  <div *ngIf="block.type === 'list'" class="mt-4">
                    <label class="rk-label">List items</label>
                    <div class="space-y-2">
                      <div *ngFor="let item of block.items; let itemIndex = index" class="flex gap-2">
                        <input class="rk-input !mt-0" [(ngModel)]="block.items![itemIndex]" [name]="'list-' + index + '-' + itemIndex" [disabled]="!canEditContent" placeholder="List item" />
                        <button class="rounded-lg px-3 text-sm text-rose-600 hover:bg-rose-50" [disabled]="!canEditContent" (click)="removeListItem(block, itemIndex)">Remove</button>
                      </div>
                    </div>
                    <button class="rk-btn rk-btn-secondary mt-3" [disabled]="!canEditContent" (click)="addListItem(block)">Add item</button>
                  </div>

                  <div *ngIf="block.type === 'image'" class="mt-4 grid gap-4">
                    <div>
                      <label class="rk-label" [for]="'image-url-' + index">Image URL</label>
                      <input id="image-url-{{ index }}" class="rk-input" [(ngModel)]="block.url" [name]="'image-url-' + index" [disabled]="!canEditContent" placeholder="https://..." />
                    </div>
                    <div>
                      <label class="rk-label" [for]="'image-alt-' + index">Alt text</label>
                      <input id="image-alt-{{ index }}" class="rk-input" [(ngModel)]="block.alt" [name]="'image-alt-' + index" [disabled]="!canEditContent" placeholder="Describe the image" />
                    </div>
                    <img *ngIf="block.url" [src]="block.url" [alt]="block.alt || ''" class="max-h-72 rounded-xl border border-slate-200 object-contain" />
                  </div>

                </article>
              </div>

              <div class="mt-6 rounded-xl bg-slate-50 p-4">
                <div class="flex items-center justify-between gap-3">
                  <p class="text-xs font-bold uppercase tracking-widest text-slate-400">Generated content payload</p>
                  <button class="text-xs font-semibold text-slate-500 hover:text-slate-950" [disabled]="!canEditContent" (click)="showJson = !showJson">
                    {{ showJson ? "Hide JSON" : "Show JSON" }}
                  </button>
                </div>
                <pre *ngIf="showJson" class="mt-3 overflow-auto text-xs leading-5 text-slate-600">{{ contentJson }}</pre>
              </div>
            </div>

            <div *ngIf="article" class="p-6 sm:p-8">
              <div *ngIf="blocks.length === 0" class="rounded-xl border border-dashed border-slate-300 p-8 text-center">
                <p class="font-semibold text-slate-800">No content blocks yet</p>
                <p class="mt-1 text-sm text-slate-500">Open the TipTap live editor above to author rich content for this draft.</p>
              </div>

              <div class="space-y-4">
                <article
                  *ngFor="let block of blocks"
                  class="rounded-xl border border-slate-200 bg-white p-4"
                  [class.border-indigo-500]="selectedBlockId === block.id"
                >
                  <div class="mb-2 flex items-center justify-between gap-2">
                    <span class="text-xs font-bold uppercase tracking-widest text-slate-400">{{ block.type }}</span>
                    <button
                      *ngIf="version && block.id"
                      class="text-xs font-semibold text-indigo-600 hover:text-indigo-800"
                      type="button"
                      (click)="selectReviewBlock(block.id!)"
                    >
                      Review comments ({{ commentsForBlock(block.id!).length }})
                    </button>
                  </div>

                  <h3
                    *ngIf="block.type === 'heading'"
                    class="font-bold text-slate-950"
                    [class.text-2xl]="block.level === 1"
                    [class.text-xl]="block.level === 2"
                    [class.text-lg]="block.level === 3 || block.level === 4"
                  >
                    {{ block.text }}
                  </h3>

                  <p *ngIf="block.type === 'paragraph'" class="whitespace-pre-wrap text-base leading-7 text-slate-700">
                    {{ block.text }}
                  </p>

                  <pre *ngIf="block.type === 'code'" class="overflow-auto rounded-xl bg-slate-950 p-4 font-mono text-sm leading-6 text-white">{{ block.text }}</pre>

                  <ul *ngIf="block.type === 'list'" class="list-disc space-y-1.5 pl-6 text-base leading-7 text-slate-700">
                    <li *ngFor="let item of block.items">{{ item }}</li>
                  </ul>

                  <figure *ngIf="block.type === 'image' && block.url" class="space-y-2">
                    <img [src]="block.url" [alt]="block.alt || ''" class="max-h-96 rounded-xl border border-slate-200 object-contain" />
                    <figcaption *ngIf="block.alt" class="text-sm text-slate-500">{{ block.alt }}</figcaption>
                  </figure>
                </article>
              </div>
            </div>
          </ng-template>
        </section>

        <aside class="space-y-6">
          <section class="rk-card p-5" *ngIf="!article && templates.length">
            <h2 class="font-bold">Article template</h2>
            <p class="mt-1 text-xs text-slate-500">Start from a governed structure for common article types.</p>
            <select class="rk-select mt-4" [(ngModel)]="templateKey" name="template">
              <option value="">Blank article</option>
              <option *ngFor="let template of templates" [value]="template.key">{{ template.name }}</option>
            </select>
            <button class="rk-btn rk-btn-secondary mt-3 w-full" (click)="applyTemplate()">Apply template</button>
          </section>

          <section class="rk-card p-5">
            <h2 class="font-bold">Article settings</h2>

            <div class="mt-5">
              <label class="rk-label" for="title">Title</label>
              <input id="title" class="rk-input" [(ngModel)]="form.title" name="title" [disabled]="!!article || !canEdit" />
            </div>

            <div class="mt-5">
              <label class="rk-label" for="slug">Slug</label>
              <input id="slug" class="rk-input" [(ngModel)]="form.slug" name="slug" [disabled]="!!article || !canEdit" />
            </div>

            <div class="mt-5">
              <label class="rk-label" for="category">Category</label>
              <select
                id="category"
                class="rk-select"
                [(ngModel)]="form.categoryId"
                name="category"
                [disabled]="!!article || !canEdit"
              >
                <option value="">No category</option>
                <option *ngFor="let category of categories" [value]="category.id">
                  {{ category.name }}
                </option>
              </select>
            </div>

            <div *ngIf="article" class="mt-5">
              <label class="rk-label" for="visibility">Visibility</label>
              <select
                id="visibility"
                class="rk-select"
                [(ngModel)]="visibility"
                name="visibility"
                [disabled]="!canEdit"
              >
                <option value="PUBLIC">Public</option>
                <option value="AUTHENTICATED">Authenticated</option>
                <option value="PRIVATE">Private</option>
              </select>
              <button class="rk-btn rk-btn-secondary mt-3 w-full" [disabled]="!canEdit" (click)="setVisibility()">
                Update visibility
              </button>
            </div>

            <button
              *ngIf="article && canEdit"
              class="rk-btn rk-btn-danger mt-3 w-full"
              (click)="archive()"
            >
              Archive article
            </button>
          </section>

          <section class="rk-card p-5" *ngIf="article && canAnalyze && !canEdit">
            <h2 class="font-bold">Article intelligence</h2>
            <p class="mt-1 text-xs leading-5 text-slate-500">Review article intelligence and dependencies without editing the article.</p>
            <div class="mt-4 grid gap-2">
              <button class="rk-btn rk-btn-secondary w-full" [disabled]="aiBusy" (click)="analyzeWithAi()">Analyze article</button>
              <button class="rk-btn rk-btn-secondary w-full" [disabled]="aiBusy" (click)="detectDependencies()">Detect dependencies</button>
            </div>
            <div *ngIf="aiResult" class="mt-4 rounded-xl bg-slate-50 p-3 text-xs leading-5 text-slate-600">
              <p class="font-bold text-slate-800">Analysis result</p>
              <pre class="mt-2 max-h-64 overflow-auto whitespace-pre-wrap">{{ aiResult | json }}</pre>
            </div>
          </section>

          <section class="rk-card p-5" *ngIf="canEdit">
            <h2 class="font-bold">AI knowledge assistant</h2>
            <p class="mt-1 text-xs leading-5 text-slate-500">Generate, improve, analyze, and detect dependencies using the backend intelligence endpoints.</p>
            <div class="mt-4 space-y-3">
              <input class="rk-input" [(ngModel)]="aiInstruction" name="aiInstruction" placeholder="Improve clarity, add examples…" />
              <button class="rk-btn rk-btn-secondary w-full" [disabled]="aiBusy || !blocks.length" (click)="improveWithAi()">{{ aiBusy ? "Working…" : "Improve current content" }}</button>
              <button *ngIf="article" class="rk-btn rk-btn-secondary w-full" [disabled]="aiBusy" (click)="analyzeWithAi()">Analyze article</button>
              <button *ngIf="article" class="rk-btn rk-btn-secondary w-full" [disabled]="aiBusy" (click)="detectDependencies()">Detect dependencies</button>
            </div>
            <div *ngIf="aiResult" class="mt-4 rounded-xl bg-slate-50 p-3 text-xs leading-5 text-slate-600">
              <p class="font-bold text-slate-800">AI result</p>
              <pre class="mt-2 max-h-64 overflow-auto whitespace-pre-wrap">{{ aiResult | json }}</pre>
            </div>
          </section>

          <section class="rk-card p-5" *ngIf="article">
            <h2 class="font-bold">Content discovery</h2>
            <div class="mt-4">
              <label class="rk-label" for="tags">Tags</label>
              <input id="tags" class="rk-input" [(ngModel)]="tagsInput" name="tags" placeholder="billing, onboarding, api" [disabled]="!canEdit" />
            </div>
            <label class="mt-4 flex items-center gap-2 text-sm"><input type="checkbox" [(ngModel)]="metadata.pinned" name="pinned" [disabled]="!canEdit" /> Pinned article</label>
            <label class="mt-3 flex items-center gap-2 text-sm"><input type="checkbox" [(ngModel)]="metadata.featured" name="featured" [disabled]="!canEdit" /> Featured article</label>
            <div class="mt-4">
              <label class="rk-label" for="related">Related article IDs</label>
              <input id="related" class="rk-input" [(ngModel)]="relatedInput" name="related" placeholder="UUID, UUID" [disabled]="!canEdit" />
            </div>
            <button class="rk-btn rk-btn-secondary mt-4 w-full" [disabled]="!canEdit" (click)="saveMetadata()">Save discovery settings</button>
          </section>

          <section class="rk-card p-5" *ngIf="article && canEdit">
            <h2 class="font-bold">Publishing schedule</h2>
            <div class="mt-3 rounded-lg bg-slate-50 p-3 text-xs leading-5 text-slate-600" *ngIf="version?.status === 'DRAFT'">
              Scheduled publishing requires independent reviewer approval. Submit this draft, then ask a reviewer to approve for scheduling.
              <button class="rk-btn rk-btn-secondary mt-3 w-full" (click)="prepareForSchedule()">Submit for review</button>
            </div>
            <p *ngIf="version?.status === 'IN_REVIEW'" class="mt-2 text-xs text-amber-700">A reviewer must approve this version for scheduling first.</p>
            <div class="mt-4" *ngIf="version?.status === 'APPROVED' || version?.status === 'SCHEDULED'">
              <label class="rk-label" for="publishAt">Publish at</label>
              <input id="publishAt" type="datetime-local" class="rk-input" [(ngModel)]="publishAt" name="publishAt" />
              <button class="rk-btn rk-btn-secondary mt-3 w-full" [disabled]="!publishAt || version?.status === 'SCHEDULED'" (click)="schedulePublish()">Schedule publish</button>
            </div>
            <div class="mt-4" *ngIf="article.status === 'PUBLISHED'">
              <label class="rk-label" for="unpublishAt">Unpublish at</label>
              <input id="unpublishAt" type="datetime-local" class="rk-input" [(ngModel)]="unpublishAt" name="unpublishAt" />
              <button class="rk-btn rk-btn-secondary mt-3 w-full" (click)="scheduleUnpublish()">Schedule unpublish</button>
            </div>
            <p *ngIf="article.scheduledPublishAt || article.scheduledUnpublishAt" class="mt-3 text-xs text-slate-500">
              Scheduled: {{ article.scheduledPublishAt || article.scheduledUnpublishAt }}
            </p>
            <button *ngIf="article.scheduledPublishAt || article.scheduledUnpublishAt" class="rk-btn rk-btn-secondary mt-3 w-full" (click)="clearSchedule()">Clear schedule</button>
          </section>

          <section class="rk-card p-5" *ngIf="article && version">
            <div class="flex items-center justify-between gap-3">
              <h2 class="font-bold">Version comparison</h2>
              <button class="text-xs font-semibold text-slate-500" *ngIf="diff" (click)="diff = null">Close</button>
            </div>
            <p *ngIf="!diff" class="mt-2 text-xs text-slate-500">Choose Compare on any version to see block-level changes.</p>
            <div *ngIf="diff" class="mt-4 space-y-2">
              <div *ngFor="let change of diff.blocks" class="rounded-lg border border-slate-200 p-3 text-xs">
                <span class="font-bold">{{ change.change }}</span> · {{ change.blockId }}
                <pre class="mt-2 max-h-32 overflow-auto whitespace-pre-wrap text-slate-500">{{ { before: change.before, after: change.after } | json }}</pre>
              </div>
              <p *ngIf="!diff.blocks.length" class="text-xs text-slate-500">No block-level changes.</p>
            </div>
          </section>

          <section class="rk-card p-5">
            <h2 class="font-bold">Version history</h2>

            <div class="mt-4 divide-y divide-slate-100">
              <div
                *ngFor="let item of versions"
                class="block w-full py-3 text-left"
                [class.bg-slate-50]="item.id === version?.id"
              >
                <button type="button" class="w-full text-left" (click)="selectVersion(item)">
                  <div class="flex items-center justify-between gap-3">
                    <span class="font-semibold">v{{ item.versionNumber }}</span>
                    <rk-status [status]="item.status" />
                  </div>
                  <p class="mt-1 text-xs text-slate-500">{{ item.changeSummary || "No change summary" }}</p>
                </button>
                <div class="mt-2 flex gap-3">
                  <button type="button" class="text-xs font-semibold text-slate-600 hover:underline" (click)="compareVersion(item)">Compare with selected version</button>
                  <button type="button" *ngIf="canEdit" class="text-xs font-semibold text-amber-700 hover:underline" (click)="rollback(item)">Rollback</button>
                </div>
              </div>
            </div>
          </section>

          <section *ngIf="article && version" class="rk-card p-5">
            <h2 class="font-bold">Block review comments</h2>
            <p class="mt-1 text-xs text-slate-500">Select a content block to comment on the exact version. Replies stay in the same thread.</p>
            <select class="rk-select mt-3" [(ngModel)]="selectedBlockId" (ngModelChange)="replyToId = null" aria-label="Review block">
              <option value="">Choose a block</option>
              <option *ngFor="let b of blocks;let i=index" [value]="b.id">{{ i+1 }}. {{ b.type }} · {{ (b.text || b.alt || '').slice(0,28) }}</option>
            </select>
            <div *ngIf="selectedBlockId" class="mt-4 space-y-2">
              <div *ngFor="let c of commentsForBlock(selectedBlockId)" class="rounded-lg border border-slate-200 p-3 text-xs" [class.ml-4]="!!c.parentId">
                <p class="font-semibold">{{ c.authorId.slice(0,8) }} · {{ c.createdAt | date:'short' }}</p>
                <p class="mt-1 whitespace-pre-wrap">{{ c.comment }}</p>
                <p *ngIf="c.resolved" class="text-emerald-700">Resolved</p>
                <button class="mt-2 mr-3 text-indigo-700" (click)="replyToId = c.id">Reply</button>
                <button *ngIf="canEdit || canReview" [disabled]="c.resolved" class="mt-2 text-amber-700" (click)="resolveReviewComment(c)">Resolve</button>
              </div>
              <p *ngIf="replyToId" class="text-xs text-indigo-700">Replying to {{ replyToId.slice(0,8) }} <button (click)="replyToId=null">Cancel</button></p>
              <textarea class="rk-textarea" [(ngModel)]="commentDraft" rows="3" placeholder="Add an actionable review comment"></textarea>
              <button *ngIf="canEdit || canReview" class="rk-btn rk-btn-secondary w-full" [disabled]="!commentDraft.trim()" (click)="addReviewComment()">Post comment</button>
            </div>
          </section>

          <section *ngIf="article && version?.status === 'IN_REVIEW' && canReview" class="rk-card p-5">
            <h2 class="font-bold">Review decision</h2>
            <p class="mt-1 text-xs text-slate-500">Resolve all review comments before approval. Independent-review separation can be enforced in tenant policy.</p>
            <textarea class="rk-textarea mt-3" [(ngModel)]="reviewNote" rows="2" placeholder="Decision rationale"></textarea>
            <div class="mt-3 flex flex-wrap gap-2">
              <button class="rk-btn rk-btn-primary" (click)="decideReview(true)">Approve & publish</button>
              <button class="rk-btn rk-btn-secondary" (click)="approveSchedule()">Approve for schedule</button>
              <button class="rk-btn rk-btn-secondary" (click)="decideReview(false)">Reject</button>
            </div>
          </section>

          <section *ngIf="article" class="rk-card p-5">
            <h2 class="font-bold">Governance & verification</h2>
            <div *ngIf="governance" class="mt-3 text-xs leading-6 text-slate-600">
              <p>Owner: {{ governance.ownerId || 'Unassigned' }}</p>
              <p>Next review: {{ governance.nextReviewAt ? (governance.nextReviewAt | date:'mediumDate') : 'Not scheduled' }}</p>
              <p>Last verified: {{ governance.verifiedAt ? (governance.verifiedAt | date:'mediumDate') : 'Never' }}</p>
            </div>
            <div *ngIf="canEdit" class="mt-3 space-y-2">
              <label class="rk-label">Owner UUID</label><input class="rk-input" [(ngModel)]="ownerInput" placeholder="User ID in this tenant" />
              <label class="rk-label">Review interval (days)</label><input class="rk-input" type="number" min="1" max="3650" [(ngModel)]="reviewIntervalInput" />
              <button class="rk-btn rk-btn-secondary w-full" (click)="saveGovernance()">Save governance</button>
            </div>
            <button class="rk-btn rk-btn-secondary mt-3 w-full" (click)="checkQuality()">Run quality checks</button>
            <div *ngIf="quality" class="mt-3 text-xs">
              <p class="font-semibold">Quality score: {{ quality.score }}/100</p>
              <p *ngFor="let warning of quality.warnings" class="mt-1 text-amber-700">{{ warning }}</p>
              <p *ngIf="!quality.warnings.length" class="text-emerald-700">All deterministic checks passed.</p>
            </div>
            <button *ngIf="canReview" class="rk-btn rk-btn-secondary mt-3 w-full" [disabled]="!quality?.readyForVerification" (click)="verifyContent()">Verify content</button>
            <h3 class="mt-4 text-sm font-semibold">Approval history</h3>
            <div *ngFor="let item of reviewHistory" class="mt-2 text-xs text-slate-600">{{ item.createdAt | date:'short' }} · {{ item.decision }} · {{ item.comment || 'No note' }}</div>
            <h3 class="mt-4 text-sm font-semibold">Verification history</h3>
            <div *ngFor="let event of verificationEvents" class="mt-2 text-xs text-slate-600">{{ event.at | date:'short' }} · {{ event.action }}</div>
          </section>

          <section *ngIf="canEdit" class="rk-card p-5">
            <h2 class="font-bold">Reusable content library</h2>
            <select class="rk-select mt-3" [(ngModel)]="selectedReusableId"><option value="">Select reusable block</option><option *ngFor="let b of reusableBlocks" [value]="b.id">{{ b.name }}</option></select>
            <button class="rk-btn rk-btn-secondary mt-2 w-full" [disabled]="!selectedReusableId" (click)="insertReusableBlock()">Insert block</button>
            <button *ngIf="blocks.length" class="rk-btn rk-btn-secondary mt-2 w-full" (click)="saveReusableBlock()">Save selected block to library</button>
            <select class="rk-select mt-3" [(ngModel)]="selectedCustomTemplateId"><option value="">Select custom template</option><option *ngFor="let t of customTemplates" [value]="t.id">{{ t.name }}</option></select>
            <button class="rk-btn rk-btn-secondary mt-2 w-full" [disabled]="!selectedCustomTemplateId" (click)="insertCustomTemplate()">Apply custom template</button>
            <button class="rk-btn rk-btn-secondary mt-2 w-full" [disabled]="!blocks.length" (click)="saveCustomTemplate()">Save article as template</button>
          </section>

          <section *ngIf="article && canEdit" class="rk-card p-5">
            <h2 class="font-bold">Assets</h2>
            <p class="mt-1 text-xs leading-5 text-slate-500">
              Upload uses the backend multipart asset endpoint.
            </p>

            <input
              class="mt-4 block w-full text-sm"
              type="file"
              (change)="uploadAsset($event)"
            />

            <a
              *ngIf="assetUrl"
              [href]="assetUrl"
              target="_blank"
              rel="noopener"
              class="mt-3 block break-all text-sm font-semibold text-slate-700 underline"
            >
              Open uploaded asset
            </a>
          </section>
        </aside>
      </div>
    </div>
  `
})
export class ArticleEditorComponent implements OnInit {
  article?: Article;
  versions: ArticleVersion[] = [];
  version?: ArticleVersion;
  categories: Category[] = [];
  blocks: EditorBlock[] = [];

  form = {
    title: "",
    slug: "",
    categoryId: ""
  };

  changeSummary = "";
  visibility: Visibility = "PUBLIC";
  preview = false;
  showJson = false;
  saving = false;
  message = "";
  error = "";
  assetUrl = "";
  aiBusy = false;
  aiInstruction = "";
  aiResult: unknown = null;
  templates: ArticleTemplate[] = [];
  templateKey = "";
  metadata: ArticleMetadata = { tags: [], relatedArticleIds: [], pinned: false, featured: false };
  tagsInput = "";
  relatedInput = "";
  publishAt = "";
  unpublishAt = "";
  diff: VersionDiff | null = null;
  comments: BlockComment[] = [];
  selectedBlockId = "";
  commentDraft = "";
  replyToId:string|null = null;
  reviewNote = "";
  governance: ArticleGovernance | null = null;
  quality: ArticleQuality | null = null;
  reviewHistory:ReviewHistory[] = [];
  verificationEvents:VerificationEvent[] = [];
  ownerInput = "";
  reviewIntervalInput = 180;
  reusableBlocks:ReusableBlock[] = [];
  selectedReusableId = "";
  customTemplates:SavedArticleTemplate[] = [];
  selectedCustomTemplateId = "";

  constructor(
    private readonly api: ApiService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    readonly auth: AuthService
  ) {}

  get canEdit(): boolean {
    return this.auth.hasAnyRole(["ADMIN", "EDITOR"]);
  }

  get canEditContent(): boolean {return this.canEdit && (!this.article || this.version?.status === "DRAFT" || this.version?.status === "REJECTED");}

  get canReview(): boolean {return this.auth.hasAnyRole(["ADMIN","REVIEWER"]);}

  get canAnalyze(): boolean {
    return this.auth.hasAnyRole(["ADMIN", "EDITOR", "REVIEWER"]);
  }

  get contentJson(): string {
    return JSON.stringify(this.buildContent(), null, 2);
  }

  ngOnInit(): void {
    this.api.categories().subscribe((items) => { this.categories = items; });
    this.api.articleTemplates().subscribe({ next: (items) => this.templates = items, error: () => undefined });
    this.refreshLibrary();

    const id = this.route.snapshot.paramMap.get("id");

    if (id) {
      this.load(id);
    } else {
      this.blocks = [this.createBlock("paragraph")];
    }
  }

  load(id: string): void {
    this.api.getArticle(id).subscribe((article) => {
      this.article = article;
      this.form = {
        title: article.title,
        slug: article.slug,
        categoryId: article.categoryId ?? ""
      };
      this.visibility = article.visibility;
      this.publishAt = this.toLocalInput(article.scheduledPublishAt);
      this.unpublishAt = this.toLocalInput(article.scheduledUnpublishAt);
      this.api.articleMetadata(id).subscribe({ next: (m) => { this.metadata = m; this.tagsInput = m.tags.join(", "); this.relatedInput = m.relatedArticleIds.join(", "); }, error: () => undefined });
      this.api.articleGovernance(id).subscribe({ next:g=>{this.governance=g;this.ownerInput=g.ownerId??"";this.reviewIntervalInput=g.reviewIntervalDays;},error:()=>undefined });
      this.api.articleQuality(id).subscribe({next:q=>this.quality=q,error:()=>undefined});
      this.api.reviewHistory(id).subscribe({next:h=>this.reviewHistory=h,error:()=>undefined});
      this.api.verificationHistory(id).subscribe({next:items=>this.verificationEvents=items,error:()=>undefined});

      this.api.getVersions(id).subscribe((versions) => {
        this.versions = versions;
        const active = versions.find(item=>item.status === "DRAFT" || item.status === "REJECTED")
          ?? versions.find(item=>item.status === "IN_REVIEW" || item.status === "APPROVED" || item.status === "SCHEDULED")
          ?? versions.find(item=>item.id===article.activeVersionId) ?? versions[0];

        if (active) {
          this.selectVersion(active);
        }
      });
    });
  }

  selectVersion(version: ArticleVersion): void {
    this.version = version;
    this.blocks = this.readBlocks(version.content);
    this.changeSummary = version.changeSummary ?? "";
    this.selectedBlockId = ""; this.commentDraft = ""; this.replyToId = null;
    if(this.article)this.api.blockComments(this.article.id,version.id).subscribe({next:cs=>this.comments=cs,error:()=>this.comments=[]});
  }

  addBlock(type: ContentBlockType): void {
    this.blocks.push(this.createBlock(type));
  }

  removeBlock(index: number): void {
    this.blocks.splice(index, 1);
  }

  moveBlock(index: number, direction: -1 | 1): void {
    const target = index + direction;

    if (target < 0 || target >= this.blocks.length) {
      return;
    }

    const current = this.blocks[index];
    this.blocks[index] = this.blocks[target];
    this.blocks[target] = current;
  }

  addListItem(block: EditorBlock): void {
    block.items ??= [];
    block.items.push("");
  }

  removeListItem(block: EditorBlock, index: number): void {
    block.items?.splice(index, 1);
  }

  normalizeBlock(block: EditorBlock): void {
    if (block.type === "heading") {
      block.level ??= 2;
      block.text ??= "";
      delete block.items;
      delete block.url;
      delete block.alt;
      return;
    }

    if (block.type === "list") {
      block.items ??= [""];
      delete block.level;
      delete block.url;
      delete block.alt;
      return;
    }

    if (block.type === "image") {
      block.url ??= "";
      block.alt ??= "";
      delete block.level;
      delete block.text;
      delete block.items;
      return;
    }

    block.text ??= "";
    delete block.level;
    delete block.items;
    delete block.url;
    delete block.alt;
  }

  save(): void {
    this.message = "";
    this.error = "";

    const content = this.buildContent();

    if (content.blocks.length === 0) {
      this.error = "Add at least one content block.";
      return;
    }

    const hasInvalidHeading = content.blocks.some(
      (block) => block.type === "heading" && (!block.level || block.level < 1 || block.level > 4)
    );

    if (hasInvalidHeading) {
      this.error = "Heading levels must be between 1 and 4.";
      return;
    }

    if(!this.canEditContent){this.error="This revision is not editable. Create a new draft first.";return;}
    this.saving = true;

    if (!this.article) {
      this.api.createArticle({
        title: this.form.title,
        slug: this.form.slug,
        categoryId: this.form.categoryId || null,
        content
      }).subscribe({
        next: (article) => {
          this.message = "Article created.";
          void this.router.navigate(["/articles", article.id]);
        },
        error: (error) => {
          this.error = error?.error?.message || "Article creation failed.";
          this.saving = false;
        },
        complete: () => {
          this.saving = false;
        }
      });

      return;
    }

    if (!this.version) {
      this.error = "No editable version is selected.";
      this.saving = false;
      return;
    }

    this.api.updateDraft(
      this.article.id,
      this.version.id,
      content,
      this.changeSummary
    ).subscribe({
      next: (version) => {
        this.version = version;
        this.message = "Draft saved.";
        this.load(this.article!.id);
      },
      error: (error) => {
        this.error = error?.error?.message || "Draft save failed.";
        this.saving = false;
      },
      complete: () => {
        this.saving = false;
      }
    });
  }

  setVisibility(): void {
    if (!this.article) {
      return;
    }

    this.api.setVisibility(this.article.id, this.visibility).subscribe({
      next: () => {
        this.message = "Visibility updated.";
      },
      error: (error) => {
        this.error = error?.error?.message || "Visibility update failed.";
      }
    });
  }

  newVersion(): void {
    if (!this.article) {
      return;
    }

    this.api.createDraftVersion(this.article.id).subscribe((version) => {
      this.versions = [version, ...this.versions];
      this.selectVersion(version);
      this.message = `Draft version ${version.versionNumber} created.`;
    });
  }

  submit(): void {
    if (!this.article || !this.version) {
      return;
    }

    this.api.submitForReview(this.article.id, this.version.id).subscribe((version) => {
      this.version = version;
      this.message = "Version submitted for review.";
      this.load(this.article!.id);
    });
  }

  archive(): void {
    if (!this.article || !confirm("Archive this article?")) {
      return;
    }

    this.api.archiveArticle(this.article.id).subscribe({
      next: () => {
        this.message = "Article archived.";
        this.load(this.article!.id);
      },
      error: (error) => {
        this.error = error?.error?.message || "Archive failed.";
      }
    });
  }

  improveWithAi(): void {
    this.aiBusy = true; this.error = "";
    this.api.aiImprove(this.buildContent(), this.aiInstruction.trim() || undefined).subscribe({
      next: (result) => {
        this.aiResult = result;
        const content = (result as any)?.content;
        if (content?.blocks && Array.isArray(content.blocks)) this.blocks = this.readBlocks(content);
        this.message = "AI improvement applied. Review it before saving.";
        this.aiBusy = false;
      },
      error: (e) => { this.aiBusy = false; this.error = e?.error?.message || "AI improvement failed."; }
    });
  }

  analyzeWithAi(): void {
    if (!this.article) return;
    this.aiBusy = true; this.error = "";
    this.api.analyzeArticleIntelligence(this.article.id).subscribe({
      next: (result) => { this.aiResult = result; this.aiBusy = false; },
      error: (e) => { this.aiBusy = false; this.error = e?.error?.message || "Article analysis failed."; }
    });
  }

  detectDependencies(): void {
    if (!this.article) return;
    this.aiBusy = true; this.error = "";
    this.api.detectArticleDependencies(this.article.id).subscribe({
      next: (result) => { this.aiResult = result; this.aiBusy = false; },
      error: (e) => { this.aiBusy = false; this.error = e?.error?.message || "Dependency detection failed."; }
    });
  }

  uploadAsset(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];

    if (!file || !this.article) {
      return;
    }

    this.api.uploadAsset(file, this.article.id).subscribe({
      next: (asset) => {
        this.assetUrl = asset.downloadUrl;
        this.message = "Asset uploaded.";
      },
      error: (error) => {
        this.error = error?.error?.message || "Asset upload failed.";
      }
    });
  }

  private newBlockId(): string { return `block-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`; }

  applyTemplate(): void {
    const template = this.templates.find(t => t.key === this.templateKey);
    if (!template) return;
    this.blocks = template.blocks.map(b => ({ id: this.newBlockId(), type: b.type as ContentBlockType, level: b.level ?? undefined, text: b.text ?? undefined, items: b.items ?? undefined }));
    const firstHeading = this.blocks.find(b => b.type === "heading");
    if (firstHeading?.text && !this.form.title) this.form.title = firstHeading.text.replace(/^.*?:\s*/, "").trim() || firstHeading.text;
  }

  saveMetadata(): void {
    if (!this.article) return;
    this.api.updateArticleMetadata(this.article.id, {
      tags: this.tagsInput.split(",").map(x => x.trim()).filter(Boolean),
      relatedArticleIds: this.relatedInput.split(",").map(x => x.trim()).filter(Boolean),
      pinned: this.metadata.pinned, featured: this.metadata.featured
    }).subscribe({ next: (m) => { this.metadata = m; this.message = "Discovery settings saved."; }, error: e => this.error = e?.error?.message || "Metadata update failed." });
  }

  compareVersion(item: ArticleVersion): void {
    if (!this.article || !this.version || item.id === this.version.id) return;
    this.api.versionDiff(this.article.id, item.id, this.version.id).subscribe({ next: d => this.diff = d, error: e => this.error = e?.error?.message || "Version comparison failed." });
  }

  rollback(item: ArticleVersion): void {
    if (!this.article || !confirm(`Create a new draft from version ${item.versionNumber}?`)) return;
    this.api.rollbackArticle(this.article.id, item.id).subscribe({ next: v => { this.versions = [v, ...this.versions]; this.selectVersion(v); this.message = `Rollback draft v${v.versionNumber} created.`; }, error: e => this.error = e?.error?.message || "Rollback failed." });
  }

  prepareForSchedule(): void {
    if (!this.article || !this.version || this.version.status !== "DRAFT") return;
    this.api.submitForReview(this.article.id, this.version.id).subscribe({
      next: version => {
        this.version = version;
        this.message = "Version submitted for review. Choose a publish time now.";
        this.load(this.article!.id);
      },
      error: e => { this.error = e?.error?.message || "Could not submit version for review."; }
    });
  }

  selectReviewBlock(id:string):void {this.selectedBlockId=id;this.replyToId=null;}
  commentsForBlock(id:string):BlockComment[] {return this.comments.filter(c=>c.blockId===id);}
  addReviewComment():void {
    if(!this.article||!this.version||!this.selectedBlockId||!this.commentDraft.trim())return;
    this.api.addThreadReply(this.article.id,this.version.id,this.selectedBlockId,this.commentDraft.trim(),this.replyToId).subscribe({
      next:c=>{this.comments=[...this.comments,c];this.commentDraft="";this.replyToId=null;this.message="Comment posted.";},
      error:e=>this.error=e?.error?.message||"Unable to post comment."});
  }
  resolveReviewComment(c:BlockComment):void {
    if(!this.article)return;
    this.api.resolveBlockComment(this.article.id,c.id).subscribe({next:()=>{c.resolved=true;this.message="Comment resolved.";},error:e=>this.error=e?.error?.message||"Unable to resolve comment."});
  }
  decideReview(approve:boolean):void {
    if(!this.article||!this.version)return;
    if(approve && !confirm("Approve and publish this version now?"))return;
    this.api.reviewVersion(this.article.id,this.version.id,approve,this.reviewNote).subscribe({next:()=>{this.message=approve?"Published after approval.":"Revision rejected.";this.load(this.article!.id);},error:e=>this.error=e?.error?.message||"Review failed."});
  }
  approveSchedule():void {
    if(!this.article||!this.version)return;
    this.api.approveVersionForSchedule(this.article.id,this.version.id,this.reviewNote).subscribe({next:()=>{this.message="Approved. An editor may now schedule publication.";this.load(this.article!.id);},error:e=>this.error=e?.error?.message||"Approval failed."});
  }
  saveGovernance():void {
    if(!this.article)return;
    this.api.updateGovernance(this.article.id,{ownerId:this.ownerInput.trim()||undefined,reviewIntervalDays:this.reviewIntervalInput}).subscribe({next:g=>{this.governance=g;this.message="Review schedule saved.";},error:e=>this.error=e?.error?.message||"Governance update failed."});
  }
  checkQuality():void {if(!this.article)return;this.api.articleQuality(this.article.id).subscribe({next:q=>this.quality=q,error:e=>this.error=e?.error?.message||"Quality check failed."});}
  verifyContent():void {if(!this.article)return;this.api.verifyArticle(this.article.id).subscribe({next:g=>{this.governance=g;this.message="Content verified.";this.checkQuality();},error:e=>this.error=e?.error?.message||"Verification failed."});}
  refreshLibrary():void {
    this.api.reusableBlocks().subscribe({next:b=>this.reusableBlocks=b,error:()=>undefined});
    this.api.customArticleTemplates().subscribe({next:t=>this.customTemplates=t,error:()=>undefined});
  }
  insertReusableBlock():void {
    const item=this.reusableBlocks.find(b=>b.id===this.selectedReusableId);if(!item)return;
    const b=this.toEditorBlock(item.block,this.blocks.length);if(b){b.id=this.newBlockId();this.blocks.push(b);}
  }
  saveReusableBlock():void {
    const target=this.blocks.find(b=>b.id===this.selectedBlockId)??this.blocks[0];if(!target)return;
    const name=prompt("Reusable block name",target.type);if(!name?.trim())return;
    this.api.createReusableBlock(name.trim(),{...target}).subscribe({next:()=>{this.message="Reusable block saved.";this.refreshLibrary();},error:e=>this.error=e?.error?.message||"Cannot save reusable block."});
  }
  insertCustomTemplate():void {
    const template=this.customTemplates.find(t=>t.id===this.selectedCustomTemplateId);if(!template)return;
    if(this.blocks.length && !confirm("Replace the current editor blocks with this template?"))return;
    this.blocks=(template.content.blocks||[]).map((b,i)=>this.toEditorBlock(b,i)).filter((b):b is EditorBlock=>b!==null).map(b=>({...b,id:this.newBlockId()}));
  }
  saveCustomTemplate():void {
    const name=prompt("Template name",this.form.title||"Article template");if(!name?.trim())return;
    const key=prompt("Unique template key",name.toLowerCase().replace(/[^a-z0-9]+/g,"-").replace(/(^-|-$)/g,""));if(!key?.trim())return;
    this.api.createCustomArticleTemplate(key.trim(),name.trim(),this.buildContent()).subscribe({next:()=>{this.message="Template saved.";this.refreshLibrary();},error:e=>this.error=e?.error?.message||"Cannot save template."});
  }

  schedulePublish(): void {
    if (!this.article || !this.version || !this.publishAt) return;
    this.api.schedulePublish(this.article.id, this.version.id, new Date(this.publishAt).toISOString()).subscribe({ next: v => { this.version = v; this.message = "Publish scheduled."; this.load(this.article!.id); }, error: e => this.error = e?.error?.message || "Scheduling failed." });
  }
  scheduleUnpublish(): void {
    if (!this.article || !this.unpublishAt) return;
    this.api.scheduleUnpublish(this.article.id, new Date(this.unpublishAt).toISOString()).subscribe({ next: a => { this.article = a; this.message = "Unpublish scheduled."; }, error: e => this.error = e?.error?.message || "Scheduling failed." });
  }
  clearSchedule(): void { if (!this.article) return; this.api.clearArticleSchedule(this.article.id).subscribe({ next: a => { this.article = a; this.message = "Schedule cleared."; } }); }
  private toLocalInput(value?: string | null): string { if (!value) return ""; const d = new Date(value); const pad=(n:number)=>String(n).padStart(2,"0"); return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`; }

  private createBlock(type: ContentBlockType): EditorBlock {
    if (type === "heading") {
      return { id: this.newBlockId(), type, level: 2, text: "" };
    }

    if (type === "list") {
      return { id: this.newBlockId(), type, items: [""] };
    }

    if (type === "image") {
      return { id: this.newBlockId(), type, url: "", alt: "" };
    }

    return { id: this.newBlockId(), type, text: "" };
  }

  private readBlocks(content: unknown): EditorBlock[] {
    if (!content || typeof content !== "object" || Array.isArray(content)) {
      return [];
    }

    const blocks = (content as { blocks?: unknown }).blocks;

    if (!Array.isArray(blocks)) {
      return [];
    }

    return blocks
      .filter((block): block is Record<string, unknown> => !!block && typeof block === "object" && !Array.isArray(block))
      .map((block, index) => this.toEditorBlock(block, index))
      .filter((block): block is EditorBlock => block !== null);
  }

  private toEditorBlock(block: Record<string, unknown>, index = 0): EditorBlock | null {
    const type = block["type"];

    if (type !== "heading" && type !== "paragraph" && type !== "image" && type !== "list" && type !== "code") {
      return null;
    }

    const editorBlock: EditorBlock = { type, id: typeof block["id"] === "string" && block["id"].trim() ? block["id"] : `block-index-${index}` };

    if (type === "heading") {
      editorBlock.level = typeof block["level"] === "number" ? block["level"] : 2;
      editorBlock.text = typeof block["text"] === "string" ? block["text"] : "";
    } else if (type === "list") {
      editorBlock.items = Array.isArray(block["items"])
        ? block["items"].filter((item): item is string => typeof item === "string")
        : [typeof block["text"] === "string" ? block["text"] : ""];
    } else if (type === "image") {
      editorBlock.url = typeof block["url"] === "string"
        ? block["url"]
        : typeof block["src"] === "string"
          ? block["src"]
          : "";
      editorBlock.alt = typeof block["alt"] === "string" ? block["alt"] : "";
    } else {
      editorBlock.text = typeof block["text"] === "string" ? block["text"] : "";
    }

    return editorBlock;
  }

  private buildContent(): { blocks: EditorBlock[] } {
    return {
      blocks: this.blocks.map((block) => {
        if (block.type === "heading") {
          return {
            id: block.id ?? `block-${this.newBlockId()}`,
            type: "heading",
            level: block.level ?? 2,
            text: block.text ?? ""
          };
        }

        if (block.type === "list") {
          return {
            id: block.id ?? `block-${this.newBlockId()}`,
            type: "list",
            items: block.items ?? []
          };
        }

        if (block.type === "image") {
          return {
            id: block.id ?? `block-${this.newBlockId()}`,
            type: "image",
            url: block.url ?? "",
            alt: block.alt ?? ""
          };
        }

        return {
          id: block.id ?? `block-${this.newBlockId()}`,
          type: block.type,
          text: block.text ?? ""
        };
      })
    };
  }
}
