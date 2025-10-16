package io.github.youngerier.logback.sls.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.LogItem;
import com.aliyun.openservices.log.exception.LogException;
import com.aliyun.openservices.log.request.PutLogsRequest;
import com.aliyun.openservices.log.common.Consts.CompressType;
import org.slf4j.Marker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SLS GZIP压缩日志Appender
 * 使用GZIP压缩替代protobuf，提供更好的兼容性和更小的依赖
 */
public class SlsGzipAppender extends AppenderBase<ILoggingEvent> {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String project;
    private String logStore;

    // 性能优化：批量处理相关配置
    private int batchSize = 100;
    private int queueSize = 10000;
    private int flushIntervalMs = 1000;

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
            addError("SlsGzipAppender initialization failed: missing required parameters");
            return;
        }

        try {
            this.client = new Client(endpoint, accessKeyId, accessKeySecret);
            this.logItemQueue = new ArrayBlockingQueue<>(queueSize);
            this.executorService = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "sls-gzip-appender");
                thread.setDaemon(true);
                return thread;
            });

            // 启动批处理线程
            isRunning.set(true);
            executorService.execute(this::processBatch);

            super.start();
            addInfo("SlsGzipAppender started successfully with GZIP compression");
        } catch (Exception e) {
            addError("SlsGzipAppender initialization failed", e);
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted() || client == null) {
            return;
        }

        try {
            LogItem item = new LogItem();
            
            // 基本日志信息
            String level = event.getLevel().toString();
            String thread = event.getThreadName();
            String logger = event.getLoggerName();
            String message = event.getFormattedMessage();

            // 异常信息处理
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy != null) {
                String throwable = ThrowableProxyUtil.asString(throwableProxy);
                item.PushBack("throwable", throwable);
            }

            // MDC上下文信息
            Map<String, String> mdc = event.getMDCPropertyMap();
            if (mdc != null && !mdc.isEmpty()) {
                for (Map.Entry<String, String> entry : mdc.entrySet()) {
                    if (entry.getValue() != null) {
                        item.PushBack("mdc." + entry.getKey(), entry.getValue());
                    }
                }
            }

            // Marker信息
            Optional.ofNullable(event.getMarkerList())
                    .filter(list -> !list.isEmpty())
                    .map(marks -> marks.stream().map(Marker::getName).toList())
                    .ifPresent(list -> item.PushBack("marker", String.join(",", list)));

            // 添加可读的日志字段
            item.PushBack("level", level);
            item.PushBack("thread", thread);
            item.PushBack("logger", logger);
            item.PushBack("message", message);

            // 使用格式化的时间而不是时间戳
            String formattedTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(event.getTimeStamp()));
            item.PushBack("time", formattedTime);

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
            // 关键改动：使用GZIP压缩替代默认的protobuf
            request.SetCompressType(CompressType.GZIP);
            client.PutLogs(request);
        } catch (LogException e) {
            addError("Failed to send logs to SLS with GZIP compression", e);
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
}