package com.meridian.transactions.model;

import com.meridian.transactions.audit.AuditEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ModelTest {

    @Test
    void account_exposes_constructor_values_and_mutable_balance() {
        Account account = new Account("ACC-4242", "Test", 10.50);

        assertEquals("ACC-4242", account.getId());
        assertEquals("Test", account.getName());
        assertEquals(10.50, account.getBalance(), 0.0);

        account.setBalance(0.00);
        assertEquals(0.00, account.getBalance(), 0.0);
        assertEquals("ACC-4242", account.getId());
    }

    @Test
    void transfer_record_holds_all_fields_and_has_value_equality() {
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        Transfer a = new Transfer("TX-1", "ACC-1", "ACC-2", 1.25, "m", at);
        Transfer b = new Transfer("TX-1", "ACC-1", "ACC-2", 1.25, "m", at);

        assertEquals("TX-1", a.reference());
        assertEquals("ACC-1", a.fromAccountId());
        assertEquals("ACC-2", a.toAccountId());
        assertEquals(1.25, a.amount(), 0.0);
        assertEquals("m", a.memo());
        assertEquals(at, a.createdAt());
        assertEquals(a, b);
        assertNotEquals(a, new Transfer("TX-2", "ACC-1", "ACC-2", 1.25, "m", at));
    }

    @Test
    void audit_event_record_holds_all_fields() {
        AuditEvent event = new AuditEvent("actor", "ACTION", "details");

        assertEquals("actor", event.actor());
        assertEquals("ACTION", event.action());
        assertEquals("details", event.details());
        assertEquals(event, new AuditEvent("actor", "ACTION", "details"));
    }
}
