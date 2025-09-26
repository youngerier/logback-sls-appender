package io.github.youngerier.logback.sls.appender;

import ch.qos.logback.classic.AsyncAppender;

/**
 * 异步SLS Protobuf日志Appender
 * 使用Logback的AsyncAppender机制包装SlsProtobufAppender，提供更好的性能
 */
public class AsyncSlsProtobufAppender extends AsyncAppender {

    private SlsProtobufAppender slsProtobufAppender;

    // SLS配置参数
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String project;
    private String logStore;

    // 批处理配置参数
    private int batchSize;
    private int queueSize;
    private int flushIntervalMs;

    @Override
    public void start() {
        // 创建并配置SlsProtobufAppender
        slsProtobufAppender = new SlsProtobufAppender();
        slsProtobufAppender.setEndpoint(endpoint);
        slsProtobufAppender.setAccessKeyId(accessKeyId);
        slsProtobufAppender.setAccessKeySecret(accessKeySecret);
        slsProtobufAppender.setProject(project);
        slsProtobufAppender.setLogStore(logStore);

        // 设置批处理参数（如果已配置）
        if (batchSize > 0) {
            slsProtobufAppender.setBatchSize(batchSize);
        }
        if (queueSize > 0) {
            slsProtobufAppender.setQueueSize(queueSize);
        }
        if (flushIntervalMs > 0) {
            slsProtobufAppender.setFlushIntervalMs(flushIntervalMs);
        }

        // 设置上下文并启动SlsProtobufAppender
        slsProtobufAppender.setContext(getContext());
        slsProtobufAppender.start();

        // 将SlsProtobufAppender添加到AsyncAppender
        addAppender(slsProtobufAppender);

        // 启动AsyncAppender
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        slsProtobufAppender.stop();
    }

    // SLS配置参数的getter/setter
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

    // 批处理配置参数的getter/setter
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @Override
    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public void setFlushIntervalMs(int flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

}