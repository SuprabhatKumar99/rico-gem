package com.ricozknow.billing;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** Admin-only tenant billing; webhooks use their own verified provider signature. */
@RestController @RequiredArgsConstructor
public class BillingController {
    private final BillingService billing;
    private final BillingGateway gateway;
    public record CheckoutRequest(@NotBlank String planCode) {}
    public record CancelRequest(boolean atPeriodEnd) {}
    @GetMapping("/api/v1/billing/plans") @PreAuthorize("hasRole('ADMIN')")
    public List<BillingService.Plan> plans(){return billing.plans();}
    @GetMapping("/api/v1/billing/overview") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> overview(){return billing.overview();}
    @GetMapping("/api/v1/billing/invoices") @PreAuthorize("hasRole('ADMIN')")
    public List<BillingService.Invoice> invoices(){return billing.invoices();}
    @PostMapping("/api/v1/billing/checkout") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,String> checkout(@Valid @RequestBody CheckoutRequest request){return billing.checkout(request.planCode());}
    @PostMapping("/api/v1/billing/portal") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,String> portal(){return billing.customerPortal();}
    @PostMapping("/api/v1/billing/cancel") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cancel(@RequestBody CancelRequest request){billing.cancellation(request.atPeriodEnd());return ResponseEntity.accepted().build();}
    @PostMapping(path="/api/v1/billing/webhooks/stripe", consumes="application/json")
    public ResponseEntity<Void> webhook(@RequestBody String raw,@RequestHeader(value="Stripe-Signature",required=false) String signature){
        if(raw.length()>1_000_000) return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        JsonNode verified=gateway.verifiedEvent(raw,signature);
        billing.acceptEvent(verified);
        return ResponseEntity.ok().build();
    }
}
