package com.challenge.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountCreatedEvent {

    @JsonProperty("account")
    private AccountInfo account;

    public AccountCreatedEvent() {
    }

    public AccountInfo getAccount() {
        return account;
    }

    public void setAccount(AccountInfo account) {
        this.account = account;
    }

    public static class AccountInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("owner")
        private String owner;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("status")
        private String status;

        public AccountInfo() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
