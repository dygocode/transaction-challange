# Transaction Authorization API

API de autorização de transações financeiras para o desafio técnico do Itaú Unibanco.

## Visão Geral

Sistema que permite:
- Abertura de contas via fila AWS SQS
- Autorização de transações (crédito/débito)
- Controle de saldo com idempotência

## Tecnologias

- Java 17
- Spring Boot 4.1.0
- Spring Data JPA
- AWS SQS
- H2 Database (desenvolvimento)
- Docker + LocalStack

## Como Executar

### Pré-requisitos
- Java 17+
- Docker
- Maven

### Passos

1. **Iniciar LocalStack (SQS)**
```bash
docker compose up -d
```

2. **Compilar e executar a aplicação**
```bash
cd transaction
./mvnw clean package -DskipTests
java -jar target/transaction-0.0.1-SNAPSHOT.jar
```

3. **Testar health check**
```bash
curl http://localhost:8080/health
```

4. **Autorizar transação**
```bash
curl -X POST http://localhost:8080/transactions/$(uuidgen) \
  -H "Content-Type: application/json" \
  -d '{
    "account_id": "UUID_DA_CONTA",
    "type": "CREDIT",
    "amount": {"value": 100.00, "currency": "BRL"}
  }'
```

## Endpoints

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/transactions/{transactionId}` | Autorizar transação |
| GET | `/health` | Health check |

## Estrutura do Projeto

```
transaction/
├── src/main/java/com/challenge/transaction/
│   ├── config/          # Configurações (AWS SQS, Jackson)
│   ├── controller/      # Endpoints REST
│   ├── dto/             # Data Transfer Objects
│   ├── exception/       # Exceções e handler global
│   ├── listener/        # Consumer SQS
│   ├── model/           # Entidades JPA
│   ├── repository/      # Repositórios
│   └── service/         # Regras de negócio
├── src/main/resources/
│   └── application.properties
├── pom.xml
└── Dockerfile
```

## Implementações Importantes

### Idempotência
- Verifica se transação já existe pelo `transactionId`
- Requisições duplicadas retornam mesmo resultado
- Previne débitos/créditos múltiplos

### BigDecimal para Valores Monetários
- Evita imprecisões de ponto flutuante (`0.1 + 0.2 = 0.3`)
- Precisão de 19 dígitos, 4 casas decimais

### Processamento Paralelo SQS
- 50 workers paralelos para processamento de mensagens
- Batch delete para melhor performance
- Poll interval configurável (100ms default)

### Tratamento de Exceções
- Handler global para todas as exceções
- Respostas padronizadas com códigos de erro
- HTTP Status apropriados (404, 400, 422, 409)

## Fluxo de Autorização

```
1. Recebe transação
2. Verifica idempotência
3. Busca conta
4. Valida tipo (CREDIT/DEBIT)
5. Calcula novo saldo
6. Saldo < 0? → RECUSA (422)
7. Saldo >= 0? → APROVA e grava
```

## Configuração

### application.properties

```properties
# AWS SQS
aws.sqs.region=sa-east-1
aws.sqs.queue.url=http://sqs.sa-east-1.localhost.localstack.cloud:4566/000000000000/conta-bancaria-criada
aws.sqs.poll-interval=100
aws.sqs.batch-size=10
aws.sqs.concurrent-workers=50

# Banco de Dados
spring.datasource.url=jdbc:h2:mem:transactiondb
spring.jpa.hibernate.ddl-auto=update
```

## Documentação Completa

Para detalhes completos sobre arquitetura, configurações, infraestrutura AWS e melhorias futuras, consulte:

**[DOCUMENTACAO.md](DOCUMENTACAO.md)**

## Testes

### P99 Performance Test

| Endpoint | P99 Latência | Requisições/seg |
|----------|--------------|-----------------|
| GET /health | 17ms | 11.120 |
| POST /transactions | 43ms | 7.498 |

### Casos de Teste

- Health Check
- Transação de CRÉDITO
- Transação de DÉBITO
- Saldo insuficiente
- Conta inexistente
- Tipo de transação inválido
- Idempotência

Consulte **[TEST_REPORT.md](TEST_REPORT.md)** para detalhes.

## Produção

### Checklist

- [ ] Configurar VPC com subnets públicas e privadas
- [ ] Criar EKS Cluster
- [ ] Configurar Aurora PostgreSQL
- [ ] Configurar ElastiCache Redis
- [ ] Configurar SQS com Dead Letter Queue
- [ ] Configurar ALB + Ingress
- [ ] Configurar CloudWatch alarms
- [ ] Configurar Route 53 + SSL

Consulte **[DOCUMENTACAO.md](DOCUMENTACAO.md)** para configuração completa.

## Autor

Desafio técnico Itaú Unibanco - Engenheiro de Software
