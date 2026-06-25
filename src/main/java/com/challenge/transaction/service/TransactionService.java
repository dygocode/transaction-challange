package com.challenge.transaction.service;

import com.challenge.transaction.dto.TransactionRequest;
import com.challenge.transaction.dto.TransactionResponse;
import com.challenge.transaction.exception.InsufficientBalanceException;
import com.challenge.transaction.exception.InvalidTransactionTypeException;
import com.challenge.transaction.model.Account;
import com.challenge.transaction.model.Transaction;
import com.challenge.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;

    public TransactionService(AccountService accountService, TransactionRepository transactionRepository) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResponse authorizeTransaction(String transactionId, TransactionRequest request) {
        log.info("Autorizando transação {} para conta {}", transactionId, request.getAccountId());

        java.util.Optional<Transaction> existing = transactionRepository.findById(transactionId);
        if (existing.isPresent()) {
            log.info("Transação {} já processada, retornando resultado existente", transactionId);
            Transaction tx = existing.get();
            Account account = accountService.getAccount(tx.getAccountId());

            TransactionResponse response = new TransactionResponse();
            TransactionResponse.TransactionInfo txInfo = new TransactionResponse.TransactionInfo();
            txInfo.setId(tx.getId());
            txInfo.setType(tx.getType());
            txInfo.setAmount(new TransactionResponse.AmountInfo(tx.getAmountValue(), tx.getAmountCurrency()));
            txInfo.setStatus(tx.getStatus());
            txInfo.setTimestamp(tx.getTimestamp());
            response.setTransaction(txInfo);

            TransactionResponse.AccountInfo accInfo = new TransactionResponse.AccountInfo();
            accInfo.setId(account.getId());
            accInfo.setBalance(new TransactionResponse.AmountInfo(account.getBalance(), account.getCurrency()));
            response.setAccount(accInfo);

            return response;
        }

        Account account = accountService.getAccount(request.getAccountId());

        String type = request.getType().toUpperCase();
        if (!type.equals("CREDIT") && !type.equals("DEBIT")) {
            throw new InvalidTransactionTypeException("Invalid transaction type: " + type);
        }

        BigDecimal amount = request.getAmount().getValue();
        BigDecimal currentBalance = account.getBalance();
        BigDecimal newBalance;

        if (type.equals("CREDIT")) {
            newBalance = currentBalance.add(amount);
        } else {
            newBalance = currentBalance.subtract(amount);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientBalanceException("Insufficient balance for debit transaction");
            }
        }

        accountService.updateBalance(account.getId(), newBalance);

        String status = "SUCCEEDED";
        String timestamp = Instant.now().toString();

        Transaction transaction = new Transaction(transactionId, account.getId(), type, amount, status);
        transaction.setTimestamp(timestamp);
        transactionRepository.save(transaction);

        TransactionResponse response = new TransactionResponse();

        TransactionResponse.TransactionInfo txInfo = new TransactionResponse.TransactionInfo();
        txInfo.setId(transactionId);
        txInfo.setType(type);
        txInfo.setAmount(new TransactionResponse.AmountInfo(amount, "BRL"));
        txInfo.setStatus(status);
        txInfo.setTimestamp(timestamp);
        response.setTransaction(txInfo);

        TransactionResponse.AccountInfo accInfo = new TransactionResponse.AccountInfo();
        accInfo.setId(account.getId());
        accInfo.setBalance(new TransactionResponse.AmountInfo(newBalance, "BRL"));
        response.setAccount(accInfo);

        log.info("Transação {} {} para conta {} - novo saldo: {}", transactionId, status, account.getId(), newBalance);
        return response;
    }
}
