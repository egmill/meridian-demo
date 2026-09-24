package com.meridian.transactions.repository;

import com.meridian.transactions.model.Account;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory account store. Seeded with demo accounts on startup.
 */
@Repository
public class AccountRepository {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public AccountRepository() {
        save(new Account("ACC-1001", "Everyday Checking", 2500.00));
        save(new Account("ACC-1002", "High-Yield Savings", 10000.00));
        save(new Account("ACC-1003", "Vendor Payee Account", 0.00));
    }

    public Optional<Account> findById(String id) {
        return Optional.ofNullable(accounts.get(id));
    }

    public List<Account> findAll() {
        List<Account> all = new ArrayList<>(accounts.values());
        all.sort(Comparator.comparing(Account::getId));
        return all;
    }

    public Account save(Account account) {
        accounts.put(account.getId(), account);
        return account;
    }
}
