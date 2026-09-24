package com.meridian.transactions.repository;

import com.meridian.transactions.model.Account;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountRepositoryTest {

    private final AccountRepository repository = new AccountRepository();

    @Test
    void finds_seeded_account_by_id() {
        Account account = repository.findById("ACC-1001").orElseThrow();
        assertEquals("Everyday Checking", account.getName());
    }

    @Test
    void returns_empty_for_unknown_account() {
        assertTrue(repository.findById("ACC-9999").isEmpty());
    }

    @Test
    void lists_accounts_sorted_by_id() {
        assertEquals("ACC-1001", repository.findAll().get(0).getId());
        assertEquals(3, repository.findAll().size());
    }
}
