package io.github.youngerier.logback.sls.appender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 测试应用程序
 * 用于演示 SLS Appender 的功能
 */
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        logger.info("=== SLS Appender 测试开始 ===");
        
        // 基本日志测试
        logger.debug("这是一条 DEBUG 级别的日志");
        logger.info("这是一条 INFO 级别的日志");
        logger.warn("这是一条 WARN 级别的日志");
        logger.error("这是一条 ERROR 级别的日志");
        
        // MDC 测试
        MDC.put("userId", "12345");
        MDC.put("requestId", "req-" + System.currentTimeMillis());
        MDC.put("sessionId", "session-abc123");
        
        logger.info("带有 MDC 上下文的日志消息");
        logger.warn("用户操作警告，用户ID: {}", MDC.get("userId"));
        
        // 异常日志测试
        try {
            simulateError();
        } catch (Exception e) {
            logger.error("捕获到异常", e);
        }
        
        // 批量日志测试
        logger.info("开始批量日志测试...");
        for (int i = 1; i <= 50; i++) {
            MDC.put("batchIndex", String.valueOf(i));
            logger.info("批量日志消息 #{}: 测试 SLS Appender 的批量处理能力", i);
            
            if (i % 10 == 0) {
                logger.warn("批量处理进度: {}/50", i);
            }
            
            // 模拟一些处理时间
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 清理 MDC
        MDC.clear();
        
        logger.info("=== SLS Appender 测试完成 ===");
        
        // 等待一段时间确保所有日志都被发送
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        logger.info("程序即将退出");
    }
    
    /**
     * 模拟一个会抛出异常的方法
     */
    private static void simulateError() throws Exception {
        throw new RuntimeException("这是一个模拟的运行时异常，用于测试异常日志记录");
    }
}
