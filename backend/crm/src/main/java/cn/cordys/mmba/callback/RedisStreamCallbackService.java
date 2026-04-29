package cn.cordys.mmba.callback;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.mmba.MmbaBehaviorTypes;
import cn.cordys.mmba.dto.MmbaAuditRequest;
import cn.cordys.tenant.service.TenantMetaService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RedisStreamCallbackService implements SmartLifecycle {

    private volatile boolean running = false;
    private final Object lifecycleLock = new Object();
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExecutorService mainExecutorService;
    private final ExecutorService streamService;
    private final ExecutorService consumerService;
    private final Map<String, ZZYConsumerService> abstractZZYConsumerMap;
    private final TenantMetaService tenantMetaService;

    private static final String STREAM_KEY = "mmba:callback:stream";
    private static final String DLQ_KEY = "mmba:callback:dlq";
    private static final String CONSUMER_GROUP = "callback_processor";
    private static final String CONSUMER_NAME = "processor_%s";

    // 实例唯一标识
    private final String instanceId = "rs" + System.currentTimeMillis();

    private final AtomicInteger processingCount = new AtomicInteger(0);
    private final Object shutdownLock = new Object();

    public RedisStreamCallbackService(@Qualifier("callbackMainTaskExecutor") ExecutorService mainExecutorService,
                                      @Qualifier("redisStreamTemplate") RedisTemplate<String, Object> redisTemplate,
                                      @Qualifier("callbackStreamTaskExecutor") ExecutorService streamService,
                                      @Qualifier("callbackConsumerTaskExecutor") ExecutorService consumerService,
                                      List<ZZYConsumerService> abstractZZYConsumerList, TenantMetaService tenantMetaService) {
        this.redisTemplate = redisTemplate;
        this.streamService = streamService;
        this.consumerService = consumerService;
        this.mainExecutorService = mainExecutorService;
        this.tenantMetaService = tenantMetaService;
        this.abstractZZYConsumerMap = new HashMap<>();
        for (ZZYConsumerService consumer : abstractZZYConsumerList) {
            abstractZZYConsumerMap.put(consumer.group(), consumer);
        }
    }

    /**
     * 接收回调接口 - 轻量级改造
     */
    public void callbackStream(JsonNode json) {
        CompletableFuture.runAsync(() -> writeToRedisStream(json), streamService);
    }

    /**
     * 写入Redis Stream
     */
    private void writeToRedisStream(JsonNode json) {
        try {
            Map<String, String> stringStringMap = tenantMetaService.listEnabledTenant();
            MmbaAuditRequest dto = MmbaAuditRequest.generate(json, stringStringMap);
            if(CollectionUtils.isEmpty(dto.getData())) {
                log.debug("MmbaAuditRequest invalid: {}", JSON.toJSONString(json));
            }
            else if(MmbaBehaviorTypes.isSupported(dto.getBehaviorType())) {
                Map<String, Object> message = new HashMap<>(3);
                message.put("ZZYAuditReceipt", JSON.toJSONString(dto));
                message.put("timestamp", System.currentTimeMillis());
                message.put("source", "callback_api");
                message.put("retryCount", 0);
                message.put("lastRetryTime", 0L);

                // 使用Redis Stream
                RecordId recordId = redisTemplate.opsForStream()
                        .add(StreamRecords.newRecord().in(STREAM_KEY).ofMap(message));

                log.debug("Message added to stream: {}", recordId);
            }
            else {
                log.debug("BehaviorType invalid: {}", dto.getBehaviorType());
            }
        } catch (Exception e) {
            log.error("Failed to write to Redis Stream", e);
            // 写入日志
        }
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (!running) {
                log.info("Starting Redis Stream consumer service...");

                try {
                    ensureStreamExists();

                    StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(STREAM_KEY);

                    boolean groupExists = groups.stream()
                            .anyMatch(group -> CONSUMER_GROUP.equals(group.groupName()));

                    if (!groupExists) {
                        redisTemplate.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
                        log.debug("Created consumer group: {}", CONSUMER_GROUP);
                    }
                    // 启动消费者
                    startStreamConsumers();
                } catch (Exception e) {
                    log.error("Consumer group may already exist or Redis not ready", e);
                }

                running = true;
                log.info("Redis Stream consumer service started");
            }
        }
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        synchronized (lifecycleLock) {
            if (running) {
                log.info("Stopping Redis Stream consumer service gracefully...");
                running = false;

                waitForPendingMessagesGracefully();

                // 关闭调度器
                shutdownExecutorService(mainExecutorService, "mainExecutorService", 10);
                shutdownExecutorService(streamService, "streamService", 10);
                shutdownExecutorService(consumerService, "consumerService", 10);

                // 关闭 Redis 连接池
                closeRedisConnectionPool();

                log.info("Redis Stream consumer service stopped");
            }
            callback.run();
        }
    }

    private void waitForPendingMessagesGracefully() {
        int maxWaitSeconds = 30;
        int checkInterval = 100; // 100ms

        log.info("Waiting for {} pending messages to complete...", processingCount.get());

        int waitedSeconds = 0;
        while (processingCount.get() > 0 && waitedSeconds < maxWaitSeconds) {
            try {
                synchronized (shutdownLock) {
                    shutdownLock.wait(checkInterval);
                }
                waitedSeconds++;
                if (processingCount.get() > 0 && waitedSeconds % 10 == 0) {
                    log.info("Still waiting for {} messages to complete, waited {} seconds",
                            processingCount.get(), waitedSeconds);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for pending messages");
                break;
            }
        }

        if (processingCount.get() > 0) {
            log.warn("Timeout waiting for {} pending messages, forcing shutdown", processingCount.get());
        } else {
            log.info("All pending messages completed");
        }
    }

    /**
     * 方案4：优雅关闭线程池
     */
    private void shutdownExecutorService(ExecutorService executor, String name, int timeoutSeconds) {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        log.info("Shutting down {}...", name);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("{} did not terminate within {} seconds, forcing shutdown", name, timeoutSeconds);
                executor.shutdownNow();

                // 等待强制关闭后的清理
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.error("{} failed to terminate", name);
                }
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while shutting down {}", name);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void closeRedisConnectionPool() {
        try {
            Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().close();
            log.info("Redis connection pool closed");
        } catch (Exception e) {
            log.warn("Error closing Redis connection pool", e);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void ensureStreamExists() {
        // 检查 Stream 是否存在
        Boolean exists = redisTemplate.hasKey(STREAM_KEY);

        if (Boolean.FALSE.equals(exists)) {
            Map<String, Object> message = new HashMap<>(2);
            message.put("init", "stream_created");
            message.put("timestamp", System.currentTimeMillis());

            // 使用Redis Stream
            RecordId recordId = redisTemplate.opsForStream()
                    .add(StreamRecords.newRecord().in(STREAM_KEY).ofMap(message));
            log.info("Created empty stream '{}' with initial message ID: {}", STREAM_KEY, recordId);
        }
    }

    /**
     * 启动多个Stream消费者
     */
    private void startStreamConsumers() {
        for (int i = 0; i < 5; i++) {
            String consumerName = String.format(CONSUMER_NAME, instanceId + "_" + i);
            streamService.submit(() -> consumeStreamWithBatch(consumerName));
        }
    }

    private void consumeStreamWithBatch(String consumerName) {
        log.debug("Stream consumer started: {}", consumerName);

        // 批量收集消息
        List<MapRecord<String, Object, Object>> pendingRecords = new ArrayList<>();
        long lastBatchTime = System.currentTimeMillis();
        final long BATCH_TIMEOUT_MS = 1000; // 1秒超时

        while (!Thread.currentThread().isInterrupted() && running) {
            try {
                // 1. 从Stream读取消息
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                        Consumer.from(CONSUMER_GROUP, consumerName),
                        StreamReadOptions.empty()
                                .count(50)
                                .block(Duration.ofSeconds(2)),
                        StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

                // 2. 如果有消息，添加到批量队列
                if (!CollectionUtils.isEmpty(records)) {
                    pendingRecords.addAll(records);
                    lastBatchTime = System.currentTimeMillis();
                    log.debug("Collected {} records, total pending: {}", records.size(), pendingRecords.size());
                }

                // 3. 达到批量大小或超时，批量处理
                long now = System.currentTimeMillis();
                boolean shouldProcess = !pendingRecords.isEmpty() &&
                        (pendingRecords.size() >= 20 || (now - lastBatchTime) >= BATCH_TIMEOUT_MS);

                if (shouldProcess) {
                    batchProcessRecords(pendingRecords, consumerName);
                    pendingRecords.clear();
                    lastBatchTime = now;
                }

                // 短暂休眠避免空转
                if (CollectionUtils.isEmpty(records) && pendingRecords.isEmpty()) {
                    Thread.sleep(100);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Stream consumer {} interrupted", consumerName);
                break;
            } catch (Exception e) {
                log.error("Stream consumer error", e);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 处理剩余的消息
        if (!pendingRecords.isEmpty()) {
            batchProcessRecords(pendingRecords, consumerName);
        }

        log.debug("Stream consumer stopped: {}", consumerName);
    }

    /**
     * 批量处理记录
     */
    private void batchProcessRecords(List<MapRecord<String, Object, Object>> records, String consumerName) {
        log.debug("Batch processing {} records", records.size());

        // 过滤掉无效记录
        List<MapRecord<String, Object, Object>> validRecords = records.stream()
                .filter(record -> {
                    Map<Object, Object> value = record.getValue();
                    return !value.containsKey("init") && value.containsKey("ZZYAuditReceipt");
                })
                .collect(Collectors.toList());

        if (validRecords.isEmpty()) {
            // 确认所有记录（包括init消息）
            acknowledgeRecords(records);
            return;
        }

        // 批量提交任务
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Map<MapRecord<String, Object, Object>, CompletableFuture<Void>> recordFutureMap = new HashMap<>();

        for (MapRecord<String, Object, Object> record : validRecords) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                processStreamRecordSync(record, consumerName);
            }, consumerService);
            futures.add(future);
            recordFutureMap.put(record, future);
        }

        // 等待所有任务完成或超时
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);

            // 所有任务成功，批量确认
            acknowledgeRecords(validRecords);

        } catch (Exception e) {
            log.error("Batch processing failed, checking individual records", e);

            // 部分失败，逐个检查并确认成功的消息
            for (MapRecord<String, Object, Object> record : validRecords) {
                CompletableFuture<Void> future = recordFutureMap.get(record);
                if (future != null && future.isDone() && !future.isCompletedExceptionally()) {
                    // 任务成功完成，确认消息
                    acknowledgeRecord(record);
                } else {
                    // 任务失败，不确认，等待重试
                    log.warn("Record processing failed, will retry: {}", record.getId().getValue());
                }
            }
        }

        // 确认非业务消息（如init消息）
        records.stream()
                .filter(record -> !validRecords.contains(record))
                .forEach(this::acknowledgeRecord);
    }

    /**
     * 单个确认消息
     */
    private void acknowledgeRecord(MapRecord<String, Object, Object> record) {
        try {
            redisTemplate.opsForStream()
                    .acknowledge(STREAM_KEY, CONSUMER_GROUP, record.getId());
        } catch (Exception e) {
            log.error("Failed to acknowledge record: {}", record.getId().getValue(), e);
        }
    }

    /**
     * 批量确认消息
     */
    private void acknowledgeRecords(List<MapRecord<String, Object, Object>> records) {
        if (records.isEmpty()) {
            return;
        }

        try {
            RecordId[] recordIds = records.stream()
                    .map(MapRecord::getId)
                    .toArray(RecordId[]::new);
            redisTemplate.opsForStream()
                    .acknowledge(STREAM_KEY, CONSUMER_GROUP, recordIds);
            log.debug("Acknowledged {} records", recordIds.length);
        } catch (Exception e) {
            log.error("Failed to batch acknowledge records", e);
            // 失败时逐个确认
            records.forEach(this::acknowledgeRecord);
        }
    }

    /**
     * 同步处理Stream记录（支持重试）
     */
    private void processStreamRecordSync(MapRecord<String, Object, Object> record, String consumerName) {
        // 增加处理计数
        processingCount.incrementAndGet();

        String messageId = record.getId().getValue();
        Map<Object, Object> value = record.getValue();

        try {
            // 解析消息
            String dataJson = String.valueOf(value.get("ZZYAuditReceipt"));

            if(!StringUtils.hasText(dataJson)){
                log.warn("Empty data in message: {}", messageId);
                return;
            }

            MmbaAuditRequest dto = JSON.parseObject(dataJson, MmbaAuditRequest.class);

            // 获取重试信息
            int retryCount = 0;
            if (value.containsKey("retryCount")) {
                retryCount = Integer.parseInt(String.valueOf(value.get("retryCount")));
            }

            long lastRetryTime = 0L;
            if (value.containsKey("lastRetryTime")) {
                lastRetryTime = Long.parseLong(String.valueOf(value.get("lastRetryTime")));
            }

            // 检查是否达到最大重试次数
            int maxRetries = 3;
            if (retryCount >= maxRetries) {
                log.error("Message exceeded max retries ({}): {}", maxRetries, messageId);
                handleFailedMessage(record, new Exception("Max retries exceeded"));
                return;
            }

            // 检查延迟重试时间（指数退避）
            if (retryCount > 0 && lastRetryTime > 0) {
                long delay = calculateBackoffDelay(retryCount);
                long elapsed = System.currentTimeMillis() - lastRetryTime;
                if (elapsed < delay) {
                    log.debug("Message {} in retry delay, remaining: {}ms", messageId, delay - elapsed);
                    return; // 未到重试时间，稍后处理
                }
            }

            // 先执行业务逻辑，成功后再确认（在批量处理中确认）
            executeWithTenantContext(messageId, dto, retryCount);

        } catch (Exception e) {
            log.error("Process stream record failed: {}", messageId, e);
            // 处理失败，更新重试信息
            handleProcessingFailure(record, e);
        } finally {
            // 减少处理计数并通知
            int remaining = processingCount.decrementAndGet();
            synchronized (shutdownLock) {
                if (remaining == 0 && !running) {
                    shutdownLock.notifyAll();
                }
            }
        }
    }

    /**
     * 计算指数退避延迟时间
     */
    private long calculateBackoffDelay(int retryCount) {
        // 指数退避：1s, 2s, 4s, 8s
        return (long) Math.pow(2, retryCount - 1) * 1000;
    }

    /**
     * 处理失败，更新重试信息到Stream
     */
    private void handleProcessingFailure(MapRecord<String, Object, Object> record, Exception e) {
        String messageId = record.getId().getValue();
        Map<Object, Object> value = record.getValue();

        try {
            int retryCount = 0;
            if (value.containsKey("retryCount")) {
                retryCount = Integer.parseInt(String.valueOf(value.get("retryCount")));
            }

            retryCount++;

            // 方案2：构建更新后的消息
            Map<String, Object> updatedMessage = new HashMap<>();
            for (Map.Entry<Object, Object> entry : value.entrySet()) {
                updatedMessage.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            updatedMessage.put("retryCount", retryCount);
            updatedMessage.put("lastRetryTime", System.currentTimeMillis());
            updatedMessage.put("lastError", e.getMessage());

            // 添加新消息到Stream（保留原消息，通过重试次数控制）
            RecordId newRecordId = redisTemplate.opsForStream()
                    .add(StreamRecords.newRecord().in(STREAM_KEY).ofMap(updatedMessage));

            log.info("Message {} retry scheduled (attempt {}), new id: {}",
                    messageId, retryCount, newRecordId);

            // 方案2：删除原消息或等待超时自动清理
            // 注意：这里不删除原消息，让它在超时后重新投递

        } catch (Exception ex) {
            log.error("Failed to update retry info for message: {}", messageId, ex);
            // 方案2：更新失败，记录到死信队列
            handleFailedMessage(record, e);
        }
    }

    /**
     * 方案1：在租户上下文中执行业务逻辑
     */
    private void executeWithTenantContext(String messageId, MmbaAuditRequest dto, int retryCount) {
        try {
            // 执行业务逻辑
            ZZYConsumerService consumer = abstractZZYConsumerMap.get(
                    MmbaBehaviorTypes.SUPPORTED.get(dto.getBehaviorType()));
            if (consumer != null) {
                consumer.mainProcess(dto);
                log.debug("Message processed successfully: {}, retryCount: {}", messageId, retryCount);
            } else {
                log.warn("No consumer found for behavior type: {}", dto.getBehaviorType());
            }
        } catch (Exception e) {
            log.error("Business logic failed: {}", messageId, e);
            throw new RuntimeException("Business logic execution failed", e);
        }
    }

    /**
     * 处理失败的消息（死信队列）
     */
    private void handleFailedMessage(MapRecord<String, Object, Object> record, Exception e) {
        try {
            Map<Object, Object> value = record.getValue();
            String messageId = record.getId().getValue();

            log.error("Moving message to DLQ: id={}, error={}", messageId, e.getMessage());

            // 写入死信Stream
            Map<String, Object> dlqMessage = new HashMap<>();
            value.forEach((key, val) -> {
                dlqMessage.put(String.valueOf(key), val);
            });
            dlqMessage.put("error", e.getMessage());
            dlqMessage.put("failedAt", System.currentTimeMillis());
            dlqMessage.put("failedFrom", instanceId);

            redisTemplate.opsForStream().add(DLQ_KEY, dlqMessage);

            // 确认原消息，避免无限重试
            acknowledgeRecord(record);

        } catch (Exception ex) {
            log.error("Handle failed message error", ex);
        }
    }
}
