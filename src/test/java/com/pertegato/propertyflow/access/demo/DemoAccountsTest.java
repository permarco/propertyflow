package com.pertegato.propertyflow.access.demo;

import com.pertegato.propertyflow.access.demo.DemoAccounts.Account;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoAccountsTest {
    @Test
    void configuredAddressResolvesTheSameAccountByEmailAndSessionId() {
        DemoAccounts accounts = new DemoAccounts(" STAFF@EXAMPLE.TEST ");
        Account account = accounts.findActiveByEmail("Staff@example.test").orElseThrow();

        assertEquals("staff@example.test", account.email());
        assertEquals(account, accounts.findActiveById(account.id()).orElseThrow());
        assertTrue(accounts.findActiveByEmail("other@example.test").isEmpty());
        assertTrue(accounts.findActiveById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void blankConfigurationLeavesStaffAccessDisabled() {
        DemoAccounts accounts = new DemoAccounts(" ");

        assertTrue(accounts.findActiveByEmail("staff@example.test").isEmpty());
        assertTrue(accounts.findActiveById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void malformedConfiguredAddressIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DemoAccounts("invalid address"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"m\u0131eter@example.test", "\u212Aonto@example.test"})
    void accountAddressRejectsUnicodeLookalikes(String address) {
        DemoAccounts accounts = new DemoAccounts("");
        assertThrows(IllegalArgumentException.class, () -> accounts.activateVerifiedTenant(address));
    }
}
