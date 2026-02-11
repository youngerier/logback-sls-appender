package io.github.youngerier.logback.sls.appender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.Consts.CompressType;
import com.aliyun.openservices.log.common.LogItem;
import com.aliyun.openservices.log.request.PutLogsRequest;
import com.aliyun.openservices.log.response.PutLogsResponse;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.encoder.Encoder;

/**
 * 高性能的 Logback Appender，用于将应用日志异步发送到阿里云日志服务 (SLS)
 * 
 * <p>主要特性：</p>
 * <ul>
 *   <li>异步批量发送，提高性能</li>
 *   <li>支持自动重试机制</li>
 *   <li>支持 MDC 上下文传递</li>
 *   <li>支持异常堆栈跟踪</li>
 *   <li>可配置的批量大小和刷新间隔</li>
 *   <li>优雅关闭处理</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * <appender name="SLS" class="io.github.youngerier.logback.sls.appender.SlsAppender">
 *     <endpoint>https://cn-hangzhou.log.aliyuncs.com</endpoint>
 *     <accessKeyId>your-access-key-id</accessKeyId>
 *     <accessKeySecret>your-access-key-secret</accessKeySecret>
 *     <project>your-project</project>
 *     <logstore>your-logstore</logstore>
 *     <batchSize>100</batchSize>
 *     <flushInterval>5000</flushInterval>
 * </appender>
 * }</pre>
 * 
 * @author youngerier
 * @version 1.0.0
 * @since 1.0.0
 */
public class SlsAppender extends AppenderBase<ILoggingEvent> {

    // ==================== 常量定义 ====================
    
    /** 默认批量大小 */
    private static final int DEFAULT_BATCH_SIZE = 100;
    
    /** 默认刷新间隔（毫秒） */
    private static final int DEFAULT_FLUSH_INTERVAL = 3000;
    
    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRIES = 3;
    
    /** 默认队列容量 */
    private static final int DEFAULT_QUEUE_CAPACITY = 10000;
    
    /** 工作线程名称 */
    private static final String WORKER_THREAD_NAME = "SLS-Appender-Worker";
    
    /** 工作线程关闭超时时间（毫秒） */
    private static final long WORKER_SHUTDOWN_TIMEOUT = 5000L;
    
    /** 队列轮询超时时间（毫秒） */
    private static final long QUEUE_POLL_TIMEOUT = 1000L;
    
    /** 重试基础延迟时间（毫秒） */
    private static final long RETRY_BASE_DELAY = 1000L;
    
    /** MDC 字段前缀 */
    private static final String MDC_PREFIX = "mdc.";
    

    
    // ==================== SLS 连接配置 ====================
    
    /** SLS 服务端点 */
    private String endpoint;
    
    /** 阿里云 AccessKey ID */
    private String accessKeyId;
    
    /** 阿里云 AccessKey Secret */
    private String accessKeySecret;
    
    /** SLS 项目名称 */
    private String project;
    
    /** SLS 日志库名称 */
    private String logstore;
    
    /** 日志来源标识 */
    private String source = "";
    
    /** 日志主题标识 */
    private String topic = "";

    // ==================== 性能配置参数 ====================
    
    /** 批量发送大小 */
    private int batchSize = DEFAULT_BATCH_SIZE;
    
    /** 刷新间隔（毫秒） */
    private int flushInterval = DEFAULT_FLUSH_INTERVAL;

    /** 最大重试次数 */
    private int maxRetries = DEFAULT_MAX_RETRIES;
    
    // 队列容量
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
    
    // 压缩类型配置
    private CompressType compressionType = CompressType.LZ4;

    // ==================== 内部组件 ====================
    
    /** SLS 客户端 */
    private volatile Client slsClient;
    
    /** 日志布局器 */
    private Layout<ILoggingEvent> layout;
    
    /** 日志编码器 */
    private Encoder<ILoggingEvent> encoder;

