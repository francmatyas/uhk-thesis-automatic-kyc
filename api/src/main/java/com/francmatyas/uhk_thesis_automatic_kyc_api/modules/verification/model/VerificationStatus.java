package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model;

public enum VerificationStatus {
    INITIATED,
    IN_PROGRESS,
    READY_FOR_AUTOCHECK,
    AUTO_PASSED,
    AUTO_FAILED,
    REQUIRES_REVIEW,
    APPROVED,
    REJECTED,
    EXPIRED
}