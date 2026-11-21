package com.ricozknow.enterprise.audit;

import com.ricozknow.audit.AuditLog;
import com.ricozknow.audit.AuditLogRepository;
import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditExportController {
    private final AuditLogRepository repo;
    private static final int PAGE_SIZE = 500;
    private static final int MAX_ROWS = 100_000;

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(@RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to) {
        if (from != null && to != null && from.isAfter(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        Specification<AuditLog> filter = (root, query, criteria) -> {
            var conditions = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            conditions.add(criteria.equal(root.get("tenantId"), TenantContext.get()));
            if (from != null) conditions.add(criteria.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) conditions.add(criteria.lessThanOrEqualTo(root.get("createdAt"), to));
            return criteria.and(conditions.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        var sort = Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by("id"));
        var first = repo.findAll(filter, PageRequest.of(0, PAGE_SIZE, sort));
        if (first.getTotalElements() > MAX_ROWS)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Export exceeds 100000 records; narrow from/to date range");
        StringBuilder csv = new StringBuilder("createdAt,actorUserId,entityType,entityId,action,ipAddress,source\n");
        for (int i = 0; i < first.getTotalPages(); i++) {
            var page = i == 0 ? first : repo.findAll(filter, PageRequest.of(i, PAGE_SIZE, sort));
            for (AuditLog x : page) {
                csv.append(csv(x.getCreatedAt())).append(',').append(csv(x.getActorUserId())).append(',')
                        .append(csv(x.getEntityType())).append(',').append(csv(x.getEntityId())).append(',')
                        .append(csv(x.getAction())).append(',').append(csv(x.getIpAddress())).append(',')
                        .append(csv(x.getSource())).append('\n');
            }
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=audit-log.csv").body(csv.toString());
    }

    static String csv(Object value) {
        String raw = value == null ? "" : value.toString();
        String trimmed = raw.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) raw = "'" + raw;
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }
}
