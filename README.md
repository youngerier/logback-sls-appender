# Logback SLS Appender

高性能的 Logback Appender，用于将应用日志发送到阿里云日志服务 (SLS)。

## 特性

- ✅ 异步批量发送，高性能
- ✅ 支持 JSON 格式（非 protobuf）
- ✅ 自动重试机制
- ✅ 支持 MDC 上下文
- ✅ 支持异常堆栈跟踪
- ✅ 可配置的批量大小和刷新间隔
- ✅ 优雅关闭处理

## 快速开始

### 1. 添加依赖

在你的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>io.github.youngerier</groupId>
    <artifactId>logback-sls-appender</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置 logback.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    
    <!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- SLS Appender -->
    <appender name="SLS" class="io.github.youngerier.logback.sls.appender.SlsAppender">
        <!-- 必需配置 -->
        <endpoint>https://cn-hangzhou.log.aliyuncs.com</endpoint>
        <accessKeyId>你的AccessKeyId</accessKeyId>
        <accessKeySecret>你的AccessKeySecret</accessKeySecret>
        <project>你的项目名</project>
        <logstore>你的日志库名</logstore>
        
        <!-- 可选配置 -->
        <source>your-app-name</source>
        <topic>application-logs</topic>
        <batchSize>100</batchSize>
        <flushInterval>5000</flushInterval>
        <maxRetries>3</maxRetries>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="SLS"/>
    </root>
    
</configuration>
```

### 3. 运行测试

```bash
mvn compile exec:java -Dexec.mainClass="io.github.youngerier.logback.sls.appender.Application"
```

## 配置参数

### 必需参数

| 参数 | 说明 | 示例 |
|------|------|------|
| endpoint | SLS 服务端点 | https://cn-hangzhou.log.aliyuncs.com |
| accessKeyId | 阿里云 AccessKey ID | LTAI5t... |
| accessKeySecret | 阿里云 AccessKey Secret | xxx |
| project | SLS 项目名 | my-project |
| logstore | SLS 日志库名 | my-logstore |

### 可选参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| source | 日志来源标识 | "" |
| topic | 日志主题 | "" |
| batchSize | 批量发送大小 | 100 |
| flushInterval | 刷新间隔(毫秒) | 5000 |
| maxRetries | 最大重试次数 | 3 |

## 日志字段

发送到 SLS 的日志包含以下字段：

- `level`: 日志级别 (DEBUG, INFO, WARN, ERROR)
- `logger`: Logger 名称
- `thread`: 线程名
- `message`: 格式化后的日志消息
- `mdc.*`: MDC 上下文字段（如果有）
- `exception`: 异常类名（如果有）
- `exception_message`: 异常消息（如果有）
- `stack_trace`: 异常堆栈跟踪（如果有）

## 性能优化建议

1. **使用异步 Appender**：对于高并发场景，建议使用 AsyncAppender 包装 SLS Appender
2. **调整批量大小**：根据日志量调整 `batchSize`，通常 50-200 之间
3. **调整刷新间隔**：根据实时性要求调整 `flushInterval`
4. **设置合适的日志级别**：避免发送过多 DEBUG 日志

## 环境变量配置

为了安全起见，建议使用环境变量配置敏感信息：

```xml
<accessKeyId>${SLS_ACCESS_KEY_ID}</accessKeyId>
<accessKeySecret>${SLS_ACCESS_KEY_SECRET}</accessKeySecret>
```

## 故障排除

1. **连接失败**：检查 endpoint 格式是否正确，是否包含 https://
2. **认证失败**：检查 AccessKey 是否正确，是否有 SLS 权限
3. **项目不存在**：确保 SLS 项目和日志库已创建
4. **日志丢失**：检查网络连接，增加重试次数

## 许可证

Apache License 2.0