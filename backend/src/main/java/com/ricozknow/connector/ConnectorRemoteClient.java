package com.ricozknow.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Read-only provider adapters; all HTTP hosts are fixed, never supplied by API callers. */
@Service @RequiredArgsConstructor
public class ConnectorRemoteClient {
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
    public record RemoteItem(String id,String name,String url,String mimeType,Instant modifiedAt) {}
    public record Page(List<RemoteItem> items,String nextCursor) {}
    public record Content(RemoteItem item,String text) {}
    public static final class RemoteException extends RuntimeException {
        public final int status;
        RemoteException(int status){super("Remote connector API returned HTTP "+status);this.status=status;}
        public boolean deleted(){return status==404||status==410;}
        public boolean retryable(){return status==408||status==429||status>=500;}
    }
    private static String e(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    private static String t(JsonNode n,String field){return n.path(field).asText("");}
    private static Instant date(String value){try{return value==null||value.isBlank()?null:Instant.parse(value);}catch(Exception e){return null;}}
    private HttpResponse<byte[]> send(String url,String token,String method,String body,Map<String,String> extra) {
        URI uri=URI.create(url);
        if(!"https".equalsIgnoreCase(uri.getScheme()))throw new IllegalArgumentException("HTTPS required");
        String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
        boolean safe=Set.of("www.googleapis.com","api.notion.com","api.atlassian.com","graph.microsoft.com","slack.com","api.github.com").contains(host);
        if(!safe)throw new IllegalArgumentException("Unsafe provider host");
        try {
            HttpRequest.Builder b=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).header("Authorization","Bearer "+token).header("Accept","application/json");
            if(host.equals("api.notion.com"))b.header("Notion-Version","2022-06-28");
            if(extra!=null)extra.forEach(b::setHeader);
            if("POST".equals(method))b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body==null?"{}":body));else b.GET();
            HttpResponse<byte[]> r=http.send(b.build(),HttpResponse.BodyHandlers.ofByteArray());
            if(r.statusCode()<200||r.statusCode()>=300)throw new RemoteException(r.statusCode());
            if(r.body().length>6_000_000)throw new IllegalArgumentException("Remote document exceeds the 6 MB ingestion limit");
            return r;
        } catch(RemoteException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("Remote provider call failed",ex);}
    }
    private JsonNode get(String uri,String access){try{return mapper.readTree(send(uri,access,"GET",null,Map.of()).body());}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    private JsonNode post(String uri,String access,String payload){try{return mapper.readTree(send(uri,access,"POST",payload,Map.of()).body());}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    private String raw(String uri,String access){return new String(send(uri,access,"GET",null,Map.of("Accept","text/plain,application/json,application/octet-stream")).body(),StandardCharsets.UTF_8);}
    private String extracted(String uri,String access){
       byte[] bytes=send(uri,access,"GET",null,Map.of("Accept","application/octet-stream")).body();
       try{return new org.apache.tika.Tika().parseToString(new java.io.ByteArrayInputStream(bytes));}
       catch(Exception ex){throw new IllegalArgumentException("Unsupported remote file format",ex);}
    }
    private String atlasCloud(String access){JsonNode r=get("https://api.atlassian.com/oauth/token/accessible-resources",access);if(!r.isArray()||r.isEmpty())throw new IllegalStateException("No accessible Atlassian Cloud site");return t(r.get(0),"id");}
    private String atlas(String p,String token){String cloud=atlasCloud(token);if(!cloud.matches("[a-zA-Z0-9-]{10,100}"))throw new IllegalArgumentException("Invalid Atlassian Cloud ID");return "https://api.atlassian.com/ex/"+(p.equals("jira")?"jira/":"confluence/")+cloud;}
    private RemoteItem item(String id,String title,String url,String mime,String modified){return new RemoteItem(id,title.isBlank()?id:title,url,mime,date(modified));}
    private static String rich(JsonNode n){StringBuilder sb=new StringBuilder();if(n.isArray())for(JsonNode it:n)sb.append(it.path("plain_text").asText(it.path("text").path("content").asText("")));return sb.toString();}
    private static String safeText(String text){return text==null?"":text.replaceAll("(?s)<[^>]+>"," ").replaceAll("\\s+"," ").trim();}
    private static boolean idSafe(String s){return s!=null&&s.matches("[A-Za-z0-9_./:!~-]{1,800}")&&!s.contains("..")&&!s.contains("//");}
    private static String pack(String id){return Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));}
    private static String unpack(String id){String value=new String(Base64.getUrlDecoder().decode(id),StandardCharsets.UTF_8);if(!value.matches("[A-Za-z0-9_,.!:-]{1,400}"))throw new IllegalArgumentException("Invalid drive identity");return value;}
    public Page browse(String p,String token,String cursor) {
        ConnectorCatalog.require(p);
        if(cursor!=null && cursor.length()>2048)throw new IllegalArgumentException("Cursor is too long");
        List<RemoteItem> out=new ArrayList<>();String next=null;JsonNode json;
        switch(p){
            case "google-drive" -> {
                String url="https://www.googleapis.com/drive/v3/files?pageSize=50&fields="+e("nextPageToken,files(id,name,mimeType,webViewLink,modifiedTime)")+"&q="+e("trashed=false")+(cursor==null?"":"&pageToken="+e(cursor));
                json=get(url,token);for(JsonNode f:json.path("files"))if(!t(f,"mimeType").equals("application/vnd.google-apps.folder"))out.add(item(t(f,"id"),t(f,"name"),t(f,"webViewLink"),t(f,"mimeType"),t(f,"modifiedTime")));next=t(json,"nextPageToken");
            }
            case "notion" -> {
                Map<String,Object> body=new LinkedHashMap<>();body.put("page_size",50);if(cursor!=null&&!cursor.isBlank())body.put("start_cursor",cursor);
                try{json=post("https://api.notion.com/v1/search",token,mapper.writeValueAsString(body));}catch(Exception ex){throw new IllegalStateException(ex);}
                for(JsonNode f:json.path("results"))if(t(f,"object").equals("page")){
                    String title=rich(f.path("properties").path("title").path("title"));if(title.isBlank())title=rich(f.path("title"));
                    out.add(item(t(f,"id"),title,t(f,"url"),"application/vnd.notion.page",t(f,"last_edited_time")));
                }next=t(json,"next_cursor");
            }
            case "confluence" -> {
                String url=atlas(p,token)+"/wiki/api/v2/pages?limit=50"+(cursor==null?"":"&cursor="+e(cursor));json=get(url,token);
                for (JsonNode f : json.path("results")) {
                    out.add(item(
                            t(f, "id"),
                            t(f, "title"),
                            "",
                            "text/html",
                            t(f.path("version"), "createdAt")
                    ));
                }
                // Atlassian next links contain cursor query value; do not follow arbitrary returned URLs.
                String link=json.path("_links").path("next").asText("");if(link.contains("cursor="))next=java.net.URLDecoder.decode(link.substring(link.indexOf("cursor=")+7).split("&")[0],StandardCharsets.UTF_8);
            }
            case "sharepoint" -> {
                // Browse SharePoint sites -> drives -> folders/files (not only the caller's OneDrive).
                String url;
                if(cursor==null)url="https://graph.microsoft.com/v1.0/sites?search=*&$top=50";
                else if(cursor.equals("me"))url="https://graph.microsoft.com/v1.0/me/drive/root/children?$top=50";
                else if(cursor.startsWith("site:"))url="https://graph.microsoft.com/v1.0/sites/"+e(cursor.substring(5))+"/drives?$top=50";
                else if(cursor.startsWith("drive:"))url="https://graph.microsoft.com/v1.0/drives/"+e(cursor.substring(6))+"/root/children?$top=50";
                else if(cursor.startsWith("folder:")){
                    String[] path=cursor.substring(7).split(":",2);if(path.length!=2)throw new IllegalArgumentException("Invalid folder cursor");
                    url="https://graph.microsoft.com/v1.0/drives/"+e(unpack(path[0]))+"/items/"+e(unpack(path[1]))+"/children?$top=50";
                }
                else throw new IllegalArgumentException("Invalid SharePoint browse cursor");
                json=get(url,token);
                if(cursor==null){
                    out.add(item("me","My drive","","container",null));
                    for(JsonNode f:json.path("value"))out.add(item("site:"+t(f,"id"),t(f,"displayName"),t(f,"webUrl"),"container",null));
                } else if(cursor.startsWith("site:")){
                    for(JsonNode f:json.path("value"))out.add(item("drive:"+t(f,"id"),t(f,"name"),t(f,"webUrl"),"container",null));
                } else {
                    String driveId=cursor.startsWith("drive:")?cursor.substring(6):cursor.equals("me")?"me":unpack(cursor.substring(7).split(":",2)[0]);
                    for(JsonNode f:json.path("value")){
                        String id=(f.has("folder")?"folder:":"file:")+pack(driveId)+":"+pack(t(f,"id"));
                        out.add(item(id,t(f,"name"),t(f,"webUrl"),f.has("folder")?"container":t(f.path("file"),"mimeType"),t(f,"lastModifiedDateTime")));
                    }
                }
                // Graph's @odata.nextLink must never be fetched verbatim: host checks still apply.
                // Paging within SharePoint folders will be supported when a scoped cursor store is available.
                next=null;
            }
            case "slack" -> {
                json=get("https://slack.com/api/conversations.list?types=public_channel,private_channel&limit=100"+(cursor==null?"":"&cursor="+e(cursor)),token);slackOk(json);
                for(JsonNode f:json.path("channels"))out.add(item(t(f,"id"),t(f,"name"),"","text/plain", ""));next=json.path("response_metadata").path("next_cursor").asText("");
            }
            case "github" -> {
                int page=cursor==null?1:Integer.parseInt(cursor);if(page<1||page>10000)throw new IllegalArgumentException("Invalid page");
                json=get("https://api.github.com/user/repos?per_page=50&page="+page+"&affiliation=owner,collaborator",token);
                for(JsonNode f:json)out.add(item(t(f,"full_name"),t(f,"name"),t(f,"html_url"),"text/markdown",t(f,"updated_at")));
                next=json.isArray()&&json.size()==50?String.valueOf(page+1):null;
            }
            case "jira" -> {
                json=get(atlas(p,token)+"/rest/api/3/project/search?maxResults=50"+(cursor==null?"":"&startAt="+e(cursor)),token);
                for(JsonNode f:json.path("values"))out.add(item(t(f,"key"),t(f,"name"),"","text/plain",""));
                if(!json.path("isLast").asBoolean(true))next=String.valueOf(json.path("startAt").asInt()+json.path("maxResults").asInt(50));
            }
            default -> throw new IllegalArgumentException("provider");
        }
        if(next!=null&&next.isBlank())next=null;
        return new Page(List.copyOf(out),next);
    }
    private static void slackOk(JsonNode json){if(!json.path("ok").asBoolean())throw new IllegalStateException("Slack API rejected request: "+json.path("error").asText("unknown"));}
    public Content fetch(String p,String token,String remoteId){
        ConnectorCatalog.require(p);
        if(!idSafe(remoteId))throw new IllegalArgumentException("Invalid remote document ID");
        JsonNode obj;RemoteItem item;String text;
        switch(p){
            case "google-drive" -> {
                obj=get("https://www.googleapis.com/drive/v3/files/"+e(remoteId)+"?fields="+e("id,name,mimeType,webViewLink,modifiedTime,trashed"),token);
                if(obj.path("trashed").asBoolean())throw new RemoteException(410);
                String mime=t(obj,"mimeType");item=item(remoteId,t(obj,"name"),t(obj,"webViewLink"),mime,t(obj,"modifiedTime"));
                if(mime.equals("application/vnd.google-apps.document"))text=raw("https://www.googleapis.com/drive/v3/files/"+e(remoteId)+"/export?mimeType=text%2Fplain",token);
                else if(mime.equals("application/vnd.google-apps.spreadsheet"))text=raw("https://www.googleapis.com/drive/v3/files/"+e(remoteId)+"/export?mimeType=text%2Fcsv",token);
                else if(mime.startsWith("text/")||mime.equals("application/json"))text=raw("https://www.googleapis.com/drive/v3/files/"+e(remoteId)+"?alt=media",token);
                else if(mime.equals("application/pdf")||mime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))text=extracted("https://www.googleapis.com/drive/v3/files/"+e(remoteId)+"?alt=media",token);
                else throw new IllegalArgumentException("Unsupported Google Drive file format: "+mime);
            }
            case "notion" -> {
                obj=get("https://api.notion.com/v1/pages/"+e(remoteId),token);
                String title=rich(obj.path("properties").path("title").path("title"));if(title.isBlank())title=remoteId;
                item=item(remoteId,title,t(obj,"url"),"application/vnd.notion.page",t(obj,"last_edited_time"));
                StringBuilder sb=new StringBuilder();String cursor=null;int total=0;
                do {JsonNode blocks=get("https://api.notion.com/v1/blocks/"+e(remoteId)+"/children?page_size=100"+(cursor==null?"":"&start_cursor="+e(cursor)),token);
                    for(JsonNode block:blocks.path("results")){String type=t(block,"type");sb.append(rich(block.path(type).path("rich_text"))).append('\n');total++;}
                    cursor=blocks.path("has_more").asBoolean()?t(blocks,"next_cursor"):null;
                }while(cursor!=null&&!cursor.isBlank()&&total<500);text=sb.toString();
            }
            case "confluence" -> {
                obj=get(atlas(p,token)+"/wiki/api/v2/pages/"+e(remoteId)+"?body-format=storage",token);
                item=item(remoteId,t(obj,"title"),"","text/html",t(obj.path("version"),"createdAt"));text=safeText(obj.path("body").path("storage").path("value").asText());
            }
            case "sharepoint" -> {
                if(!remoteId.startsWith("file:"))throw new IllegalArgumentException("Select a file, not a folder or site");
                String[] paths=remoteId.substring(5).split(":",2);if(paths.length!=2)throw new IllegalArgumentException("Invalid SharePoint item");
                String drive=unpack(paths[0]),file=unpack(paths[1]);
                String prefix=drive.equals("me")?"https://graph.microsoft.com/v1.0/me/drive/items/":"https://graph.microsoft.com/v1.0/drives/"+e(drive)+"/items/";
                obj=get(prefix+e(file),token);
                item=item(remoteId,t(obj,"name"),t(obj,"webUrl"),t(obj.path("file"),"mimeType"),t(obj,"lastModifiedDateTime"));
                // Microsoft Graph returns a short-lived, preauthenticated download URL.
                String download=t(obj,"@microsoft.graph.downloadUrl");
                URI loc=URI.create(download);String host=loc.getHost()==null?"":loc.getHost().toLowerCase(Locale.ROOT);
                if(!"https".equalsIgnoreCase(loc.getScheme())||!(host.endsWith(".sharepoint.com")||host.endsWith(".1drv.com")||host.endsWith(".sharepoint-df.com")))throw new IllegalArgumentException("Unexpected Microsoft file download host");
                try {HttpResponse<byte[]> r=http.send(HttpRequest.newBuilder(loc).timeout(Duration.ofSeconds(25)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
                    if(r.statusCode()!=200)throw new RemoteException(r.statusCode());if(r.body().length>6_000_000)throw new IllegalArgumentException("Remote document is over 6 MB");
                    text= item.mimeType().startsWith("text/")?new String(r.body(),StandardCharsets.UTF_8):new org.apache.tika.Tika().parseToString(new java.io.ByteArrayInputStream(r.body()));
                }catch(RemoteException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("Microsoft document extraction failed",ex);}
            }
            case "slack" -> {
                obj=get("https://slack.com/api/conversations.info?channel="+e(remoteId),token);slackOk(obj);
                item=item(remoteId,t(obj.path("channel"),"name"),"","text/plain","");StringBuilder sb=new StringBuilder();String cursor=null;int count=0;
                do {JsonNode messages=get("https://slack.com/api/conversations.history?channel="+e(remoteId)+"&limit=100"+(cursor==null?"":"&cursor="+e(cursor)),token);slackOk(messages);
                    for(JsonNode msg:messages.path("messages")){sb.append(msg.path("text").asText("")).append('\n');count++;}
                    cursor=messages.path("has_more").asBoolean()?messages.path("response_metadata").path("next_cursor").asText(""):null;
                }while(cursor!=null&&!cursor.isBlank()&&count<500);text=sb.toString();
            }
            case "github" -> {
                if(!remoteId.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))throw new IllegalArgumentException("Invalid repository ID");
                obj=get("https://api.github.com/repos/"+remoteId,token);
                item=item(remoteId,t(obj,"full_name"),t(obj,"html_url"),"text/markdown",t(obj,"updated_at"));
                try {JsonNode readme=get("https://api.github.com/repos/"+remoteId+"/readme",token);
                    text=new String(Base64.getMimeDecoder().decode(t(readme,"content")),StandardCharsets.UTF_8);
                }catch(RemoteException ex){if(ex.status!=404)throw ex;text=t(obj,"description");}
            }
            case "jira" -> {
                String cloud=atlas(p,token);obj=get(cloud+"/rest/api/3/project/"+e(remoteId),token);
                item=item(remoteId,t(obj,"name"),"","text/plain","");
                JsonNode issues=get(cloud+"/rest/api/3/search/jql?maxResults=100&jql="+e("project = \""+remoteId+"\" ORDER BY updated DESC"),token);
                StringBuilder sb=new StringBuilder();for(JsonNode issue:issues.path("issues"))sb.append(t(issue,"key")).append(": ").append(t(issue.path("fields"),"summary")).append('\n');text=sb.toString();
            }
            default->throw new IllegalArgumentException("provider");
        }
        if(text.length()>3_000_000)text=text.substring(0,3_000_000);
        return new Content(item,text);
    }
}
