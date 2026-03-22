package com.francmatyas.uhk_thesis_automatic_kyc_api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TableDTO {
    private List<Column> columns;
    private List<Map<String, Object>> rows;
    private int pageNumber;
    private int pageSize;
    private int totalPages;
    private long totalElements;
}