    // ==================== 异步处理组件 ====================
    
    /** 事件队列 */
    private volatile BlockingQueue<ILoggingEvent> eventQueue;
    
    /** 工作线程 */
    private volatile Thread workerThread;
    
    /** 运行状态标识 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ==================== 生命周期管理 ====================
    
    /**
     * 启动 SLS Appender
     * 
     * <p>执行以下初始化步骤：</p>
     * <ol>
     *   <li>验证必需的配置参数</li>
     *   <li>初始化 SLS 客户端</li>
     *   <li>创建事件队列</li>
     *   <li>启动异步工作线程</li>
     * </ol>
     */
    @Override
    public void start() {
        if (started) {
            addWarn("SLS Appender is already started");
            return;
        }

        // 验证必需配置
        if (!validateConfiguration()) {
            addError("SLS Appender configuration validation failed, appender will not start. Please check endpoint and other required configurations.");
            return;
        }

        try {
            // 初始化 SLS 客户端
            slsClient = new Client(endpoint, accessKeyId, accessKeySecret);
            addInfo("SLS client initialized successfully");

            // 初始化队列
            eventQueue = new LinkedBlockingQueue<>(queueCapacity);
            addInfo("Event queue initialized with capacity: " + queueCapacity);

            // 启动工作线程
            running.set(true);
            workerThread = new Thread(this::processEvents, WORKER_THREAD_NAME);
            workerThread.setDaemon(true);
            workerThread.start();
            addInfo("Worker thread started: " + WORKER_THREAD_NAME);

            super.start();
            addInfo("SLS Appender started successfully - Project: " + project + 
                    ", Logstore: " + logstore + 
                    ", BatchSize: " + batchSize + 
                    ", FlushInterval: " + flushInterval + "ms");

        } catch (Exception e) {
            addError("Failed to start SLS Appender", e);
            // 清理资源
            cleanup();
        }
    }

    /**
     * 停止 SLS Appender
     * 
     * <p>执行以下清理步骤：</p>
     * <ol>
     *   <li>停止接收新的日志事件</li>
     *   <li>等待工作线程结束</li>
     *   <li>处理剩余的日志事件</li>
     *   <li>清理资源</li>
     * </ol>
     */
    @Override
    public void stop() {
        if (!started) {
            addWarn("SLS Appender is not started");
            return;
        }

        addInfo("Stopping SLS Appender...");
        running.set(false);

        // 等待工作线程结束
        if (workerThread != null) {
            try {
                workerThread.interrupt();
                workerThread.join(WORKER_SHUTDOWN_TIMEOUT);
                if (workerThread.isAlive()) {
                    addWarn("Worker thread did not stop within timeout, forcing shutdown");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                addWarn("Interrupted while waiting for worker thread to stop");
            }
        }

        // 处理剩余的日志
        flushRemainingEvents();

        // 清理资源
        cleanup();

        super.stop();
        addInfo("SLS Appender stopped successfully");
    }

    /**
     * 验证配置参数的有效性
     * @return 配置是否有效
     */
    private boolean validateConfiguration() {
        // 验证必需的字符串参数
        if (isNullOrEmpty(endpoint)) {
            addError("SLS endpoint cannot be null or empty");
            return false;
        }
        if (isNullOrEmpty(accessKeyId)) {
            addError("SLS accessKeyId cannot be null or empty");
            return false;
        }
        if (isNullOrEmpty(accessKeySecret)) {
            addError("SLS accessKeySecret cannot be null or empty");
            return false;
        }
        if (isNullOrEmpty(project)) {
            addError("SLS project cannot be null or empty");
            return false;
        }
        if (isNullOrEmpty(logstore)) {
            addError("SLS logstore cannot be null or empty");
            return false;
        }

        // 验证数值参数的合理性
        if (batchSize <= 0 || batchSize > 4096) {
            addError("Batch size must be between 1 and 4096, current value: " + batchSize);
            return false;
        }
        if (flushInterval <= 0 || flushInterval > 300000) {
            addError("Flush interval must be between 1ms and 300000ms (5 minutes), current value: " + flushInterval);
            return false;
        }
        if (maxRetries < 0 || maxRetries > 10) {
            addError("Max retries must be between 0 and 10, current value: " + maxRetries);
            return false;
        }
        if (queueCapacity <= 0 || queueCapacity > 100000) {
            addError("Queue capacity must be between 1 and 100000, current value: " + queueCapacity);
            return false;
        }

        // 验证 endpoint 格式
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            addError("SLS endpoint must start with http:// or https://, current value: " + endpoint);
            return false;
        }

        // 验证 layout 和 encoder 不能同时为空
        if (layout == null && encoder == null) {
            addError("Either layout or encoder must be configured");
            return false;
        }

        // 验证 source 和 topic 长度限制
        if (source != null && source.length() > 128) {
            addError("Source length cannot exceed 128 characters, current length: " + source.length());
            return false;
        }
        if (topic != null && topic.length() > 128) {
            addError("Topic length cannot exceed 128 characters, current length: " + topic.length());
            return false;
        }

        return true;
    }

