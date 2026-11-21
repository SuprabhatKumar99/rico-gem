package com.ricozknow.article;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.common.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Phase1DocumentExchangeTest {
 @Mock ArticleService articleService;
 @Mock ArticleRepository articles;
 @Mock com.ricozknow.category.CategoryRepository categories;
 @Mock ArticleTagRepository tags;
 @Mock ArticleWorkflowService workflow;
 @InjectMocks ArticleBulkService bulk;
 UUID tenant;
 @BeforeEach void setup(){tenant=UUID.randomUUID();TenantContext.set(tenant);}
 @AfterEach void clear(){TenantContext.clear();}
 @Test void allExportFormatsGenerateNonEmptyTenantScopedFiles() throws Exception {
  Article a=new Article();a.setTitle("FAQ");a.setSlug("faq");
  ReflectionTestUtils.setField(a,"id",UUID.randomUUID());
  ArticleVersion v=new ArticleVersion();v.setArticle(a);
  v.setContent(new ObjectMapper().readTree("{\"blocks\":[{\"id\":\"b1\",\"type\":\"heading\",\"level\":1,\"text\":\"FAQ\"},{\"id\":\"b2\",\"type\":\"paragraph\",\"text\":\"Welcome\"}]}"));
  a.setActiveVersion(v);
  when(articles.findAllByTenantId(tenant)).thenReturn(List.of(a));
  for(String format:List.of("markdown","html","docx","pdf")) {
   byte[] zipped=bulk.exportArchive(format);
   try(ZipInputStream zip=new ZipInputStream(new java.io.ByteArrayInputStream(zipped))) {
    ZipEntry entry=zip.getNextEntry();assertThat(entry).isNotNull();assertThat(entry.getName()).endsWith("."+(format.equals("markdown")?"md":format));
    byte[] body=zip.readAllBytes();assertThat(body.length).isGreaterThan(20);
    if(format.equals("html"))assertThat(new String(body,StandardCharsets.UTF_8)).contains("<h1>","Welcome");
    if(format.equals("pdf"))assertThat(new String(body,0,4,StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    if(format.equals("docx"))assertThat(body[0]).isEqualTo((byte)'P');
   }
  }
 }
 @Test void exportRejectsUnknownFormat() {
  assertThatThrownBy(()->bulk.exportArchive("exe")).isInstanceOf(IllegalArgumentException.class);
  verifyNoInteractions(articles);
 }
}
