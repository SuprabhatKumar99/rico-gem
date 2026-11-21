import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { BlockComment, SavedArticleTemplate, ReusableBlock } from '../models';
import { Editor, Extension, Node } from '@tiptap/core';
import { StarterKit } from '@tiptap/starter-kit';
import { Highlight } from '@tiptap/extension-highlight';
import { Image } from '@tiptap/extension-image';
import { TableKit } from '@tiptap/extension-table';
import { Placeholder } from '@tiptap/extension-placeholder';
import { Collaboration } from '@tiptap/extension-collaboration';
import { CollaborationCaret } from '@tiptap/extension-collaboration-caret';
import { HocuspocusProvider } from '@hocuspocus/provider';
import { Plugin } from '@tiptap/pm/state';
import { IndexeddbPersistence } from 'y-indexeddb';
import * as Y from 'yjs';

const ids = Extension.create({
  name:'blockIdentity',
  addGlobalAttributes(){return [{types:['heading','paragraph','codeBlock','bulletList','orderedList','image','blockquote','table'],attributes:{
    blockId:{default:null,parseHTML:element=>element.getAttribute('data-block-id'),renderHTML:attrs=>attrs['blockId']?{'data-block-id':attrs['blockId']}:{}}
  }}]}
  ,addProseMirrorPlugins(){return [new Plugin({appendTransaction:(transactions,_oldState,newState)=>{
    if(!transactions.some(t=>t.docChanged))return null;
    const tr=newState.tr;let updated=false;
    newState.doc.descendants((node,pos)=>{
      if(['paragraph','heading','codeBlock','image','bulletList','orderedList','blockquote','table'].includes(node.type.name)&&!node.attrs['blockId']){
        const id=typeof crypto!=='undefined' && 'randomUUID' in crypto?crypto.randomUUID():`block-${Date.now()}-${Math.random().toString(36).slice(2)}`;
        tr.setNodeMarkup(pos,undefined,{...node.attrs,blockId:id});updated=true;
      }
    });
    return updated?tr:null;
  }})]}
});
const legacy = Node.create({
  name:'legacyBlock',group:'block',atom:true,
  addAttributes(){return {raw:{default:'{}'},blockId:{default:null}}},
  parseHTML(){return [{tag:'div[data-legacy-block]'}]},
  renderHTML(){return ['div',{'data-legacy-block':'',class:'legacy-block'},'Imported legacy content (preserved)']}
});

