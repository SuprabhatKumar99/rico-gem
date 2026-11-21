package com.ricozknow.article.dto;
import java.util.List;
public record ArticleTemplateResponse(String key, String name, String description, List<BlockTemplate> blocks) {
    public record BlockTemplate(String type, Integer level, String text, List<String> items) {}
}
