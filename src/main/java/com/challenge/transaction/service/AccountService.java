package com.challenge.transaction.service;

import com.challenge.transaction.exception.AccountNotFoundException;
import com.challenge.transaction.model.Account;
import com.challenge.transaction.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account createAccount(String id, String owner, String createdAt, String status) {
        log.info("Criando conta {} para titular {}", id, owner);
        Account account = new Account(id, owner, createdAt, status);
        return accountRepository.save(account);
    }

    @Transactional
    public void createAccountsBatch(List<Account> accounts) {
        accountRepository.saveAll(accounts);
    }

    public Account getAccount(String id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Conta não encontrada: " + id));
    }

    @Transactional
    public Account updateBalance(String accountId, BigDecimal newBalance) {
        Account account = getAccount(accountId);
        account.setBalance(newBalance);
        return accountRepository.save(account);
    }
}