@Component({
  standalone:true,
  selector:'rk-collaboration',
  imports:[CommonModule,FormsModule,RouterLink],
  template:`
  <main class="space-y-5">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <div><a routerLink="/articles" class="text-blue-700 underline">← Articles</a>
        <h1 class="text-3xl font-bold mt-2">Rich text · live collaboration</h1>
        <p class="text-slate-600 text-sm">TipTap · Yjs CRDT · draft autosave and shared cursors</p>
      </div>
      <div class="flex gap-2">
        <a [routerLink]="['/articles',articleId,'review']" class="rk-btn">Review workspace</a>
        <a [routerLink]="['/articles',articleId]" class="rk-btn">Versions / governance</a>
      </div>
    </header>
    <div *ngIf="error" class="rounded-lg bg-rose-50 text-rose-800 p-3" role="alert">{{error}} <button class="underline" (click)="reconnect()">Retry</button></div>
    <div class="rounded-lg border p-3 flex gap-3 justify-between flex-wrap" role="status">
      <span><strong>Status:</strong> {{status}} · {{syncState}}</span>
      <span><strong>Collaborators:</strong> {{participants.join(', ') || 'connecting…'}}</span>
    </div>
    <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
      <section class="rk-card p-4 min-w-0">
        <div class="flex flex-wrap gap-1 mb-3 border-b pb-3" role="toolbar" aria-label="Rich-text formatting">
          <button class="rk-btn" (click)="action('bold')" title="Ctrl+B">Bold</button>
          <button class="rk-btn" (click)="action('italic')" title="Ctrl+I">Italic</button>
          <button class="rk-btn" (click)="action('underline')" title="Ctrl+U">Underline</button>
          <button class="rk-btn" (click)="action('highlight')">Highlight</button>
          <button class="rk-btn" (click)="action('heading')">Heading</button>
          <button class="rk-btn" (click)="action('bullet')">Bullets</button>
          <button class="rk-btn" (click)="action('ordered')">Numbered</button>
          <button class="rk-btn" (click)="action('quote')">Quote</button>
          <button class="rk-btn" (click)="action('callout')">Callout</button>
          <button class="rk-btn" (click)="action('code')">Code</button>
          <button class="rk-btn" (click)="action('table')">Table</button>
          <button class="rk-btn" (click)="loadLibrary()">Content library</button>
          <button class="rk-btn" (click)="addLink()">Link</button>
          <button class="rk-btn" (click)="addImage()">Image URL</button>
          <label class="rk-btn cursor-pointer">Attach file <input type="file" class="hidden" (change)="upload($event)" /></label>
          <button class="rk-btn" (click)="action('undo')">Undo</button>
          <button class="rk-btn" (click)="action('redo')">Redo</button>
        </div>
        <div *ngIf="libraryOpen" class="flex flex-wrap gap-2 p-2 border rounded mb-3">
          <button *ngFor="let item of templates" class="rk-btn" (click)="insertTemplate(item)">Template: {{item.name}}</button>
          <button *ngFor="let item of snippets" class="rk-btn" (click)="insertSnippet(item)">Block: {{item.name}}</button>
          <p *ngIf="!templates.length && !snippets.length" class="text-sm text-slate-500">No saved templates or blocks.</p>
        </div>
        <p class="text-xs text-slate-500 mb-3">Tip: Type <code>/</code> at the start of a paragraph for block commands. Markdown headings and lists are supported on paste.</p>
        <div *ngIf="slashOpen" class="flex flex-wrap gap-2 mb-2 p-2 rounded border bg-slate-50">
          <button class="rk-btn" (click)="slash('heading')">Heading</button><button class="rk-btn" (click)="slash('bullet')">List</button>
          <button class="rk-btn" (click)="slash('code')">Code block</button><button class="rk-btn" (click)="slash('table')">Table</button>
        </div>
        <div #editorElement class="ricoz-rich-editor min-h-[450px] rounded-lg border p-5" aria-label="Collaborative rich text editor"></div>
        <p class="text-xs text-slate-500 mt-4">Edits merge automatically. Changes are persisted after a short debounce. A server-confirmed save is required before submitting a draft for review.</p>
      </section>
      <aside class="rk-card p-4 space-y-4">
        <h2 class="font-semibold">Review comments</h2>
        <p class="text-sm text-slate-500">Select a paragraph or heading to show its anchored discussion. Comments are visible only to staff.</p>
        <p class="text-xs">Selected block: <code>{{selectedBlockId || 'None'}}</code></p>
        <div class="space-y-3 max-h-[400px] overflow-y-auto">
          <div *ngFor="let comment of selectedComments" class="rounded-lg bg-slate-50 p-3 text-sm">
            <p>{{comment.comment}}</p>
            <p class="text-xs text-slate-500 mt-1">{{comment.createdAt | date:'short'}} · {{comment.resolved ? 'Resolved':'Open'}}</p>
            <button *ngIf="!comment.resolved" (click)="resolve(comment.id)" class="text-blue-700 underline text-xs mt-1">Resolve</button>
          </div>
          <p *ngIf="!selectedComments.length" class="text-sm text-slate-500">No comments on this block.</p>
        </div>
        <textarea [(ngModel)]="commentText" class="rk-input w-full" rows="3" maxlength="2000" placeholder="Add a reviewer note"></textarea>
        <button class="rk-btn rk-btn-primary" [disabled]="!selectedBlockId || !commentText.trim()" (click)="comment()">Post comment</button>
        <hr />
        <button class="rk-btn" (click)="exportJson()">Export TipTap JSON backup</button>
        <label class="flex gap-2 text-xs items-center"><input type="checkbox" [checked]="offlineEnabled" (change)="offlineToggle($event)" /> Enable local offline drafts on this device</label>
        <p class="text-xs text-amber-700" *ngIf="offlineEnabled">Offline cache stores article text unencrypted in this browser. Do not enable on shared computers.</p>
        <p class="text-xs text-slate-500">Do not close a tab until the status is Connected and synced.</p>
      </aside>
    </div>
  </main>`
})
export class CollaborationComponent implements OnInit,OnDestroy {
  private readonly api=inject(ApiService);
  private readonly auth=inject(AuthService);
  private readonly route=inject(ActivatedRoute);
  private readonly subscriptions=new Subscription();
  @ViewChild('editorElement',{static:true}) editorElement!:ElementRef<HTMLElement>;
  readonly articleId=this.route.snapshot.paramMap.get('articleId')||'';
  readonly versionId=this.route.snapshot.paramMap.get('versionId')||'';
  editor:Editor|null=null;provider:HocuspocusProvider|null=null;doc:Y.Doc|null=null;private roomName='';
  offline:IndexeddbPersistence|null=null;offlineEnabled=localStorage.getItem('rk_collab_offline_cache')==='true';slashOpen=false;
  status='Connecting';syncState='Waiting for document';error='';participants:string[]=[];
  selectedBlockId='';comments:BlockComment[]=[];commentText='';
  libraryOpen=false;templates:SavedArticleTemplate[]=[];snippets:ReusableBlock[]=[];
  get selectedComments(){return this.comments.filter(x=>x.blockId===this.selectedBlockId);}
  ngOnInit(){this.connect();this.fetchComments();}
  reconnect(){this.destroyEditor();this.connect();}
  private connect(){
    this.error='';this.status='Authorizing';
    this.subscriptions.add(this.api.collaborationTicket(this.articleId,this.versionId).subscribe({
      next: ticket=>{
        this.roomName=ticket.room;
        this.doc=new Y.Doc();
        if(this.offlineEnabled)this.offline=new IndexeddbPersistence(ticket.room,this.doc);
        const name=this.auth.user?.email||'Editor';
        const identity={name,color:'#2563eb'};
        this.provider=new HocuspocusProvider({
          url:ticket.websocketUrl,name:ticket.room,document:this.doc,token:ticket.token,
          onStatus:({status})=>this.status=status==='connected'?'Connected':'Reconnecting',
          onSynced:()=>{this.syncState='Synced with server';this.populateSelection();},
          onAuthenticationFailed:()=>{this.error='Authentication expired or access revoked. Reopen the editor.';this.status='Denied';}
        });
        this.provider.on('awarenessUpdate',()=>{
          const awareness=this.provider?.awareness;
          const states=awareness?Array.from(awareness.getStates().values()):[];
          this.participants=[...new Set(states.map(s=>String(s['user']?.name||'')).filter(Boolean))];
        });
        this.editor=new Editor({
          element:this.editorElement.nativeElement,
          extensions:[StarterKit.configure({undoRedo:false}),Highlight,Image.configure({allowBase64:false}),
            TableKit,ids,legacy,
            Placeholder.configure({placeholder:'Start writing…'}),
            Collaboration.configure({document:this.doc}),
            CollaborationCaret.configure({provider:this.provider,user:identity})],
          editorProps:{attributes:{class:'tiptap prose prose-slate max-w-none min-h-[380px] focus:outline-none'},
            handlePaste:(_view,event)=>this.handleMarkdownPaste(event)},
          onUpdate:()=>{this.syncState='Changes pending autosave';this.populateSelection();this.updateSlash();},
          onSelectionUpdate:()=>this.populateSelection()
        });
      },error:e=>{this.status='Unavailable';this.error=e.status===409?'This version is no longer an editable draft.':'Unable to authorize editing. Check your role and collaboration service.';}
    }));
  }
  private updateSlash(){
    if(!this.editor)return;
    const {$from}=this.editor.state.selection;
    this.slashOpen=$from.parent.textContent.substring(0,$from.parentOffset).trim()==='/';
  }
  slash(command:string){
    if(!this.editor)return;
    const {$from}=this.editor.state.selection;
    const from=$from.pos-1;
    this.editor.chain().focus().deleteRange({from:Math.max(0,from),to:$from.pos}).run();
    this.slashOpen=false;this.action(command);
  }
  offlineToggle(e:Event){
    const checked=(e.target as HTMLInputElement).checked;
    this.offlineEnabled=checked;
    localStorage.setItem('rk_collab_offline_cache',checked?'true':'false');
    if(checked && this.doc && this.roomName){this.offline?.destroy();this.offline=new IndexeddbPersistence(this.roomName,this.doc);}
    if(!checked && this.offline){void this.offline.clearData();this.offline.destroy();this.offline=null;}
  }
  private populateSelection(){
    if(!this.editor)return;
    const {$from}=this.editor.state.selection;
    for(let depth=$from.depth;depth>=0;depth--){
      const id=$from.node(depth).attrs['blockId'];
      if(typeof id==='string' && id){this.selectedBlockId=id;return;}
    }
    this.selectedBlockId='';
  }
  action(action:string){
    if(!this.editor)return;
    const c=this.editor.chain().focus();
    switch(action){
      case 'bold':c.toggleBold().run();break;
      case 'italic':c.toggleItalic().run();break;
      case 'underline':c.toggleUnderline().run();break;
      case 'highlight':c.toggleHighlight().run();break;
      case 'heading':c.toggleHeading({level:2}).run();break;
      case 'bullet':c.toggleBulletList().run();break;
      case 'ordered':c.toggleOrderedList().run();break;
      case 'quote':c.toggleBlockquote().run();break;
      case 'callout':c.insertContent({type:'blockquote',content:[{type:'paragraph',content:[{type:'text',text:'Note: '}]}]}).run();break;
      case 'code':c.toggleCodeBlock().run();break;
      case 'table':c.insertTable({rows:3,cols:3,withHeaderRow:true}).run();break;
      case 'undo':c.undo().run();break;
      case 'redo':c.redo().run();break;
    }
  }
  loadLibrary(){
    this.libraryOpen=!this.libraryOpen;
    if(this.libraryOpen){
      this.api.customArticleTemplates().subscribe({next:v=>this.templates=v,error:()=>this.error='Unable to load templates.'});
      this.api.reusableBlocks().subscribe({next:v=>this.snippets=v,error:()=>this.error='Unable to load saved blocks.'});
    }
  }
  private contentNodes(blocks:any[]):any[]{
    const text=(value:any)=>value?[{type:'text',text:String(value)}]:[];
    return blocks.map(b=>{
      if(b.type==='heading')return {type:'heading',attrs:{level:Math.max(1,Math.min(4,b.level||2))},content:text(b.text)};
      if(b.type==='code')return {type:'codeBlock',content:text(b.text)};
      if(b.type==='list')return {type:'bulletList',content:(b.items||[]).map((item:string)=>({type:'listItem',content:[{type:'paragraph',content:text(item)}]}))};
      if(b.type==='image' && /^https:\/\//i.test(b.url||''))return {type:'image',attrs:{src:b.url,alt:b.alt||''}};
      return {type:'paragraph',content:text(b.text||'')};
    });
  }
  insertTemplate(item:SavedArticleTemplate){
    const content:any=item.content;
    if(Array.isArray(content?.blocks))this.editor?.chain().focus().insertContent(this.contentNodes(content.blocks)).run();
    this.libraryOpen=false;
  }
  insertSnippet(item:ReusableBlock){this.editor?.chain().focus().insertContent(this.contentNodes([item.block])).run();this.libraryOpen=false;}
  addLink(){
    const url=prompt('HTTPS link URL');
    if(!url||!/^https:\/\//i.test(url))return;
    this.editor?.chain().focus().setLink({href:url}).run();
  }
  addImage(){
    const src=prompt('HTTPS image URL');
    if(src && /^https:\/\//i.test(src))this.editor?.chain().focus().setImage({src}).run();
  }
  upload(event:Event){
    const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;
    this.api.uploadAsset(file,this.articleId).subscribe({next:asset=>{
      if(file.type.startsWith('image/') && asset.downloadUrl && /^https:\/\//.test(asset.downloadUrl))
        this.editor?.chain().focus().setImage({src:asset.downloadUrl,alt:file.name}).run();
      else if(asset.downloadUrl && /^https:\/\//.test(asset.downloadUrl))
        this.editor?.chain().focus().insertContent({type:'text',text:file.name,marks:[{type:'link',attrs:{href:asset.downloadUrl}}]}).run();
      this.error='';
    },error:()=>this.error='Upload failed. Verify media storage and file permissions.'});
  }
  /** Text-only Markdown paste: escaped by ProseMirror; no executable HTML injection. */
  private handleMarkdownPaste(event:ClipboardEvent):boolean {
    const plain=event.clipboardData?.getData('text/plain')||'';
    if(!/^(#{1,4} |[-*] |\d+\. )/m.test(plain))return false;
    const nodes:any[]=[];
    for(const line of plain.split(/\r?\n/)){
      if(!line.trim())continue;
      const h=line.match(/^(#{1,4})\s+(.*)$/);
      if(h)nodes.push({type:'heading',attrs:{level:h[1].length},content:[{type:'text',text:h[2]}]});
      else nodes.push({type:'paragraph',content:[{type:'text',text:line.replace(/^([-*]|\d+\.)\s+/,'')}]});
    }
    if(nodes.length){this.editor?.chain().focus().insertContent(nodes).run();event.preventDefault();return true;}
    return false;
  }
  private fetchComments(){this.api.blockComments(this.articleId,this.versionId).subscribe({next:c=>this.comments=c,error:()=>{}});}
  comment(){if(!this.selectedBlockId||!this.commentText.trim())return;
    this.api.addBlockComment(this.articleId,this.versionId,this.selectedBlockId,this.commentText.trim())
      .subscribe({next:()=>{this.commentText='';this.fetchComments();},error:()=>this.error='Unable to post review comment.'});
  }
  resolve(id:string){this.api.resolveBlockComment(this.articleId,id).subscribe({next:()=>this.fetchComments(),error:()=>this.error='Unable to resolve comment.'});}
  exportJson(){const content=this.editor?.getJSON();if(!content)return;
    const blob=new Blob([JSON.stringify(content,null,2)],{type:'application/json'});
    const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=`ricoz-${this.versionId}.json`;link.click();URL.revokeObjectURL(url);
  }
  private destroyEditor(){this.offline?.destroy();this.offline=null;this.editor?.destroy();this.editor=null;this.provider?.destroy();this.provider=null;this.doc?.destroy();this.doc=null;this.roomName='';}
  ngOnDestroy(){this.subscriptions.unsubscribe();this.destroyEditor();}
}
