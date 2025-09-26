# Logback SLS Appender

这是一个用于将日志发送到阿里云日志服务(SLS)的Logback Appender库。该库使用Protocol Buffers格式序列化日志消息，提高传输效率和存储密度。

## 功能特点

- 支持将Logback日志直接发送到阿里云SLS
- 使用Protocol Buffers序列化日志消息
- 支持自定义日志字段
- 高效的日志传输和存储

## 快速开始

### 1. 添加依赖

在你的Maven项目中添加以下依赖:

```xml
<dependency>
    <groupId>io.github.youngerier</groupId>
    <artifactId>logback-sls-appender</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. 配置Logback

在`logback.xml`或`logback-spring.xml`中添加SLS Appender配置:

```xml
<configuration>
    <appender name="SLS" class="io.github.youngerier.logback.sls.appender.SlsProtobufAppender">
        <endpoint>your-endpoint</endpoint>
        <accessKeyId>your-access-key-id</accessKeyId>
        <accessKeySecret>your-access-key-secret</accessKeySecret>
        <project>your-sls-project</project>
        <logStore>your-logstore</logStore>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="SLS" />
    </root>
</configuration>
```

### 3. 配置参数说明

| 参数 | 描述 | 必填 |
| --- | --- | --- |
| endpoint | 阿里云SLS服务的Endpoint | 是 |
| accessKeyId | 阿里云访问密钥ID | 是 |
| accessKeySecret | 阿里云访问密钥Secret | 是 |
| project | SLS项目名称 | 是 |
| logStore | SLS日志库名称 | 是 |

## 日志格式

日志消息使用Protocol Buffers格式序列化，包含以下字段:

```protobuf
message LogMessage {
  string level = 1;     // 日志级别
  string thread = 2;    // 线程名称
  string logger = 3;    // 日志记录器名称
  string message = 4;   // 日志消息内容
  int64 timestamp = 5;  // 时间戳(毫秒)
}
```

## 构建项目

如果你想从源码构建该项目:

```bash
git clone https://github.com/youngerier/logback-sls-appender.git
cd logback-sls-appender
mvn clean install
```

## 注意事项

- 确保你的阿里云账号有足够的权限访问SLS服务
- 建议在生产环境中使用异步Appender包装SlsProtobufAppender，以避免日志操作阻塞应用程序

## 许可证

[Apache License 2.0](LICENSE)