package io.meterforge.controlplane.identity.application;

import io.meterforge.controlplane.common.exception.ResourceNotFoundException;
import io.meterforge.controlplane.common.exception.UnauthorizedException;
import io.meterforge.controlplane.identity.api.dto.AuthResponse;
import io.meterforge.controlplane.identity.api.dto.LoginRequest;
import io.meterforge.controlplane.identity.api.dto.UserProfileResponse;
import io.meterforge.controlplane.identity.api.dto.UserSummaryDto;
import io.meterforge.controlplane.identity.api.dto.UserWorkspaceDto;
import io.meterforge.controlplane.identity.domain.User;
import io.meterforge.controlplane.identity.domain.UserRepository;
import io.meterforge.controlplane.identity.infrastructure.JwtTokenService;
import io.meterforge.controlplane.workspace.domain.Workspace;
import io.meterforge.controlplane.workspace.domain.WorkspaceMember;
import io.meterforge.controlplane.workspace.domain.WorkspaceMemberRepository;
import io.meterforge.controlplane.workspace.domain.WorkspaceRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final String cookieName;
    private final boolean cookieSecure;

    public AuthService(
            UserRepository userRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceRepository workspaceRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            @Value("${meterforge.jwt.cookie-name:mf_session}") String cookieName,
            @Value("${meterforge.jwt.cookie-secure:false}") boolean cookieSecure
    ) {
        this.userRepository = userRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse login(LoginRequest request, HttpServletResponse response) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new UnauthorizedException("User account is not active");
        }

        String token = jwtTokenService.generateToken(user.getId(), user.getEmail());

        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(jwtTokenService.getExpirationSeconds())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return getUserProfile(user);
    }

    public void logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return getUserProfile(user);
    }

    private UserProfileResponse getUserProfile(User user) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByIdUserId(user.getId());
        List<UUID> workspaceIds = memberships.stream()
                .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
                .map(m -> m.getId().getWorkspaceId())
                .toList();

        Map<UUID, Workspace> workspaceMap = workspaceRepository.findAllById(workspaceIds).stream()
                .collect(Collectors.toMap(Workspace::getId, w -> w));

        List<UserWorkspaceDto> workspaces = new ArrayList<>();
        for (WorkspaceMember member : memberships) {
            if ("ACTIVE".equalsIgnoreCase(member.getStatus())) {
                Workspace ws = workspaceMap.get(member.getId().getWorkspaceId());
                if (ws != null && "ACTIVE".equalsIgnoreCase(ws.getStatus())) {
                    workspaces.add(new UserWorkspaceDto(ws.getId(), ws.getName(), ws.getSlug(), member.getRole()));
                }
            }
        }

        return new UserProfileResponse(
                new UserSummaryDto(user.getId(), user.getEmail(), user.getStatus()),
                workspaces
        );
    }
}
