package io.meterforge.controlplane.identity.api;

import io.meterforge.controlplane.common.exception.UnauthorizedException;
import io.meterforge.controlplane.identity.api.dto.AuthResponse;
import io.meterforge.controlplane.identity.api.dto.LoginRequest;
import io.meterforge.controlplane.identity.api.dto.UserProfileResponse;
import io.meterforge.controlplane.identity.application.AuthService;
import io.meterforge.controlplane.identity.infrastructure.StaffPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.login(request, response);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        authService.logout(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMe(@AuthenticationPrincipal StaffPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        UserProfileResponse userProfile = authService.getMe(principal.userId());
        return ResponseEntity.ok(userProfile);
    }
}
