package com.ricozknow.article;
import com.ricozknow.article.dto.ArticleTemplateResponse;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class ArticleTemplateService {
 public List<ArticleTemplateResponse> list() {
  return List.of(
   new ArticleTemplateResponse("how-to","How-to guide","Step-by-step instructions",List.of(
    new ArticleTemplateResponse.BlockTemplate("heading",1,"How to …",null),
    new ArticleTemplateResponse.BlockTemplate("paragraph",null,"Overview",null),
    new ArticleTemplateResponse.BlockTemplate("heading",2,"Steps",null),
    new ArticleTemplateResponse.BlockTemplate("list",null,null,List.of("Step 1","Step 2","Step 3")),
    new ArticleTemplateResponse.BlockTemplate("heading",2,"Troubleshooting",null),
    new ArticleTemplateResponse.BlockTemplate("paragraph",null,"Common issues and fixes",null))),
   new ArticleTemplateResponse("faq","FAQ","Question and answer format",List.of(
    new ArticleTemplateResponse.BlockTemplate("heading",1,"Frequently asked questions",null),
    new ArticleTemplateResponse.BlockTemplate("heading",2,"Question",null),
    new ArticleTemplateResponse.BlockTemplate("paragraph",null,"Answer",null))),
   new ArticleTemplateResponse("troubleshooting","Troubleshooting","Problem, cause and resolution format",List.of(
    new ArticleTemplateResponse.BlockTemplate("heading",1,"Troubleshooting: …",null),
    new ArticleTemplateResponse.BlockTemplate("paragraph",null,"Problem",null),
    new ArticleTemplateResponse.BlockTemplate("heading",2,"Cause",null),
    new ArticleTemplateResponse.BlockTemplate("paragraph",null,"Likely cause",null),
    new ArticleTemplateResponse.BlockTemplate("heading",2,"Resolution",null),
    new ArticleTemplateResponse.BlockTemplate("list",null,null,List.of("Check …","Change …","Verify …"))))
  );
 }
}
