package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model;

public enum VerificationStepType {
    // Základní kroky – vždy se vytváří pro každou verifikaci
    PERSONAL_INFO,
    DOC_OCR,
    FACE_MATCH,
    LIVENESS,
    AML_SCREEN,
    // Volitelné kroky – vytváří se podle konfigurace šablony cesty
    EMAIL_VERIFICATION,
    PHONE_VERIFICATION,
    AML_QUESTIONNAIRE
}