    /**
     * 检查字符串是否为 null 或空
     */
    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        if (slsClient != null) {
            try {
                slsClient.shutdown();
                addInfo("SLS client shutdown completed");
            } catch (Exception e) {
                addWarn("Error during SLS client shutdown", e);
            } finally {
                slsClient = null;
            }
        }
        
        if (eventQueue != null) {
            eventQueue.clear();
            eventQueue = null;
        }
        
        workerThread = null;
    }

    // ==================== 日志事件处理 ====================

    /**
     * 追加日志事件到队列
     * 
     * <p>该方法将日志事件异步添加到内部队列中，由工作线程负责批量处理。
     * 如果队列已满，将丢弃该事件并记录警告。</p>
     * 
     * @param event 要处理的日志事件
     */
    @Override
    protected void append(ILoggingEvent event) {
        if (!started || !running.get()) {
            addWarn("SLS Appender is not running, dropping log event");
            return;
        }

        if (event == null) {
            addWarn("Received null log event, ignoring");
            return;
        }

        try {
            // 将事件加入队列，如果队列满了则丢弃
            if (!eventQueue.offer(event)) {
                addWarn("Event queue is full (capacity: " + queueCapacity + "), dropping log event from logger: " + event.getLoggerName());
            }
        } catch (Exception e) {
            addError("Failed to append event to queue", e);
        }
    }

    /**
     * 处理事件的工作线程方法
     * 
     * <p>该方法在独立线程中运行，负责：</p>
     * <ul>
     *   <li>从队列中获取日志事件</li>
     *   <li>按批次大小或时间间隔进行批量处理</li>
     *   <li>将批次发送到 SLS</li>
     *   <li>处理发送过程中的异常</li>
     * </ul>
     */
    private void processEvents() {
        List<ILoggingEvent> batch = new ArrayList<>(batchSize);
        List<ILoggingEvent> pendingRetryBatch = null;
        long lastFlushTime = System.currentTimeMillis();

        addInfo("Event processing thread started");

        try {
            while (running.get() || (eventQueue != null && !eventQueue.isEmpty()) || (pendingRetryBatch != null)) {
                try {
                    // 若存在待重试的批次，优先重试，避免该批次无限增长
                    if (pendingRetryBatch != null) {
                        try {
                            sendBatch(new ArrayList<>(pendingRetryBatch));
                            pendingRetryBatch = null;
                            lastFlushTime = System.currentTimeMillis();
                            addInfo("Pending retry batch sent successfully");
                        } catch (Exception e) {
                            addError("Retrying pending batch failed, will retry again", e);
                            // 简单退避，避免忙等
                            Thread.sleep(Math.min(flushInterval, 1000));
                        }
                        continue;
                    }

                    // 从队列中获取事件
                    ILoggingEvent event = eventQueue.poll(QUEUE_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (event != null) {
                        batch.add(event);
                    }

                    // 检查是否需要发送批次
                    long currentTime = System.currentTimeMillis();
                    boolean shouldFlush = batch.size() >= batchSize ||
                            (currentTime - lastFlushTime >= flushInterval && !batch.isEmpty());

                    if (shouldFlush) {
                        try {
                            sendBatch(new ArrayList<>(batch));
                            batch.clear();
                            lastFlushTime = currentTime;
                        } catch (Exception e) {
                            addError("Failed to send batch, will hold it for retry without growing", e);
                            // 记录当前批次作为待重试批次，并清空当前批次，避免无限增长
                            pendingRetryBatch = new ArrayList<>(batch);
                            batch.clear();
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    addInfo("Event processing thread interrupted");
                    break;
                } catch (Exception e) {
                    addError("Unexpected error in event processing thread", e);
                    // 继续处理，避免线程退出
                }
            }
        } finally {
            // 先尝试发送待重试批次
            if (pendingRetryBatch != null && !pendingRetryBatch.isEmpty()) {
                try {
                    addInfo("Sending final pending retry batch of " + pendingRetryBatch.size() + " events");
                    sendBatch(pendingRetryBatch);
                } catch (Exception e) {
                    addError("Failed to send final pending retry batch", e);
                }
            }
            // 发送剩余的批次
            if (!batch.isEmpty()) {
                try {
                    addInfo("Sending final batch of " + batch.size() + " events");
                    sendBatch(batch);
                } catch (Exception e) {
                    addError("Failed to send final batch", e);
                }
            }
            addInfo("Event processing thread stopped");
        }
    }

    /**
     * 发送日志批次到 SLS
     * 
     * <p>将日志事件转换为 SLS LogItem 并批量发送。</p>
     * 
     * @param events 要发送的日志事件列表
     */
    private void sendBatch(List<ILoggingEvent> events) {
        if (events == null || events.isEmpty()) {
            addWarn("Attempted to send empty or null events batch");
            return;
        }

        long startTime = System.currentTimeMillis();
        List<LogItem> logItems = new ArrayList<>(events.size());

        try {
            // 转换事件为 LogItem
            for (ILoggingEvent event : events) {
                try {
                    LogItem logItem = createLogItem(event);
                    if (logItem != null) {
                        logItems.add(logItem);
                    } else {
                        addWarn("Failed to create LogItem for event from logger: " + event.getLoggerName());
                    }
                } catch (Exception e) {
                    addError("Error creating LogItem for event from logger: " + event.getLoggerName(), e);
                }
            }

            if (!logItems.isEmpty()) {
                sendToSls(logItems);
                long duration = System.currentTimeMillis() - startTime;
                addInfo("Batch sent successfully: " + logItems.size() + " items in " + duration + "ms");
            } else {
                addWarn("No valid LogItems created from " + events.size() + " events");
            }

        } catch (Exception e) {
            addError("Failed to send batch of " + events.size() + " events", e);
            throw new RuntimeException("Batch send failed", e);
        }
    }

    /**
     * 处理剩余的事件
     * 
     * <p>在 Appender 关闭时调用，确保队列中剩余的日志事件得到处理。</p>
     */
    private void flushRemainingEvents() {
        if (eventQueue == null) {
            return;
        }

        List<ILoggingEvent> remainingEvents = new ArrayList<>();
        int drainedCount = eventQueue.drainTo(remainingEvents);

        if (drainedCount > 0) {
            addInfo("Flushing " + drainedCount + " remaining events from queue");
            
            try {
                // 分批处理剩余事件，避免单次处理过多事件
                int batchStart = 0;
                while (batchStart < remainingEvents.size()) {
                    int batchEnd = Math.min(batchStart + batchSize, remainingEvents.size());
                    List<ILoggingEvent> batch = remainingEvents.subList(batchStart, batchEnd);
                    
                    try {
                        sendBatch(batch);
                    } catch (Exception e) {
                        addError("Failed to flush batch starting at index " + batchStart, e);
                        // 继续处理下一批，不因为一批失败而停止
                    }
                    
                    batchStart = batchEnd;
                }
                
                addInfo("Completed flushing remaining events");
            } catch (Exception e) {
                addError("Error during flush of remaining events", e);
            }
        } else {
            addInfo("No remaining events to flush");
        }
    }

    /**
     * 创建 LogItem 对象
     * 
     * @param event 日志事件
     * @return LogItem 对象，如果创建失败返回 null
     */
    private LogItem createLogItem(ILoggingEvent event) {
        if (event == null) {
            addWarn("Cannot create LogItem from null event");
            return null;
        }

        try {
            LogItem logItem = new LogItem();
            
            // 设置时间戳（转换为秒）
            logItem.SetTime((int) (event.getTimeStamp() / 1000));
            
            // 处理日志内容
            String content = formatLogContent(event);
            if (content == null || content.trim().isEmpty()) {
                addWarn("Log content is empty, skipping event");
                return null;
            }
            logItem.PushBack("content", content);
            
            // 添加基本字段
            addBasicFields(logItem, event);
            
            // 添加 MDC 属性
            addMdcProperties(logItem, event);
            
            // 添加异常信息
            addExceptionInfo(logItem, event);
            
            return logItem;
        } catch (Exception e) {
            addError("Failed to create LogItem for event: " + event.getFormattedMessage(), e);
            return null;
        }
    }

    /**
     * 格式化日志内容
     */
    private String formatLogContent(ILoggingEvent event) {
        try {
            if (encoder != null) {
                byte[] encoded = encoder.encode(event);
                return encoded != null ? new String(encoded).trim() : null;
            } else if (layout != null) {
                return layout.doLayout(event);
            } else {
                // 默认格式化
                return String.format("[%s] %s - %s", 
                    event.getLevel(), 
                    event.getLoggerName(), 
                    event.getFormattedMessage());
            }
        } catch (Exception e) {
            addWarn("Failed to format log content, using fallback format", e);
            return String.format("[%s] %s - %s", 
                event.getLevel(), 
                event.getLoggerName(), 
                event.getFormattedMessage());
        }
    }

    /**
     * 添加基本字段
     */
    private void addBasicFields(LogItem logItem, ILoggingEvent event) {
        try {
            // 日志级别
            if (event.getLevel() != null) {
                logItem.PushBack("level", event.getLevel().toString());
            }
            
            // Logger 名称
            if (event.getLoggerName() != null && !event.getLoggerName().isEmpty()) {
                logItem.PushBack("logger", event.getLoggerName());
            }
            
            // 线程名
            if (event.getThreadName() != null && !event.getThreadName().isEmpty()) {
                logItem.PushBack("thread", event.getThreadName());
            }
            
            // 原始消息
            if (event.getMessage() != null && !event.getMessage().isEmpty()) {
                logItem.PushBack("message", event.getMessage());
            }
            
            // 格式化消息
            String formattedMessage = event.getFormattedMessage();
            if (formattedMessage != null && !formattedMessage.isEmpty() && 
                !formattedMessage.equals(event.getMessage())) {
                logItem.PushBack("formatted_message", formattedMessage);
            }
        } catch (Exception e) {
            addWarn("Failed to add basic fields to LogItem", e);
        }
    }

    /**
     * 添加 MDC 属性
     */
    private void addMdcProperties(LogItem logItem, ILoggingEvent event) {
        try {
            Map<String, String> mdcPropertyMap = event.getMDCPropertyMap();
            if (mdcPropertyMap != null && !mdcPropertyMap.isEmpty()) {
                for (Map.Entry<String, String> entry : mdcPropertyMap.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    
                    if (key != null && !key.isEmpty() && value != null) {
                        // 添加 mdc. 前缀以区分 MDC 属性
                        String mdcKey = MDC_PREFIX + key;
                        // 限制值的长度，避免过长的值
                        String mdcValue = value.length() > 1000 ? value.substring(0, 1000) + "..." : value;
                        logItem.PushBack(mdcKey, mdcValue);
                    }
                }
            }
        } catch (Exception e) {
            addWarn("Failed to add MDC properties to LogItem", e);
        }
    }

    /**
     * 添加异常信息
     */
    private void addExceptionInfo(LogItem logItem, ILoggingEvent event) {
        try {
            if (event.getThrowableProxy() != null) {
                
                // 使用 Logback 标准的 ThrowableProxyUtil 处理堆栈跟踪
                String stackTrace = ThrowableProxyUtil.asString(event.getThrowableProxy());
                if (!stackTrace.isEmpty()) {
                    // 限制堆栈跟踪的长度，避免过长
                    String truncatedStackTrace = stackTrace.length() > 20000 ? 
                        stackTrace.substring(0, 20000) + "\n... (truncated)" : stackTrace;
                    logItem.PushBack("exception_stack_trace", truncatedStackTrace);
                }
            }
        } catch (Exception e) {
            addWarn("Failed to add exception info to LogItem", e);
        }
    }

    /**
     * 发送到 SLS
     * 
     * <p>使用指数退避策略进行重试，提高发送成功率。</p>
     * <p>支持 GZIP 压缩以减少网络传输量。</p>
     * 
     * @param logItems 要发送的日志项列表
     * @throws Exception 当所有重试都失败时抛出异常
     */
    private void sendToSls(List<LogItem> logItems) throws Exception {
        if (logItems == null || logItems.isEmpty()) {
            addWarn("Attempted to send empty or null log items list");
            return;
        }

        Exception lastException = null;
        int retries = 0;
        
        while (retries <= maxRetries) {
            try {
                PutLogsRequest request = new PutLogsRequest(project, logstore, topic, source, logItems);
                
                // 设置压缩类型
                request.SetCompressType(compressionType);
                
                PutLogsResponse response = slsClient.PutLogs(request);

                if (response != null) {
                    String compressionInfo = compressionType != CompressType.NONE ? " (compressed with " + compressionType + ")" : "";
                    addInfo("Successfully sent " + logItems.size() + " log items to SLS" + compressionInfo + " (attempt " + (retries + 1) + ")");
                    return;
                } else {
                    throw new RuntimeException("Received null response from SLS");
                }

            } catch (Exception e) {
                lastException = e;
                retries++;
                
                if (retries > maxRetries) {
                    addError("Failed to send logs to SLS after " + maxRetries + " retries. Last error: " + e.getMessage(), e);
                    throw new RuntimeException("Failed to send logs to SLS after " + maxRetries + " retries", e);
                } else {
                    long delayMs = RETRY_BASE_DELAY * retries;
                    addWarn("Failed to send logs to SLS, retrying in " + delayMs + "ms (attempt " + retries + "/" + maxRetries + "): " + e.getMessage());
                    
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        addWarn("Retry delay interrupted, aborting send operation");
                        throw new RuntimeException("Send operation interrupted during retry delay", ie);
                    }
                }
            }
        }
        
        // 这里不应该到达，但为了安全起见
        if (lastException != null) {
            throw lastException;
        }
    }







    // Getter 和 Setter 方法
    // ==========================================

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.trim().isEmpty()) {
            String trimmedEndpoint = endpoint.trim();
            // 基本的 URL 格式验证
            if (trimmedEndpoint.startsWith("http://") || trimmedEndpoint.startsWith("https://")) {
                this.endpoint = trimmedEndpoint;
            } else {
                addWarn("Invalid endpoint format (must start with http:// or https://): " + trimmedEndpoint);
            }
        } else {
            addWarn("Endpoint cannot be null or empty, ignoring invalid value");
        }
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        if (accessKeyId != null && !accessKeyId.trim().isEmpty()) {
            this.accessKeyId = accessKeyId.trim();
        } else {
            addWarn("AccessKeyId cannot be null or empty, ignoring invalid value");
        }
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        if (accessKeySecret != null && !accessKeySecret.trim().isEmpty()) {
            this.accessKeySecret = accessKeySecret.trim();
        } else {
            addWarn("AccessKeySecret cannot be null or empty, ignoring invalid value");
        }
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        if (project != null && !project.trim().isEmpty()) {
            this.project = project.trim();
        } else {
            addWarn("Project cannot be null or empty, ignoring invalid value");
        }
    }

    public String getLogstore() {
        return logstore;
    }

    public void setLogstore(String logstore) {
        if (logstore != null && !logstore.trim().isEmpty()) {
            this.logstore = logstore.trim();
        } else {
            addWarn("Logstore cannot be null or empty, ignoring invalid value");
        }
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        if (source != null) {
            String trimmedSource = source.trim();
            if (trimmedSource.length() <= 128) {
                this.source = trimmedSource.isEmpty() ? null : trimmedSource;
            } else {
                addWarn("Source length exceeds 128 characters, truncating: " + trimmedSource);
                this.source = trimmedSource.substring(0, 128);
            }
        } else {
            this.source = null;
        }
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        if (topic != null) {
            String trimmedTopic = topic.trim();
            if (trimmedTopic.length() <= 128) {
                this.topic = trimmedTopic.isEmpty() ? null : trimmedTopic;
            } else {
                addWarn("Topic length exceeds 128 characters, truncating: " + trimmedTopic);
                this.topic = trimmedTopic.substring(0, 128);
            }
        } else {
            this.topic = null;
        }
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize > 0 && batchSize <= 4096) {
            this.batchSize = batchSize;
        } else {
            addWarn("Invalid batchSize (must be 1-4096): " + batchSize + ", using default: " + DEFAULT_BATCH_SIZE);
            this.batchSize = DEFAULT_BATCH_SIZE;
        }
    }

    public int getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(int flushInterval) {
        if (flushInterval >= 1000 && flushInterval <= 300000) {
            this.flushInterval = flushInterval;
        } else {
            addWarn("Invalid flushInterval (must be 1000-300000ms): " + flushInterval + ", using default: " + DEFAULT_FLUSH_INTERVAL);
            this.flushInterval = DEFAULT_FLUSH_INTERVAL;
        }
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        if (maxRetries >= 0 && maxRetries <= 10) {
            this.maxRetries = maxRetries;
        } else {
            addWarn("Invalid maxRetries (must be 0-10): " + maxRetries + ", using default: " + DEFAULT_MAX_RETRIES);
            this.maxRetries = DEFAULT_MAX_RETRIES;
        }
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        if (queueCapacity > 0 && queueCapacity <= 100000) {
            this.queueCapacity = queueCapacity;
        } else {
            addWarn("Invalid queueCapacity (must be 1-100000): " + queueCapacity + ", using default: " + DEFAULT_QUEUE_CAPACITY);
            this.queueCapacity = DEFAULT_QUEUE_CAPACITY;
        }
    }

    public CompressType getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(CompressType compressionType) {
        this.compressionType = compressionType != null ? compressionType : CompressType.LZ4;
        addInfo("Compression type set to: " + this.compressionType);
    }
    
    public void setCompressionType(String compressionType) {
        if (compressionType == null || compressionType.trim().isEmpty()) {
            setCompressionType(CompressType.LZ4);
            return;
        }
        
        try {
            CompressType type = CompressType.valueOf(compressionType.toUpperCase());
            setCompressionType(type);
        } catch (IllegalArgumentException e) {
            addWarn("Invalid compression type: " + compressionType + ", using default LZ4");
            setCompressionType(CompressType.LZ4);
        }
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