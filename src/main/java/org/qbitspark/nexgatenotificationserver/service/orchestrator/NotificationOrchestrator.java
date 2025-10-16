package org.qbitspark.nexgatenotificationserver.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.dto.NotificationEvent;
import org.qbitspark.nexgatenotificationserver.dto.Recipient;
import org.qbitspark.nexgatenotificationserver.service.batch.NotificationBatchProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOrchestrator {

    private final NotificationBatchProcessor batchProcessor;

    @Value("${notification.batch.size:15}")
    private int batchSize;

    public void process(NotificationEvent event) {
        String correlationId = UUID.randomUUID().toString();
        List<Recipient> allRecipients = event.getRecipients();

        log.info("🚀 Starting notification processing:");
        log.info("   Type: {}", event.getType());
        log.info("   Total Recipients: {}", allRecipients.size());
        log.info("   Channels: {}", event.getChannels());
        log.info("   Priority: {}", event.getPriority());
        log.info("   CorrelationId: {}", correlationId);

        // Split into batches
        List<List<Recipient>> batches = splitIntoBatches(allRecipients, batchSize);
        log.info("📦 Split into {} batches (size: {})", batches.size(), batchSize);

        // Process batches in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            int batchNumber = i + 1;
            List<Recipient> batch = batches.get(i);

            log.info("⚡ Dispatching batch #{} with {} recipients", batchNumber, batch.size());
            CompletableFuture<Void> future = batchProcessor.processBatch(
                    correlationId,
                    batchNumber,
                    batch,
                    event
            );
            futures.add(future);
        }

        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log.info("🎉 All batches completed for correlationId: {}", correlationId);
                })
                .exceptionally(ex -> {
                    log.error("❌ Error processing batches: {}", ex.getMessage(), ex);
                    return null;
                });
    }

    private <T> List<List<T>> splitIntoBatches(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(list.subList(i, end));
        }
        return batches;
    }
}