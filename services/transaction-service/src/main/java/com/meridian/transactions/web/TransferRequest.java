package com.meridian.transactions.web;

import jakarta.validation.constraints.NotBlank;

public record TransferRequest(
        @NotBlank String fromAccountId,
        @NotBlank String toAccountId,
        double amount,
        String memo) {
}
