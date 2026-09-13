package com.commerceos.identity.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for the 2factor.in SMS-OTP gateway (session based).
 *
 * Send:   POST https://2factor.in/API/V1/{api_key}/SMS/{mobile}/AUTOGEN
 *         -> { "Status": "Success", "Details": "<session_id>" }
 * Verify: POST https://2factor.in/API/V1/{api_key}/SMS/VERIFY/{session_id}/{otp}
 *         -> { "Status": "Success", "Details": "OTP Matched" }
 */
@Service
public class TwoFactorSmsClient {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TwoFactorSmsClient.class);

    @Value("${app.sms.two-factor.api-key:}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean sendDirectTextSms(String mobile, String otpCode) throws IOException, InterruptedException {
        String digits = mobile == null ? "" : mobile.replaceAll("[^0-9]", "");
        String cleanMobile;
        if (digits.length() == 10) {
            cleanMobile = "91" + digits;
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            cleanMobile = digits;
        } else if (digits.length() > 10) {
            cleanMobile = "91" + digits.substring(digits.length() - 10);
        } else {
            cleanMobile = digits;
        }
        String url = String.format("https://2factor.in/API/V1/%s/SMS/%s/%s", apiKey, cleanMobile, otpCode);
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        log.info("2factor.in Direct Text SMS status={} body={}", response.statusCode(), body);
        return response.statusCode() == 200 && body != null && body.contains("\"Success\"");
    }

    public String sendAutoGenerateOtp(String mobile) throws IOException, InterruptedException {
        String digits = mobile == null ? "" : mobile.replaceAll("[^0-9]", "");
        String cleanMobile;
        if (digits.length() == 10) {
            cleanMobile = "91" + digits;
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            cleanMobile = digits;
        } else if (digits.length() > 10) {
            cleanMobile = "91" + digits.substring(digits.length() - 10);
        } else {
            cleanMobile = digits;
        }
        String url = String.format("https://2factor.in/API/V1/%s/SMS/%s/AUTOGEN/OTP1", apiKey, cleanMobile);
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        log.info("2factor.in AUTOGEN status={} body={}", response.statusCode(), body);
        if (response.statusCode() == 200 && body != null && body.contains("\"Success\"")) {
            return extractField(body, "Details");
        }
        log.error("2factor.in SMS gateway failed or invalid key: status={} body={}", response.statusCode(), body);
        return null;
    }

    public boolean verifyOtp(String sessionId, String otpInput) throws IOException, InterruptedException {
        String url = String.format("https://2factor.in/API/V1/%s/SMS/VERIFY/%s/%s", apiKey, sessionId, otpInput != null ? otpInput.trim() : "");
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        log.info("2factor.in VERIFY status={} body={}", response.statusCode(), body);
        return response.statusCode() == 200
                && body != null
                && body.contains("\"Success\"")
                && (body.contains("OTP Matched") || body.contains("OTP Valid"));
    }

    private String extractField(String json, String field) {
        if (json == null) return null;
        try {
            int idx = json.indexOf("\"" + field + "\"");
            if (idx < 0) return null;
            int colon = json.indexOf(':', idx);
            int q1 = json.indexOf('"', colon);
            int q2 = json.indexOf('"', q1 + 1);
            if (q1 < 0 || q2 < 0) return null;
            String value = json.substring(q1 + 1, q2).trim();
            return value.isEmpty() ? null : value;
        } catch (Exception e) {
            return null;
        }
    }
}