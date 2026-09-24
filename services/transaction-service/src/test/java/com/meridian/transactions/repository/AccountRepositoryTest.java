package com.meridian.transactions.repository;

import com.meridian.transactions.model.Account;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void seeds_three_demo_accounts_with_expected_ids_names_and_balances() {
        List<Account> all = repository.findAll();

        assertEquals(List.of("ACC-1001", "ACC-1002", "ACC-1003"),
                all.stream().map(Account::getId).toList());
        assertEquals(List.of("Everyday Checking", "High-Yield Savings", "Vendor Payee Account"),
                all.stream().map(Account::getName).toList());
        assertEquals(2500.00, all.get(0).getBalance(), 0.0);
        assertEquals(10000.00, all.get(1).getBalance(), 0.0);
        assertEquals(0.00, all.get(2).getBalance(), 0.0);
    }

    @Test
    void find_all_sorts_lexicographically_regardless_of_insertion_order() {
        repository.save(new Account("ACC-0001", "Zed", 1.00));
        repository.save(new Account("ACC-2000", "Alpha", 1.00));

        List<String> ids = repository.findAll().stream().map(Account::getId).toList();
        assertEquals(List.of("ACC-0001", "ACC-1001", "ACC-1002", "ACC-1003", "ACC-2000"), ids);
    }

    @Test
    void save_returns_the_same_instance_and_makes_it_findable() {
        Account account = new Account("ACC-7777", "Test Account", 12.34);

        Account saved = repository.save(account);

        assertSame(account, saved);
        assertSame(account, repository.findById("ACC-7777").orElseThrow());
        assertEquals(4, repository.findAll().size());
    }

    @Test
    void save_with_existing_id_replaces_the_account() {
        Account replacement = new Account("ACC-1001", "Renamed", 1.00);

        repository.save(replacement);

        Account found = repository.findById("ACC-1001").orElseThrow();
        assertSame(replacement, found);
        assertEquals("Renamed", found.getName());
        assertEquals(3, repository.findAll().size());
    }

    @Test
    void find_by_id_returns_live_instance_reflecting_balance_changes() {
        repository.findById("ACC-1003").orElseThrow().setBalance(99.99);

        assertEquals(99.99, repository.findById("ACC-1003").orElseThrow().getBalance(), 0.0);
    }

    @Test
    void find_all_returns_a_copy_that_does_not_affect_the_store() {
        List<Account> all = repository.findAll();
        all.clear();

        assertEquals(3, repository.findAll().size());
    }

    @Test
    void find_by_id_is_case_sensitive() {
        assertTrue(repository.findById("acc-1001").isEmpty());
    }
}
