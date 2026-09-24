package com.meridian.transactions.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionMessageTest {

    @Test
    void account_not_found_message_names_the_account() {
        AccountNotFoundException ex = new AccountNotFoundException("ACC-4242");
        assertEquals("Account not found: ACC-4242", ex.getMessage());
        assertTrue(ex instanceof RuntimeException);
    }

    @Test
    void insufficient_funds_message_names_the_account() {
        InsufficientFundsException ex = new InsufficientFundsException("ACC-4242");
        assertEquals("Insufficient funds in account ACC-4242", ex.getMessage());
        assertTrue(ex instanceof RuntimeException);
    }
}
