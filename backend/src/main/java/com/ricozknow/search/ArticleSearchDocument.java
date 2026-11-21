package com.ricozknow.search;

import java.util.List;

public class ArticleSearchDocument {
    public String tenantId;
    public String articleId;
    public String versionId;
    public String slug;
    public String title;
    public List<String> headings;
    public String body;
    public List<String> keywords;
    public List<String> tags;
    public String categoryId;
    public String categoryName;
    public String visibility;
    public String publishedAt;
    public String language;
    public String articleType;
    public String sourceType = "ARTICLE";
    public String sourceId;

    public ArticleSearchDocument() {}

    public ArticleSearchDocument(String tenantId, String articleId, String versionId, String slug,
                                 String title, List<String> headings, String body, List<String> keywords,
                                 List<String> tags, String categoryId, String categoryName,
                                 String visibility, String publishedAt, String language,
                                 String articleType, String sourceType, String sourceId) {
        this.tenantId=tenantId; this.articleId=articleId; this.versionId=versionId;
        this.slug=slug; this.title=title; this.headings=headings; this.body=body;
        this.keywords=keywords; this.tags=tags; this.categoryId=categoryId;
        this.categoryName=categoryName; this.visibility=visibility; this.publishedAt=publishedAt;
        this.language=language; this.articleType=articleType; this.sourceType=sourceType; this.sourceId=sourceId;
    }
}
