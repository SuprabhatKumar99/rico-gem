package com.ricozknow.search;
import com.ricozknow.analytics.AnalyticsEventRepository; import com.ricozknow.common.TenantContext; import com.ricozknow.article.ArticleRepository; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import java.util.*;
@Service @RequiredArgsConstructor public class SearchSuggestionService{
 private final AnalyticsEventRepository events; private final ArticleRepository articles;
 public List<String> popular(){return events.popularQueries(TenantContext.get()).stream().map(r->String.valueOf(r[0])).toList();}
 public List<String> suggest(String q){String term=q==null?"":q.trim().toLowerCase(); List<String> out=new ArrayList<>(); if(term.length()<2)return out; for(Object[] r:events.popularQueries(TenantContext.get())){String x=String.valueOf(r[0]);if(x.toLowerCase().contains(term)||distance(x.toLowerCase(),term)<=2)out.add(x);if(out.size()>=6)break;}return out;}
 private int distance(String a,String b){int[] p=new int[b.length()+1];for(int j=0;j<p.length;j++)p[j]=j;for(int i=1;i<=a.length();i++){int prev=p[0];p[0]=i;for(int j=1;j<=b.length();j++){int cur=p[j];p[j]=Math.min(Math.min(p[j-1]+1,p[j]+1),prev+(a.charAt(i-1)==b.charAt(j-1)?0:1));prev=cur;}}return p[b.length()];}
}