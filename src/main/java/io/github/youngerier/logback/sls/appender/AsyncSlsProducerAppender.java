package io.github.youngerier.logback.sls.appender;

import ch.qos.logback.classic.AsyncAppender;

/**
 * 异步SLS Producer日志Appender
 * 使用Logback的AsyncAppender机制包装SlsProducerAppender，提供更好的性能
 */
public class AsyncSlsProducerAppender extends AsyncAppender {

    private SlsProducerAppender slsProducerAppender;

    // SLS配置参数
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String project;
    private String logStore;
    private String topic;
    private String source;

    // Producer配置参数
    private int totalSizeInBytes;
    private long maxBlockMs;
    private int ioThreadCount;
    private int batchSizeThresholdInBytes;
    private int batchCountThreshold;
    private int lingerMs;
    private int retries;
    private long baseRetryBackoffMs;
    private long maxRetryBackoffMs;

    @Override
    public void start() {
        // 创建并配置SlsProducerAppender
        slsProducerAppender = new SlsProducerAppender();
        slsProducerAppender.setContext(getContext());
        slsProducerAppender.setName("SlsProducerAppender-" + getName());

        // 设置SLS配置
        if (endpoint != null) slsProducerAppender.setEndpoint(endpoint);
        if (accessKeyId != null) slsProducerAppender.setAccessKeyId(accessKeyId);
        if (accessKeySecret != null) slsProducerAppender.setAccessKeySecret(accessKeySecret);
        if (project != null) slsProducerAppender.setProject(project);
        if (logStore != null) slsProducerAppender.setLogStore(logStore);
        if (topic != null) slsProducerAppender.setTopic(topic);
        if (source != null) slsProducerAppender.setSource(source);

        // 设置Producer配置参数
        if (totalSizeInBytes > 0) slsProducerAppender.setTotalSizeInBytes(totalSizeInBytes);
        if (maxBlockMs > 0) slsProducerAppender.setMaxBlockMs(maxBlockMs);
        if (ioThreadCount > 0) slsProducerAppender.setIoThreadCount(ioThreadCount);
        if (batchSizeThresholdInBytes > 0) slsProducerAppender.setBatchSizeThresholdInBytes(batchSizeThresholdInBytes);
        if (batchCountThreshold > 0) slsProducerAppender.setBatchCountThreshold(batchCountThreshold);
        if (lingerMs > 0) slsProducerAppender.setLingerMs(lingerMs);
        if (retries > 0) slsProducerAppender.setRetries(retries);
        if (baseRetryBackoffMs > 0) slsProducerAppender.setBaseRetryBackoffMs(baseRetryBackoffMs);
        if (maxRetryBackoffMs > 0) slsProducerAppender.setMaxRetryBackoffMs(maxRetryBackoffMs);

        // 启动SlsProducerAppender
        slsProducerAppender.start();

        // 将SlsProducerAppender添加到AsyncAppender
        addAppender(slsProducerAppender);

        // 启动AsyncAppender
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        if (slsProducerAppender != null) {
            slsProducerAppender.stop();
        }
    }

    // SLS配置参数的Setter方法
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
        this.topic = topic;
    }

    public void setSource(String source) {
        this.source = source;
    }

    // Producer配置参数的Setter方法
    public void setTotalSizeInBytes(int totalSizeInBytes) {
        this.totalSizeInBytes = totalSizeInBytes;
    }

    public void setMaxBlockMs(long maxBlockMs) {
        this.maxBlockMs = maxBlockMs;
    }

    public void setIoThreadCount(int ioThreadCount) {
        this.ioThreadCount = ioThreadCount;
    }

    public void setBatchSizeThresholdInBytes(int batchSizeThresholdInBytes) {
        this.batchSizeThresholdInBytes = batchSizeThresholdInBytes;
    }

    public void setBatchCountThreshold(int batchCountThreshold) {
        this.batchCountThreshold = batchCountThreshold;
    }

    public void setLingerMs(int lingerMs) {
        this.lingerMs = lingerMs;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public void setBaseRetryBackoffMs(long baseRetryBackoffMs) {
        this.baseRetryBackoffMs = baseRetryBackoffMs;
    }

    public void setMaxRetryBackoffMs(long maxRetryBackoffMs) {
        this.maxRetryBackoffMs = maxRetryBackoffMs;
    }
}