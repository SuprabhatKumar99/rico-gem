package com.ricozknow.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Never logs, retains or sends user content to AI vendors. */
@RestController
@RequestMapping("/api/v1/knowledge-security")
@RequiredArgsConstructor
public class DlpPreviewController {
    private final SensitiveContentScanner scanner;
    public record ScanRequest(@NotNull @Size(max=200000) String content) {}
    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public List<SensitiveContentScanner.Finding> scan(@Valid @RequestBody ScanRequest request){
        return scanner.scan(request.content());
    }
}
