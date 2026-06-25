# Documentação da API de Autorização de Transações

## Sumário

- [Visão Geral](#visão-geral)
- [Arquitetura](#arquitetura)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Configuração](#configuração)
- [Endpoints da API](#endpoints-da-api)
- [Fluxo de Autorização](#fluxo-de-autorização)
- [Models](#models)
- [DTOs](#dtos)
- [Serviços](#serviços)
- [Listener SQS](#listener-sqs)
- [Tratamento de Exceções](#tratamento-de-exceções)
- [Decisões de Design](#decisões-de-design)
- [Escalabilidade e Infraestrutura AWS](#escalabilidade-e-infraestrutura-aws)
- [Melhorias Futuras](#melhorias-futuras)

---

## Visão Geral

Esta aplicação é uma API de autorização de transações financeiras desenvolvida para o desafio técnico do Itaú Unibanco. O sistema permite:

1. **Abertura de contas** via fila AWS SQS
2. **Autorização de transações** (crédito/débito)
3. **Consulta de saldo** em tempo real

### Tecnologias Utilizadas

| Tecnologia | Versão | Descrição |
|------------|--------|-----------|
| Java | 17 | Linguagem principal |
| Spring Boot | 4.1.0 | Framework web |
| Spring Data JPA | - | Acesso a dados |
| H2 Database | - | Banco de dados em memória |
| AWS SQS | - | Fila de mensagens |
| Jackson | 3.x | Serialização JSON |
| Maven | - | Gerenciador de dependências |

---

## Arquitetura

A aplicação segue o padrão **Hexagonal (Ports & Adapters)** com separação clara de responsabilidades:

```
┌─────────────────────────────────────────────────────────────┐
│                      CLIENTE                                │
└─────────────────────────┬───────────────────────────────────┘
                          │ HTTP POST /transactions/{id}
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    CONTROLLER                                │
│  TransactionController / HealthController                    │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                     SERVICE                                  │
│  TransactionService / AccountService                         │
└─────────────────────────┬───────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  Repository │  │  Repository │  │   SQS       │
│  Account    │  │  Transaction│  │   Listener  │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │
       ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────┐
│                    H2 DATABASE / AWS SQS                     │
└─────────────────────────────────────────────────────────────┘
```

---

## Estrutura do Projeto

```
transaction/
├── src/main/java/com/challenge/transaction/
│   ├── TransactionApplication.java          # Ponto de entrada
│   ├── config/                              # Configurações
│   │   ├── AwsSqsConfig.java               # Configuração AWS SQS
│   │   └── JacksonConfig.java              # Configuração Jackson
│   ├── controller/                          # Camada de apresentação
│   │   ├── TransactionController.java      # Controller de transações
│   │   └── HealthController.java           # Controller de health check
│   ├── dto/                                 # Data Transfer Objects
│   │   ├── AccountCreatedEvent.java        # Evento de conta criada
│   │   ├── TransactionRequest.java         # Requisição de transação
│   │   ├── TransactionResponse.java        # Resposta de transação
│   │   └── ErrorResponse.java              # Resposta de erro
│   ├── exception/                           # Exceções customizadas
│   │   ├── AccountNotFoundException.java   # Conta não encontrada
│   │   ├── InsufficientBalanceException.java # Saldo insuficiente
│   │   ├── InvalidTransactionTypeException.java # Tipo inválido
│   │   └── GlobalExceptionHandler.java     # Handler global
│   ├── listener/                            # Consumers SQS
│   │   └── AccountCreatedListener.java     # Listener de contas
│   ├── model/                               # Entidades JPA
│   │   ├── Account.java                    # Entidade Conta
│   │   └── Transaction.java                # Entidade Transação
│   ├── repository/                          # Repositórios
│   │   ├── AccountRepository.java          # Repository de contas
│   │   └── TransactionRepository.java      # Repository de transações
│   └── service/                             # Regras de negócio
│       ├── AccountService.java             # Serviço de contas
│       └── TransactionService.java         # Serviço de transações
├── src/main/resources/
│   └── application.properties              # Configurações da aplicação
├── src/test/java/                           # Testes
├── pom.xml                                  # Dependências Maven
└── Dockerfile                               # Container
```

---

## Configuração

### application.properties

```properties
# Porta do servidor
server.port=8080

# AWS SQS Configuration
aws.sqs.region=sa-east-1
aws.sqs.queue.url=http://sqs.sa-east-1.localhost.localstack.cloud:4566/000000000000/conta-bancaria-criada
aws.sqs.poll-interval=100
aws.sqs.batch-size=10
aws.sqs.concurrent-workers=50

# AWS Credentials (LocalStack)
aws.access-key=test
aws.secret-key=test

# Banco de Dados H2 (memória)
spring.datasource.url=jdbc:h2:mem:transactiondb
spring.jpa.hibernate.ddl-auto=update
```

### Explicação das Configurações SQS

| Propriedade | Valor | Descrição |
|-------------|-------|-----------|
| `aws.sqs.region` | `sa-east-1` | Região AWS onde a fila SQS está localizada |
| `aws.sqs.queue.url` | `http://...` | URL completa da fila SQS (LocalStack ou AWS) |
| `aws.sqs.poll-interval` | `100` | Intervalo em milissegundos entre cada poll na fila |
| `aws.sqs.batch-size` | `10` | Quantidade máxima de mensagens por poll (máx SQS: 10) |
| `aws.sqs.concurrent-workers` | `50` | Número de threads paralelas para processamento |
| `aws.access-key` | `test` | Chave de acesso AWS (LocalStack: test) |
| `aws.secret-key` | `test` | Chave secreta AWS (LocalStack: test) |

#### Detalhes de Cada Propriedade

**`aws.sqs.region`**
- Região geográfica do SQS
- LocalStack usa `sa-east-1` por padrão
- Produção: mesma região dos outros serviços AWS

**`aws.sqs.queue.url`**
- URL da fila onde as mensagens de abertura de conta são publicadas
- LocalStack: `http://sqs.sa-east-1.localhost.localstack.cloud:4566/...`
- Produção: `https://sqs.sa-east-1.amazonaws.com/{account-id}/conta-bancaria-criada`

**`aws.sqs.poll-interval`**
- Tempo de espera entre cada consulta à fila (em ms)
- `100ms`: Polling agressivo para alta performance
- `5000ms`: Polling conservador para reduzir custos
- Produtos com SQS pagam por request (reduzir = economizar)

**`aws.sqs.batch-size`**
- Máximo de mensagens recuperadas por chamada
- SQS aceita no máximo 10 mensagens por request
- Valor recomendado: `10` (máximo permitido)

**`aws.sqs.concurrent-workers`**
- Número de threads processando mensagens simultaneamente
- `50`: Processa até 50 mensagens ao mesmo tempo
- Equilíbrio entre throughput e uso de CPU/memória
- Mais workers = mais throughput, mais recursos

#### Configuração para Produção

```properties
# Produção (AWS Real)
aws.sqs.region=sa-east-1
aws.sqs.queue.url=https://sqs.sa-east-1.amazonaws.com/123456789/conta-bancaria-criada
aws.sqs.poll-interval=200
aws.sqs.batch-size=10
aws.sqs.concurrent-workers=20

# Credenciais via Environment Variables (NUNCA no arquivo)
# AWS_ACCESS_KEY_ID=xxx
# AWS_SECRET_ACCESS_KEY=xxx
```

**Diferenças Locais vs Produção:**

| Configuração | Local (LocalStack) | Produção (AWS) |
|--------------|-------------------|----------------|
| Queue URL | `http://sqs...localhost:4566` | `https://sqs...amazonaws.com` |
| Credenciais | `test/test` | IAM Role ou Secrets Manager |
| Poll Interval | 100ms (agressivo) | 200ms (conservador) |
| Concurrent Workers | 50 (máx performance) | 20 (economia de custo) |

---

## Endpoints da API

### 1. Autorizar Transação

**Requisição:**
```
POST /transactions/{transactionId}
```

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "account_id": "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975",
  "type": "CREDIT",
  "amount": {
    "value": 100.50,
    "currency": "BRL"
  }
}
```

**Resposta (Sucesso - 201):**
```json
{
  "transaction": {
    "id": "8e8ae808-b154-48b5-9f3e-553935cc4543",
    "type": "CREDIT",
    "amount": {
      "value": 100.50,
      "currency": "BRL"
    },
    "status": "SUCCEEDED",
    "timestamp": "2025-07-08T15:57:55-03:00"
  },
  "account": {
    "id": "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975",
    "balance": {
      "value": 100.50,
      "currency": "BRL"
    }
  }
}
```

**Respostas de Erro:**

| HTTP Status | Código | Descrição |
|-------------|--------|-----------|
| 404 | ACCOUNT_NOT_FOUND | Conta não encontrada |
| 400 | INVALID_TRANSACTION_TYPE | Tipo de transação inválido |
| 422 | INSUFFICIENT_BALANCE | Saldo insuficiente para débito |
| 409 | DUPLICATE_TRANSACTION | Transação já processada (idempotência) |

---

### 2. Health Check

**Requisição:**
```
GET /health
```

**Resposta:**
```json
{
  "status": "UP"
}
```

---

## Fluxo de Autorização

### Fluxo Principal

```
1. Recebe POST /transactions/{transactionId}
         │
         ▼
2. Verifica idempotência (transação já existe?)
         │
    ┌────┴────┐
    │         │
   SIM       NÃO
    │         │
    ▼         ▼
3a. Retorna  3b. Busca conta
    resultado     existente
    anterior         │
                     ▼
              4. Valida tipo (CREDIT/DEBIT)
                     │
                     ▼
              5. Calcula novo saldo
                     │
            ┌────────┴────────┐
            │                 │
         CREDIT            DEBIT
            │                 │
            ▼                 ▼
      saldo + valor     saldo - valor
                            │
                            ▼
                     6. Saldo < 0?
                            │
                       ┌────┴────┐
                       │         │
                      SIM       NÃO
                       │         │
                       ▼         ▼
                  7a. RECUSA   7b. APROVA
                     (422)     e grava
```

### Lógica de Cálculo

```java
// CRÉDITO: soma ao saldo
novoSaldo = saldoAtual.add(valor);

// DÉBITO: subtrai do saldo
novoSaldo = saldoAtual.subtract(valor);
if (novoSaldo.compareTo(BigDecimal.ZERO) < 0) {
    throw new InsufficientBalanceException("Saldo insuficiente");
}
```

---

## Models

### Account

| Campo | Tipo | Descrição |
|-------|------|-----------|
| id | String (UUID) | Identificador único da conta |
| owner | String | Identificador do titular |
| balance | BigDecimal | Saldo da conta (precision=19, scale=4) |
| currency | String | Moeda (padrão: BRL) |
| status | String | Status (ENABLED/DISABLED) |
| createdAt | String | Data de criação (timestamp Unix) |

### Transaction

| Campo | Tipo | Descrição |
|-------|------|-----------|
| id | String (UUID) | Identificador único da transação |
| accountId | String | ID da conta associada |
| type | String | Tipo (CREDIT/DEBIT) |
| amountValue | BigDecimal | Valor da transação |
| amountCurrency | String | Moeda (BRL) |
| status | String | Status (SUCCEEDED/FAILED) |
| timestamp | String | Data/hora (ISO 8601) |

---

## DTOs

### AccountCreatedEvent

Evento recebido via SQS quando uma nova conta é aberta:

```json
{
  "account": {
    "id": "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975",
    "owner": "315e3cfe-f4af-4cd2-b298-a449e614349a",
    "created_at": "1634874339",
    "status": "ENABLED"
  }
}
```

### TransactionRequest

Requisição de autorização:

```json
{
  "account_id": "UUID",
  "type": "CREDIT|DEBIT",
  "amount": {
    "value": 100.00,
    "currency": "BRL"
  }
}
```

### TransactionResponse

Resposta da autorização:

```json
{
  "transaction": {
    "id": "UUID",
    "type": "CREDIT|DEBIT",
    "amount": { "value": 100.00, "currency": "BRL" },
    "status": "SUCCEEDED|FAILED",
    "timestamp": "ISO8601"
  },
  "account": {
    "id": "UUID",
    "balance": { "value": 100.00, "currency": "BRL" }
  }
}
```

### ErrorResponse

Resposta de erro:

```json
{
  "error": "CÓDIGO_ERRO",
  "message": "Mensagem descritiva"
}
```

---

## Serviços

### AccountService

| Método | Descrição | Transacional |
|--------|-----------|--------------|
| `createAccount(id, owner, createdAt, status)` | Cria nova conta com saldo zero | Sim |
| `getAccount(id)` | Busca conta por ID | Não |
| `updateBalance(accountId, newBalance)` | Atualiza saldo da conta | Sim |

### TransactionService

| Método | Descrição | Transacional |
|--------|-----------|--------------|
| `authorizeTransaction(transactionId, request)` | Autoriza transação (CREDIT/DEBIT) | Sim |

**Lógica de Idempotência:**
- Verifica se transação já existe pelo `transactionId`
- Se existir, retorna resultado anterior sem modificar saldo
- Se não existir, processa normalmente

---

## Listener SQS

### AccountCreatedListener

**Configuração:**
- Fila: `conta-bancaria-criada`
- Intervalo de polling: 5 segundos (configurável)
- Máximo de mensagens: 10 por lote
- Long polling: 5 segundos

**Fluxo:**
1. Consume mensagens da fila SQS
2. Deserializa JSON para `AccountCreatedEvent`
3. Cria conta via `AccountService.createAccount()`
4. Remove mensagem da fila após processamento
5. Em caso de erro, loga e continua (não remove mensagem)

---

## Tratamento de Exceções

### Exceções Customizadas

| Exceção | HTTP Status | Descrição |
|---------|-------------|-----------|
| `AccountNotFoundException` | 404 | Conta não encontrada |
| `InsufficientBalanceException` | 422 | Saldo insuficiente |
| `InvalidTransactionTypeException` | 400 | Tipo de transação inválido |
| `DataIntegrityViolationException` | 409 | Transação duplicada |

### GlobalExceptionHandler

Trata todas as exceções de forma centralizada:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // Mapeia exceções para respostas HTTP apropriadas
}
```

---

## Decisões de Design

### 1. BigDecimal para Valores Monetários

**Problema:** `Double` apresenta imprecisões em operações de ponto flutuante:
```java
0.1 + 0.2 = 0.30000000000000004  // Double
0.1 + 0.2 = 0.3                  // BigDecimal
```

**Solução:** Utilizar `BigDecimal` com precisão de 19 dígitos e 4 casas decimais.

### 2. Idempotência

**Problema:** Requisições duplicadas podem causar débitos/créditos múltiplos.

**Solução:** 
- Verificação em aplicação: `transactionRepository.findById()`
- Restrição no banco: constraint de unicidade no `transactionId`

### 3. SQS Long Polling

**Problema:** Polling frequente consome recursos desnecessariamente.

**Solução:** Utilizar `WaitTimeSeconds=5` (long polling) para reduzir chamadas vazias.

### 4. H2 In-Memory Database

**Motivação:** Facilita testes locais e desenvolvimento.

**Produção:** Substituir por PostgreSQL ou Aurora.

---

## Execução Local

### Pré-requisitos
- Java 17+
- Docker
- Maven

### Passos

1. **Iniciar LocalStack (SQS):**
```bash
docker compose up -d
```

2. **Compilar e executar:**
```bash
cd transaction
./mvnw clean package -DskipTests
java -jar target/transaction-0.0.1-SNAPSHOT.jar
```

3. **Testar health check:**
```bash
curl http://localhost:8080/health
```

4. **Autorizar transação:**
```bash
curl -X POST http://localhost:8080/transactions/$(uuidgen) \
  -H "Content-Type: application/json" \
  -d '{
    "account_id": "UUID_DA_CONTA",
    "type": "CREDIT",
    "amount": {"value": 100.00, "currency": "BRL"}
  }'
  ```

---

## Escalabilidade e Infraestrutura AWS

### Diagrama de Arquitetura AWS

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            INTERNET                                             │
└─────────────────────────────────┬───────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           AWS CLOUD                                             │
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────────┐   │
│  │                         us-east-1 / sa-east-1                            │   │
│  │                                                                          │   │
│  │  ┌──────────────┐                                                       │   │
│  │  │  Route 53    │  DNS + Health Check + Failover                        │   │
│  │  └──────┬───────┘                                                       │   │
│  │         │                                                                │   │
│  │         ▼                                                                │   │
│  │  ┌──────────────┐                                                       │   │
│  │  │  CloudFront  │  CDN + Cache + SSL                                    │   │
│  │  └──────┬───────┘                                                       │   │
│  │         │                                                                │   │
│  │         ▼                                                                │   │
│  │  ┌──────────────┐                                                       │   │
│  │  │  API Gateway │  Rate Limiting + API Key + Throttling                 │   │
│  │  └──────┬───────┘                                                       │   │
│  │         │                                                                │   │
│  │         ▼                                                                │   │
│  │  ┌──────────────────────────────────────────────────────────────────┐   │   │
│  │  │                        VPC                                       │   │   │
│  │  │                                                                   │   │   │
│  │  │  ┌───────────────────────────────────────────────────────────┐   │   │   │
│  │  │  │                    Public Subnet                          │   │   │   │
│  │  │  │  ┌──────────────────────────────────────────────────┐    │   │   │   │
│  │  │  │  │              Application Load Balancer            │    │   │   │   │
│  │  │  │  │  - Health Check: /health                         │    │   │   │   │
│  │  │  │  │  - Target Group: Kubernetes NodePort             │    │   │   │   │
│  │  │  │  │  - SSL/TLS: ACM Certificate                      │    │   │   │   │
│  │  │  │  └───────────────────────┬──────────────────────────┘    │   │   │   │
│  │  │  └──────────────────────────┼───────────────────────────────┘   │   │   │
│  │  │                              │                                   │   │   │
│  │  │  ┌──────────────────────────┼───────────────────────────────┐   │   │   │
│  │  │  │                    Private Subnet                         │   │   │   │
│  │  │  │                              │                           │   │   │   │
│  │  │  │      ┌───────────────────────┼──────────────────────┐   │   │   │   │
│  │  │  │      │                       │                      │   │   │   │   │
│  │  │  │      ▼                       ▼                      ▼   │   │   │   │
│  │  │  │  ┌────────┐            ┌────────┐            ┌────────┐ │   │   │   │
 │  │  │  │  │  EKS   │            │  EKS   │            │  EKS   │ │   │   │   │
│  │  │  │  │  Node 1 │            │ Node 2 │            │ Node 3 │ │   │   │   │
│  │  │  │  │  ┌───┐ │            │  ┌───┐ │            │  ┌───┐ │ │   │   │   │
│  │  │  │  │  │Pod│ │            │  │Pod│ │            │  │Pod│ │ │   │   │   │
│  │  │  │  │  └───┘ │            │  └───┘ │            │  └───┘ │ │   │   │   │
│  │  │  │  └───┬────┘            └───┬────┘            └───┬────┘ │   │   │   │
│  │  │  │      │                     │                     │      │   │   │   │
│  │  │  │      └─────────────────────┼─────────────────────┘      │   │   │   │
│  │  │  │                              │                          │   │   │   │
│  │  │  │      ┌───────────────────────┼──────────────────────┐   │   │   │   │
│  │  │  │      │                       │                      │   │   │   │   │
│  │  │  │      ▼                       ▼                      ▼   │   │   │   │
│  │  │  │  ┌─────────┐          ┌──────────┐          ┌─────────┐│   │   │   │
│  │  │  │  │  RDS    │          │  SQS     │          │ ElastiC ││   │   │   │
│  │  │  │  │ Aurora  │          │ Standard │          │  Redis  ││   │   │   │
│  │  │  │  │PostgreSQL│         │  Queue   │          │  Cache  ││   │   │   │
│  │  │  │  │ (Writer)│          │          │          │         ││   │   │   │
│  │  │  │  └────┬────┘          └──────────┘          └─────────┘│   │   │   │
│  │  │  │       │                                                 │   │   │   │
│  │  │  │       ▼                                                 │   │   │   │
│  │  │  │  ┌─────────┐                                           │   │   │   │
│  │  │  │  │  RDS    │                                           │   │   │   │
│  │  │  │  │ Aurora  │                                           │   │   │   │
│  │  │  │  │PostgreSQL│                                          │   │   │   │
│  │  │  │  │ (Reader)│                                           │   │   │   │
│  │  │  │  └─────────┘                                           │   │   │   │
│  │  │  └───────────────────────────────────────────────────────┘   │   │   │
│  │  └──────────────────────────────────────────────────────────────┘   │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐   │   │
│  │  │                      Monitoring                              │   │   │
│  │  │  ┌────────────┐  ┌────────────┐  ┌────────────┐            │   │   │
│  │  │  │ CloudWatch │  │  X-Ray     │  │  SNS/SQS   │            │   │   │
│  │  │  │  Metrics   │  │  Tracing   │  │  Alarms    │            │   │   │
│  │  │  └────────────┘  └────────────┘  └────────────┘            │   │   │
│  │  └──────────────────────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Componentes AWS

| Componente | Serviço | Função |
|------------|---------|--------|
| DNS | Route 53 | Roteamento DNS, failover, health checks |
| CDN | CloudFront | Cache estático, SSL, proteção DDoS |
| API Gateway | API Gateway | Rate limiting, throttling, API keys |
| Load Balancer | ALB | Distribuição de carga, health checks |
| Compute | EKS (Kubernetes) | Containers orquestrados, auto-scaling |
| Banco de Dados | Aurora PostgreSQL | Banco relacional, Multi-AZ, read replicas |
| Cache | ElastiCache Redis | Cache de contas, sessões |
| Fila | SQS Standard | Filas de mensagens, decoupling |
| Monitoramento | CloudWatch | Métricas, logs, alarmes |
| Rastreamento | X-Ray | Distributed tracing |

### Configuração EKS (Kubernetes)

#### Deployment

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: transaction-service
  namespace: transaction
spec:
  replicas: 3
  selector:
    matchLabels:
      app: transaction-service
  template:
    metadata:
      labels:
        app: transaction-service
    spec:
      containers:
        - name: transaction
          image: 123456789.dkr.ecr.sa-east-1.amazonaws.com/transaction:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "production"
            - name: AWS_REGION
              value: "sa-east-1"
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: transaction-secrets
                  key: db-password
          resources:
            requests:
              cpu: "500m"
              memory: "1Gi"
            limits:
              cpu: "1000m"
              memory: "2Gi"
          livenessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
```

#### Service

```yaml
# k8s/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: transaction-service
  namespace: transaction
spec:
  selector:
    app: transaction-service
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
  type: ClusterIP
```

#### Ingress

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: transaction-ingress
  namespace: transaction
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
spec:
  rules:
    - host: api.transaction.com.br
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: transaction-service
                port:
                  number: 80
```

### Configuração Aurora PostgreSQL

```
Writer Instance:
  - db.r6g.large (2 vCPU, 16 GB RAM)
  - Multi-AZ
  
Reader Instances:
  - 2x db.r6g.large
  - Auto-scaling read replicas
  
Endpoints:
  - Writer: transaction.cluster-xxxx.sa-east-1.rds.amazonaws.com:5432
  - Reader: transaction.cluster-ro-xxxx.sa-east-1.rds.amazonaws.com:5432
```

### Configuração ElastiCache Redis

```
Node Type: cache.r6g.large
Cluster Mode: Enabled
Shards: 3
Replicas per Shard: 2
Multi-AZ: Yes

Use Cases:
  - Cache de saldo de contas (TTL: 5 min)
  - Cache de transações recentes (TTL: 1 min)
  - Rate limiting por conta
```

### SQS Configuration

```
Queue: conta-bancaria-criada
Type: Standard
Visibility Timeout: 30s
Message Retention: 4 days
Receive Wait Time: 20s (long polling)

Dead Letter Queue:
  - conta-bancaria-criada-dlq
  - Max Receive Count: 3
```

### Pipeline CI/CD

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│  Code   │    │ CodeBuild│   │  ECR    │    │   EKS   │
│  Commit │───▶│ (Build) │───▶│  Push   │───▶│ kubectl │
└─────────┘    └─────────┘    └─────────┘    └─────────┘

Estratégia: Rolling Update
  - Deploy via kubectl apply
  - Rollback via kubectl rollout undo
  - Zero downtime
```

### Environment Variables (Produção)

```properties
# Banco de Dados
spring.datasource.url=jdbc:postgresql://transaction.cluster-xxxx.sa-east-1.rds.amazonaws.com:5432/transactiondb
spring.datasource.username=transaction_user
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate

# Redis Cache
spring.cache.type=redis
spring.data.redis.host=transaction.xxxx.ng.0001.sa-east-1.cache.amazonaws.com
spring.data.redis.port=6379

# SQS
aws.sqs.region=sa-east-1
aws.sqs.queue.url=https://sqs.sa-east-1.amazonaws.com/123456789/conta-bancaria-criada
aws.sqs.poll-interval=100
aws.sqs.batch-size=10
aws.sqs.concurrent-workers=10

# Monitoring
management.endpoints.web.exposure.include=health,metrics,prometheus
management.metrics.export.prometheus.enabled=true
```

### Estimativa de Custos Mensais (AWS)

| Serviço | Configuração | Custo Estimado |
|---------|--------------|----------------|
| EKS | 1 cluster + 3 nodes (m5.xlarge) | ~$300 |
| EC2 (Worker Nodes) | 3x m5.xlarge (ondemand) | ~$430 |
| Aurora PostgreSQL | 3 instances (r6g.large) | ~$500 |
| ElastiCache Redis | 3 shards, r6g.large | ~$400 |
| SQS | 1M requests | ~$0.40 |
| ALB | 1 ALB + LCU | ~$50 |
| ECR | Storage | ~$10 |
| CloudWatch | Logs + Metrics | ~$30 |
| **Total** | | **~$1,720/mês** |

### Checklist de Produção

- [ ] Configurar VPC com subnets públicas e privadas
- [ ] Criar Security Groups para cada componente
- [ ] Criar EKS Cluster (managed node groups)
- [ ] Instalar AWS Load Balancer Controller
- [ ] Configurar RDS com Multi-AZ e autom backups
- [ ] Configurar ElastiCache com cluster mode
- [ ] Criar Namespace transaction
- [ ] Configurar Secrets (Kubernetes Secrets)
- [ ] Configurar Ingress com ALB
- [ ] Configurar CloudWatch alarms
- [ ] Configurar Route 53 com health checks
- [ ] Configurar SSL/TLS com ACM
- [ ] Teste de carga em staging
- [ ] Documentação de runbooks

### Comandos Úteis (Kubernetes)

```bash
# Ver pods
kubectl get pods -n transaction

# Ver logs
kubectl logs -f deployment/transaction-service -n transaction

# Escalar manualmente
kubectl scale deployment transaction-service --replicas=5 -n transaction

# Ver HPA
kubectl get hpa -n transaction

# Rolling update
kubectl set image deployment/transaction-service \
  transaction=123456789.dkr.ecr.sa-east-1.amazonaws.com/transaction:v2.0 -n transaction

# Rollback
kubectl rollout undo deployment/transaction-service -n transaction

# Ver status do rollout
kubectl rollout status deployment/transaction-service -n transaction
```

---

## Melhorias Futuras

> **Nota:** As melhorias listadas abaixo não foram implementadas nesta fase porque requerem um cluster EKS real para testes. Tais recursos são essenciais para produção, mas não puderam ser validados localmente.

### 1. Auto Scaling (HPA + Cluster Autoscaler)

**O que é:** Horizontal Pod Autoscaler escala o número de Pods baseado em métricas de CPU/Memória. Cluster Autoscaler adiciona/remove nodes do cluster automaticamente.

**Por que não foi implementado:** Requer cluster EKS rodando na AWS com Metrics Server instalado.

```yaml
# k8s/hpa.yaml (futuro)
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: transaction-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: transaction-service
  minReplicas: 3
  maxReplicas: 50
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

**Benefícios:**
- Escala automática baseada em demanda
- Redução de custos em períodos de baixa
- Alta disponibilidade em picos de tráfego

### 2. GitOps com ArgoCD

**O que é:** Estratégia de deploy onde o Git é a fonte da verdade. ArgoCD sincroniza automaticamente o estado desejado (Git) com o estado real do cluster.

**Por que não foi implementado:** Requer ArgoCD instalado no cluster EKS.

```yaml
# argocd/application.yaml (futuro)
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: transaction-service
spec:
  source:
    repoURL: https://github.com/org/transaction-infra.git
    path: k8s/overlays/production
  destination:
    server: https://kubernetes.default.svc
    namespace: transaction
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

**Benefícios:**
- Deploy automático via Git commit
- Rollback instantâneo (revert do commit)
- Histórico completo de deploys
- Auditoria e compliance

### 3. Canary Deployments com Argo Rollouts

**O que é:** Deploy gradual onde uma nova versão recebe uma porcentagem pequena do tráfego (ex: 10%) antes de ser liberada para 100%.

**Por que não foi implementado:** Requer Argo Rollouts + AWS ALB Controller configurados.

```yaml
# k8s/rollout.yaml (futuro)
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: transaction-service
spec:
  strategy:
    canary:
      steps:
        - setWeight: 10
        - pause: {duration: 5m}
        - setWeight: 50
        - pause: {duration: 5m}
        - setWeight: 100
```

**Benefícios:**
- Redução de risco em deploys
- Rollback automático se métricas degradarem
- Zero downtime
- Teste em produção com tráfego real

### 4. Monitoramento Avançado (Prometheus + Grafana)

**O que é:** Stack completa de monitoramento com métricas detalhadas, dashboards e alertas.

**Por que não foi implementado:** Requer Prometheus + Grafana instalados no cluster.

**Benefícios:**
- Dashboards em tempo real
- Alertas proativos
- Métricas de negócio (transações/s, taxa de erro, latência P99)

### 5. Distributed Tracing (X-Ray / Jaeger)

**O que é:** Rastreamento de requisições através de múltiplos serviços para identar gargalos e erros.

**Por que não foi implementado:** Requer ADOT Collector ou Jaeger instalado no cluster.

**Benefícios:**
- Identificação de gargalos
- Debug de requisições distribuídas
- Análise de dependências entre serviços

### Roadmap de Implementação

| Fase | Recurso | Prioridade | Dependência |
|------|---------|------------|-------------|
| 1 | EKS Cluster | Alta | AWS Account |
| 2 | HPA + Metrics Server | Alta | Fase 1 |
| 3 | Cluster Autoscaler | Média | Fase 1 |
| 4 | ArgoCD | Média | Fase 1 |
| 5 | Argo Rollouts | Baixa | Fase 4 |
| 6 | Prometheus + Grafana | Média | Fase 1 |
| 7 | X-Ray / Jaeger | Baixa | Fase 1 |
