package com.francmatyas.uhk_thesis_automatic_kyc_api.model;

import com.francmatyas.uhk_thesis_automatic_kyc_api.annotations.DisplayFieldType;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.Optional;

public class Column {
    @Getter
    private final String header;
    @Getter
    private final int order;
    @Getter
    private final DisplayFieldType type;
    private final Field field;
    @Getter
    private final String accessorKey;
    @Getter
    private final boolean hidden;
    @Getter
    private final boolean sortable;
    @Getter
    private final boolean filterable;
    @Getter
    private final String referenceKey;
    @Getter
    private final String referenceTemplate;
    @Getter
    private final String width;
    @Getter
    private final boolean copyable;

    public Column(String header, int order, DisplayFieldType type, boolean hidden, boolean sortable, boolean filterable, String referenceKey, String referenceTemplate, String width, boolean copyable, Field field) {
        this.header = header;
        this.order = order;
        this.type = type;
        this.field = field;
        this.hidden = hidden;
        this.sortable = sortable;
        this.filterable = filterable;
        this.referenceKey = referenceKey;
        this.referenceTemplate = referenceTemplate;
        this.width = width;
        this.copyable = copyable;
        this.accessorKey = field.getName();
        this.field.setAccessible(true);
    }

    public Object getValue(Object dto) {
        try {
            return field.get(dto);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Pokud referenceTemplate není prázdný, nahraď každý `{key}`
     * odpovídající hodnotou pole v DTO.
     */
    public Optional<String> getReferenceUri(Object dto) {
        if (referenceTemplate.isBlank()) {
            return Optional.empty();
        }
        String uri = referenceTemplate;

        try {
            Field refField = dto.getClass().getDeclaredField(referenceKey);
            refField.setAccessible(true);
            Object val = refField.get(dto);
            uri = uri.replace("{" + referenceKey + "}", val == null ? "" : val.toString());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(
                    "Failed to resolve reference key `" + referenceKey + "` on " + dto.getClass(), e);
        }
        return Optional.of(uri);
    }
}
