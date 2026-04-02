package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditActorType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogCommand;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto.CompleteDocumentUploadRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.dto.FlowPresignRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.service.DocumentService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.FlowIdDocumentRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.FlowLivenessRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.FlowPersonalInfoRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.FlowSendOtpRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.FlowVerifyOtpRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service.VerificationService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_otp.model.OtpType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_otp.service.VerificationOtpService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.service.VerificationStepService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/flow/verify/v1")
@RequiredArgsConstructor
public class FlowController {

    private final VerificationService verificationService;
    private final DocumentService documentService;
    private final VerificationOtpService verificationOtpService;
    private final VerificationStepService verificationStepService;
    private final AuditLogService auditLogService;

    @GetMapping("/{token}")
    public ResponseEntity<?> getFlow(@PathVariable String token) {
        try {
            Verification v = resolveActiveFlow(token);
            JsonNode config = v.getJourneyTemplate() != null ? v.getJourneyTemplate().getConfigJson() : null;
            var steps = verificationStepService.findAllByVerification(v.getId()).stream()
                    .map(s -> Map.of("stepType", s.getStepType().name(), "status", s.getStatus().name()))
                    .toList();
            return ResponseEntity.ok(Map.of(
                    "verificationId", v.getId(),
                    "status", v.getStatus(),
                    "expiresAt", v.getExpiresAt() != null ? v.getExpiresAt().toString() : "",
                    "journeyConfig", config != null ? config : Map.of(),
                    "steps", steps
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (FlowGoneException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{token}/email/send-code")
    public ResponseEntity<?> sendEmailCode(
            @PathVariable String token,
            @Valid @RequestBody FlowSendOtpRequest req,
            HttpServletRequest httpReq) {
        try {
            Verification v = resolveActiveFlow(token);
            String rawCode = verificationOtpService.generate(v, OtpType.EMAIL, req.getContact(), null);
            auditFlow(httpReq, v, "EMAIL_OTP_SENT", AuditResult.SUCCESS, null);
            return ResponseEntity.ok(Map.of("code", rawCode));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (FlowGoneException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{token}/email/verify-code")
    public ResponseEntity<?> verifyEmailCode(
            @PathVariable String token,
            @Valid @RequestBody FlowVerifyOtpRequest req,
            HttpServletRequest httpReq) {
        try {
            Verification v = resolveActiveFlow(token);
            boolean verified = verificationOtpService.verify(v, OtpType.EMAIL, req.getCode());
            auditFlow(httpReq, v, "EMAIL_OTP_VERIFIED",
                    verified ? AuditResult.SUCCESS : AuditResult.FAILURE, null);
            return ResponseEntity.ok(Map.of("verified", verified));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (FlowGoneException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        } catch (VerificationOtpService.OtpException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{token}/phone/send-code")
    public ResponseEntity<?> sendPhoneCode(
            @PathVariable String token,
            @Valid @RequestBody FlowSendOtpRequest req,
            HttpServletRequest httpReq) {
        try {
            Verification v = resolveActiveFlow(token);
            String rawCode = verificationOtpService.generate(v, OtpType.PHONE, req.getContact(), req.getDialCode());
            auditFlow(httpReq, v, "PHONE_OTP_SENT", AuditResult.SUCCESS, null);
            return ResponseEntity.ok(Map.of("code", rawCode));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (FlowGoneException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{token}/phone/verify-code")
    public ResponseEntity<?> verifyPhoneCode(
            @PathVariable String token,
            @Valid @RequestBody FlowVerifyOtpRequest req,
            HttpServletRequest httpReq) {
        try {
            Verification v = resolveActiveFlow(token);
            boolean verified = verificationOtpService.verify(v, OtpType.PHONE, req.getCode());
            auditFlow(httpReq, v, "PHONE_OTP_VERIFIED",
                    verified ? AuditResult.SUCCESS : AuditResult.FAILURE, null);
            return ResponseEntity.ok(Map.of("verified", verified));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (FlowGoneException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        } catch (VerificationOtpService.OtpException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{token}/personal-info")
    public ResponseEntity<?> savePersonalInfo(
            @PathVariable String token,
            @Valid @RequestBody FlowPersonalInfoRequest req,
            HttpServletRequest httpReq) {
        try {
            Verification v = resolveActiveFlow(token);
            verificationService.savePersonalInfo(v, req.getFirstName(), req.getLastName(), req.getDateOfBirth());
            auditFlow(httpReq, v, "PERSONAL_INFO_SAVED", AuditResult.SUCCESS, null);
            return ResponseEntity.ok(Map.of("saved", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (FlowGoneException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{token}/id-document")
    public ResponseEntity<?> submitIdDocument(
            @PathVariable String token,
            @Valid @RequestBody FlowIdDocumentRequest req,
            HttpServletRequest httpReq) {
        try {
            Verification v = resolveActiveFlow(token);
            verificationService.submitIdDocument(v, req.getDocumentType(),
                    req.getFrontDocumentId(), req.getBackDocumentId());
            auditFlow(httpReq, v, "DOCUMENT_SUBMITTED", AuditResult.SUCCESS,
                    Map.of("documentType", req.getDocumentType().name()));
            return ResponseEntity.ok(Map.of("dispatched", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (FlowGoneException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
    }

    @PostMapping("/{token}/liveness")
    public ResponseEntity<?> submitLiveness(
            @PathVariable String token,
            @Valid @RequestBody FlowLivenessRequest req,
            HttpServletRequest httpReq) {
        try {
            Verification v = resolveActiveFlow(token);
            verificationService.submitLiveness(v, req.getImages());
            auditFlow(httpReq, v, "LIVENESS_SUBMITTED", AuditResult.SUCCESS,
                    Map.of("imageCount", req.getImages().size()));
            return ResponseEntity.ok(Map.of("dispatched", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (FlowGoneException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
    }

    @PostMapping("/{token}/aml")
    public ResponseEntity<?> submitAml(
            @PathVariable String token,
            @RequestBody JsonNode answers,
            HttpServletRequest httpReq) {
        try {
            Verification v = resolveActiveFlow(token);
            verificationService.submitAml(v, answers);
            auditFlow(httpReq, v, "AML_SUBMITTED", AuditResult.SUCCESS, null);
            return ResponseEntity.ok(Map.of("saved", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (FlowGoneException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{token}/documents/presign")
    public ResponseEntity<?> presignDocumentUpload(
            @PathVariable String token,
            @Valid @RequestBody FlowPresignRequest req) {
        try {
            Verification v = resolveActiveFlow(token);
            return ResponseEntity.ok(documentService.presignFlowUpload(
                    v.getId(), v.getTenantId(), req.getFilename(), req.getContentType(), req.getCategory()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (FlowGoneException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{token}/documents/{documentId}/complete")
    public ResponseEntity<?> completeDocumentUpload(
            @PathVariable String token,
            @PathVariable UUID documentId,
            @RequestBody(required = false) CompleteDocumentUploadRequest req) {
        try {
            Verification v = verificationService.findByToken(token);
            return ResponseEntity.ok(documentService.completeFlowUpload(documentId, v.getId(), req));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
        }
    }

    @PostMapping("/{token}/finalize")
    public ResponseEntity<?> finalize(@PathVariable String token, HttpServletRequest httpReq) {
        try {
            Verification v = verificationService.finalizeFlow(token);
            auditFlow(httpReq, v, "VERIFICATION_FINALIZE", AuditResult.SUCCESS,
                    Map.of("newStatus", v.getStatus().name()));
            return ResponseEntity.ok(verificationService.toDetailResponse(v, false));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------

    private Verification resolveActiveFlow(String token) {
        Verification v = verificationService.findByToken(token);
        if (verificationService.expireIfFlowTimedOut(v)) {
            throw new FlowGoneException("verification_expired");
        }
        if (v.getStatus() == VerificationStatus.EXPIRED) {
            throw new FlowGoneException("verification_expired");
        }
        if (v.getStatus() != VerificationStatus.INITIATED && v.getStatus() != VerificationStatus.IN_PROGRESS) {
            throw new FlowGoneException("verification_already_submitted");
        }
        return v;
    }

    private void auditFlow(HttpServletRequest httpReq, Verification v, String action,
                           AuditResult result, Map<String, Object> metadata) {
        try {
            String ip = httpReq.getHeader("CF-Connecting-IP");
            if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
            auditLogService.log(new AuditLogCommand(
                    v.getTenantId(), null, AuditActorType.SYSTEM, null,
                    "VERIFICATION", v.getId().toString(),
                    action, null, null, metadata,
                    ip, httpReq.getHeader("User-Agent"), null, null,
                    result, null
            ));
        } catch (Exception ignored) {}
    }

    private static class FlowGoneException extends RuntimeException {
        FlowGoneException(String msg) {
            super(msg);
        }
    }
}