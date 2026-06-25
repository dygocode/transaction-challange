package com.challenge.transaction.listener;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.challenge.transaction.dto.AccountCreatedEvent;
import com.challenge.transaction.service.AccountService;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AccountCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(AccountCreatedListener.class);

    private final AmazonSQS amazonSQS;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;
    private final String queueUrl;
    private final Executor sqsExecutor;
    private final AtomicInteger processedCount = new AtomicInteger(0);

    public AccountCreatedListener(AmazonSQS amazonSQS,
                                  AccountService accountService,
                                  ObjectMapper objectMapper,
                                  @Value("${aws.sqs.queue.url}") String queueUrl,
                                  @Value("${aws.sqs.batch-size:10}") int batchSize,
                                  @Value("${aws.sqs.concurrent-workers:5}") int concurrentWorkers,
                                  Executor sqsExecutor) {
        this.amazonSQS = amazonSQS;
        this.accountService = accountService;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
        this.batchSize = batchSize;
        this.concurrentWorkers = concurrentWorkers;
        this.sqsExecutor = sqsExecutor;
    }

    private final int batchSize;
    private final int concurrentWorkers;

    @Scheduled(fixedDelayString = "${aws.sqs.poll-interval:1000}")
    public void pollMessages() {
        try {
            ReceiveMessageRequest request = new ReceiveMessageRequest(queueUrl)
                    .withMaxNumberOfMessages(batchSize)
                    .withWaitTimeSeconds(2)
                    .withAttributeNames("All");

            List<Message> messages = amazonSQS.receiveMessage(request).getMessages();

            if (messages.isEmpty()) {
                return;
            }

            log.info("Recebidas {} mensagens da fila", messages.size());

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            List<DeleteMessageBatchRequestEntry> deleteEntries = new ArrayList<>();

            for (Message message : messages) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(
                        () -> processMessage(message), sqsExecutor);
                futures.add(future);
                deleteEntries.add(new DeleteMessageBatchRequestEntry(
                        message.getMessageId(), message.getReceiptHandle()));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            if (!deleteEntries.isEmpty()) {
                deleteMessagesBatch(deleteEntries);
            }

            int total = processedCount.addAndGet(messages.size());
            if (total % 1000 == 0) {
                log.info("Total de contas processadas: {}", total);
            }

        } catch (Exception e) {
            log.error("Erro ao consumir mensagens SQS: {}", e.getMessage());
        }
    }

    private void processMessage(Message message) {
        try {
            AccountCreatedEvent event = objectMapper.readValue(
                    message.getBody(), AccountCreatedEvent.class);

            AccountCreatedEvent.AccountInfo accountInfo = event.getAccount();

            accountService.createAccount(
                    accountInfo.getId(),
                    accountInfo.getOwner(),
                    accountInfo.getCreatedAt(),
                    accountInfo.getStatus()
            );

        } catch (Exception e) {
            log.error("Falha ao processar mensagem {}: {}", message.getMessageId(), e.getMessage());
        }
    }

    private void deleteMessagesBatch(List<DeleteMessageBatchRequestEntry> entries) {
        try {
            DeleteMessageBatchRequest batchRequest = new DeleteMessageBatchRequest(queueUrl, entries);
            amazonSQS.deleteMessageBatch(batchRequest);
        } catch (Exception e) {
            log.error("Erro ao deletar mensagens em lote: {}", e.getMessage());
        }
    }
}
