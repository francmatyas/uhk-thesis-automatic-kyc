package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import java.lang.annotation.*;

/**
 * Označuje koncový bod, který pro tenant uživatele vyžaduje aktivního tenanta.
 * Provider uživatelé jsou povoleni i bez tenanta.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireActiveTenant {
}
