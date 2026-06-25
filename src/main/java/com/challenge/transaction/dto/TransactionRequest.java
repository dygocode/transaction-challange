package com.challenge.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class TransactionRequest {

    @JsonProperty("account_id")
    private String accountId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("amount")
    private AmountInfo amount;

    public TransactionRequest() {
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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

    public static class AmountInfo {
        @JsonProperty("value")
        private BigDecimal value;

        @JsonProperty("currency")
        private String currency;

        public AmountInfo() {
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
