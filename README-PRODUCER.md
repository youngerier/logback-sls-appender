# Logback SLS Appender - Producer版本

这是基于 `aliyun-log-producer` 的 Logback SLS Appender 实现，完全避免了 Protobuf 依赖冲突问题。

## 特性

- ✅ **零 Protobuf 依赖**: 使用 `aliyun-log-producer` 替代直接使用 `Client`，完全避免 Protobuf 版本冲突
- ✅ **高性能异步发送**: 基于 Producer 的异步批量发送机制
- ✅ **自动重试机制**: 内置智能重试和错误处理
- ✅ **资源管理**: 自动管理内存使用和线程池
- ✅ **优雅关闭**: 确保所有日志在应用关闭时都能发送完成
- ✅ **丰富配置**: 支持详细的性能调优参数

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.youngerier</groupId>
    <artifactId>logback-sls-appender</artifactId>
    <version>0.0.3-SNAPSHOT</version>
</dependency>
```

### 2. 配置 logback.xml

#### 同步版本 (SlsProducerAppender)

```xml
<configuration>
    <appender name="SLS_PRODUCER" class="io.github.youngerier.logback.sls.appender.SlsProducerAppender">
        <!-- SLS基本配置 -->
        <endpoint>https://your-region.log.aliyuncs.com</endpoint>
        <accessKeyId>your-access-key-id</accessKeyId>
        <accessKeySecret>your-access-key-secret</accessKeySecret>
        <project>your-project</project>
        <logStore>your-logstore</logStore>
        <topic>your-topic</topic>
        <source>your-source</source>
        
        <!-- Producer性能配置 (可选) -->
        <totalSizeInBytes>104857600</totalSizeInBytes> <!-- 100MB -->
        <maxBlockMs>60000</maxBlockMs> <!-- 60秒 -->
        <ioThreadCount>8</ioThreadCount>
        <batchSizeThresholdInBytes>524288</batchSizeThresholdInBytes> <!-- 512KB -->
        <batchCountThreshold>4096</batchCountThreshold>
        <lingerMs>2000</lingerMs> <!-- 2秒 -->
        <retries>10</retries>
        <baseRetryBackoffMs>100</baseRetryBackoffMs>
        <maxRetryBackoffMs>50000</maxRetryBackoffMs> <!-- 50秒 -->
    </appender>

    <root level="INFO">
        <appender-ref ref="SLS_PRODUCER"/>
    </root>
</configuration>
```

#### 异步版本 (AsyncSlsProducerAppender) - 推荐

```xml
<configuration>
    <appender name="ASYNC_SLS_PRODUCER" class="io.github.youngerier.logback.sls.appender.AsyncSlsProducerAppender">
        <!-- SLS基本配置 -->
        <endpoint>https://your-region.log.aliyuncs.com</endpoint>
        <accessKeyId>your-access-key-id</accessKeyId>
        <accessKeySecret>your-access-key-secret</accessKeySecret>
        <project>your-project</project>
        <logStore>your-logstore</logStore>
        <topic>your-topic</topic>
        <source>your-source</source>
        
        <!-- AsyncAppender配置 -->
        <queueSize>1024</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <includeCallerData>false</includeCallerData>
        
        <!-- Producer性能配置 -->
        <totalSizeInBytes>104857600</totalSizeInBytes>
        <maxBlockMs>60000</maxBlockMs>
        <ioThreadCount>8</ioThreadCount>
        <batchSizeThresholdInBytes>524288</batchSizeThresholdInBytes>
        <batchCountThreshold>4096</batchCountThreshold>
        <lingerMs>2000</lingerMs>
        <retries>10</retries>
        <baseRetryBackoffMs>100</baseRetryBackoffMs>
        <maxRetryBackoffMs>50000</maxRetryBackoffMs>
    </appender>

    <root level="INFO">
        <appender-ref ref="ASYNC_SLS_PRODUCER"/>
    </root>
