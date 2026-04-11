package com.allinweb.ch.util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Logback appender that forwards each log event into {@link ConsoleRingBuffer}.
 * Wire it up in logback.xml by adding an {@code <appender>} of this class and
 * referencing it from the root logger.
 */
public class ConsoleRingBufferAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
        try {
            String line = String.format("%s [%s] %s - %s",
                    event.getLevel(),
                    event.getLoggerName(),
                    event.getThreadName(),
                    event.getFormattedMessage());
            ConsoleRingBuffer.append(line);
        } catch (Throwable t) {
            // Never throw from inside the logging pipeline.
        }
    }
}
