package com.commerceos.identity.controller;

import com.commerceos.identity.domain.UserAccount;
import com.commerceos.identity.repository.UserAccountRepository;
import com.commerceos.identity.security.JwtAuthValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth/mfa")
public class MfaController {

    private final UserAccountRepository userRepository;
    private final JwtAuthValidator jwtAuthValidator;
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int TIME_STEP_SECONDS = 30;

    public MfaController(UserAccountRepository userRepository, JwtAuthValidator jwtAuthValidator) {
        this.userRepository = userRepository;
        this.jwtAuthValidator = jwtAuthValidator;
    }

    private boolean isAuthorizedUserOrAdmin(String authHeader, String targetUserId) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = jwtAuthValidator.authenticate(authHeader);
        if (principalOpt.isEmpty()) {
            return false;
        }
        return principalOpt.get().isOwnerOrAdmin(targetUserId);
    }

    private String hashBackupCode(String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupMfa(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam String userId) {
        if (!isAuthorizedUserOrAdmin(authHeader, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: MFA setup requires authenticated user ownership.");
        }

        return userRepository.findById(UUID.fromString(userId)).map(user -> {
            // Generate crypto-random 20-byte base32/base64 secret per user
            byte[] secretBytes = new byte[20];
            secureRandom.nextBytes(secretBytes);
            String secret = Base64.getEncoder().withoutPadding().encodeToString(secretBytes);

            // Generate raw backup codes for display to user once, and hash them for persistence
            List<String> plainBackupCodes = new ArrayList<>();
            List<String> hashedBackupCodes = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                int code = 100000 + secureRandom.nextInt(900000);
                String codeStr = String.valueOf(code);
                plainBackupCodes.add(codeStr);
                hashedBackupCodes.add(hashBackupCode(codeStr));
            }

            user.setMfaSecretKey(secret);
            userRepository.save(user);

            String qrCodeUrl = "otpauth://totp/CommerceOS:" + user.getEmail() + "?secret=" + secret + "&issuer=CommerceOS";

            return ResponseEntity.ok(MfaSetupResponse.builder()
                    .secretKey(secret)
                    .qrCodeUrl(qrCodeUrl)
                    .backupCodes(plainBackupCodes)
                    .build());
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/verify")
    public ResponseEntity<String> verifyMfa(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam String userId,
            @RequestParam String totpCode) {
        if (!isAuthorizedUserOrAdmin(authHeader, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: MFA verification requires authenticated user ownership.");
        }

        return userRepository.findById(UUID.fromString(userId)).map(user -> {
            String secret = user.getMfaSecretKey();
            if (secret == null || secret.isBlank()) {
                return ResponseEntity.badRequest().body("MFA_NOT_INITIALIZED");
            }

            // Verify TOTP RFC 6238 with +/- 1 clock skew window
            long currentWindow = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
            boolean isValid = false;

            for (long i = -1; i <= 1; i++) {
                if (validateTotp(secret, currentWindow + i, totpCode)) {
                    isValid = true;
                    break;
                }
            }

            if (isValid) {
                user.setMfaEnabled(true);
                userRepository.save(user);
                return ResponseEntity.ok("MFA_ENABLED_SUCCESSFULLY");
            } else {
                return ResponseEntity.status(401).body("INVALID_MFA_CODE");
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    private boolean validateTotp(String base64Secret, long timeStep, String code) {
        try {
            byte[] key = Base64.getDecoder().decode(base64Secret);
            byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "RAW"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0xF;
            long truncatedHash = 0;
            for (int i = 0; i < 4; ++i) {
                truncatedHash <<= 8;
                truncatedHash |= (hash[offset + i] & 0xFF);
            }
            truncatedHash &= 0x7FFFFFFF;
            truncatedHash %= 1000000;

            String expectedCode = String.format("%06d", truncatedHash);
            return expectedCode.equals(code.trim());
        } catch (Exception e) {
            return false;
        }
    }

    public static class MfaSetupResponse {
        private String secretKey;
        private String qrCodeUrl;
        private List<String> backupCodes;

        public MfaSetupResponse() {}

        public MfaSetupResponse(String secretKey, String qrCodeUrl, List<String> backupCodes) {
            this.secretKey = secretKey;
            this.qrCodeUrl = qrCodeUrl;
            this.backupCodes = backupCodes;
        }

        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

        public String getQrCodeUrl() { return qrCodeUrl; }
        public void setQrCodeUrl(String qrCodeUrl) { this.qrCodeUrl = qrCodeUrl; }

        public List<String> getBackupCodes() { return backupCodes; }
        public void setBackupCodes(List<String> backupCodes) { this.backupCodes = backupCodes; }

        public static MfaSetupResponseBuilder builder() {
            return new MfaSetupResponseBuilder();
        }

        public static class MfaSetupResponseBuilder {
            private String secretKey;
            private String qrCodeUrl;
            private List<String> backupCodes;

            MfaSetupResponseBuilder() {}

            public MfaSetupResponseBuilder secretKey(String secretKey) { this.secretKey = secretKey; return this; }
            public MfaSetupResponseBuilder qrCodeUrl(String qrCodeUrl) { this.qrCodeUrl = qrCodeUrl; return this; }
            public MfaSetupResponseBuilder backupCodes(List<String> backupCodes) { this.backupCodes = backupCodes; return this; }

            public MfaSetupResponse build() {
                return new MfaSetupResponse(secretKey, qrCodeUrl, backupCodes);
            }
        }
    }
}
