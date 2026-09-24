package com.meridian.transactions.service;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountId) {
        super("Insufficient funds in account " + accountId);
    }
}
