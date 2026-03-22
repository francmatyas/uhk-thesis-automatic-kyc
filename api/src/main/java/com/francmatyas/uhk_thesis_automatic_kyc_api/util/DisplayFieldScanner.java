package com.francmatyas.uhk_thesis_automatic_kyc_api.util;

import com.francmatyas.uhk_thesis_automatic_kyc_api.annotations.DisplayField;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Column;

import java.util.*;

public class DisplayFieldScanner {
    /**
     * Načte všechna pole třídy `clazz` anotovaná @DisplayField,
     * pro každé vytvoří Column a vrátí je seřazené podle `order()`.
     */
    public static List<Column> getColumns(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(DisplayField.class))
                .map(f -> {
                    DisplayField ann = f.getAnnotation(DisplayField.class);
                    return new Column(
                            ann.header(),
                            ann.order(),
                            ann.type(),
                            ann.hidden(),
                            ann.sortable(),
                            ann.filterable(),
                            ann.referenceKey(),
                            ann.referenceTemplate(),
                            ann.width(),
                            f
                    );
                })
                .sorted(Comparator.comparingInt(Column::getOrder))
                .toList();
    }

    /**
     * Pro daný seznam DTO a předem načtené sloupce vrátí datové řádky
     * jako List<List<Object>> ve stejném pořadí sloupců.
     */
    public static List<List<Object>> getDataRows(List<?> dtos, List<Column> columns) {
        return dtos.stream()
                .map(dto -> columns.stream()
                        .map(col -> col.getValue(dto))
                        .toList()
                )
                .toList();
    }

    public static List<Map<String, Object>> getDataMaps(List<?> dtos, List<Column> columns) {
        return dtos.stream()
                .map(dto -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (Column col : columns) {
                        row.put(col.getAccessorKey(), col.getValue(dto));
                    }
                    return row;
                })
                .toList();
    }
}
