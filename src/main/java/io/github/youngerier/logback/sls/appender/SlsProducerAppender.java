package io.github.youngerier.logback.sls.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.aliyun.openservices.aliyun.log.producer.Callback;
import com.aliyun.openservices.aliyun.log.producer.LogProducer;
import com.aliyun.openservices.aliyun.log.producer.Producer;
import com.aliyun.openservices.aliyun.log.producer.ProducerConfig;
import com.aliyun.openservices.aliyun.log.producer.ProjectConfig;
import com.aliyun.openservices.aliyun.log.producer.Result;
import com.aliyun.openservices.log.common.LogItem;
import org.slf4j.Marker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * SLS Producer日志Appender
 * 使用aliyun-log-producer替代直接使用Client，完全避免protobuf依赖
 */
public class SlsProducerAppender extends AppenderBase<ILoggingEvent> {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String project;
    private String logStore;
    private String topic = "";
    private String source = "";

    // Producer配置参数
    private int totalSizeInBytes = 100 * 1024 * 1024; // 100MB
    private long maxBlockMs = 60 * 1000; // 60秒
    private int ioThreadCount = 8;
    private int batchSizeThresholdInBytes = 512 * 1024; // 512KB
    private int batchCountThreshold = 4096;
    private int lingerMs = 2000; // 2秒
    private int retries = 10;
    private long baseRetryBackoffMs = 100;
    private long maxRetryBackoffMs = 50 * 1000; // 50秒

    private transient Producer producer;

    // 添加自定义的isEmpty方法替代StringUtils
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    @Override
    public void start() {
        if (isEmpty(endpoint) || isEmpty(accessKeyId) || isEmpty(accessKeySecret) 
            || isEmpty(project) || isEmpty(logStore)) {
            addError("Missing required configuration: endpoint, accessKeyId, accessKeySecret, project, or logStore");
            return;
        }

        try {
            // 创建Producer配置
            ProducerConfig producerConfig = new ProducerConfig();
            producerConfig.setTotalSizeInBytes(totalSizeInBytes);
            producerConfig.setMaxBlockMs(maxBlockMs);
            producerConfig.setIoThreadCount(ioThreadCount);
            producerConfig.setBatchSizeThresholdInBytes(batchSizeThresholdInBytes);
            producerConfig.setBatchCountThreshold(batchCountThreshold);
            producerConfig.setLingerMs(lingerMs);
            producerConfig.setRetries(retries);
            producerConfig.setBaseRetryBackoffMs(baseRetryBackoffMs);
            producerConfig.setMaxRetryBackoffMs(maxRetryBackoffMs);

            // 创建Producer
            producer = new LogProducer(producerConfig);
            
            // 添加项目配置
            producer.putProjectConfig(new ProjectConfig(project, endpoint, accessKeyId, accessKeySecret));

            super.start();
            addInfo("SlsProducerAppender started successfully");
        } catch (Exception e) {
            addError("Failed to start SlsProducerAppender", e);
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted() || producer == null) {
            return;
        }

        try {
            LogItem logItem = createLogItem(event);
            
            // 使用Producer异步发送日志
            producer.send(project, logStore, topic, source, logItem, new Callback() {
                @Override
                public void onCompletion(Result result) {
                    if (!result.isSuccessful()) {
                        addError("Failed to send log to SLS: " + result.toString());
                    }
                }
            });
        } catch (Exception e) {
            addError("Failed to process log event", e);
        }
    }

    private LogItem createLogItem(ILoggingEvent event) {
        LogItem item = new LogItem((int) (event.getTimeStamp() / 1000));

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

        // MDC上下文处理
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null && !mdc.isEmpty()) {
            for (Map.Entry<String, String> entry : mdc.entrySet()) {
                item.PushBack("mdc." + entry.getKey(), entry.getValue());
            }
        }

        // Marker处理
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

        return item;
    }

    @Override
    public void stop() {
        if (producer != null) {
            try {
                // 优雅关闭Producer，等待所有日志发送完成
                producer.close();
                addInfo("SlsProducerAppender stopped successfully");
            } catch (Exception e) {
                addError("Error stopping SlsProducerAppender", e);
            }
        }
        super.stop();
    }

    // Getter/Setter方法用于logback.xml配置
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

    public void setTopic(String topic) {
        this.topic = topic != null ? topic : "";
    }

    public void setSource(String source) {
        this.source = source != null ? source : "";
    }

    // Producer配置参数的Setter方法
    public void setTotalSizeInBytes(int totalSizeInBytes) {
        this.totalSizeInBytes = totalSizeInBytes > 0 ? totalSizeInBytes : 100 * 1024 * 1024;
    }

    public void setMaxBlockMs(long maxBlockMs) {
        this.maxBlockMs = maxBlockMs > 0 ? maxBlockMs : 60 * 1000;
    }

    public void setIoThreadCount(int ioThreadCount) {
        this.ioThreadCount = ioThreadCount > 0 ? ioThreadCount : 8;
    }

    public void setBatchSizeThresholdInBytes(int batchSizeThresholdInBytes) {
        this.batchSizeThresholdInBytes = batchSizeThresholdInBytes > 0 ? batchSizeThresholdInBytes : 512 * 1024;
    }

    public void setBatchCountThreshold(int batchCountThreshold) {
        this.batchCountThreshold = batchCountThreshold > 0 ? batchCountThreshold : 4096;
    }

    public void setLingerMs(int lingerMs) {
        this.lingerMs = lingerMs > 0 ? lingerMs : 2000;
    }

    public void setRetries(int retries) {
        this.retries = retries > 0 ? retries : 10;
    }

    public void setBaseRetryBackoffMs(long baseRetryBackoffMs) {
        this.baseRetryBackoffMs = baseRetryBackoffMs > 0 ? baseRetryBackoffMs : 100;
    }

    public void setMaxRetryBackoffMs(long maxRetryBackoffMs) {
        this.maxRetryBackoffMs = maxRetryBackoffMs > 0 ? maxRetryBackoffMs : 50 * 1000;
    }
}