package com.challenge.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {

    @JsonProperty("transaction")
    private TransactionInfo transaction;

    @JsonProperty("account")
    private AccountInfo account;

    public TransactionResponse() {
    }

    public TransactionInfo getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionInfo transaction) {
        this.transaction = transaction;
    }

    public AccountInfo getAccount() {
        return account;
    }

    public void setAccount(AccountInfo account) {
        this.account = account;
    }

    public static class TransactionInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("type")
        private String type;

        @JsonProperty("amount")
        private AmountInfo amount;

        @JsonProperty("status")
        private String status;

        @JsonProperty("timestamp")
        private String timestamp;

        public TransactionInfo() {
        }

        public TransactionInfo(String id, String type, AmountInfo amount, String status, String timestamp) {
            this.id = id;
            this.type = type;
            this.amount = amount;
            this.status = status;
            this.timestamp = timestamp;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public AmountInfo getAmount() {
            return amount;
        }

        public void setAmount(AmountInfo amount) {
            this.amount = amount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class AccountInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("balance")
        private AmountInfo balance;

        public AccountInfo() {
        }

        public AccountInfo(String id, AmountInfo balance) {
            this.id = id;
            this.balance = balance;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public AmountInfo getBalance() {
            return balance;
        }

        public void setBalance(AmountInfo balance) {
            this.balance = balance;
        }
    }

    public static class AmountInfo {
        @JsonProperty("value")
        private BigDecimal value;

        @JsonProperty("currency")
        private String currency;

        public AmountInfo() {
        }

        public AmountInfo(BigDecimal value, String currency) {
            this.value = value;
            this.currency = currency;
        }

        public BigDecimal getValue() {
            return value;
        }

        public void setValue(BigDecimal value) {
            this.value = value;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }
}
