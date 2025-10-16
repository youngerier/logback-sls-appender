# Logback SLS Appender - GZIP版本

这是一个为阿里云日志服务(SLS)设计的Logback Appender，**使用GZIP压缩替代protobuf**，提供更好的兼容性和更少的依赖。

## 主要特性

- ✅ **GZIP压缩**: 使用GZIP压缩替代protobuf，减少网络传输量
- ✅ **零protobuf依赖**: 完全移除protobuf依赖，避免版本冲突
- ✅ **批量处理**: 支持批量发送日志，提高性能
- ✅ **异步支持**: 提供异步版本，避免阻塞主线程
- ✅ **MDC支持**: 完整支持MDC上下文信息
- ✅ **异常处理**: 完整的异常堆栈跟踪
- ✅ **高性能**: 队列缓冲和批处理机制

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.youngerier</groupId>
    <artifactId>logback-sls-appender</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. 配置logback.xml

#### 同步版本 (SlsGzipAppender)

```xml
<configuration>
    <appender name="SLS_GZIP" class="io.github.youngerier.logback.sls.appender.SlsGzipAppender">
        <endpoint>your-region.log.aliyuncs.com</endpoint>
        <accessKeyId>your-access-key-id</accessKeyId>
        <accessKeySecret>your-access-key-secret</accessKeySecret>
        <project>your-project-name</project>
        <logStore>your-logstore-name</logStore>
        
        <!-- 可选配置 -->
        <batchSize>100</batchSize>
        <queueSize>10000</queueSize>
        <flushIntervalMs>1000</flushIntervalMs>
    </appender>

    <root level="INFO">
        <appender-ref ref="SLS_GZIP"/>
    </root>
</configuration>
```

#### 异步版本 (AsyncSlsGzipAppender) - 推荐

```xml
<configuration>
    <appender name="ASYNC_SLS_GZIP" class="io.github.youngerier.logback.sls.appender.AsyncSlsGzipAppender">
        <endpoint>your-region.log.aliyuncs.com</endpoint>
        <accessKeyId>your-access-key-id</accessKeyId>
        <accessKeySecret>your-access-key-secret</accessKeySecret>
        <project>your-project-name</project>
        <logStore>your-logstore-name</logStore>
        
        <!-- SLS批处理配置 -->
        <batchSize>100</batchSize>
        <flushIntervalMs>1000</flushIntervalMs>
        
        <!-- AsyncAppender配置 -->
        <queueSize>1024</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <includeCallerData>true</includeCallerData>
    </appender>

    <root level="INFO">
        <appender-ref ref="ASYNC_SLS_GZIP"/>
    </root>
</configuration>
```

## 配置参数

### 必需参数

| 参数 | 说明 | 是否必需 |
|------|------|----------|
| endpoint | SLS服务端点 | 是 |
| accessKeyId | 阿里云访问密钥ID | 是 |
| accessKeySecret | 阿里云访问密钥Secret | 是 |
| project | SLS项目名称 | 是 |
| logStore | SLS日志库名称 | 是 |

### 可选参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| batchSize | 批处理大小 | 100 |
| queueSize | 队列大小 | 10000 |
| flushIntervalMs | 刷新间隔(毫秒) | 1000 |

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

## 与protobuf版本的区别

| 特性 | GZIP版本 | Protobuf版本 |
|------|----------|--------------|
| 压缩方式 | GZIP | Protobuf |
| 依赖大小 | 更小 | 更大 |
| 兼容性 | 更好 | 可能有版本冲突 |
| 性能 | 略低 | 略高 |
| 维护性 | 更简单 | 更复杂 |

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
            logger.info("Processing request with GZIP compression");
            // 业务逻辑
        } catch (Exception e) {
            logger.error("Processing failed", e);
        } finally {
            MDC.clear();  // 清理MDC上下文
        }
    }
}
```

## 构建项目

```bash
git clone https://github.com/youngerier/logback-sls-appender.git
cd logback-sls-appender
mvn clean install
```

## 注意事项

1. **GZIP压缩**: 使用GZIP压缩可以显著减少网络传输量，但CPU使用率会略有增加
2. **异步推荐**: 生产环境建议使用AsyncSlsGzipAppender以避免阻塞主线程
3. **权限配置**: 确保阿里云账号有足够权限访问SLS服务
4. **网络连接**: 确保应用服务器能够访问阿里云SLS服务端点

## 迁移指南

从protobuf版本迁移到GZIP版本:

1. 更新类名: `SlsProtobufAppender` → `SlsGzipAppender`
2. 更新类名: `AsyncSlsProtobufAppender` → `AsyncSlsGzipAppender`
3. 无需修改其他配置参数
4. 重新编译和部署

## 许可证

[Apache License 2.0](LICENSE)