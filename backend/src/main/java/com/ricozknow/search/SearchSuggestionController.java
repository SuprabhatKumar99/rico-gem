package com.ricozknow.search;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/portal/search/suggestions") @RequiredArgsConstructor public class SearchSuggestionController{
 private final SearchSuggestionService service;
 @GetMapping public List<String> suggestions(@RequestParam(required=false) String q){return q==null||q.isBlank()?service.popular():service.suggest(q);}
}