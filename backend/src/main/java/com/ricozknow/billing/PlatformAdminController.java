package com.ricozknow.billing;

import com.ricozknow.user.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Explicit operator allowlist layered on top of a live ADMIN principal; no tenant-id headers are trusted. */
@RestController @RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class PlatformAdminController {
    private final JdbcTemplate db;
    @Value("${RICOZ_PLATFORM_OPERATOR_PRINCIPALS:}") private String operatorPrincipals;
    private void authorize(){
        Object principal=SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!(principal instanceof AuthenticatedUser user) ||
           Arrays.stream(operatorPrincipals.split(",")).map(String::trim).filter(s->!s.isBlank())
             .noneMatch(identity->identity.equalsIgnoreCase(user.tenantId()+":"+user.email())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Platform operator access required");
    }
    public record PriceConfiguration(String stripePriceId) {}
    @PutMapping("/plans/{code}/stripe-price") @Transactional
    public Map<String,String> configurePrice(@PathVariable String code,@RequestBody PriceConfiguration request){
        authorize();
        if(!Set.of("GROWTH","BUSINESS").contains(code) || request.stripePriceId()==null ||
            !request.stripePriceId().matches("price_[a-zA-Z0-9]{5,140}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"An active Stripe Price ID is required");
        db.update("update saas_plan set stripe_price_id=? where code=?",request.stripePriceId(),code);
        return Map.of("plan",code,"priceConfigured","true");
    }
    @GetMapping("/overview") @Transactional(readOnly=true)
    public Map<String,Object> overview(){
        authorize();
        List<Map<String,Object>> tenants=db.queryForList("""
            select t.id,t.slug,t.name,t.status as tenant_status,
              s.plan_code,s.status as billing_status,
              (select count(*) from users u where u.tenant_id=t.id and u.status<>'DISABLED') as seats
            from tenants t left join saas_subscription s on s.tenant_id=t.id
            order by t.created_at desc limit 200
            """);
        List<Map<String,Object>> revenue=db.queryForList("""
            select currency,count(*) as paid_invoices,coalesce(sum(amount_paid_minor),0) as received_minor
            from saas_invoice where status='paid' group by currency
            """);
        List<Map<String,Object>> priceConfiguration=db.queryForList("select code,name,(stripe_price_id is not null) as stripe_configured from saas_plan where visible order by code");
        return Map.of("tenants",tenants,"paidInvoiceTotals",revenue,"plans",priceConfiguration,
          "note","Invoice payments are reconciled receipts, not recognized revenue or ARR");
    }
}
