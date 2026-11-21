package com.ricozknow.widget;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController public class WidgetController{
 @Value("${ricozknow.widget.frontend-base-url:http://localhost:4200}") String frontend;
 @GetMapping(value="/widget/ricozknow.js",produces="application/javascript")
 public ResponseEntity<String> script(){String js="(()=>{const s=document.currentScript;const t=s&&s.dataset.tenant;if(!t){console.error('RicozKnow widget: data-tenant is required');return;}const b="+q(frontend)+";const f=document.createElement('iframe');f.src=b+'/widget?tenant='+encodeURIComponent(t);f.title='Support chat';f.style.cssText='position:fixed;right:20px;bottom:20px;width:390px;height:620px;border:0;z-index:2147483647;background:transparent;pointer-events:auto';f.allow='clipboard-write';document.body.appendChild(f);})();";return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"public,max-age=300").body(js);}
 private String q(String s){return "'"+s.replace("\\","\\\\").replace("'","\\'")+"'";}
}