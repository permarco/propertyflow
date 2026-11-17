package com.pertegato.propertyflow.caseprocessing.contract;

public class CaseActionException extends RuntimeException {

    private final String code;

    public CaseActionException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
