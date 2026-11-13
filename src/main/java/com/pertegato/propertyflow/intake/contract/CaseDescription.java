package com.pertegato.propertyflow.intake.contract;

public final class CaseDescription {

    public static final int MAX_LENGTH = 2000;

    private CaseDescription() {
    }

    public static String normalizeLineEndings(String text) {
        if (text == null) {
            return null;
        }
        // Native form posts can encode a single textarea line break as CRLF.
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
