package com.ricozknow.enterprise.scope;

import com.ricozknow.common.TenantContext;
import com.ricozknow.rag.RagApiKey;
import com.ricozknow.rag.RagApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiScopeService {
    private final RagApiKeyRepository repository;
    @Transactional public Set<String> update(UUID id, Set<String> scopes) {
        Set<String> normalized = scopes.stream().map(String::trim).map(String::toUpperCase).collect(Collectors.toSet());
        for (String s : normalized) ApiScope.valueOf(s);
        RagApiKey key = repository.findById(id).filter(k -> TenantContext.get().equals(k.getTenantId())).orElseThrow(() -> new IllegalArgumentException("API key not found"));
        key.setScopes(String.join(",", normalized)); repository.save(key); return normalized;
    }
    @Transactional(readOnly = true) public Set<String> get(UUID id) {
        RagApiKey key = repository.findById(id).filter(k -> TenantContext.get().equals(k.getTenantId())).orElseThrow(() -> new IllegalArgumentException("API key not found"));
        return Arrays.stream(key.getScopes().split(",")).map(String::trim).filter(x -> !x.isBlank()).collect(Collectors.toSet());
    }
}
