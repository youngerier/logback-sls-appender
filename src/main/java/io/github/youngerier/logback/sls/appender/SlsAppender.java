package io.github.youngerier.logback.sls.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.encoder.Encoder;
import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.LogItem;
import com.aliyun.openservices.log.request.PutLogsRequest;
import com.aliyun.openservices.log.response.PutLogsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logback appender for Alibaba Cloud SLS (Simple Log Service)
 * 阿里云日志服务 Logback Appender
 */
public class SlsAppender extends AppenderBase<ILoggingEvent> {

    // SLS 连接配置
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String project;
    private String logstore;
    private String source = "";
    private String topic = "";

    // 批量发送配置
    private int batchSize = 100;
    // 3秒
    private int flushInterval = 3000;

    private int maxRetries = 3;

    // 内部组件
    private Client slsClient;
    private Layout<ILoggingEvent> layout;
    private Encoder<ILoggingEvent> encoder;

    // 异步处理
    private BlockingQueue<ILoggingEvent> eventQueue;
    private Thread workerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void start() {
        if (started) {
            return;
        }

        // 验证必需配置
        if (endpoint == null || endpoint.trim().isEmpty()) {
            addError("SLS endpoint is required");
            return;
        }
        if (accessKeyId == null || accessKeyId.trim().isEmpty()) {
            addError("SLS accessKeyId is required");
            return;
        }
        if (accessKeySecret == null || accessKeySecret.trim().isEmpty()) {
            addError("SLS accessKeySecret is required");
            return;
        }
        if (project == null || project.trim().isEmpty()) {
            addError("SLS project is required");
            return;
        }
        if (logstore == null || logstore.trim().isEmpty()) {
            addError("SLS logstore is required");
            return;
        }

        try {
            // 初始化 SLS 客户端
            slsClient = new Client(endpoint, accessKeyId, accessKeySecret);

            // 初始化队列
            eventQueue = new LinkedBlockingQueue<>();

            // 启动工作线程
            running.set(true);
            workerThread = new Thread(this::processEvents, "SLS-Appender-Worker");
            workerThread.setDaemon(true);
            workerThread.start();

            super.start();
            addInfo("SLS Appender started successfully");

        } catch (Exception e) {
            addError("Failed to start SLS Appender", e);
        }
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }

        running.set(false);

        // 等待工作线程结束
        if (workerThread != null) {
            try {
                workerThread.interrupt();
                workerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 处理剩余的日志
        flushRemainingEvents();

        super.stop();
        addInfo("SLS Appender stopped");
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!started || !running.get()) {
            return;
        }

        try {
            // 将事件加入队列，如果队列满了则丢弃
            if (!eventQueue.offer(event)) {
                addWarn("Event queue is full, dropping log event");
            }
        } catch (Exception e) {
            addError("Failed to append event to queue", e);
        }
    }

