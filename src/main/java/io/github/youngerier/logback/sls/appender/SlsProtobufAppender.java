package io.github.youngerier.logback.sls.appender;

import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.github.youngerier.logback.sls.proto.LogMessage;

import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.LogItem;
import com.aliyun.openservices.log.exception.LogException;
import com.aliyun.openservices.log.request.PutLogsRequest;

import java.util.Collections;

public class SlsProtobufAppender extends AppenderBase<ILoggingEvent> {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String project;
    private String logStore;

    private transient Client client;

    @Override
    public void start() {
        super.start();
        this.client = new Client(endpoint, accessKeyId, accessKeySecret);
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            // 构建protobuf对象
            LogMessage logMessage = LogMessage.newBuilder()
                    .setLevel(event.getLevel().toString())
                    .setThread(event.getThreadName())
                    .setLogger(event.getLoggerName())
                    .setMessage(event.getFormattedMessage())
                    .setTimestamp(event.getTimeStamp())
                    .build();

            byte[] data = logMessage.toByteArray();

            // 封装为 SLS LogItem
            LogItem item = new LogItem();
            item.PushBack("protobuf", new String(data));
            // ⚠️ SLS 日志存储 key/value，需要考虑是否 base64 或 hex 编码保存

            // 修复PutLogs方法调用
            PutLogsRequest request = new PutLogsRequest(project, logStore, "", Collections.singletonList(item));
            client.PutLogs(request);

        } catch (LogException e) {
            addError("Failed to send log to SLS", e);
        }
    }

    // getter/setter for logback.xml 配置
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public void setProject(String project) { this.project = project; }
    public void setLogStore(String logStore) { this.logStore = logStore; }
}
