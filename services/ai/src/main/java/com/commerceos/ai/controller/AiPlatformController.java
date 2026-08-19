package com.commerceos.ai.controller;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI & Clinical Decision Support Assistive Controller.
 * IMPORTANT: All outputs from this service are assistive preliminary heuristics only.
 * Final pharmacy dispensing strictly requires licensed human pharmacist verification.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiPlatformController {

    @PostMapping("/prescription/ocr")
    public ResponseEntity<OcrResult> parsePrescriptionOcr(@RequestBody Map<String, String> request) {
        String imageUrl = request.getOrDefault("imageUrl", "");

        // No automated OCR without cloud vision model; honest state preserves clinical safety.
        OcrResult result = OcrResult.builder()
                .status(imageUrl == null || imageUrl.isBlank() ? "IMAGE_MISSING" : "PENDING_MANUAL_REVIEW")
                .doctorName(null)
                .doctorRegistrationNo(null)
                .extractedText("")
                .confidenceScore(null)
                .extractedMedicines(List.of())
                .disclaimer("Preliminary automated intake. Licensed pharmacist review mandatory before fulfillment.")
                .build();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/drug-interaction/check")
    public ResponseEntity<InteractionResult> checkDrugInteractions(@RequestBody List<String> salts) {
        boolean hasConflict = (salts.contains("Amoxicillin") && salts.contains("Methotrexate")) ||
                              (salts.contains("Warfarin") && salts.contains("Aspirin"));
        InteractionResult result = InteractionResult.builder()
                .hasInteraction(hasConflict)
                .highestSeverity(hasConflict ? "HIGH" : "NONE")
                .recommendation(hasConflict
                        ? "CRITICAL: Potential severe drug-drug interaction detected. Consult prescribing physician and licensed pharmacist before dispensing."
                        : "No preliminary interactions detected in baseline rule set. Pharmacist review required.")
                .disclaimer("Clinical decision support heuristic only; not a substitute for professional medical judgement.")
                .build();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/patient-allergy/check")
    public ResponseEntity<PatientAllergyCheckResult> checkPatientAllergies(@RequestBody PatientAllergyCheckRequest request) {
        List<String> userAllergies = request.getKnownAllergies() != null ? request.getKnownAllergies() : List.of();
        List<String> itemSalts = request.getItemSalts() != null ? request.getItemSalts() : List.of();

        boolean allergyFlag = false;
        String flaggedAllergy = null;

        for (String allergy : userAllergies) {
            for (String salt : itemSalts) {
                if (salt.toLowerCase().contains(allergy.toLowerCase()) || allergy.toLowerCase().contains(salt.toLowerCase())) {
                    allergyFlag = true;
                    flaggedAllergy = allergy;
                    break;
                }
            }
        }

        PatientAllergyCheckResult result = PatientAllergyCheckResult.builder()
                .hasAllergyConflict(allergyFlag)
                .flaggedAllergy(flaggedAllergy)
                .severity(allergyFlag ? "CRITICAL_ALLERGY" : "SAFE")
                .message(allergyFlag
                        ? "ALERT: Potential allergen match with " + flaggedAllergy + ". Pharmacist intervention mandatory!"
                        : "No baseline allergy conflict detected.")
                .disclaimer("Patient allergy checks are supplementary. Verify with patient medical records before dispensing.")
                .build();

        return ResponseEntity.ok(result);
    }

    @Data
    @Builder
    public static class OcrResult {
        private String status;
        private String doctorName;
        private String doctorRegistrationNo;
        private String extractedText;
        private Double confidenceScore;
        private List<ExtractedMedicine> extractedMedicines;
        private String disclaimer;
    }

    @Data
    @Builder
    public static class ExtractedMedicine {
        private String name;
        private String dosage;
        private Integer durationDays;
    }

    @Data
    @Builder
    public static class InteractionResult {
        private Boolean hasInteraction;
        private String highestSeverity;
        private String recommendation;
        private String disclaimer;
    }

    @Data
    public static class PatientAllergyCheckRequest {
        private List<String> knownAllergies;
        private List<String> itemSalts;
    }

    @Data
    @Builder
    public static class PatientAllergyCheckResult {
        private Boolean hasAllergyConflict;
        private String flaggedAllergy;
        private String severity;
        private String message;
        private String disclaimer;
    }
}