    /**
     * 处理事件的工作线程方法
     */
    private void processEvents() {
        List<ILoggingEvent> batch = new ArrayList<>();
        long lastFlushTime = System.currentTimeMillis();

        while (running.get() || !eventQueue.isEmpty()) {
            try {
                // 从队列中获取事件
                ILoggingEvent event = eventQueue.poll(1000, TimeUnit.MILLISECONDS);
                if (event != null) {
                    batch.add(event);
                }

                // 检查是否需要发送批次
                long currentTime = System.currentTimeMillis();
                boolean shouldFlush = batch.size() >= batchSize ||
                        (currentTime - lastFlushTime >= flushInterval && !batch.isEmpty());

                if (shouldFlush) {
                    sendBatch(batch);
                    batch.clear();
                    lastFlushTime = currentTime;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                addError("Error in worker thread", e);
            }
        }

        // 发送剩余的批次
        if (!batch.isEmpty()) {
            sendBatch(batch);
        }
    }

    /**
     * 发送日志批次到 SLS
     */
    private void sendBatch(List<ILoggingEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        try {
            List<LogItem> logItems = new ArrayList<>();

            for (ILoggingEvent event : events) {
                LogItem logItem = createLogItem(event);
                if (logItem != null) {
                    logItems.add(logItem);
                }
            }

            if (!logItems.isEmpty()) {
                sendToSls(logItems);
            }

        } catch (Exception e) {
            addError("Failed to send batch to SLS", e);
        }
    }

    /**
     * 创建 SLS LogItem
     */
    private LogItem createLogItem(ILoggingEvent event) {
        try {
            LogItem logItem = new LogItem();
            logItem.SetTime((int) (event.getTimeStamp() / 1000));

            // 基本字段
            logItem.PushBack("level", event.getLevel().toString());
            logItem.PushBack("logger", event.getLoggerName());
            logItem.PushBack("thread", event.getThreadName());

            // 格式化消息
            String message;
            if (layout != null) {
                message = layout.doLayout(event);
            } else if (encoder != null) {
                message = new String(encoder.encode(event));
            } else {
                message = event.getFormattedMessage();
            }
            logItem.PushBack("message", message);

            // MDC 属性
            Map<String, String> mdcPropertyMap = event.getMDCPropertyMap();
            if (mdcPropertyMap != null && !mdcPropertyMap.isEmpty()) {
                for (Map.Entry<String, String> entry : mdcPropertyMap.entrySet()) {
                    logItem.PushBack("mdc." + entry.getKey(), entry.getValue());
                }
            }

            // 异常信息
            if (event.getThrowableProxy() != null) {
                logItem.PushBack("exception", event.getThrowableProxy().getClassName());
                logItem.PushBack("exception_message", event.getThrowableProxy().getMessage());

                // 堆栈跟踪
                StringBuilder stackTrace = new StringBuilder();
                for (int i = 0; i < event.getThrowableProxy().getStackTraceElementProxyArray().length; i++) {
                    stackTrace.append(event.getThrowableProxy().getStackTraceElementProxyArray()[i].toString());
                    if (i < event.getThrowableProxy().getStackTraceElementProxyArray().length - 1) {
                        stackTrace.append("\n");
                    }
                }
                logItem.PushBack("stack_trace", stackTrace.toString());
            }

            return logItem;

        } catch (Exception e) {
            addError("Failed to create LogItem", e);
            return null;
        }
    }

    /**
     * 发送到 SLS
     */
    private void sendToSls(List<LogItem> logItems) {
        int retries = 0;
        while (retries < maxRetries) {
            try {
                PutLogsRequest request = new PutLogsRequest(project, logstore, topic, source, logItems);
                PutLogsResponse response = slsClient.PutLogs(request);

                if (response != null) {
                    addInfo("Successfully sent " + logItems.size() + " log items to SLS");
                    return;
                }

            } catch (Exception e) {
                retries++;
                if (retries >= maxRetries) {
                    addError("Failed to send logs to SLS after " + maxRetries + " retries", e);
                } else {
                    addWarn("Failed to send logs to SLS, retrying (" + retries + "/" + maxRetries + ")", e);
                    try {
                        // 指数退避
                        Thread.sleep(1000L * retries);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    /**
     * 处理剩余的事件
     */
    private void flushRemainingEvents() {
        List<ILoggingEvent> remainingEvents = new ArrayList<>();
        eventQueue.drainTo(remainingEvents);

        if (!remainingEvents.isEmpty()) {
            addInfo("Flushing " + remainingEvents.size() + " remaining events");
            sendBatch(remainingEvents);
        }
    }

    // Getter and Setter methods
    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getLogstore() {
        return logstore;
    }

    public void setLogstore(String logstore) {
        this.logstore = logstore;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(int flushInterval) {
        this.flushInterval = flushInterval;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Layout<ILoggingEvent> getLayout() {
        return layout;
    }

    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    public Encoder<ILoggingEvent> getEncoder() {
        return encoder;
    }

    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }
}