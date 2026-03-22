package com.francmatyas.uhk_thesis_automatic_kyc_api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DisplayField {
    String header();

    int order() default 0;

    DisplayFieldType type() default DisplayFieldType.STRING; // Podle potřeby lze rozšířit o další typy.

    boolean hidden() default false; // Volitelné: skryje pole v tabulce

    boolean sortable() default true; // Volitelné: umožní řazení pole v tabulce

    boolean filterable() default true; // Volitelné: umožní filtrování pole v tabulce

    String referenceKey() default ""; // Volitelné: odkaz na jiné pole v tabulce

    String width() default ""; // Volitelné: nastaví šířku sloupce

    /**
     * Řetězec ve stylu URI šablony, který lze vykreslit nahrazením
     * každého `{key}` hodnotou daného pole.
     * např. "/users/{id}" nebo "/{module}/{id}"
     */
    String referenceTemplate() default ""; // Volitelné: formátování referenčního pole
}
