package com.ricozknow.enterprise.metering;

import com.ricozknow.common.TenantContext;
import jakarta.servlet.FilterChain; import jakarta.servlet.ServletException; import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component @RequiredArgsConstructor
public class UsageMeteringFilter extends OncePerRequestFilter {
 private final UsageMeteringService service;
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
   try { chain.doFilter(request,response); } finally {
     if (TenantContext.getOrNull()!=null && request.getRequestURI().startsWith("/api/v1/")) {
       try { service.increment("API_REQUESTS",1); if (request.getRequestURI().startsWith("/api/v1/rag/")) service.increment("RAG_REQUESTS",1); } catch(Exception ignored) {}
     }
   }
 }
}
