package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model;

public enum CheckType {
    // Základní kontroly – spouští se vždy
    PERSONAL_INFO,
    DOC_OCR,
    DOC_DATA_MATCH,
    FACE_MATCH,
    LIVENESS,
    SANCTIONS,
    PEP,
    // Volitelné kontroly – podle kroků šablony cesty
    EMAIL_VERIFICATION,
    PHONE_VERIFICATION,
    AML_QUESTIONNAIRE
}
