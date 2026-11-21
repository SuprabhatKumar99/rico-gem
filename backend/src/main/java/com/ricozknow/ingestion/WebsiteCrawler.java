package com.ricozknow.ingestion;

import com.ricozknow.common.SsrfGuard;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component @RequiredArgsConstructor
public class WebsiteCrawler {
    private final SsrfGuard ssrfGuard;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

    public CrawlResult crawl(String startUrl, int maxPages) {
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(); queue.add(startUrl);
        String host = URI.create(startUrl).getHost();
        StringBuilder combined = new StringBuilder();
        while (!queue.isEmpty() && visited.size() < Math.max(1, Math.min(maxPages, 100))) {
            String url = queue.removeFirst();
            if (!visited.add(url)) continue;
            try {
                ssrfGuard.assertSafeTarget(url);
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", "RicozKnowCrawler/1.0").GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) continue;
                String contentType = response.headers().firstValue("content-type").orElse("");
                if (!contentType.contains("text/html")) continue;
                Document doc = Jsoup.parse(response.body(), url);
                combined.append("\n\n# ").append(doc.title()).append("\n").append(doc.body().text());
                for (var link : doc.select("a[href]")) {
                    if (queue.size() + visited.size() >= maxPages) break;
                    try {
                        URI target = URI.create(link.absUrl("href"));
                        if (target.getHost() != null && target.getHost().equalsIgnoreCase(host) && ("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme()))) queue.add(target.toString());
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }
        return new CrawlResult(combined.toString().trim(), visited.size(), List.copyOf(visited));
    }
    public record CrawlResult(String text, int pages, List<String> urls) {}
}
