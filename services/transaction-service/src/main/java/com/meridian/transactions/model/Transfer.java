package com.meridian.transactions.model;

import java.time.Instant;

public record Transfer(
        String reference,
        String fromAccountId,
        String toAccountId,
        double amount,
        String memo,
        Instant createdAt) {
}
