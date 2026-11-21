package com.ricozknow.enterprise.mfa;

import com.ricozknow.common.TenantContext;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
public class MfaController {
    private final UserRepository users;
    private final MfaService mfa;

    @PostMapping("/enroll")
    @PreAuthorize("isAuthenticated()")
    public MfaService.Enrollment enroll() { return mfa.enroll(user()); }

    @PostMapping("/enable")
    @PreAuthorize("isAuthenticated()")
    public MfaService.RecoveryCodes enable(@RequestBody(required = false) CodePayload body,
                                            @RequestParam(required = false) String code) {
        return mfa.enable(user(), proof(body, code));
    }

    @PostMapping("/disable")
    @PreAuthorize("isAuthenticated()")
    public void disable(@RequestBody(required = false) CodePayload body,
                        @RequestParam(required = false) String code) {
        mfa.disable(user(), proof(body, code));
    }

    @PostMapping("/recovery-codes/regenerate")
    @PreAuthorize("isAuthenticated()")
    public MfaService.RecoveryCodes regenerate(@RequestBody(required = false) CodePayload body,
                                                @RequestParam(required = false) String code) {
        return mfa.regenerate(user(), proof(body, code));
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public Status status() {
        return new Status(user().isMfaEnabled());
    }

    public record Status(boolean enabled) {}
    public record CodePayload(String code) {}

    // Accept old query-param callers temporarily; new UI sends codes in JSON body
    // to avoid accidental proxy/access-log capture of authenticator/recovery codes.
    private String proof(CodePayload body, String legacyCode) {
        return body != null && body.code() != null ? body.code().trim() : legacyCode;
    }

    private User user() {
        return users.findById(((com.ricozknow.user.AuthenticatedUser) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal()).userId())
                .filter(u -> TenantContext.get().equals(u.getTenantId())).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
