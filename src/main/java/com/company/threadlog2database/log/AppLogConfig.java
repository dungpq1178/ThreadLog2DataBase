package com.company.threadlog2database.log;

/**
 * Created by DungPQ16 on 24/09/2025 at 3:50 PM.
 */
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
public class AppLogConfig {

//    @Value("${main.datasource.url}")
//    private  String jdbcUrl;
//    @Value("${main.datasource.username}")
//    private  String user;
//    @Value("${main.datasource.password}")
//    private  String pass;

    @Value("${app.log.batch-size:200}")
    private int batchSize;

    @Value("${app.log.flush-interval-ms:1000}")
    private long flushIntervalMs;

    @Value("${app.log.queue-capacity:10000}")
    private int queueCapacity;

    @Bean(destroyMethod = "close")
    public AsyncDbLogger asyncDbLogger(DataSource dataSource) throws Exception {
        // Grab JDBC info from the pool (works for HikariDataSource)
        String url;
        String user;
        String pass;
        if (dataSource instanceof HikariDataSource hds) {
            url  = hds.getJdbcUrl();
            user = hds.getUsername();
            pass = hds.getPassword();
        } else {
            // Fallback: open a conn once just to discover URL (not used later)
            try (Connection c = dataSource.getConnection()) {
                url = c.getMetaData().getURL();
            }
            // No username/password discovery here; set them in a custom factory if needed.
            user = System.getProperty("APP_DB_USER", "");
            pass = System.getProperty("APP_DB_PASS", "");
        }

        // Choose one constructor based on URL
        final AsyncDbLogger logger = AsyncDbLogger.createDefault(url,user,pass);

        // Optionally override defaults (capacity, batch, interval)
        // If you want to set these, expose a ctor that accepts them, or add setters on AsyncDbLogger.
        // For brevity, we’ll assume you added a custom factory:
        // return new AsyncDbLogger(url, user, pass, insertSql, queueCapacity, batchSize, flushIntervalMs);

        return logger;
    }

    @Bean
    public AppLogService appLogService(AsyncDbLogger logger) {
        return new AppLogService(logger);
    }
}
