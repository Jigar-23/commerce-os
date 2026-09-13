package com.commerceos.identity.controller;

import com.commerceos.identity.domain.UserAccount;
import com.commerceos.identity.repository.UserAccountRepository;
import com.commerceos.identity.security.JwtTokenProvider;
import com.commerceos.identity.security.TwoFactorSmsClient;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.commerceos.identity.domain.UserSession;
import com.commerceos.identity.repository.UserSessionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    private final UserAccountRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TwoFactorSmsClient twoFactorSmsClient;
    private final OtpChallengeRepository otpChallengeRepository;

    public AuthController(
            UserAccountRepository userRepository,
            UserSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            TwoFactorSmsClient twoFactorSmsClient,
            OtpChallengeRepository otpChallengeRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.twoFactorSmsClient = twoFactorSmsClient;
        this.otpChallengeRepository = otpChallengeRepository;
    }

    private static String hashSha256(String input) {
        if (input == null) return "";
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Cryptographic hashing failed", e);
        }
    }

    private String hashRefreshToken(String token) {
        return hashSha256(token);
    }

    private String extractClientIp(jakarta.servlet.http.HttpServletRequest req) {
        if (req == null) return "127.0.0.1";
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String remote = req.getRemoteAddr();
        return (remote != null && !remote.isBlank()) ? remote : "127.0.0.1";
    }

    private String extractUserAgent(jakarta.servlet.http.HttpServletRequest req) {
        if (req == null) return "CommerceOS Client App";
        String ua = req.getHeader("User-Agent");
        return (ua != null && !ua.isBlank()) ? ua : "CommerceOS Client App";
    }

    private String resolveTenantScope(UserAccount user) {
        if (user != null && user.getRoles() != null) {
            if (user.getRoles().contains("ROLE_ADMIN") || user.getRoles().contains("ROLE_PLATFORM_OPERATOR")) {
                return "PLATFORM_ROOT";
            }
            if (user.getRoles().contains("ROLE_SELLER")) {
                return "MERCHANT_" + user.getId();
            }
            if (user.getRoles().contains("ROLE_RIDER")) {
                return "FLEET_RIDER";
            }
        }
        return "COMMERCEOS_CUSTOMER_RETAIL";
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (request.getEmail() == null || request.getPassword() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(409).build();
        }

        UserAccount user = UserAccount.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .roles(Set.of("ROLE_CUSTOMER"))
                .accountStatus("ACTIVE")
                .failedLoginAttempts(0)
                .mfaEnabled(false)
                .build();

        userRepository.save(user);

        String tenantScope = resolveTenantScope(user);
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), user.getRoles(), tenantScope
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());

        UserSession session = UserSession.builder()
                .userId(user.getId())
                .refreshTokenHash(hashRefreshToken(refreshToken))
                .ipAddress(extractClientIp(httpRequest))
                .userAgent(extractUserAgent(httpRequest))
                .deviceId(UUID.randomUUID().toString())
                .sessionStatus("ACTIVE")
                .tokenGeneration(1)
                .isRevoked(false)
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
        sessionRepository.save(session);

        return ResponseEntity.ok(AuthResponse.builder()
                .userId(user.getId().toString())
                .email(user.getEmail())
                .roles(user.getRoles())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        UserAccount user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        // Account Lockout check
        if ("LOCKED".equals(user.getAccountStatus())) {
            if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(Instant.now())) {
                return ResponseEntity.status(423).build(); // 423 Locked
            } else {
                user.setAccountStatus("ACTIVE");
                user.setFailedLoginAttempts(0);
            }
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= 5) {
                user.setAccountStatus("LOCKED");
                user.setLockoutUntil(Instant.now().plus(30, ChronoUnit.MINUTES));
            }
            userRepository.save(user);
            return ResponseEntity.status(401).build();
        }

        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        String tenantScope = resolveTenantScope(user);
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), user.getRoles(), tenantScope
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());

        UserSession session = UserSession.builder()
                .userId(user.getId())
                .refreshTokenHash(hashRefreshToken(refreshToken))
                .ipAddress(extractClientIp(httpRequest))
                .userAgent(extractUserAgent(httpRequest))
                .deviceId(UUID.randomUUID().toString())
                .sessionStatus("ACTIVE")
                .tokenGeneration(1)
                .isRevoked(false)
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
        sessionRepository.save(session);

        return ResponseEntity.ok(AuthResponse.builder()
                .userId(user.getId().toString())
                .email(user.getEmail())
                .roles(user.getRoles())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        String token = request.getRefreshToken();
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).build();
        }

        String userId = jwtTokenProvider.getUserIdFromToken(token);
        String tokenHash = hashRefreshToken(token);

        Optional<UserSession> sessionOpt = sessionRepository.findByRefreshTokenHash(tokenHash);

        if (sessionOpt.isPresent()) {
            UserSession existingSession = sessionOpt.get();

            if ("ROTATED".equals(existingSession.getSessionStatus())) {
                log.warn("SECURITY ALERT: Refresh token reuse detected for userId: {}. Revoking all sessions.", userId);
                sessionRepository.revokeAllUserSessions(UUID.fromString(userId));
                return ResponseEntity.status(401).build();
            }

            if (Boolean.TRUE.equals(existingSession.getIsRevoked()) || existingSession.getExpiresAt().isBefore(Instant.now())) {
                return ResponseEntity.status(401).build();
            }

            existingSession.setSessionStatus("ROTATED");
            sessionRepository.save(existingSession);

            UserAccount user = userRepository.findById(existingSession.getUserId()).orElse(null);
            if (user == null) {
                return ResponseEntity.status(401).build();
            }

            String tenantScope = resolveTenantScope(user);
            String newAccessToken = jwtTokenProvider.generateAccessToken(
                    user.getId().toString(), user.getEmail(), user.getRoles(), tenantScope
            );
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());

            UserSession newSession = UserSession.builder()
                    .userId(user.getId())
                    .refreshTokenHash(hashRefreshToken(newRefreshToken))
                    .ipAddress(extractClientIp(httpRequest))
                    .userAgent(extractUserAgent(httpRequest))
                    .deviceId(existingSession.getDeviceId())
                    .sessionStatus("ACTIVE")
                    .tokenGeneration(existingSession.getTokenGeneration() + 1)
                    .isRevoked(false)
                    .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                    .build();
            sessionRepository.save(newSession);

            return ResponseEntity.ok(AuthResponse.builder()
                    .userId(user.getId().toString())
                    .email(user.getEmail())
                    .roles(user.getRoles())
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .build());
        }

        return ResponseEntity.status(401).build();
    }

    private static final java.security.SecureRandom secureRandom = new java.security.SecureRandom();
    private static final long CHALLENGE_OTP_EXPIRY_MS = 5 * 60 * 1000;
    private static final long CHALLENGE_OTP_RESEND_COOLDOWN_SECONDS = 30;
    private static final int CHALLENGE_OTP_MAX_ATTEMPTS = 5;

    @PostMapping("/otp/send")
    public ResponseEntity<SendOtpResponse> sendOtp(@RequestBody SendOtpRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String phone = request.getPhone().trim();
        String digitsOnly = phone.replaceAll("[^0-9]", "");
        String mobile = digitsOnly.length() > 10 ? digitsOnly.substring(digitsOnly.length() - 10) : digitsOnly;
        String phoneHash = hashSha256(phone);
        String clientIp = extractClientIp(httpRequest);

        // Multi-Level Abuse Rate Limiting
        Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
        if (otpChallengeRepository.countRecentChallengesForPhone(phoneHash, tenMinutesAgo) >= 5) {
            return ResponseEntity.status(429).build();
        }
        if (otpChallengeRepository.countRecentChallengesForIp(clientIp, tenMinutesAgo) >= 20) {
            return ResponseEntity.status(429).build();
        }

        String code = String.format("%06d", 100000 + secureRandom.nextInt(900000));
        String providerSessionId = null;

        if (!twoFactorSmsClient.isEnabled()) {
            log.error("SMS gateway is unconfigured or disabled: failing closed in production.");
            return ResponseEntity.status(503).build();
        }

        try {
            boolean sent = twoFactorSmsClient.sendDirectTextSms(mobile, code);
            if (!sent) {
                providerSessionId = twoFactorSmsClient.sendAutoGenerateOtp(mobile);
                if (providerSessionId == null) {
                    log.error("SMS gateway failed to dispatch OTP challenge.");
                    return ResponseEntity.status(502).build();
                }
            }
        } catch (Exception e) {
            log.error("Error communicating with 2Factor SMS gateway", e);
            return ResponseEntity.status(502).build();
        }

        String challengeId = "otp_" + UUID.randomUUID() + "_" + mobile;
        String otpHash = hashSha256(code + "_commerceos_otp_salt");

        OtpChallengeEntity challenge = OtpChallengeEntity.builder()
                .challengeId(challengeId)
                .phoneHash(phoneHash)
                .otpHash(otpHash)
                .providerSessionId(providerSessionId)
                .attemptsLeft(CHALLENGE_OTP_MAX_ATTEMPTS)
                .expiresAt(Instant.now().plusMillis(CHALLENGE_OTP_EXPIRY_MS))
                .resendAvailableAt(Instant.now().plusSeconds(CHALLENGE_OTP_RESEND_COOLDOWN_SECONDS))
                .status("ACTIVE")
                .ipAddress(clientIp)
                .build();

        otpChallengeRepository.save(challenge);

        return ResponseEntity.ok(SendOtpResponse.builder()
                .message("OTP sent to " + phone)
                .challengeId(challengeId)
                .expiresInSeconds((int) (CHALLENGE_OTP_EXPIRY_MS / 1000))
                .resendAfterSeconds((int) CHALLENGE_OTP_RESEND_COOLDOWN_SECONDS)
                .build());
    }

    @PostMapping("/otp/verify")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<AuthResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        String challengeId = request.getChallengeId();
        String phone = request.getPhone() != null ? request.getPhone().trim() : "";
        String code = request.getOtpCode() != null ? request.getOtpCode().trim() : "";
        if (challengeId == null || challengeId.isBlank() || phone.isEmpty() || code.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Optional<OtpChallengeEntity> challengeOpt = otpChallengeRepository.findByChallengeIdForUpdate(challengeId);
        if (challengeOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        OtpChallengeEntity challenge = challengeOpt.get();
        String expectedPhoneHash = hashSha256(phone);

        if (!expectedPhoneHash.equals(challenge.getPhoneHash()) || !"ACTIVE".equals(challenge.getStatus())) {
            return ResponseEntity.status(401).build();
        }

        if (Instant.now().isAfter(challenge.getExpiresAt())) {
            challenge.setStatus("EXPIRED");
            otpChallengeRepository.save(challenge);
            return ResponseEntity.status(401).build();
        }

        if (challenge.getAttemptsLeft() <= 0) {
            challenge.setStatus("LOCKED");
            otpChallengeRepository.save(challenge);
            return ResponseEntity.status(429).build();
        }

        String submittedHash = hashSha256(code + "_commerceos_otp_salt");
        boolean matches = java.security.MessageDigest.isEqual(
                submittedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                challenge.getOtpHash().getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        if (!matches && challenge.getProviderSessionId() != null) {
            matches = twoFactorSmsClient.verifyAutoGeneratedOtp(challenge.getProviderSessionId(), code);
        }

        if (!matches) {
            int left = challenge.getAttemptsLeft() - 1;
            challenge.setAttemptsLeft(left);
            if (left <= 0) {
                challenge.setStatus("LOCKED");
            }
            otpChallengeRepository.save(challenge);
            return left <= 0 ? ResponseEntity.status(429).build() : ResponseEntity.status(401).build();
        }

        // Successfully verified
        challenge.setStatus("VERIFIED");
        challenge.setVerifiedAt(Instant.now());
        otpChallengeRepository.save(challenge);

        UserAccount user = userRepository.findByPhone(phone).orElseGet(() -> {
            UserAccount newUser = UserAccount.builder()
                    .phone(phone)
                    .email(phone.replaceAll("[^0-9]", "") + "@mobile.commerceos.io")
                    .fullName("Mobile Customer (" + phone + ")")
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .roles(Set.of("ROLE_CUSTOMER"))
                    .accountStatus("ACTIVE")
                    .phoneVerified(true)
                    .failedLoginAttempts(0)
                    .mfaEnabled(false)
                    .build();
            return userRepository.save(newUser);
        });

        if (!Boolean.TRUE.equals(user.getPhoneVerified())) {
            user.setPhoneVerified(true);
            userRepository.save(user);
        }

        String tenantScope = resolveTenantScope(user);
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getEmail(), user.getRoles(), tenantScope
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());

        return ResponseEntity.ok(AuthResponse.builder()
                .userId(user.getId().toString())
                .email(user.getEmail())
                .roles(user.getRoles())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build());
    }

    @PostMapping("/webauthn/challenge")
    public ResponseEntity<?> getWebAuthnChallenge(@RequestParam String email) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(
                Map.of(
                        "error", "NOT_IMPLEMENTED",
                        "message", "WebAuthn hardware key registration requires FIDO2 authenticator binding; use OTP / Password MFA."
                )
        );
    }

    public static class SendOtpRequest {
        private String phone;
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
    }

    public static class SendOtpResponse {
        private String message;
        private String challengeId;
        private Integer expiresInSeconds;
        private Integer resendAfterSeconds;

        public SendOtpResponse() {}
        public SendOtpResponse(String message, String challengeId, Integer expiresInSeconds, Integer resendAfterSeconds) {
            this.message = message;
            this.challengeId = challengeId;
            this.expiresInSeconds = expiresInSeconds;
            this.resendAfterSeconds = resendAfterSeconds;
        }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getChallengeId() { return challengeId; }
        public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
        public Integer getExpiresInSeconds() { return expiresInSeconds; }
        public void setExpiresInSeconds(Integer expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
        public Integer getResendAfterSeconds() { return resendAfterSeconds; }
        public void setResendAfterSeconds(Integer resendAfterSeconds) { this.resendAfterSeconds = resendAfterSeconds; }

        public static SendOtpResponseBuilder builder() { return new SendOtpResponseBuilder(); }
        public static class SendOtpResponseBuilder {
            private String message;
            private String challengeId;
            private Integer expiresInSeconds;
            private Integer resendAfterSeconds;
            SendOtpResponseBuilder() {}
            public SendOtpResponseBuilder message(String message) { this.message = message; return this; }
            public SendOtpResponseBuilder challengeId(String challengeId) { this.challengeId = challengeId; return this; }
            public SendOtpResponseBuilder expiresInSeconds(Integer expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; return this; }
            public SendOtpResponseBuilder resendAfterSeconds(Integer resendAfterSeconds) { this.resendAfterSeconds = resendAfterSeconds; return this; }
            public SendOtpResponse build() { return new SendOtpResponse(message, challengeId, expiresInSeconds, resendAfterSeconds); }
        }
    }

    public static class VerifyOtpRequest {
        private String challengeId;
        private String phone;
        private String otpCode;

        public String getChallengeId() { return challengeId; }
        public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getOtpCode() { return otpCode; }
        public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    }

    public static class RegisterRequest {
        private String email;
        private String fullName;
        private String phone;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RefreshTokenRequest {
        private String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class AuthResponse {
        private String userId;
        private String email;
        private Set<String> roles;
        private String accessToken;
        private String refreshToken;

        public AuthResponse() {}
        public AuthResponse(String userId, String email, Set<String> roles, String accessToken, String refreshToken) {
            this.userId = userId;
            this.email = email;
            this.roles = roles;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Set<String> getRoles() { return roles; }
        public void setRoles(Set<String> roles) { this.roles = roles; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

        public static AuthResponseBuilder builder() { return new AuthResponseBuilder(); }
        public static class AuthResponseBuilder {
            private String userId;
            private String email;
            private Set<String> roles;
            private String accessToken;
            private String refreshToken;
            AuthResponseBuilder() {}
            public AuthResponseBuilder userId(String userId) { this.userId = userId; return this; }
            public AuthResponseBuilder email(String email) { this.email = email; return this; }
            public AuthResponseBuilder roles(Set<String> roles) { this.roles = roles; return this; }
            public AuthResponseBuilder accessToken(String accessToken) { this.accessToken = accessToken; return this; }
            public AuthResponseBuilder refreshToken(String refreshToken) { this.refreshToken = refreshToken; return this; }
            public AuthResponse build() { return new AuthResponse(userId, email, roles, accessToken, refreshToken); }
        }
    }

    public static class MapResponse {
        private String challenge;
        private String rpId;

        public MapResponse() {}
        public MapResponse(String challenge, String rpId) {
            this.challenge = challenge;
            this.rpId = rpId;
        }

        public String getChallenge() { return challenge; }
        public void setChallenge(String challenge) { this.challenge = challenge; }
        public String getRpId() { return rpId; }
        public void setRpId(String rpId) { this.rpId = rpId; }

        public static MapResponseBuilder builder() { return new MapResponseBuilder(); }
        public static class MapResponseBuilder {
            private String challenge;
            private String rpId;
            MapResponseBuilder() {}
            public MapResponseBuilder challenge(String challenge) { this.challenge = challenge; return this; }
            public MapResponseBuilder rpId(String rpId) { this.rpId = rpId; return this; }
            public MapResponse build() { return new MapResponse(challenge, rpId); }
        }
    }

    public static class OtpRecord {
        private String phone;
        private String code;
        private String sessionId;
        private int attemptsLeft = CHALLENGE_OTP_MAX_ATTEMPTS;
        private long createdAt = System.currentTimeMillis();

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public int getAttemptsLeft() { return attemptsLeft; }
        public void setAttemptsLeft(int attemptsLeft) { this.attemptsLeft = attemptsLeft; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        static OtpRecord challenge(String phone, String code, String sessionId) {
            OtpRecord record = new OtpRecord();
            record.phone = phone;
            record.code = code;
            record.sessionId = sessionId;
            return record;
        }

        boolean matches(String input, TwoFactorSmsClient client) {
            if (sessionId != null && !sessionId.isBlank()) {
                try {
                    return client.verifyOtp(sessionId, input);
                } catch (Exception e) {
                    return false;
                }
            }
            return code != null && code.equals(input);
        }
    }
}

