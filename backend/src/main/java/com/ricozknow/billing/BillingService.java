package com.ricozknow.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/** Server-authoritative billing states. UI redirects and client-supplied plan prices never activate entitlements. */
@Service @RequiredArgsConstructor
public class BillingService {
    private final JdbcTemplate db;
    private final BillingGateway gateway;
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${ricozknow.billing.trial-expiry-check-ms:3600000}")
    @Transactional
    public void expireSelfManagedTrials(){
        db.update("update saas_subscription set status='FREE',plan_code='FREE',updated_at=now() where provider='NONE' and status='TRIALING' and trial_ends_at<now()");
    }

    public record Plan(String code,String name,long monthlyPriceMinor,String currency,int maxSeats,
                       int maxArticles,int maxConnectors,int maxAiRequests,boolean contactSales) {}
    public record Subscription(UUID tenantId,String plan,String status,String customerId,
                               String subscriptionId,Instant periodEnd,Instant trialEnd,boolean cancelAtPeriodEnd) {}
    public record Invoice(String id,String status,String currency,long due,long paid,String url,Instant paidAt) {}
    private record ConfiguredPlan(String code,String stripePriceId) {}

    @Transactional(readOnly=true)
    public List<Plan> plans(){
        return db.query("select code,name,monthly_price_minor,currency,max_seats,max_articles,max_connectors,max_ai_requests,code='ENTERPRISE' from saas_plan where visible and active order by monthly_price_minor,code",
           (rs,i)->new Plan(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getString(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),rs.getInt(8),rs.getBoolean(9)));
    }
    private Subscription find(UUID tenant,boolean lock){
        String sql="select tenant_id,plan_code,status,provider_customer_id,provider_subscription_id,current_period_end,trial_ends_at,cancel_at_period_end from saas_subscription where tenant_id=?"+(lock?" for update":"");
        return db.query(sql,(rs,i)->new Subscription((UUID)rs.getObject(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
            rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toInstant(),rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toInstant(),rs.getBoolean(8)),tenant)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Tenant subscription missing; run V36"));
    }
    @Transactional(readOnly=true)
    public Subscription current(){return find(TenantContext.get(),false);}
    @Transactional(readOnly=true)
    public List<Invoice> invoices(){
        return db.query("select provider_invoice_id,status,currency,amount_due_minor,amount_paid_minor,hosted_invoice_url,paid_at from saas_invoice where tenant_id=? order by updated_at desc limit 100",
            (rs,i)->new Invoice(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getLong(5),rs.getString(6),rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toInstant()),TenantContext.get());
    }
    public Map<String,Object> overview(){
        Subscription s=current();
        String effective=effectivePlan(s);
        List<Map<String,Object>> quota=db.queryForList("select code,max_seats,max_articles,max_connectors,max_ai_requests from saas_plan where code=?",effective);
        Map<String,Object> usage=Map.of(
            "seats",db.queryForObject("select count(*) from users where tenant_id=? and status<>'DISABLED'",Long.class,TenantContext.get()),
            "articles",db.queryForObject("select count(*) from articles where tenant_id=? and status<>'ARCHIVED'",Long.class,TenantContext.get()),
            "connectors",db.queryForObject("select count(*) from connector_source where tenant_id=? and disabled=false",Long.class,TenantContext.get()),
            "aiRequestsThisMonth",db.query("select requests from ai_quota_counter where tenant_id=? and period=to_char(now() at time zone 'UTC','YYYY-MM')",rs->rs.next()?rs.getInt(1):0,TenantContext.get()));
        return Map.of("subscription",s,"effectivePlan",effective,"limits",quota.get(0),"usage",usage);
    }
    static String effectivePlan(Subscription s){
        if(s.status().equals("LEGACY"))return "LEGACY";
        if(s.status().equals("TRIALING") && s.trialEnd()!=null && s.trialEnd().isAfter(Instant.now()))return s.plan();
        if(s.status().equals("ACTIVE"))return s.plan();
        return "FREE";
    }
    /** 0 means unlimited. Locks the subscription row so parallel seat/article/source admissions serialize. */
    @Transactional
    public int limit(UUID tenant,String field){
        if(!Set.of("max_seats","max_articles","max_connectors","max_ai_requests").contains(field))
            throw new IllegalArgumentException("Unsupported entitlement metric");
        Subscription s=find(tenant,true);
        Integer allowed=db.queryForObject("select "+field+" from saas_plan where code=?",Integer.class,effectivePlan(s));
        return allowed==null?0:allowed;
    }
    @Transactional
    public void checkCapacity(UUID tenant,String metric){
        String table,suffix;
        switch(metric){
            case "max_seats" -> {table="users";suffix=" and status <> 'DISABLED'";}
            case "max_articles" -> {table="articles";suffix=" and status <> 'ARCHIVED'";}
            case "max_connectors" -> {table="connector_source";suffix=" and disabled=false";}
            default -> throw new IllegalArgumentException("Unknown capacity type");
        }
        int cap=limit(tenant,metric);
        if(cap==0)return;
        Long count=db.queryForObject("select count(*) from "+table+" where tenant_id=?"+suffix,Long.class,tenant);
        if(count!=null&&count>=cap)throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,"Plan limit reached: "+metric);
    }
    @Transactional
    public Map<String,String> checkout(String requestedPlan){
        UUID tenant=TenantContext.get();
        Subscription s=find(tenant,true);
        if(s.subscriptionId()!=null && !Set.of("CANCELED","UNPAID").contains(s.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Use the billing portal to change an existing subscription");
        ConfiguredPlan plan=db.query("select code,stripe_price_id from saas_plan where code=? and active and visible and monthly_price_minor>0",
            (rs,i)->new ConfiguredPlan(rs.getString(1),rs.getString(2)),requestedPlan)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Plan not purchasable"));
        if(plan.stripePriceId()==null||!plan.stripePriceId().matches("price_[a-zA-Z0-9]+"))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Stripe Price is not configured for this plan");
        String customer=s.customerId();
        if(customer==null){
            String name=db.queryForObject("select name from tenants where id=?",String.class,tenant);
            customer=gateway.customer(tenant.toString(),name);
            if(!customer.matches("cus_[a-zA-Z0-9]+"))throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Invalid payment customer response");
            db.update("update saas_subscription set provider_customer_id=?,provider='STRIPE',updated_at=now() where tenant_id=?",customer,tenant);
        }
        Map<String,String> result=gateway.checkout(customer,plan.stripePriceId(),tenant.toString());
        String sessionId=result.getOrDefault("sessionId","");
        if(!sessionId.startsWith("cs_"))throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Invalid checkout response");
        db.update("insert into saas_checkout_attempt(tenant_id,plan_code,provider_session_id) values(?,?,?)",tenant,plan.code(),sessionId);
        return result;
    }
    @Transactional(readOnly=true)
    public Map<String,String> customerPortal(){
        String customer=current().customerId();
        if(customer==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"No billing customer is linked to this tenant");
        return gateway.portal(customer);
    }
    /** Sends a provider request; changes local entitlement only after a signed subscription webhook. */
    @Transactional(readOnly=true)
    public void cancellation(boolean atEnd){
        Subscription s=current();
        if(s.subscriptionId()==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"No active provider subscription");
        gateway.setCancelAtPeriodEnd(s.subscriptionId(),atEnd);
    }
    @Transactional
    public void acceptEvent(JsonNode event){
        String eventId=event.path("id").asText("");
        String type=event.path("type").asText("");
        long created=event.path("created").asLong(0);
        if(!eventId.matches("evt_[a-zA-Z0-9]+")||created<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Malformed billing event");
        int inserted=db.update("insert into saas_billing_event(provider,provider_event_id,event_type,event_created) values('STRIPE',?,?,?) on conflict do nothing",eventId,type,created);
        if(inserted==0)return;
        JsonNode obj=event.path("data").path("object");
        String customer=obj.path("customer").asText("");
        UUID tenant=tenantForCustomer(customer);
        if(tenant==null)return; // Unlinked customers never mutate a tenant.
        if(type.equals("checkout.session.completed")){
            String session=obj.path("id").asText();
            String ref=obj.path("client_reference_id").asText();
            Integer approved=db.query("select 1 from saas_checkout_attempt where tenant_id=? and provider_session_id=?",
               rs->rs.next()?1:null,tenant,session);
            if(approved==null&&tenant.toString().equals(ref))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Checkout attempt not committed; retry event");
            if(approved!=null&&tenant.toString().equals(ref)){
                String sub=obj.path("subscription").asText("");
                if(sub.matches("sub_[a-zA-Z0-9]+")){
                    db.update("update saas_subscription set provider_subscription_id=coalesce(provider_subscription_id,?),updated_at=now() where tenant_id=? and (provider_subscription_id is null or provider_subscription_id=?)",sub,tenant,sub);
                }
                db.update("update saas_checkout_attempt set completed_at=now() where tenant_id=? and provider_session_id=?",tenant,session);
            }
        } else if(type.startsWith("customer.subscription.")){
            updateSubscription(tenant,type,obj,created);
        } else if(type.startsWith("invoice.")){
            updateInvoice(tenant,obj,type,created);
        }
        db.update("update saas_billing_event set tenant_id=? where provider='STRIPE' and provider_event_id=?",tenant,eventId);
    }
    private UUID tenantForCustomer(String customer){
        if(customer==null||!customer.startsWith("cus_"))return null;
        return db.query("select tenant_id from saas_subscription where provider_customer_id=?",(rs,i)->(UUID)rs.getObject(1),customer)
            .stream().findFirst().orElse(null);
    }
    private void updateSubscription(UUID tenant,String type,JsonNode obj,long eventTime){
        String subscriptionId=obj.path("id").asText("");
        if(!subscriptionId.matches("sub_[a-zA-Z0-9]+"))return;
        // provider subscription ownership established by customer mapping; don't attach a second unrelated subscription silently.
        List<Long> last=db.query("select last_event_created from saas_subscription where tenant_id=? and (provider_subscription_id is null or provider_subscription_id=?) for update",
            (rs,i)->rs.getLong(1),tenant,subscriptionId);
        if(last.isEmpty()||eventTime<last.get(0))return;
        String price=obj.path("items").path("data").path(0).path("price").path("id").asText("");
        List<String> plan=db.query("select code from saas_plan where stripe_price_id=? and active",(rs,i)->rs.getString(1),price);
        // Deleted subscriptions may omit item prices; preserve previous plan in that case.
        if(plan.isEmpty()&&!type.equals("customer.subscription.deleted"))return;
        String stripeStatus=obj.path("status").asText("incomplete");
        String state=switch(stripeStatus){case "active" -> "ACTIVE";case "trialing" -> "TRIALING";
            case "past_due" -> "PAST_DUE";case "unpaid" -> "UNPAID";case "canceled" -> "CANCELED";default -> "INCOMPLETE";};
        if(type.equals("customer.subscription.deleted"))state="CANCELED";
        long end=obj.path("current_period_end").asLong(0); long trial=obj.path("trial_end").asLong(0);
        db.update("""
            update saas_subscription set plan_code=case when ? is null then plan_code else ? end,
            status=?,provider='STRIPE',provider_subscription_id=?,current_period_end=?,trial_ends_at=?,
            cancel_at_period_end=?,last_event_created=?,updated_at=now() where tenant_id=?
            """,plan.isEmpty()?null:plan.get(0),plan.isEmpty()?null:plan.get(0),state,subscriptionId,
            end>0?java.sql.Timestamp.from(Instant.ofEpochSecond(end)):null,
            trial>0?java.sql.Timestamp.from(Instant.ofEpochSecond(trial)):null,
            obj.path("cancel_at_period_end").asBoolean(false),eventTime,tenant);
    }
    private void updateInvoice(UUID tenant,JsonNode invoice,String eventType,long created){
        String id=invoice.path("id").asText("");if(!id.matches("in_[a-zA-Z0-9]+"))return;
        String status=invoice.path("status").asText("unknown");
        String currency=invoice.path("currency").asText("usd");
        if(!currency.matches("[a-z]{3}"))currency="usd";
        String url=invoice.path("hosted_invoice_url").asText("");
        String pdf=invoice.path("invoice_pdf").asText("");
        db.update("""
            insert into saas_invoice(tenant_id,provider_invoice_id,provider_subscription_id,status,currency,amount_due_minor,amount_paid_minor,hosted_invoice_url,invoice_pdf_url,paid_at,last_event_created)
            values(?,?,?,?,?,?,?,?,?,?,?) on conflict(provider_invoice_id) do update set
            status=excluded.status,amount_due_minor=excluded.amount_due_minor,amount_paid_minor=excluded.amount_paid_minor,
            hosted_invoice_url=excluded.hosted_invoice_url,invoice_pdf_url=excluded.invoice_pdf_url,paid_at=excluded.paid_at,last_event_created=excluded.last_event_created,updated_at=now()
            where saas_invoice.tenant_id=excluded.tenant_id and excluded.last_event_created>=saas_invoice.last_event_created
            """,tenant,id,invoice.path("subscription").asText(""),status,currency,
             Math.max(0,invoice.path("amount_due").asLong()),Math.max(0,invoice.path("amount_paid").asLong()),
             safeStripeUrl(url),safeStripeUrl(pdf),status.equals("paid")?java.sql.Timestamp.from(Instant.ofEpochSecond(Math.max(1,invoice.path("status_transitions").path("paid_at").asLong(Instant.now().getEpochSecond())))):null,created);
    }
    private static String safeStripeUrl(String url){
        try{java.net.URI uri=java.net.URI.create(url);String host=uri.getHost();
            return "https".equals(uri.getScheme())&&host!=null&&(host.equals("stripe.com")||host.endsWith(".stripe.com"))?url:null;
        }catch(Exception ex){return null;}
    }
}
