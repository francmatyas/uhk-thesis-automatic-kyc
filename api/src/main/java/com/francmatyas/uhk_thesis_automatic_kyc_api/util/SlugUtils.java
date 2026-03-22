package com.francmatyas.uhk_thesis_automatic_kyc_api.util;

import java.text.Normalizer;

public final class SlugUtils {

    private SlugUtils() {
        // Utility třída
    }

    /**
     * Převede řetězec na URL-friendly slug odstraněním diakritiky,
     * převodem na malá písmena a nahrazením mezer pomlčkami.
     *
     * @param input vstupní řetězec
     * @return slugifikovaný řetězec
     */
    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // Normalizace do NFD (dekomponovaný tvar) a odstranění diakritiky
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutDiacritics = normalized.replaceAll("\\p{M}", "");

        // Převod na malá písmena a nahrazení whitespace znaků pomlčkami
        return withoutDiacritics
                .toLowerCase()
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-+", "-");
    }
}
