package com.company.threadlog2database.log;

/**
 * Created by DungPQ16 on 24/09/2025 at 3:52 PM.
 */

import java.time.Instant;

public class AppLogService {
    private final AsyncDbLogger logger;

    public AppLogService(AsyncDbLogger logger) {
        this.logger = logger;
    }

    public void info(String source, String message) {
        logger.log(new AsyncDbLogger.LogRecord(Instant.now(), "INFO", source, message, null));
    }

    public void error(String source, String message, String contextJson) {
        logger.log(new AsyncDbLogger.LogRecord(Instant.now(), "ERROR", source, message, contextJson));
    }

    // add debug/warn, etc…
}
