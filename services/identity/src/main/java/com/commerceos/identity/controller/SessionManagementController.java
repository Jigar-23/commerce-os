package com.commerceos.identity.controller;

import com.commerceos.identity.domain.UserSession;
import com.commerceos.identity.repository.UserSessionRepository;
import com.commerceos.identity.security.JwtAuthValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/sessions")
public class SessionManagementController {

    private final UserSessionRepository sessionRepository;
    private final JwtAuthValidator jwtAuthValidator;

    public SessionManagementController(UserSessionRepository sessionRepository, JwtAuthValidator jwtAuthValidator) {
        this.sessionRepository = sessionRepository;
        this.jwtAuthValidator = jwtAuthValidator;
    }

    private boolean isAuthorizedUserOrAdmin(String authHeader, String targetUserId) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = jwtAuthValidator.authenticate(authHeader);
        if (principalOpt.isEmpty()) {
            return false;
        }
        return principalOpt.get().isOwnerOrAdmin(targetUserId);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<UserSession>> getActiveSessions(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID userId
    ) {
        if (!isAuthorizedUserOrAdmin(authHeader, userId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(sessionRepository.findByUserIdAndIsRevokedFalse(userId));
    }

    @PostMapping("/{sessionId}/revoke")
    public ResponseEntity<String> revokeSession(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID sessionId
    ) {
        return sessionRepository.findById(sessionId).map(session -> {
            if (!isAuthorizedUserOrAdmin(authHeader, session.getUserId().toString())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Session revocation requires session owner authority.");
            }
            session.setIsRevoked(true);
            session.setSessionStatus("REVOKED_USER");
            sessionRepository.save(session);
            return ResponseEntity.ok("SESSION_REVOKED");
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/user/{userId}/revoke-all")
    public ResponseEntity<String> revokeAllUserSessions(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID userId
    ) {
        if (!isAuthorizedUserOrAdmin(authHeader, userId.toString())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Revoke-all requires account owner authority.");
        }
        List<UserSession> activeSessions = sessionRepository.findByUserIdAndIsRevokedFalse(userId);
        for (UserSession session : activeSessions) {
            session.setIsRevoked(true);
            session.setSessionStatus("REVOKED_USER");
        }
        sessionRepository.saveAll(activeSessions);
        return ResponseEntity.ok("ALL_SESSIONS_REVOKED");
    }
}
