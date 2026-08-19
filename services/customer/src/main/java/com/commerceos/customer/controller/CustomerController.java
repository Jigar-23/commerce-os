package com.commerceos.customer.controller;

import com.commerceos.customer.domain.CustomerAddress;
import com.commerceos.customer.domain.CustomerProfile;
import com.commerceos.customer.repository.CustomerAddressRepository;
import com.commerceos.customer.repository.CustomerProfileRepository;
import com.commerceos.customer.security.JwtAuthValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerProfileRepository profileRepository;
    private final CustomerAddressRepository addressRepository;
    private final JwtAuthValidator jwtAuthValidator;

    private boolean isAuthorizedCustomerOrAdmin(String authHeader, UUID targetCustomerId) {
        Optional<JwtAuthValidator.AuthenticatedPrincipal> principalOpt = jwtAuthValidator.authenticate(authHeader);
        if (principalOpt.isEmpty()) {
            return false;
        }
        return principalOpt.get().isOwnerOrAdmin(targetCustomerId.toString());
    }

    @GetMapping("/{identityId}")
    public ResponseEntity<CustomerProfile> getProfileByIdentityId(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID identityId
    ) {
        if (!isAuthorizedCustomerOrAdmin(authHeader, identityId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return profileRepository.findByIdentityId(identityId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody CustomerProfile profile
    ) {
        if (profile.getIdentityId() != null && !isAuthorizedCustomerOrAdmin(authHeader, profile.getIdentityId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Profile creation requires matching authenticated principal.");
        }
        return ResponseEntity.ok(profileRepository.save(profile));
    }

    @GetMapping("/{customerId}/addresses")
    public ResponseEntity<List<CustomerAddress>> getCustomerAddresses(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID customerId
    ) {
        if (!isAuthorizedCustomerOrAdmin(authHeader, customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(addressRepository.findByCustomerProfileId(customerId));
    }

    @PostMapping("/{customerId}/addresses")
    public ResponseEntity<?> addAddress(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID customerId,
            @RequestBody CustomerAddress address
    ) {
        if (!isAuthorizedCustomerOrAdmin(authHeader, customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: Address creation requires authenticated customer ownership.");
        }

        // Strict Geocoding Requirement: Zero Synthetic / Random Coordinate Fabrication
        if (address.getLatitude() == null || address.getLongitude() == null ||
            Double.isNaN(address.getLatitude()) || Double.isNaN(address.getLongitude())) {
            return ResponseEntity.badRequest().body("GEOCODING_REQUIRED: Explicit, verified latitude and longitude are strictly required.");
        }

        return profileRepository.findById(customerId).map(profile -> {
            address.setCustomerProfile(profile);
            if (Boolean.TRUE.equals(address.getIsDefaultShipping())) {
                List<CustomerAddress> existing = addressRepository.findByCustomerProfileId(customerId);
                for (CustomerAddress a : existing) {
                    a.setIsDefaultShipping(false);
                    a.setIsDefault(false);
                }
                addressRepository.saveAll(existing);
            }
            return ResponseEntity.ok(addressRepository.save(address));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{customerId}/addresses/{addressId}/default-shipping")
    public ResponseEntity<CustomerAddress> setDefaultShippingAddress(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID customerId,
            @PathVariable UUID addressId
    ) {
        if (!isAuthorizedCustomerOrAdmin(authHeader, customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<CustomerAddress> addresses = addressRepository.findByCustomerProfileId(customerId);
        CustomerAddress target = null;
        for (CustomerAddress a : addresses) {
            if (a.getId().equals(addressId)) {
                a.setIsDefaultShipping(true);
                a.setIsDefault(true);
                target = a;
            } else {
                a.setIsDefaultShipping(false);
                a.setIsDefault(false);
            }
        }
        if (target != null) {
            addressRepository.saveAll(addresses);
            return ResponseEntity.ok(target);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{customerId}/gdpr-erase")
    public ResponseEntity<?> eraseCustomerPii(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID customerId
    ) {
        if (!isAuthorizedCustomerOrAdmin(authHeader, customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN: GDPR erasure requires authenticated subject ownership.");
        }
        return profileRepository.findById(customerId).map(profile -> {
            String pseudonym = "ANONYMIZED_GDPR_" + UUID.randomUUID().toString().substring(0, 8);
            profile.setFullName("GDPR Erased User");
            profile.setEmail(pseudonym.toLowerCase() + "@anonymized.commerceos.io");
            profile.setPhone(null);
            profile.setKnownAllergiesJson("[]");
            profile.setChronicConditionsJson("[]");
            profile.setStatus("ERASED_GDPR");

            // Remove address records
            List<CustomerAddress> addresses = addressRepository.findByCustomerProfileId(customerId);
            addressRepository.deleteAll(addresses);

            profileRepository.save(profile);

            return ResponseEntity.ok(GdprErasureResponse.builder()
                    .customerId(customerId.toString())
                    .status("ERASED_GDPR")
                    .pseudonymHash(pseudonym)
                    .erasedAt(java.time.Instant.now().toString())
                    .message("Customer PII successfully erased under GDPR / DPDP right-to-be-forgotten protocol.")
                    .build());
        }).orElse(ResponseEntity.notFound().build());
    }

    @lombok.Data
    @lombok.Builder
    public static class GdprErasureResponse {
        private String customerId;
        private String status;
        private String pseudonymHash;
        private String erasedAt;
        private String message;
    }
}
