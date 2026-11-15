package com.pertegato.propertyflow.caseprocessing.contract;

public record StaffCaseQuery(String query, String urgency, boolean includeClosed, String sort, boolean descending, int page) {
}
