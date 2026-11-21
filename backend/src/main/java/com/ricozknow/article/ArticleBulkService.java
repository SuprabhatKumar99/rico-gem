package com.ricozknow.article;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ricozknow.article.dto.ArticleCreateRequest;
import com.ricozknow.common.TenantContext;
import com.ricozknow.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.Loader;
import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

@Service @RequiredArgsConstructor
public class ArticleBulkService {
 private final ArticleService articleService; private final ArticleRepository articles;
 private final CategoryRepository categories; private final ArticleTagRepository tagRepository; private final ArticleWorkflowService workflow; private final ObjectMapper mapper=new ObjectMapper();

 @Transactional public List<Article> importFiles(List<MultipartFile> files) throws IOException {
  List<Article> out=new ArrayList<>();
  if(files.size()>50) throw new IllegalArgumentException("Up to 50 files per import");
  for(MultipartFile f:files){
   if(f.isEmpty() || f.getSize()>8_000_000) throw new IllegalArgumentException("Each import must be non-empty and at most 8 MB");
   String name=f.getOriginalFilename()==null?"":f.getOriginalFilename();
   out.addAll(importOne(f.getBytes(),name,0));
  }
  return out;
 }
 private List<Article> importOne(byte[] data,String name,int depth) throws IOException {
  if(depth>2)throw new IllegalArgumentException("Archive nesting exceeds safe limit");
  String lower=name.toLowerCase(Locale.ROOT);
  if(lower.endsWith(".csv"))return importCsv(data);
  if(lower.endsWith(".md")||lower.endsWith(".markdown"))return List.of(importMarkdown(data,name));
  if(lower.endsWith(".html")||lower.endsWith(".htm"))return List.of(importHtml(data,name));
  if(lower.endsWith(".pdf")||lower.endsWith(".docx")) {
   String text;
   try { text=new Tika().parseToString(new ByteArrayInputStream(data)); }
   catch(Exception ex) {throw new IOException("Cannot extract content from "+name,ex);}
   if(text==null||text.isBlank())throw new IllegalArgumentException("No extractable text in "+name);
   return List.of(importMarkdown(("# "+stripExtension(name)+"\n\n"+text).getBytes(StandardCharsets.UTF_8),stripExtension(name)+".md"));
  }
  if(lower.endsWith(".zip")) {
   List<Article> out=new ArrayList<>(); long total=0;
   try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data))) {
    ZipEntry entry;
    while((entry=zip.getNextEntry())!=null){
     if(entry.isDirectory()) continue;
     String entryName=entry.getName();
     if(entryName.contains("..") || entryName.startsWith("/") || entryName.contains("\\"))throw new IllegalArgumentException("Invalid archive entry");
     if(out.size()>=50)throw new IllegalArgumentException("Archive contains too many entries");
     ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
     while((n=zip.read(chunk))!=-1){total+=n;if(total>8_000_000)throw new IllegalArgumentException("Decompressed archive too large");buffer.write(chunk,0,n);}
     out.addAll(importOne(buffer.toByteArray(),entryName,depth+1));
     zip.closeEntry();
    }
   } return out;
  }
  throw new IllegalArgumentException("Unsupported import file: "+name);
 }
 private String stripExtension(String name){String s=name.replace('\\','/');s=s.substring(s.lastIndexOf('/')+1);return s.replaceFirst("(?i)\\.[^.]+$","");}
 private Article importHtml(byte[] data,String filename) throws IOException {
  var doc=Jsoup.parse(new String(data,StandardCharsets.UTF_8));
  String title=doc.title().isBlank()?stripExtension(filename):doc.title().trim();
  StringBuilder md=new StringBuilder();
  for(Element e:doc.body().select("h1,h2,h3,h4,p,pre,ul,ol")) {
   String tag=e.tagName();
   if(tag.matches("h[1-4]"))md.append("#".repeat(Integer.parseInt(tag.substring(1)))).append(' ').append(e.text()).append("\n\n");
   else if(tag.equals("ul")||tag.equals("ol")){for(Element li:e.select(":scope > li"))md.append("- ").append(li.text()).append('\n');md.append('\n');}
   else md.append(e.wholeText().isBlank()?e.text():e.wholeText()).append("\n\n");
  }
  if(md.isEmpty())md.append(doc.body().text());
  String front="---\ntitle: "+title.replace('\n',' ').replace('\r',' ')+"\n---\n\n";
  return importMarkdown((front+md).getBytes(StandardCharsets.UTF_8),title+".md");
 }
 private List<Article> importCsv(byte[] bytes) throws IOException {
  List<Article> out=new ArrayList<>(); String text=new String(bytes,StandardCharsets.UTF_8);
  List<String> lines=csvRows(text); if(lines.size()<2)return out;
  String[] headers=csvLine(lines.get(0)); Map<String,Integer> idx=new HashMap<>(); for(int i=0;i<headers.length;i++)idx.put(headers[i].trim().toLowerCase(Locale.ROOT),i);
  if(lines.size()>1001)throw new IllegalArgumentException("CSV import is limited to 1000 articles per batch");
  for(int r=1;r<lines.size();r++){if(lines.get(r).isBlank())continue; String[] c=csvLine(lines.get(r));
   String title=val(c,idx,"title"); String slug=val(c,idx,"slug"); String lang=empty(val(c,idx,"language"))?"en":val(c,idx,"language");
   if(title.isBlank()||slug.isBlank())continue; String categoryName=val(c,idx,"category"); UUID categoryId=null;
   if(!categoryName.isBlank()) categoryId=categories.findByTenantIdAndSlug(TenantContext.get(),slug(categoryName)).map(x->x.getId()).orElse(null);
   String tags=val(c,idx,"tags"); String content=val(c,idx,"content");
   ObjectNode root=mapper.createObjectNode(); ArrayNode blocks=root.putArray("blocks");
   ObjectNode h=blocks.addObject();h.put("type","heading");h.put("level",1);h.put("text",title);
   ObjectNode p=blocks.addObject();p.put("type","paragraph");p.put("text",content);
   Article a=articleService.createDraft(new ArticleCreateRequest(title,slug,categoryId,lang,root)); if(!tags.isBlank()) workflow.updateMetadata(a.getId(), new com.ricozknow.article.dto.ArticleMetadataRequest(Arrays.asList(tags.split(";")), null, null, null)); out.add(a);
  } return out;
 }
 private Article importMarkdown(byte[] bytes,String filename) throws IOException {
  String text=new String(bytes,StandardCharsets.UTF_8).replace("\r","");
  String title=filename==null?"Imported article":filename.replaceFirst("\\.(md|markdown)$","");
  String slug=slug(title); String language="en"; List<String> tags=new ArrayList<>();
  if(text.startsWith("---\n")){int end=text.indexOf("\n---",4); if(end>0){String fm=text.substring(4,end); for(String line:fm.split("\n")){String[] kv=line.split(":",2); if(kv.length<2)continue; switch(kv[0].trim().toLowerCase(Locale.ROOT)){case "title"->title=kv[1].trim();case "slug"->slug=kv[1].trim();case "language"->language=kv[1].trim();case "tags"->tags=Arrays.stream(kv[1].split(",")).map(String::trim).filter(x->!x.isBlank()).toList();}} text=text.substring(end+4).trim();}}
  ObjectNode root=mapper.createObjectNode(); ArrayNode blocks=root.putArray("blocks");
  for(String line:text.split("\n\n+")){String part=line.trim(); if(part.isBlank())continue; ObjectNode b=blocks.addObject(); if(part.startsWith("#")){int n=0;while(n<part.length()&&part.charAt(n)=='#')n++;b.put("type","heading");b.put("level",Math.min(4,n));b.put("text",part.substring(n).trim());}else if(part.lines().allMatch(x->x.trim().startsWith("- "))){b.put("type","list");ArrayNode items=b.putArray("items");part.lines().forEach(x->items.add(x.trim().substring(2)));}else{b.put("type","paragraph");b.put("text",part);}}
  Article a=articleService.createDraft(new ArticleCreateRequest(title,slug,null,language,root));
  if(!tags.isEmpty()) workflow.updateMetadata(a.getId(), new com.ricozknow.article.dto.ArticleMetadataRequest(tags, null, null, null));
  return a;
 }
 public byte[] exportCsv() throws IOException {
  StringBuilder s=new StringBuilder("title,slug,language,status,tags\n");
  for(Article a:articles.findAllByTenantId(TenantContext.get())){
   String tagText=tagRepository.findByTenantIdAndArticleIdOrderByTagAsc(TenantContext.get(),a.getId()).stream().map(ArticleTag::getTag).reduce((x,y)->x+";"+y).orElse("");
   s.append(csv(a.getTitle())).append(',').append(csv(a.getSlug())).append(',').append(csv(a.getLanguage())).append(',').append(a.getStatus()).append(',').append(csv(tagText)).append('\n');
  }
  return s.toString().getBytes(StandardCharsets.UTF_8);
 }
 public byte[] exportMarkdownZip() throws IOException {return exportArchive("markdown");}
 /** Export one archive containing one file per article; CSV remains a flat export. */
 public byte[] exportArchive(String format) throws IOException {
  if(!Set.of("markdown","html","docx","pdf").contains(format))throw new IllegalArgumentException("Unsupported export format: "+format);
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  try(ZipOutputStream zip=new ZipOutputStream(out)) {
   for(Article article:articles.findAllByTenantId(TenantContext.get())) {
    ArticleVersion v=exportVersion(article);if(v==null)continue;
    String base=slug(article.getTitle())+"-"+article.getId();
    String ext=format.equals("markdown")?"md":format;
    zip.putNextEntry(new ZipEntry(base+"."+ext));
    byte[] body=switch(format){
     case "markdown"->toMarkdown(article,v).getBytes(StandardCharsets.UTF_8);
     case "html"->toHtml(article,v).getBytes(StandardCharsets.UTF_8);
     case "docx"->toDocx(article,v);
     case "pdf"->toPdf(article,v);
     default->throw new IllegalArgumentException(format);
    };
    zip.write(body);zip.closeEntry();
   }
  }
  return out.toByteArray();
 }
 private ArticleVersion exportVersion(Article article) {
  if(article.getActiveVersion()!=null)return article.getActiveVersion();
  return articleService.versionHistory(article.getId()).stream().findFirst().orElse(null);
 }
 private String toHtml(Article a,ArticleVersion v) {
  StringBuilder out=new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>")
      .append(org.jsoup.nodes.Entities.escape(a.getTitle())).append("</title></head><body>");
  var blocks=v.getContent().path("blocks");
  for(var b:blocks){String type=b.path("type").asText();String text=org.jsoup.nodes.Entities.escape(b.path("text").asText());
   if(type.equals("heading")){int level=Math.max(1,Math.min(4,b.path("level").asInt(2)));out.append("<h").append(level).append(">").append(text).append("</h").append(level).append(">");}
   else if(type.equals("list")){out.append("<ul>");for(var item:b.path("items"))out.append("<li>").append(org.jsoup.nodes.Entities.escape(item.asText())).append("</li>");out.append("</ul>");}
   else if(type.equals("code"))out.append("<pre><code>").append(text).append("</code></pre>");
   else if(type.equals("image")){String url=b.path("url").asText();if(url.startsWith("https://")||url.startsWith("http://"))out.append("<img src=\"").append(org.jsoup.nodes.Entities.escape(url)).append("\" alt=\"").append(org.jsoup.nodes.Entities.escape(b.path("alt").asText())).append("\">");}
   else out.append("<p>").append(text).append("</p>");
  }
  return out.append("</body></html>").toString();
 }
 private byte[] toDocx(Article a,ArticleVersion v) throws IOException {
  try(XWPFDocument doc=new XWPFDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
   XWPFParagraph title=doc.createParagraph();title.setStyle("Title");title.createRun().setText(a.getTitle());
   for(var b:v.getContent().path("blocks")){
    var paragraph=doc.createParagraph();String type=b.path("type").asText();
    if(type.equals("heading"))paragraph.setStyle("Heading"+Math.max(1,Math.min(4,b.path("level").asInt(2))));
    if(type.equals("list")){for(var item:b.path("items")){var li=doc.createParagraph();li.createRun().setText("• "+item.asText());}continue;}
    if(type.equals("image")){paragraph.createRun().setText("[Image: "+b.path("alt").asText()+"] "+b.path("url").asText());continue;}
    paragraph.createRun().setText(b.path("text").asText());
   }
   doc.write(out);return out.toByteArray();
  }
 }
 private byte[] toPdf(Article a,ArticleVersion v) throws IOException {
  try(PDDocument document=new PDDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
   List<String> lines=new ArrayList<>();lines.add(a.getTitle());lines.add("");
   for(var b:v.getContent().path("blocks")) {
    if(b.path("type").asText().equals("list"))for(var item:b.path("items"))lines.add("- "+item.asText());
    else lines.add(b.path("type").asText().equals("image")?"[Image] "+b.path("alt").asText():b.path("text").asText());
    lines.add("");
   }
   PDPage page=null;PDPageContentStream stream=null;int y=0;
   try {
    for(String line:lines){
     for(String raw:line.split("\\R",-1)){
      String printable=raw.replaceAll("[^\\x20-\\x7e]","?");
      while(printable.length()>90){if(y<=0){if(stream!=null)stream.close();page=new PDPage();document.addPage(page);stream=new PDPageContentStream(document,page);y=740;}stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),11);stream.newLineAtOffset(40,y);stream.showText(printable.substring(0,90));stream.endText();y-=16;printable=printable.substring(90);}
      if(y<=0){if(stream!=null)stream.close();page=new PDPage();document.addPage(page);stream=new PDPageContentStream(document,page);y=740;}
      stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),11);stream.newLineAtOffset(40,y);stream.showText(printable);stream.endText();y-=16;
     }
    }
   }finally{if(stream!=null)stream.close();}
   document.save(out);return out.toByteArray();
  }
 }
 private String toMarkdown(Article a,ArticleVersion v){
  StringBuilder out=new StringBuilder("---\ntitle: ").append(a.getTitle()).append("\nslug: ").append(a.getSlug()).append("\nlanguage: ").append(a.getLanguage()).append("\n---\n\n");
  var blocks=v.getContent()==null?null:v.getContent().get("blocks");
  if(blocks!=null&&blocks.isArray()) for(var b:blocks){
   String type=b.path("type").asText();
   if("heading".equals(type)) out.append("#".repeat(Math.max(1,Math.min(4,b.path("level").asInt(2))))).append(' ').append(b.path("text").asText()).append("\n\n");
   else if("list".equals(type)){b.path("items").forEach(i->out.append("- ").append(i.asText()).append('\n'));out.append('\n');}
   else if("image".equals(type))out.append("![").append(b.path("alt").asText()).append("](").append(b.path("url").asText()).append(")\n\n");
   else if("code".equals(type))out.append("```\n").append(b.path("text").asText()).append("\n```\n\n");
   else out.append(b.path("text").asText()).append("\n\n");
  }
  return out.toString();
 }
 private String slug(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");}
 private String val(String[] a,Map<String,Integer> i,String k){Integer x=i.get(k);return x==null||x>=a.length?"":a[x];} private boolean empty(String s){return s==null||s.isBlank();}
 /** RFC-4180-style record splitting, preserving newlines inside quoted fields. */
 private List<String> csvRows(String text){
  List<String> rows=new ArrayList<>();StringBuilder row=new StringBuilder();boolean quoted=false;
  for(int i=0;i<text.length();i++){
   char c=text.charAt(i);
   if(c=='"'){
    if(quoted&&i+1<text.length()&&text.charAt(i+1)=='"'){row.append(c).append(c);i++;continue;}
    quoted=!quoted;
   }
   if((c=='\n'||c=='\r')&&!quoted){
    if(row.length()>0){rows.add(row.toString());row.setLength(0);}
    if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;
   }else row.append(c);
  }
  if(quoted)throw new IllegalArgumentException("CSV has an unterminated quoted field");
  if(row.length()>0)rows.add(row.toString());
  return rows;
 }
 private String[] csvLine(String line){
  if(line==null||line.isEmpty()) return new String[0];
  List<String> values=new ArrayList<>(); StringBuilder current=new StringBuilder(); boolean quoted=false;
  for(int i=0;i<line.length();i++){
   char ch=line.charAt(i);
   if(ch=='\"'){
    if(quoted && i+1<line.length() && line.charAt(i+1)=='\"'){ current.append('\"'); i++; }
    else quoted=!quoted;
   } else if(ch==',' && !quoted){ values.add(current.toString()); current.setLength(0); }
   else current.append(ch);
  }
  values.add(current.toString());
  return values.toArray(new String[0]);
 }
 private String csv(String value){
  if(value==null) value="";
  // CSV exports are routinely opened in spreadsheets; quote formula-prefixed text as literal.
  if(!value.isBlank() && "=+-@".indexOf(value.stripLeading().charAt(0))>=0)value="'"+value;
  return "\""+value.replace("\"","\"\"")+"\"";
 }
}
