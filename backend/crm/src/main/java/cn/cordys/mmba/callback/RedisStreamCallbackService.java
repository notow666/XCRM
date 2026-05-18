package cn.cordys.mmba.callback;

import cn.cordys.common.constants.CrmLoggers;
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

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = CrmLoggers.MMBA_CALLBACK)
public class RedisStreamCallbackService implements SmartLifecycle {
    private static final String MESSAGE_DTO_FIELD = "ZZYAuditReceipt";
    private static final String MESSAGE_RAW_PAYLOAD_FIELD = "rawPayload";

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
    private static final String CONSUMER_NAME = "%s-%d";

    /** 单机进程内稳定，减少每次启动全新 consumer 名导致的孤儿 PEL */
    private final String consumerBaseId = buildConsumerBaseId();
    /** DLQ 元数据，区分写入实例 */
    private final String instanceTag = consumerBaseId + "_rs" + System.currentTimeMillis();

    private static final int STREAM_PARALLEL_CONSUMERS = 5;
    private static final int READ_BATCH_COUNT = 80;
    private static final int BATCH_FLUSH_SIZE = 32;
    private static final long BATCH_FLUSH_TIMEOUT_MS = 500L;
    private static final Duration READ_BLOCK = Duration.ofSeconds(3);
    private static final long BATCH_FUTURE_WAIT_SECONDS = 120L;
    private static final int MAX_PROCESS_ATTEMPTS = 3;

    private final AtomicInteger processingCount = new AtomicInteger(0);
    private final Object shutdownLock = new Object();

    /**
     * 单条消息在处理链上的 ACK 语义，供批量提交与 XACK 对齐。
     */
    private enum StreamMessageDisposition {
        /** 业务处理完成，需对当前 Stream entry XACK */
        ACK,
        /** 退避窗口内等，不 XACK，依赖 XREADGROUP id=0 再次拉回 PEL */
        PENDING_NO_ACK,
        /** 已在当前逻辑分支内 XACK（DLQ、重试尾写后 ACK 原消息等） */
        ALREADY_ACKED
    }

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

