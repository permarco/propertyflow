package com.pertegato.propertyflow.access.contract;

import java.util.Objects;

public final class StaffAuthenticationException extends RuntimeException {

    private final StaffAuthenticationErrorCode code;

    public StaffAuthenticationException(StaffAuthenticationErrorCode code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    public StaffAuthenticationErrorCode code() {
        return code;
    }
}
