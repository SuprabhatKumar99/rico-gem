package com.ricozknow.article;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ricozknow.article.dto.*;
import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.user.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class ReusableContentService {
 private final ArticleContentTemplateRepository templates;
 private final ArticleReusableBlockRepository blocks;
 private final ContentValidator validator;
 private final AuditService audit;
 private final ObjectMapper mapper;
 private UUID actor(){var a=SecurityContextHolder.getContext().getAuthentication();if(a!=null&&a.getPrincipal() instanceof AuthenticatedUser u)return u.userId();throw new IllegalStateException("Login required");}
 @Transactional(readOnly=true) public List<ArticleContentTemplate> templates(){return templates.findByTenantIdOrderByNameAsc(TenantContext.get());}
 @Transactional public ArticleContentTemplate createTemplate(ContentTemplateRequest req){
  validator.validate(req.content());
  String key=req.key().trim().toLowerCase(Locale.ROOT);
  if(!key.matches("[a-z0-9][a-z0-9_-]{1,119}"))throw new IllegalArgumentException("Invalid template key");
  if(templates.existsByTenantIdAndTemplateKey(TenantContext.get(),key))throw new IllegalArgumentException("Template key already exists");
  ArticleContentTemplate t=new ArticleContentTemplate();t.setTemplateKey(key);t.setName(req.name().trim());t.setDescription(req.description());t.setContent(req.content().deepCopy());t.setCreatedBy(actor());templates.save(t);
  audit.record("ArticleContentTemplate",t.getId(),"TEMPLATE_CREATED",null,key);return t;
 }
 @Transactional public void deleteTemplate(UUID id){var t=templates.findByTenantIdAndId(TenantContext.get(),id).orElseThrow();templates.delete(t);audit.record("ArticleContentTemplate",id,"TEMPLATE_DELETED",null,null);}
 @Transactional(readOnly=true) public List<ArticleReusableBlock> blocks(){return blocks.findByTenantIdOrderByNameAsc(TenantContext.get());}
 @Transactional public ArticleReusableBlock createBlock(ReusableBlockRequest req){
  if(req.block()==null||!req.block().isObject())throw new IllegalArgumentException("Block must be an object");
  ObjectNode wrapper=mapper.createObjectNode();wrapper.putArray("blocks").add(req.block().deepCopy());validator.validate(wrapper);
  ArticleReusableBlock b=new ArticleReusableBlock();b.setName(req.name().trim());b.setBlock(req.block().deepCopy());b.setCreatedBy(actor());blocks.save(b);audit.record("ArticleReusableBlock",b.getId(),"REUSABLE_BLOCK_CREATED",null,b.getName());return b;
 }
 @Transactional public void deleteBlock(UUID id){var b=blocks.findByTenantIdAndId(TenantContext.get(),id).orElseThrow();blocks.delete(b);audit.record("ArticleReusableBlock",id,"REUSABLE_BLOCK_DELETED",null,null);}
}