    private static String buildConsumerBaseId() {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // keep default
        }
        host = host.replaceAll("[^a-zA-Z0-9._-]", "_");
        String jvmId = ManagementFactory.getRuntimeMXBean().getName().replace(':', '_');
        return host + "_" + jvmId;
    }

    /**
     * 接收回调：异步写入 Stream，HTTP 线程快速返回。
     */
    public void callbackStream(JsonNode json) {
        CompletableFuture.runAsync(() -> writeToRedisStream(json), streamService)
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        log.error("[mmba-callback-queue] 异步写入队列任务失败（线程池拒绝或执行异常）", ex);
                    }
                });
    }

    private void writeToRedisStream(JsonNode json) {
        try {
            String rawPayload = json == null ? null : json.toString();
            Map<String, String> stringStringMap = tenantMetaService.listEnabledTenant();
            MmbaAuditRequest dto = MmbaAuditRequest.generate(json, stringStringMap);
            if(dto == null) {
                return;
            }
            Map<String, Object> message = new HashMap<>(8);
            message.put(MESSAGE_DTO_FIELD, JSON.toJSONString(dto));
            message.put(MESSAGE_RAW_PAYLOAD_FIELD, rawPayload);
            message.put("timestamp", System.currentTimeMillis());
            message.put("source", "callback_api");
            message.put("retryCount", 0);
            message.put("lastRetryTime", 0L);

            RecordId recordId = redisTemplate.opsForStream()
                    .add(StreamRecords.newRecord().in(STREAM_KEY).ofMap(message));

            log.info("[mmba-callback-queue] 已入队 streamId={}, behaviorType={}", recordId, dto.getBehaviorType());
        } catch (Exception e) {
            log.error("[mmba-callback-queue] 写入 Redis Stream 失败", e);
        }
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (!running) {
                log.info("[mmba-callback-queue] 启动 Stream 消费 consumerBaseId={}", consumerBaseId);
                try {
                    ensureStreamExists();

                    StreamInfo.XInfoGroups groups = redisTemplate.opsForStream().groups(STREAM_KEY);

                    boolean groupExists = groups.stream()
                            .anyMatch(group -> CONSUMER_GROUP.equals(group.groupName()));

                    if (!groupExists) {
                        redisTemplate.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
                        log.debug("[mmba-callback-queue] 已创建消费组: {}", CONSUMER_GROUP);
                    }
                    startStreamConsumers();
                    running = true;
                    log.info("[mmba-callback-queue] Stream 消费已启动: stream={}, group={}, parallelism={}, consumerBaseId={}",
                            STREAM_KEY, CONSUMER_GROUP, STREAM_PARALLEL_CONSUMERS, consumerBaseId);
                } catch (Exception e) {
                    running = false;
                    log.error("[mmba-callback-queue] 消费端启动失败（回调仍会尝试入队但不会被消费，请检查 Redis）", e);
                }
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
                log.debug("[mmba-callback-queue] 正在优雅停止 Stream 消费...");
                running = false;

                waitForPendingMessagesGracefully();

                shutdownExecutorService(mainExecutorService, "mainExecutorService", 10);
                shutdownExecutorService(streamService, "streamService", 10);
                shutdownExecutorService(consumerService, "consumerService", 10);

                log.info("[mmba-callback-queue] Stream 消费已停止");
            }
            callback.run();
        }
    }

    private void waitForPendingMessagesGracefully() {
        final long maxWaitMs = TimeUnit.SECONDS.toMillis(60);
        final long checkIntervalMs = 200L;

        log.debug("[mmba-callback-queue] 等待在途业务处理完成, processingCount={}", processingCount.get());

        long waitedMs = 0;
        long lastLogMs = 0;
        while (processingCount.get() > 0 && waitedMs < maxWaitMs) {
            try {
                synchronized (shutdownLock) {
                    shutdownLock.wait(checkIntervalMs);
                }
                waitedMs += checkIntervalMs;
                if (processingCount.get() > 0 && waitedMs - lastLogMs >= TimeUnit.SECONDS.toMillis(5)) {
                    lastLogMs = waitedMs;
                    log.debug("[mmba-callback-queue] 仍在等待在途消息, count={}, waitedMs={}",
                            processingCount.get(), waitedMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[mmba-callback-queue] 等待在途消息时被中断");
                break;
            }
        }

        if (processingCount.get() > 0) {
            log.warn("[mmba-callback-queue] 等待在途消息超时, 剩余 processingCount={}, 将强制关闭线程池",
                    processingCount.get());
        } else {
            log.debug("[mmba-callback-queue] 在途消息已全部结束");
        }
    }

    private void shutdownExecutorService(ExecutorService executor, String name, int timeoutSeconds) {
        if (executor == null || executor.isShutdown()) {
            return;
        }

        log.debug("Shutting down {}...", name);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("{} did not terminate within {} seconds, forcing shutdown", name, timeoutSeconds);
                executor.shutdownNow();

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

    @Override
    public boolean isRunning() {
        return running;
    }

    private void ensureStreamExists() {
        Boolean exists = redisTemplate.hasKey(STREAM_KEY);

        if (Boolean.FALSE.equals(exists)) {
            Map<String, Object> message = new HashMap<>(2);
            message.put("init", "stream_created");
            message.put("timestamp", System.currentTimeMillis());

            RecordId recordId = redisTemplate.opsForStream()
                    .add(StreamRecords.newRecord().in(STREAM_KEY).ofMap(message));
            log.debug("[mmba-callback-queue] 已创建 Stream '{}' 初始消息 id={}", STREAM_KEY, recordId);
        }
    }

    private void startStreamConsumers() {
        for (int i = 0; i < STREAM_PARALLEL_CONSUMERS; i++) {
            String consumerName = String.format(CONSUMER_NAME, consumerBaseId, i);
            streamService.submit(() -> consumeStreamWithBatch(consumerName));
        }
    }

    private void consumeStreamWithBatch(String consumerName) {
        log.debug("[mmba-callback-queue] 消费循环启动 consumer={}", consumerName);

        LinkedHashMap<String, MapRecord<String, Object, Object>> pendingRecordMap = new LinkedHashMap<>();
        long lastBatchTime = System.currentTimeMillis();

        while (!Thread.currentThread().isInterrupted() && running) {
            try {
                // 先拉本 consumer 的 PEL（含退避未 ACK 的消息），避免只靠 ">" 永远拿不到
                List<MapRecord<String, Object, Object>> pelBatch = Optional.ofNullable(
                        redisTemplate.opsForStream().read(
                                Consumer.from(CONSUMER_GROUP, consumerName),
                                StreamReadOptions.empty().count(READ_BATCH_COUNT),
                                StreamOffset.create(STREAM_KEY, ReadOffset.from("0"))))
                        .orElse(Collections.emptyList());

                if (!CollectionUtils.isEmpty(pelBatch)) {
                    mergePendingRecords(pendingRecordMap, pelBatch, consumerName, "PEL");
                    lastBatchTime = System.currentTimeMillis();
                    log.debug("[mmba-callback-queue] 自 PEL 拉取 {} 条 consumer={}", pelBatch.size(), consumerName);
                }

                List<MapRecord<String, Object, Object>> records = Optional.ofNullable(
                        redisTemplate.opsForStream().read(
                                Consumer.from(CONSUMER_GROUP, consumerName),
                                StreamReadOptions.empty().count(READ_BATCH_COUNT).block(READ_BLOCK),
                                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())))
                        .orElse(Collections.emptyList());

                if (!CollectionUtils.isEmpty(records)) {
                    mergePendingRecords(pendingRecordMap, records, consumerName, "NEW");
                    lastBatchTime = System.currentTimeMillis();
                    log.debug("[mmba-callback-queue] 自新消息拉取 {} 条 consumer={}", records.size(), consumerName);
                }

                long now = System.currentTimeMillis();
                boolean shouldProcess = !pendingRecordMap.isEmpty()
                        && (pendingRecordMap.size() >= BATCH_FLUSH_SIZE
                        || (now - lastBatchTime) >= BATCH_FLUSH_TIMEOUT_MS);

                if (shouldProcess) {
                    batchProcessRecords(new ArrayList<>(pendingRecordMap.values()), consumerName);
                    pendingRecordMap.clear();
                    lastBatchTime = now;
                }

            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    log.debug("[mmba-callback-queue] 消费线程被中断 consumer={}", consumerName);
                    break;
                }
                log.error("[mmba-callback-queue] 消费循环异常 consumer={}", consumerName, e);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (!pendingRecordMap.isEmpty()) {
            batchProcessRecords(new ArrayList<>(pendingRecordMap.values()), consumerName);
        }

        log.debug("[mmba-callback-queue] 消费循环结束 consumer={}", consumerName);
    }

    private void mergePendingRecords(LinkedHashMap<String, MapRecord<String, Object, Object>> pendingRecordMap,
                                     List<MapRecord<String, Object, Object>> sourceRecords,
                                     String consumerName,
                                     String sourceType) {
        int skipped = 0;
        for (MapRecord<String, Object, Object> record : sourceRecords) {
            String streamId = record.getId().getValue();
            MapRecord<String, Object, Object> previous = pendingRecordMap.putIfAbsent(streamId, record);
            if (previous != null) {
                skipped++;
            }
        }
        // XREADGROUP id=0 会在未 XACK 前每轮重复返回同一批 PEL；与本地 map 去重是预期行为，勿按条打 INFO
        if (skipped > 0 && log.isDebugEnabled()) {
            log.debug("[mmba-callback-queue] 合并去重: consumer={}, source={}, skippedDup={}, mapSize={}, incoming={}",
                    consumerName, sourceType, skipped, pendingRecordMap.size(), sourceRecords.size());
        }
    }

    private void batchProcessRecords(List<MapRecord<String, Object, Object>> records, String consumerName) {
        List<MapRecord<String, Object, Object>> validRecords = records.stream()
                .filter(record -> {
                    Map<Object, Object> value = record.getValue();
                    return !value.containsKey("init") && value.containsKey(MESSAGE_DTO_FIELD);
                })
                .collect(Collectors.toList());

        if (validRecords.isEmpty()) {
            acknowledgeRecords(records);
            return;
        }

        Map<MapRecord<String, Object, Object>, CompletableFuture<StreamMessageDisposition>> futureByRecord =
                new LinkedHashMap<>(validRecords.size());
        for (MapRecord<String, Object, Object> record : validRecords) {
            futureByRecord.put(record,
                    CompletableFuture.supplyAsync(
                            () -> processStreamRecord(record, consumerName),
                    consumerService));
        }

        CompletableFuture<?>[] all = futureByRecord.values().toArray(new CompletableFuture[0]);
        try {
            CompletableFuture.allOf(all).get(BATCH_FUTURE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("[mmba-callback-queue] 批次等待超时（{}s），按单条完成情况决定 ACK, consumer={}, batchSize={}",
                    BATCH_FUTURE_WAIT_SECONDS, consumerName, validRecords.size(), e);
        } catch (Exception e) {
            log.error("[mmba-callback-queue] 批次等待异常 consumer={}", consumerName, e);
        }

        int acked = 0;
        int pendingNoAck = 0;
        for (Map.Entry<MapRecord<String, Object, Object>, CompletableFuture<StreamMessageDisposition>> e
                : futureByRecord.entrySet()) {
            MapRecord<String, Object, Object> record = e.getKey();
            CompletableFuture<StreamMessageDisposition> future = e.getValue();
            try {
                if (!future.isDone()) {
                    log.warn("[mmba-callback-queue] 任务未完成不 ACK: streamId={}", record.getId().getValue());
                    continue;
                }
                if (future.isCompletedExceptionally()) {
                    log.warn("[mmba-callback-queue] 任务异常完成不 ACK: streamId={}", record.getId().getValue());
                    continue;
                }
                StreamMessageDisposition disposition = future.join();
                if (disposition == StreamMessageDisposition.ACK) {
                    acknowledgeRecord(record);
                    acked++;
                } else if (disposition == StreamMessageDisposition.PENDING_NO_ACK) {
                    pendingNoAck++;
                }
            } catch (Exception ex) {
                log.warn("[mmba-callback-queue] 解析单条处理结果失败不 ACK: streamId={}", record.getId().getValue(), ex);
            }
        }

        log.info("[mmba-callback-queue] 批次处理结束 consumer={}, valid={}, acked={}, pendingNoAck={}",
                consumerName, validRecords.size(), acked, pendingNoAck);

        records.stream()
                .filter(record -> !validRecords.contains(record))
                .forEach(this::acknowledgeRecord);
    }

    /**
     * 先从消费组确认，再从 Stream 中删除 entry，避免主队列无限增长占内存。
     * 顺序必须为 XACK 再 XDEL（先释放 PEL，再删数据）。
     */
    private void acknowledgeRecord(MapRecord<String, Object, Object> record) {
        RecordId id = record.getId();
        try {
            redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, id);
        } catch (Exception e) {
            log.error("[mmba-callback-queue] XACK 失败 streamId={}", id.getValue(), e);
            return;
        }
        try {
            Long removed = redisTemplate.opsForStream().delete(STREAM_KEY, id);
            if (removed == null || removed == 0) {
                log.debug("[mmba-callback-queue] XDEL 未删除到条目（可能已删） streamId={}", id.getValue());
            }
        } catch (Exception e) {
            log.error("[mmba-callback-queue] XACK 成功但 XDEL 失败 streamId={}", id.getValue(), e);
        }
    }

    private void acknowledgeRecords(List<MapRecord<String, Object, Object>> records) {
        if (records.isEmpty()) {
            return;
        }

        RecordId[] recordIds = records.stream()
                .map(MapRecord::getId)
                .toArray(RecordId[]::new);
        try {
            redisTemplate.opsForStream()
                    .acknowledge(STREAM_KEY, CONSUMER_GROUP, recordIds);
            log.debug("[mmba-callback-queue] 批量 XACK {} 条", recordIds.length);
        } catch (Exception e) {
            log.error("[mmba-callback-queue] 批量 XACK 失败，改为逐条", e);
            records.forEach(this::acknowledgeRecord);
            return;
        }
        try {
            Long removed = redisTemplate.opsForStream().delete(STREAM_KEY, recordIds);
            log.debug("[mmba-callback-queue] 批量 XDEL 请求 {} 条, removed={}", recordIds.length, removed);
        } catch (Exception e) {
            log.error("[mmba-callback-queue] 批量 XACK 成功但 XDEL 失败，将逐条补删", e);
            for (RecordId id : recordIds) {
                try {
                    redisTemplate.opsForStream().delete(STREAM_KEY, id);
                } catch (Exception ex) {
                    log.error("[mmba-callback-queue] 补删失败 streamId={}", id.getValue(), ex);
                }
            }
        }
    }

    /**
     * 处理单条 Stream 记录；不在 CompletableFuture 中向外抛异常，避免 allOf 语义混乱。
     */
    private StreamMessageDisposition processStreamRecord(MapRecord<String, Object, Object> record, String consumerName) {
        processingCount.incrementAndGet();
        String messageId = record.getId().getValue();
        try {
            Map<Object, Object> value = record.getValue();
            String dataJson = String.valueOf(value.get(MESSAGE_DTO_FIELD));
            String rawPayload = value.containsKey(MESSAGE_RAW_PAYLOAD_FIELD)
                    ? String.valueOf(value.get(MESSAGE_RAW_PAYLOAD_FIELD))
                    : null;

            if (!StringUtils.hasText(dataJson)) {
                log.warn("[mmba-callback-queue] 无业务载荷，送入 DLQ: streamId={}", messageId);
                handleFailedMessage(record, new IllegalArgumentException("empty ZZYAuditReceipt"));
                return StreamMessageDisposition.ALREADY_ACKED;
            }

            MmbaAuditRequest dto;
            try {
                dto = JSON.parseObject(dataJson, MmbaAuditRequest.class);
                dto.setRawPayload(rawPayload);
                dto.setStreamId(messageId);
                dto.setStreamConsumer(consumerName);
            } catch (Exception parseEx) {
                log.error("[mmba-callback-queue] 反序列化失败，送入 DLQ: streamId={}", messageId, parseEx);
                handleFailedMessage(record, parseEx);
                return StreamMessageDisposition.ALREADY_ACKED;
            }

            int retryCount = 0;
            if (value.containsKey("retryCount")) {
                retryCount = Integer.parseInt(String.valueOf(value.get("retryCount")));
            }

            long lastRetryTime = 0L;
            if (value.containsKey("lastRetryTime")) {
                lastRetryTime = Long.parseLong(String.valueOf(value.get("lastRetryTime")));
            }

            if (retryCount >= MAX_PROCESS_ATTEMPTS) {
                log.error("[mmba-callback-queue] 超过最大重试次数，送入 DLQ: streamId={}, retryCount={}",
                        messageId, retryCount);
                handleFailedMessage(record, new Exception("Max retries exceeded"));
                return StreamMessageDisposition.ALREADY_ACKED;
            }

            if (retryCount > 0 && lastRetryTime > 0) {
                long delay = calculateBackoffDelay(retryCount);
                long elapsed = System.currentTimeMillis() - lastRetryTime;
                if (elapsed < delay) {
                    log.debug("[mmba-callback-queue] 退避中不 ACK，待 PEL 再次拉回: streamId={}, remainingMs={}, consumer={}",
                            messageId, delay - elapsed, consumerName);
                    return StreamMessageDisposition.PENDING_NO_ACK;
                }
            }

            try {
                log.debug("[mmba-callback-queue] 准备执行业务 streamId={}, consumer={}, behaviorType={}, retryCount={}, dataCount={}",
                        messageId, consumerName, dto.getBehaviorType(), retryCount,
                        dto.getData() == null ? 0 : dto.getData().size());
                executeWithTenantContext(messageId, dto, retryCount);
                log.debug("[mmba-callback-queue] 消费成功: streamId={}, behaviorType={}, consumer={}",
                        messageId, dto.getBehaviorType(), consumerName);
                return StreamMessageDisposition.ACK;
            } catch (Exception businessEx) {
                log.warn("[mmba-callback-queue] 业务失败，将重试或 DLQ: streamId={}", messageId, businessEx);
                return handleProcessingFailureDisposition(record, businessEx);
            }
        } catch (Exception e) {
            log.error("[mmba-callback-queue] 处理异常: streamId={}", messageId, e);
            return handleProcessingFailureDisposition(record, e);
        } finally {
            int remaining = processingCount.decrementAndGet();
            synchronized (shutdownLock) {
                if (remaining == 0 && !running) {
                    shutdownLock.notifyAll();
                }
            }
        }
    }

    private long calculateBackoffDelay(int retryCount) {
        return (long) Math.pow(2, retryCount - 1) * 1000;
    }

    private StreamMessageDisposition handleProcessingFailureDisposition(MapRecord<String, Object, Object> record,
                                                                          Exception e) {
        String messageId = record.getId().getValue();
        Map<Object, Object> value = record.getValue();

        try {
            int retryCount = 0;
            if (value.containsKey("retryCount")) {
                retryCount = Integer.parseInt(String.valueOf(value.get("retryCount")));
            }
            retryCount++;

            Map<String, Object> updatedMessage = new HashMap<>();
            for (Map.Entry<Object, Object> entry : value.entrySet()) {
                updatedMessage.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            updatedMessage.put("retryCount", retryCount);
            updatedMessage.put("lastRetryTime", System.currentTimeMillis());
            updatedMessage.put("lastError", e.getMessage());

            RecordId newRecordId = redisTemplate.opsForStream()
                    .add(StreamRecords.newRecord().in(STREAM_KEY).ofMap(updatedMessage));

            acknowledgeRecord(record);

            log.debug("[mmba-callback-queue] 已写重试尾消息并 XACK 原消息: oldId={}, newId={}, attempt={}",
                    messageId, newRecordId != null ? newRecordId.getValue() : "null", retryCount);
            return StreamMessageDisposition.ALREADY_ACKED;
        } catch (Exception ex) {
            log.error("[mmba-callback-queue] 调度重试失败，转入 DLQ: streamId={}", messageId, ex);
            handleFailedMessage(record, e);
            return StreamMessageDisposition.ALREADY_ACKED;
        }
    }

    private void executeWithTenantContext(String messageId, MmbaAuditRequest dto, int retryCount) {
        ZZYConsumerService consumer = abstractZZYConsumerMap.get(
                MmbaBehaviorTypes.SUPPORTED.get(dto.getBehaviorType()));
        if (consumer != null) {
            log.debug("[mmba-callback-queue] 分发消费者 streamId={}, consumer={}, group={}, behaviorType={}, retryCount={}",
                    messageId, dto.getStreamConsumer(), consumer.group(), dto.getBehaviorType(), retryCount);
            consumer.mainProcess(dto);
            log.debug("[mmba-callback-queue] 业务处理结束: streamId={}, retryCount={}", messageId, retryCount);
        } else {
            log.warn("[mmba-callback-queue] 无对应消费者仍确认消息（避免阻塞通道）: streamId={}, behaviorType={}",
                    messageId, dto.getBehaviorType());
        }
    }

    private void handleFailedMessage(MapRecord<String, Object, Object> record, Exception e) {
        try {
            Map<Object, Object> value = record.getValue();
            String messageId = record.getId().getValue();

            Map<String, Object> dlqMessage = new HashMap<>();
            value.forEach((key, val) -> dlqMessage.put(String.valueOf(key), val));
            dlqMessage.put("error", e.getMessage());
            dlqMessage.put("failedAt", System.currentTimeMillis());
            dlqMessage.put("failedFrom", instanceTag);

            RecordId dlqId = redisTemplate.opsForStream().add(DLQ_KEY, dlqMessage);
            acknowledgeRecord(record);

            log.error("[mmba-callback-queue] 已进入死信: streamId={}, dlqId={}, err={}",
                    messageId, dlqId != null ? dlqId.getValue() : "null", e.getMessage());
        } catch (Exception ex) {
            log.error("[mmba-callback-queue] 写入死信失败 streamId={}", record.getId().getValue(), ex);
        }
    }
}
