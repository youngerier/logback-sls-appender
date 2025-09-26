package io.github.youngerier.logback.sls.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.LogItem;
import com.aliyun.openservices.log.exception.LogException;
import com.aliyun.openservices.log.request.PutLogsRequest;
import io.github.youngerier.logback.sls.proto.LogMessage;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlsProtobufAppender extends AppenderBase<ILoggingEvent> {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String project;
    private String logStore;

    // 性能优化：批量处理相关配置
    private int batchSize = 100;
    private int queueSize = 10000;
    private int flushIntervalMs = 1000;
    private boolean useBase64Encoding = true;

    private transient Client client;
    private transient BlockingQueue<LogItem> logItemQueue;
    private transient ExecutorService executorService;
    private final transient AtomicBoolean isRunning = new AtomicBoolean(false);

    // 添加自定义的isEmpty方法替代StringUtils
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    @Override
    public void start() {
        // 边界条件检查：验证必要参数
        if (isEmpty(endpoint) || isEmpty(accessKeyId) ||
                isEmpty(accessKeySecret) || isEmpty(project) ||
                isEmpty(logStore)) {
            addError("SlsProtobufAppender initialization failed: missing required parameters");
            return;
        }

        try {
            this.client = new Client(endpoint, accessKeyId, accessKeySecret);
            this.logItemQueue = new ArrayBlockingQueue<>(queueSize);
            this.executorService = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "sls-log-appender");
                thread.setDaemon(true);
                return thread;
            });

            // 启动批处理线程
            isRunning.set(true);
            executorService.execute(this::processBatch);

            super.start();
            addInfo("SlsProtobufAppender started successfully");
        } catch (Exception e) {
            addError("SlsProtobufAppender initialization failed", e);
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        // 边界条件检查：验证appender是否正常运行
        if (!isStarted() || client == null || !isRunning.get()) {
            return;
        }

        try {
            // 边界条件检查：验证事件不为空
            if (event == null) {
                return;
            }

            // 构建protobuf对象
            LogMessage.Builder builder = LogMessage.newBuilder();

            // 边界条件检查：验证各字段不为空
            String level = event.getLevel() != null ? event.getLevel().toString() : "UNKNOWN";
            String thread = event.getThreadName() != null ? event.getThreadName() : "";
            String logger = event.getLoggerName() != null ? event.getLoggerName() : "";
            String message = event.getFormattedMessage() != null ? event.getFormattedMessage() : "";

            LogMessage logMessage = builder
                    .setLevel(level)
                    .setThread(thread)
                    .setLogger(logger)
                    .setMessage(message)
                    .setTimestamp(event.getTimeStamp())
                    .build();

            byte[] data = logMessage.toByteArray();

            // 封装为 SLS LogItem
            LogItem item = new LogItem();

            // 性能优化：使用Base64编码二进制数据
            if (useBase64Encoding) {
                item.PushBack("protobuf", Base64.getEncoder().encodeToString(data));
            } else {
                // Always use Base64 encoding to avoid data corruption
                item.PushBack("protobuf",new String(data));
            }

            // 性能优化：使用队列和批处理
            boolean offered = logItemQueue.offer(item);
            if (!offered) {
                addWarn("Log queue is full, dropping log event");
            }
        } catch (Exception e) {
            addError("Failed to process log event", e);
        }
    }

    // 性能优化：批量处理日志
    private void processBatch() {
        List<LogItem> batch = new ArrayList<>(batchSize);

        while (isRunning.get()) {
            try {
                LogItem item = logItemQueue.poll(flushIntervalMs, TimeUnit.MILLISECONDS);

                if (item != null) {
                    batch.add(item);
                }

                // 当达到批处理大小或超时时发送
                if (batch.size() >= batchSize || (!batch.isEmpty() && item == null)) {
                    sendBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                addWarn("Log processing thread interrupted", e);
            } catch (Exception e) {
                addError("Error in batch processing", e);
            }
        }

        // 关闭时发送剩余日志
        if (!batch.isEmpty()) {
            sendBatch(batch);
        }
    }

    private void sendBatch(List<LogItem> batch) {
        if (batch.isEmpty() || client == null) {
            return;
        }

        try {
            PutLogsRequest request = new PutLogsRequest(project, logStore, "", batch);
            client.PutLogs(request);
        } catch (LogException e) {
            addError("Failed to send logs to SLS", e);
        }
    }

    @Override
    public void stop() {
        isRunning.set(false);
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        super.stop();
    }

    // getter/setter for logback.xml 配置
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public void setLogStore(String logStore) {
        this.logStore = logStore;
    }

    // 性能优化参数配置
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize > 0 ? batchSize : 100;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize > 0 ? queueSize : 10000;
    }

    public void setFlushIntervalMs(int flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs > 0 ? flushIntervalMs : 1000;
    }

    public void setUseBase64Encoding(boolean useBase64Encoding) {
        this.useBase64Encoding = useBase64Encoding;
    }
}
