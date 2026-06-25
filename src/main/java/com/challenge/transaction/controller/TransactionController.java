package com.challenge.transaction.controller;

import com.challenge.transaction.dto.TransactionRequest;
import com.challenge.transaction.dto.TransactionResponse;
import com.challenge.transaction.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> authorizeTransaction(
            @PathVariable String transactionId,
            @RequestBody TransactionRequest request) {

        TransactionResponse response = transactionService.authorizeTransaction(transactionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
