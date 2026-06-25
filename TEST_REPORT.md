# API de Transações - Relatório de Testes

## Ambiente

- **Aplicação**: Spring Boot 4.1.0 + Java 17
- **Banco de Dados**: H2 (em memória)
- **SQS**: LocalStack (Docker)
- **Porta**: 8080

---

## Resultados dos Testes

### 1. Verificação de Saúde

**Endpoint**: `GET /health`

**Resposta**:
```json
{
  "status": "UP"
}
```

**Status**: ✅ APROVADO

---

### 2. Transação de CRÉDITO

**Endpoint**: `POST /transactions/{transactionId}`

**Requisição**:
```json
{
  "account_id": "eb54c6a1-83ea-4517-8c98-7bb557b287cc",
  "type": "CREDIT",
  "amount": {
    "value": 100.50,
    "currency": "BRL"
  }
}
```

**Resposta**:
```json
{
  "transaction": {
    "id": "F0359AAE-509D-485F-981E-F0B0992030DE",
    "type": "CREDIT",
    "amount": {
      "value": 100.5,
      "currency": "BRL"
    },
    "status": "SUCCEEDED",
    "timestamp": "2026-06-25T00:05:06.879936Z"
  },
  "account": {
    "id": "eb54c6a1-83ea-4517-8c98-7bb557b287cc",
    "balance": {
      "value": 100.5,
      "currency": "BRL"
    }
  }
}
```

**Resultado**: Saldo aumentou de 0,0 para 100,5

**Status**: ✅ APROVADO

---

### 3. Transação de DÉBITO (Bem-sucedida)

**Endpoint**: `POST /transactions/{transactionId}`

**Requisição**:
```json
{
  "account_id": "eb54c6a1-83ea-4517-8c98-7bb557b287cc",
  "type": "DEBIT",
  "amount": {
    "value": 50.25,
    "currency": "BRL"
  }
}
```

**Resposta**:
```json
{
  "transaction": {
    "id": "B9FAC4B7-B2C5-4BB5-91FB-E1CB752D7D98",
    "type": "DEBIT",
    "amount": {
      "value": 50.25,
      "currency": "BRL"
    },
    "status": "SUCCEEDED",
    "timestamp": "2026-06-25T00:05:11.207637Z"
  },
  "account": {
    "id": "eb54c6a1-83ea-4517-8c98-7bb557b287cc",
    "balance": {
      "value": 50.25,
      "currency": "BRL"
    }
  }
}
```

**Resultado**: Saldo diminuiu de 100,5 para 50,25

**Status**: ✅ APROVADO

---

### 4. Transação de DÉBITO (Saldo Insuficiente)

**Endpoint**: `POST /transactions/{transactionId}`

**Requisição**:
```json
{
  "account_id": "eb54c6a1-83ea-4517-8c98-7bb557b287cc",
  "type": "DEBIT",
  "amount": {
    "value": 200.00,
    "currency": "BRL"
  }
}
```

**Resposta**:
```json
{
  "error": "INSUFFICIENT_BALANCE",
  "message": "Saldo insuficiente para transação de débito"
}
```

**Resultado**: Transação recusada, saldo inalterado (50,25)

**Status**: ✅ APROVADO

---

### 5. Conta Inexistente

**Endpoint**: `POST /transactions/{transactionId}`

**Requisição**:
```json
{
  "account_id": "conta-inexistente",
  "type": "CREDIT",
  "amount": {
    "value": 100.00,
    "currency": "BRL"
  }
}
```

**Resposta**:
```json
{
  "error": "ACCOUNT_NOT_FOUND",
  "message": "Conta não encontrada: conta-inexistente"
}
```

**Status**: ✅ APROVADO

---

### 6. Tipo de Transação Inválido

**Endpoint**: `POST /transactions/{transactionId}`

**Requisição**:
```json
{
  "account_id": "eb54c6a1-83ea-4517-8c98-7bb557b287cc",
  "type": "INVALID",
  "amount": {
    "value": 100.00,
    "currency": "BRL"
  }
}
```

**Resposta**:
```json
{
  "error": "INVALID_TRANSACTION_TYPE",
  "message": "Tipo de transação inválido: INVALID"
}
```

**Status**: ✅ APROVADO

---

### 7. Idempotência

**Endpoint**: `POST /transactions/{transactionId}`

**Primeira Requisição**:
```json
{
  "account_id": "eb54c6a1-83ea-4517-8c98-7bb557b287cc",
  "type": "CREDIT",
  "amount": {
    "value": 25.00,
    "currency": "BRL"
  }
}
```

**Resposta**: Saldo aumentou de 50,25 para 75,25

**Requisição Duplicada** (mesmo transactionId):
```json
{
  "account_id": "eb54c6a1-83ea-4517-8c98-7bb557b287cc",
  "type": "CREDIT",
  "amount": {
    "value": 25.00,
    "currency": "BRL"
  }
}
```

**Resposta**: Mesma resposta retornada, saldo inalterado (75,25)

**Resultado**: Requisição duplicada não modificou o saldo

**Status**: ✅ APROVADO

---

## Resumo

| Caso de Teste | Esperado | Resultado | Status |
|---------------|----------|-----------|--------|
| Verificação de Saúde | `{"status": "UP"}` | `{"status": "UP"}` | ✅ APROVADO |
| Transação de CRÉDITO | Saldo + valor | Saldo aumentado corretamente | ✅ APROVADO |
| Transação de DÉBITO | Saldo - valor | Saldo diminuído corretamente | ✅ APROVADO |
| Saldo Insuficiente | Transação recusada | 422 INSUFFICIENT_BALANCE | ✅ APROVADO |
| Conta Inexistente | Conta não encontrada | 404 ACCOUNT_NOT_FOUND | ✅ APROVADO |
| Tipo Inválido | Tipo rejeitado | 400 INVALID_TRANSACTION_TYPE | ✅ APROVADO |
| Idempotência | Mesmo resultado em duplicata | Sem processamento duplo | ✅ APROVADO |

**Resultado Geral**: ✅ **TODOS OS TESTES APROVADOS**