</configuration>
```

## 配置参数

### SLS基本配置

| 参数 | 描述 | 必需 | 默认值 |
|------|------|------|--------|
| endpoint | SLS服务端点 | 是 | - |
| accessKeyId | 阿里云访问密钥ID | 是 | - |
| accessKeySecret | 阿里云访问密钥Secret | 是 | - |
| project | SLS项目名称 | 是 | - |
| logStore | SLS日志库名称 | 是 | - |
| topic | 日志主题 | 否 | "" |
| source | 日志来源 | 否 | "" |

### Producer性能配置

| 参数 | 描述 | 默认值 |
|------|------|--------|
| totalSizeInBytes | Producer缓存的最大字节数 | 100MB |
| maxBlockMs | 发送阻塞的最大时间 | 60秒 |
| ioThreadCount | IO线程数 | 8 |
| batchSizeThresholdInBytes | 批量发送的字节阈值 | 512KB |
| batchCountThreshold | 批量发送的日志条数阈值 | 4096 |
| lingerMs | 批量等待时间 | 2秒 |
| retries | 重试次数 | 10 |
| baseRetryBackoffMs | 基础重试间隔 | 100ms |
| maxRetryBackoffMs | 最大重试间隔 | 50秒 |

## 日志格式

日志消息包含以下字段:

```
- level      // 日志级别
- thread     // 线程名称
- logger     // 日志记录器名称
- message    // 日志消息内容
- time       // 格式化时间 (yyyy-MM-dd HH:mm:ss.SSS)
- mdc.*      // MDC上下文 (以mdc.前缀)
- throwable  // 异常信息
- marker     // Marker信息
```

## 与其他版本的对比

| 特性 | Producer版本 | GZIP版本 | Protobuf版本 |
|------|-------------|----------|--------------|
| Protobuf依赖 | ❌ 无 | ⚠️ 间接依赖 | ✅ 直接依赖 |
| 性能 | 🚀 最高 | ⚡ 高 | ⚡ 高 |
| 稳定性 | 🛡️ 最佳 | ✅ 好 | ⚠️ 可能冲突 |
| 资源管理 | 🎯 自动 | 📝 手动 | 📝 手动 |
| 异步支持 | ✅ 内置 | ✅ 外部 | ✅ 外部 |
| 重试机制 | 🔄 智能 | 🔄 基础 | 🔄 基础 |
| 维护成本 | 🟢 低 | 🟡 中 | 🔴 高 |

## 使用示例

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class Example {
    private static final Logger logger = LoggerFactory.getLogger(Example.class);
    
    public void process(String requestId, String userId) {
        MDC.put("requestId", requestId);
        MDC.put("userId", userId);
        
        try {
            logger.info("Processing request with Producer");
            // 业务逻辑
        } catch (Exception e) {
            logger.error("Processing failed", e);
        } finally {
            MDC.clear();  // 清理MDC上下文
        }
    }
}
```

## 性能调优建议

### 高吞吐量场景
```xml
<totalSizeInBytes>209715200</totalSizeInBytes> <!-- 200MB -->
<ioThreadCount>16</ioThreadCount>
<batchSizeThresholdInBytes>1048576</batchSizeThresholdInBytes> <!-- 1MB -->
<batchCountThreshold>8192</batchCountThreshold>
<lingerMs>1000</lingerMs> <!-- 1秒 -->
```

### 低延迟场景
```xml
<totalSizeInBytes>52428800</totalSizeInBytes> <!-- 50MB -->
<ioThreadCount>4</ioThreadCount>
<batchSizeThresholdInBytes>262144</batchSizeThresholdInBytes> <!-- 256KB -->
<batchCountThreshold>1024</batchCountThreshold>
<lingerMs>500</lingerMs> <!-- 0.5秒 -->
```

### 资源受限场景
```xml
<totalSizeInBytes>26214400</totalSizeInBytes> <!-- 25MB -->
<ioThreadCount>2</ioThreadCount>
<batchSizeThresholdInBytes>131072</batchSizeThresholdInBytes> <!-- 128KB -->
<batchCountThreshold>512</batchCountThreshold>
<lingerMs>3000</lingerMs> <!-- 3秒 -->
```

## 构建项目

```bash
git clone https://github.com/youngerier/logback-sls-appender.git
cd logback-sls-appender
mvn clean install
```

## 注意事项

1. **推荐使用异步版本**: `AsyncSlsProducerAppender` 提供更好的性能
2. **合理配置缓存大小**: 根据应用的内存情况调整 `totalSizeInBytes`
3. **监控重试情况**: 关注日志中的重试和错误信息
4. **优雅关闭**: Producer 会在应用关闭时自动等待所有日志发送完成

## 故障排除

### 常见问题

1. **内存不足**: 减少 `totalSizeInBytes` 或增加应用内存
2. **发送超时**: 增加 `maxBlockMs` 或检查网络连接
3. **批量过大**: 减少 `batchSizeThresholdInBytes` 或 `batchCountThreshold`

### 日志监控

Producer 会输出详细的调试信息，可以通过以下方式启用:

```xml
<logger name="com.aliyun.openservices.aliyun.log.producer" level="DEBUG"/>
```

## 许可证

[Apache License 2.0](LICENSE)