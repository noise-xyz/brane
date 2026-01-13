// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class DebugLoggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("sh.brane.debug");

    @AfterEach
    void reset() {
        BraneDebug.setEnabled(false);
        logger.detachAndStopAllAppenders();
    }

    @Test
    void doesNotLogWhenDisabled() {
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        DebugLogger.log("should not appear");

        assertTrue(appender.list.isEmpty());
    }

    @Test
    void logsSanitizedMessagesWhenEnabled() {
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        BraneDebug.setEnabled(true);
        DebugLogger.log("payload {\"privateKey\":\"0x123\"}");

        assertEquals(1, appender.list.size());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("0x***[REDACTED]***"));
    }
}
