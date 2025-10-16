package io.github.youngerier.logback.sls.appender;

import ch.qos.logback.classic.AsyncAppender;

/**
 * 异步SLS GZIP日志Appender
 * 使用Logback的AsyncAppender机制包装SlsGzipAppender，提供更好的性能
 * 使用GZIP压缩替代protobuf，减少依赖并提供更好的兼容性
 */
public class AsyncSlsGzipAppender extends AsyncAppender {

    private SlsGzipAppender slsGzipAppender;

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
        // 创建并配置SlsGzipAppender
        slsGzipAppender = new SlsGzipAppender();
        slsGzipAppender.setEndpoint(endpoint);
        slsGzipAppender.setAccessKeyId(accessKeyId);
        slsGzipAppender.setAccessKeySecret(accessKeySecret);
        slsGzipAppender.setProject(project);
        slsGzipAppender.setLogStore(logStore);
        
        // 设置批处理参数
        if (batchSize > 0) {
            slsGzipAppender.setBatchSize(batchSize);
        }
        if (queueSize > 0) {
            slsGzipAppender.setQueueSize(queueSize);
        }
        if (flushIntervalMs > 0) {
            slsGzipAppender.setFlushIntervalMs(flushIntervalMs);
        }

        slsGzipAppender.setContext(getContext());
        slsGzipAppender.start();

        // 将SlsGzipAppender添加到AsyncAppender
        addAppender(slsGzipAppender);

        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        if (slsGzipAppender != null) {
            slsGzipAppender.stop();
        }
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
        super.setQueueSize(queueSize);
        this.queueSize = queueSize;
    }

    public void setFlushIntervalMs(int flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }
}