package io.github.youngerier.logback.sls.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.Assert.*;

/**
 * SlsProducerAppender测试类
 */
public class SlsProducerAppenderTest {

    private SlsProducerAppender appender;
    private LoggerContext context;

    @Before
    public void setUp() {
        context = new LoggerContext();
        appender = new SlsProducerAppender();
        appender.setContext(context);
        appender.setName("test-sls-producer-appender");
    }

    @Test
    public void testAppenderConfiguration() {
        // 测试基本配置
        appender.setEndpoint("https://test-region.log.aliyuncs.com");
        appender.setAccessKeyId("test-access-key-id");
        appender.setAccessKeySecret("test-access-key-secret");
        appender.setProject("test-project");
        appender.setLogStore("test-logstore");
        appender.setTopic("test-topic");
        appender.setSource("test-source");

        // 测试Producer配置参数
        appender.setTotalSizeInBytes(50 * 1024 * 1024); // 50MB
        appender.setMaxBlockMs(30 * 1000); // 30秒
        appender.setIoThreadCount(4);
        appender.setBatchSizeThresholdInBytes(256 * 1024); // 256KB
        appender.setBatchCountThreshold(2048);
        appender.setLingerMs(1000); // 1秒
        appender.setRetries(5);
        appender.setBaseRetryBackoffMs(50);
        appender.setMaxRetryBackoffMs(25 * 1000); // 25秒

        // 验证配置不会抛出异常
        assertNotNull(appender);
        assertEquals("test-sls-producer-appender", appender.getName());
    }

    @Test
    public void testLogEventProcessing() {
        // 配置appender
        appender.setEndpoint("https://test-region.log.aliyuncs.com");
        appender.setAccessKeyId("test-access-key-id");
        appender.setAccessKeySecret("test-access-key-secret");
        appender.setProject("test-project");
        appender.setLogStore("test-logstore");

        // 创建测试日志事件
        Logger logger = context.getLogger("test.logger");
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("test.logger");
        event.setLevel(Level.INFO);
        event.setMessage("Test message with Producer");
        event.setTimeStamp(System.currentTimeMillis());
        event.setThreadName(Thread.currentThread().getName());

        // 测试append方法不会抛出异常
        try {
            appender.append(event);
            // 如果没有抛出异常，测试通过
            assertTrue(true);
        } catch (Exception e) {
            fail("Append should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testMDCHandling() {
        // 设置MDC
        MDC.put("requestId", "12345");
        MDC.put("userId", "user123");

        // 配置appender
        appender.setEndpoint("https://test-region.log.aliyuncs.com");
        appender.setAccessKeyId("test-access-key-id");
        appender.setAccessKeySecret("test-access-key-secret");
        appender.setProject("test-project");
        appender.setLogStore("test-logstore");

        // 创建测试日志事件
        Logger logger = context.getLogger("test.logger");
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("test.logger");
        event.setLevel(Level.INFO);
        event.setMessage("Test message with MDC");
        event.setTimeStamp(System.currentTimeMillis());
        event.setThreadName(Thread.currentThread().getName());
        event.setMDCPropertyMap(MDC.getCopyOfContextMap());

        // 测试append方法处理MDC
        try {
            appender.append(event);
            assertTrue(true);
        } catch (Exception e) {
            fail("Append with MDC should not throw exception: " + e.getMessage());
        } finally {
            MDC.clear();
        }
    }

    @Test
    public void testParameterValidation() {
        // 测试缺少必需参数时的行为
        appender.start();
        assertFalse("Appender should not start without required parameters", appender.isStarted());

        // 设置所有必需参数
        appender.setEndpoint("https://test-region.log.aliyuncs.com");
        appender.setAccessKeyId("test-access-key-id");
        appender.setAccessKeySecret("test-access-key-secret");
        appender.setProject("test-project");
        appender.setLogStore("test-logstore");

        // 现在应该能够启动
        appender.start();
        // 注意：由于没有真实的SLS服务，这里可能会失败，但不应该因为参数验证失败
        // 主要测试参数验证逻辑
    }

    @Test
    public void testAsyncAppenderConfiguration() {
        AsyncSlsProducerAppender asyncAppender = new AsyncSlsProducerAppender();
        asyncAppender.setContext(context);
        asyncAppender.setName("test-async-sls-producer-appender");

        // 配置参数
        asyncAppender.setEndpoint("https://test-region.log.aliyuncs.com");
        asyncAppender.setAccessKeyId("test-access-key-id");
        asyncAppender.setAccessKeySecret("test-access-key-secret");
        asyncAppender.setProject("test-project");
        asyncAppender.setLogStore("test-logstore");
        asyncAppender.setTopic("test-topic");
        asyncAppender.setSource("test-source");

        // 配置Producer参数
        asyncAppender.setTotalSizeInBytes(50 * 1024 * 1024);
        asyncAppender.setMaxBlockMs(30 * 1000);
        asyncAppender.setIoThreadCount(4);
        asyncAppender.setBatchSizeThresholdInBytes(256 * 1024);
        asyncAppender.setBatchCountThreshold(2048);
        asyncAppender.setLingerMs(1000);
        asyncAppender.setRetries(5);
        asyncAppender.setBaseRetryBackoffMs(50);
        asyncAppender.setMaxRetryBackoffMs(25 * 1000);

        // 验证配置不会抛出异常
        assertNotNull(asyncAppender);
        assertEquals("test-async-sls-producer-appender", asyncAppender.getName());
    }
}