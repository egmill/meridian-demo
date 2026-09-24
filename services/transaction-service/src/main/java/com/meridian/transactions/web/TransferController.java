package com.meridian.transactions.web;

import com.meridian.transactions.model.Transfer;
import com.meridian.transactions.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transfer create(@Valid @RequestBody TransferRequest request) {
        return transferService.transfer(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.memo());
    }
}
