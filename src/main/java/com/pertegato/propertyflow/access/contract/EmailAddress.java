package com.pertegato.propertyflow.access.contract;

import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailAddress {
    private static final Pattern ADDRESS = Pattern.compile(
            "[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?",
            Pattern.CASE_INSENSITIVE);
    private static final String INVALID_ADDRESS = "Eine gültige E-Mail-Adresse ist erforderlich.";

    private EmailAddress() {
    }

    public static String normalize(String email) {
        String address = email == null ? "" : email.strip();
        // Check ASCII syntax before Unicode lowercasing can turn characters such as K into ASCII.
        if (address.length() > 254 || !ADDRESS.matcher(address).matches()) {
            throw new IllegalArgumentException(INVALID_ADDRESS);
        }

        String localPart = address.substring(0, address.indexOf('@'));
        if (localPart.length() > 64 || localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")) {
            throw new IllegalArgumentException(INVALID_ADDRESS);
        }
        return address.toLowerCase(Locale.ROOT);
    }
}
