package com.pertegato.propertyflow.caseprocessing.contract;

import java.util.List;

public record StaffCasePage(List<StaffCaseSummary> rows, long total, int page, int totalPages) {
    public StaffCasePage {
        rows = List.copyOf(rows);
    }
}
