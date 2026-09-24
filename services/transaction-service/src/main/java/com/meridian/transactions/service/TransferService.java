package com.meridian.transactions.service;

import com.meridian.transactions.audit.AuditClient;
import com.meridian.transactions.audit.AuditEvent;
import com.meridian.transactions.model.Account;
import com.meridian.transactions.model.Transfer;
import com.meridian.transactions.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final AuditClient auditClient;
    private final AtomicLong sequence = new AtomicLong(1000);

    public TransferService(AccountRepository accountRepository, AuditClient auditClient) {
        this.accountRepository = accountRepository;
        this.auditClient = auditClient;
    }

    /**
     * Moves funds between two accounts and records the transfer in the audit log.
     * The audit record is written before balances change, so a failed audit
     * write aborts the transfer.
     */
    public synchronized Transfer transfer(String fromAccountId, String toAccountId, double amount, String memo) {
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }

        Account from = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        Account to = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new AccountNotFoundException(toAccountId));

        if (from.getBalance() < amount) {
            throw new InsufficientFundsException(fromAccountId);
        }

        String reference = "TX-" + sequence.incrementAndGet();
        auditClient.record(new AuditEvent(
                "transaction-service",
                "TRANSFER",
                String.format("%s %s -> %s amount=%s memo=%s", reference, fromAccountId, toAccountId, amount, memo)));

        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);

        return new Transfer(reference, fromAccountId, toAccountId, amount, memo, Instant.now());
    }
}
