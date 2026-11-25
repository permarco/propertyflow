package com.pertegato.propertyflow.access.contract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailAddressTest {

    @Test
    void normalizesSpacesAndLetterCase() {
        assertEquals("staff@example.test", EmailAddress.normalize(" STAFF@EXAMPLE.TEST "));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "invalid address", "m\u0131eter@example.test", "\u212Aonto@example.test"})
    void rejectsMalformedAddressesAndUnicodeLookalikes(String address) {
        assertThrows(IllegalArgumentException.class, () -> EmailAddress.normalize(address));
    }
}
