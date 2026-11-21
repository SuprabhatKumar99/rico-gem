import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { ApiService } from "../core/api.service";
import { AuthService } from "../core/auth.service";
import { Article, ArticleVersion, BlockComment, ReviewHistory } from "../models";

interface ReviewBlock { id?:string; type:string; text?:string; level?:number; items?:string[]; url?:string; alt?:string; }

/** Reviewer-facing, read-only article viewer with version-bound block annotations. */
@Component({
  selector:"rk-article-review",standalone:true,
  imports:[CommonModule,FormsModule,RouterLink],
  template:`
    <div class="mx-auto max-w-6xl space-y-6">
      <a routerLink="/articles" class="text-sm font-semibold text-slate-600">← Articles</a>
      <div class="flex flex-wrap justify-between gap-3">
        <div><p class="text-xs font-semibold uppercase text-slate-400">Review workspace</p>
          <h1 class="text-3xl font-bold">{{ article?.title || 'Review article' }}</h1>
          <p class="mt-1 text-sm text-slate-500">Internal comments are visible only to authorized staff.</p></div>
        <a *ngIf="article" class="rk-btn rk-btn-secondary" [routerLink]="['/articles',article.id]">Open editor & history</a>
      </div>
      <div *ngIf="error" role="alert" class="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{{ error }}</div>
      <div *ngIf="notice" role="status" class="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-700">{{ notice }}</div>
      <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_310px]">
        <section class="rk-card p-5 space-y-5">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <h2 class="font-bold">Read-only version</h2>
            <select class="rk-select w-auto" aria-label="Article version" [(ngModel)]="selectedVersionId" (ngModelChange)="chooseVersion($event)">
              <option *ngFor="let v of versions" [value]="v.id">Version {{ v.versionNumber }} · {{ v.status }}</option>
            </select>
          </div>
          <div *ngFor="let block of blocks;let index=index" class="rounded-xl border p-4" [class.border-indigo-500]="selectedBlockId===blockId(block,index)">
            <div class="mb-3 flex items-center justify-between gap-3"><span class="text-xs font-bold uppercase tracking-wide text-slate-500">Block {{ index+1 }}</span>
              <button class="text-xs font-semibold text-indigo-700" (click)="selectBlock(blockId(block,index))">Comment · {{ commentsFor(blockId(block,index)).length }}</button></div>
            <h3 *ngIf="block.type==='heading'" class="text-xl font-bold">{{ block.text }}</h3>
            <p *ngIf="block.type==='paragraph'" class="whitespace-pre-wrap text-sm leading-7">{{ block.text }}</p>
            <pre *ngIf="block.type==='code'" class="overflow-auto rounded-lg bg-slate-900 p-4 text-sm text-white">{{ block.text }}</pre>
            <ul *ngIf="block.type==='list'" class="list-disc pl-6"><li *ngFor="let item of block.items">{{ item }}</li></ul>
            <img *ngIf="block.type==='image' && block.url" [src]="block.url" [alt]="block.alt||''" class="max-h-64 rounded-lg object-contain" />
            <div *ngIf="selectedBlockId===blockId(block,index)" class="mt-4 border-t pt-3">
              <div *ngFor="let c of commentsFor(selectedBlockId)" class="mb-2 rounded-lg bg-slate-50 p-3 text-xs" [class.ml-4]="!!c.parentId">
                <p class="font-semibold">{{ c.authorId.slice(0,8) }} · {{ c.createdAt | date:'short' }}</p>
                <p class="mt-1 whitespace-pre-wrap">{{ c.comment }}</p>
                <div class="mt-2 flex gap-3"><button class="text-indigo-700" (click)="replyToId=c.id">Reply</button>
                  <button *ngIf="!c.resolved" class="text-amber-700" (click)="resolve(c)">Resolve</button>
                  <span *ngIf="c.resolved" class="text-emerald-700">Resolved</span></div>
              </div>
              <p *ngIf="replyToId" class="text-xs text-indigo-700">Reply to {{ replyToId.slice(0,8) }} <button (click)="replyToId=null">Cancel</button></p>
              <textarea class="rk-textarea mt-2" [(ngModel)]="comment" rows="2" placeholder="Reviewer comment, question or correction"></textarea>
              <button class="rk-btn rk-btn-secondary mt-2" [disabled]="!comment.trim()" (click)="addComment()">Post comment</button>
            </div>
          </div>
        </section>
        <aside class="space-y-5">
          <section class="rk-card p-5" *ngIf="version?.status==='IN_REVIEW' && canReview">
            <h2 class="font-bold">Decision</h2>
            <p class="mt-2 text-xs text-slate-600">Resolve comments before approving. Approval for scheduling does not immediately publish.</p>
            <textarea [(ngModel)]="decisionComment" class="rk-textarea mt-3" rows="3" placeholder="Reviewer rationale"></textarea>
            <div class="mt-3 flex flex-col gap-2">
              <button class="rk-btn rk-btn-primary" (click)="decide(true)">Approve & publish</button>
              <button class="rk-btn rk-btn-secondary" (click)="approveForSchedule()">Approve for scheduled publish</button>
              <button class="rk-btn rk-btn-secondary" (click)="decide(false)">Reject for revision</button>
            </div>
          </section>
          <section class="rk-card p-5"><h2 class="font-bold">Review history</h2>
            <p *ngIf="!history.length" class="mt-2 text-xs text-slate-500">No recorded decisions yet.</p>
            <div *ngFor="let h of history" class="mt-3 border-t pt-3 text-xs text-slate-600">
              <p class="font-bold">{{ h.decision }}</p><p>{{ h.createdAt | date:'medium' }}</p><p>{{ h.comment }}</p>
            </div>
          </section>
        </aside>
      </div>
    </div>
  `
})
export class ArticleReviewComponent implements OnInit {
  article?:Article;versions:ArticleVersion[]=[];version?:ArticleVersion;selectedVersionId="";
  blocks:ReviewBlock[]=[];comments:BlockComment[]=[];selectedBlockId="";
  comment="";replyToId:string|null=null;decisionComment="";history:ReviewHistory[]=[];
  error="";notice="";
  constructor(private readonly route:ActivatedRoute,private readonly api:ApiService,private readonly auth:AuthService){}
  get canReview(){return this.auth.hasAnyRole(["ADMIN","REVIEWER"]);}
  ngOnInit(){const id=this.route.snapshot.paramMap.get("id");if(!id)return;
    this.api.getArticle(id).subscribe({next:a=>{this.article=a;this.reload();},error:e=>this.error=e?.error?.message||"Article unavailable."});}
  reload(){if(!this.article)return;const id=this.article.id;
    this.api.getVersions(id).subscribe({next:versions=>{this.versions=versions;
      const picked=versions.find(v=>v.id===this.selectedVersionId)||versions.find(v=>v.id===this.article!.activeVersionId)||versions[0];
      if(picked)this.chooseVersion(picked.id);},error:e=>this.error=e?.error?.message||"Versions unavailable."});
    this.api.reviewHistory(id).subscribe({next:items=>this.history=items,error:()=>undefined});}
  chooseVersion(id:string){if(!this.article)return;
    this.selectedVersionId=id;this.version=this.versions.find(v=>v.id===id);
    const data=this.version?.content as {blocks?:ReviewBlock[]}|null;
    this.blocks=Array.isArray(data?.blocks)?data.blocks:[];this.selectedBlockId="";this.replyToId=null;
    this.api.blockComments(this.article.id,id).subscribe({next:items=>this.comments=items,error:()=>this.comments=[]});}
  blockId(block:ReviewBlock,index:number):string{return block.id||`block-index-${index}`;}
  selectBlock(blockId:string){this.selectedBlockId=blockId;this.replyToId=null;}
  commentsFor(id:string):BlockComment[]{return this.comments.filter(c=>c.blockId===id);}
  addComment(){if(!this.article||!this.version||!this.selectedBlockId||!this.comment.trim())return;
    this.api.addThreadReply(this.article.id,this.version.id,this.selectedBlockId,this.comment.trim(),this.replyToId).subscribe({next:item=>{this.comments=[...this.comments,item];this.comment="";this.replyToId=null;this.notice="Comment posted.";},error:e=>this.error=e?.error?.message||"Comment failed."});}
  resolve(item:BlockComment){if(!this.article)return;this.api.resolveBlockComment(this.article.id,item.id).subscribe({next:()=>{item.resolved=true;this.notice="Comment resolved.";},error:e=>this.error=e?.error?.message||"Unable to resolve."});}
  decide(approve:boolean){if(!this.article||!this.version)return;
    if(approve&&!confirm("Approve and publish this version now?"))return;
    this.api.reviewVersion(this.article.id,this.version.id,approve,this.decisionComment).subscribe({next:()=>{this.notice=approve?"Published.":"Rejected for revision.";this.reload();},error:e=>this.error=e?.error?.message||"Decision failed."});}
  approveForSchedule(){if(!this.article||!this.version)return;
    this.api.approveVersionForSchedule(this.article.id,this.version.id,this.decisionComment).subscribe({next:()=>{this.notice="Approved for scheduling.";this.reload();},error:e=>this.error=e?.error?.message||"Approval failed."});}
}
