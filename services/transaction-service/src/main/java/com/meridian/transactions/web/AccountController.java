package com.meridian.transactions.web;

import com.meridian.transactions.model.Account;
import com.meridian.transactions.repository.AccountRepository;
import com.meridian.transactions.service.AccountNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<Account> list() {
        return accountRepository.findAll();
    }

    @GetMapping("/{id}")
    public Account get(@PathVariable String id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
