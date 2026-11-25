package com.pertegato.propertyflow.access.contract;

public enum McpScope {
    TENANT_CASES("tenant:cases:read", false, "Eigene Fälle lesen"),
    TENANT_MESSAGES("tenant:messages:write", false, "Ergänzungen vorbereiten"),
    TENANT_INTAKE("tenant:cases:create", false, "Neue Meldungen vorbereiten"),
    STAFF_CASES("staff:cases:read", true, "Fälle der Bewirtschaftung lesen"),
    STAFF_MESSAGES("staff:messages:write", true, "Externe Fallnachrichten vorbereiten"),
    STAFF_TRANSITIONS("staff:cases:transition", true, "Statusänderungen vorbereiten");

    private final String value;
    private final boolean staff;
    private final String label;

    McpScope(String value, boolean staff, String label) {
        this.value = value;
        this.staff = staff;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public boolean staff() {
        return staff;
    }

    public String label() {
        return label;
    }
}
