package com.company.threadlog2database.log;

/**
 * Created by DungPQ16 on 24/09/2025 at 3:48 PM.
 */

import org.springframework.beans.factory.annotation.Value;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * AsyncDbLogger
 *  - Offer log records to a bounded queue (non-blocking).
 *  - Background thread batches and inserts via JDBC.
 *  - Flush triggers: batch size or flush interval timeout.
 */
public class AsyncDbLogger implements AutoCloseable {
    public static final int DEFAULT_CAPACITY = 10_000;

    public static final class LogRecord {
        public final Instant ts;
        public final String level;
        public final String source;
        public final String message;
        public final String contextJson; // optional JSON string

        public LogRecord(Instant ts, String level, String source, String message, String contextJson) {
            this.ts = ts == null ? Instant.now() : ts;
            this.level = level == null ? "INFO" : level;
            this.source = source == null ? "app" : source;
            this.message = message == null ? "" : message;
            this.contextJson = contextJson;
        }
    }

    private final String jdbcUrl;

    private final String user;

    private final String pass;
    private final String insertSql; // parameterized SQL
    private final LinkedBlockingDeque<LogRecord> queue;
    private final int batchSize;
    private final long flushIntervalMillis;

    private volatile boolean running = true;
    private final Thread worker;


    public AsyncDbLogger(
            String jdbcUrl,
            String user,
            String pass,
            String insertSql,
            int capacity,
            int batchSize,
            long flushIntervalMillis
    ) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.pass = pass;
        this.insertSql = insertSql;
        this.queue = new LinkedBlockingDeque<>(Math.max(1, 999_999_999));
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMillis = Math.max(100L, flushIntervalMillis);

        this.worker = new Thread(this::runLoop, "async-db-logger");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    public static AsyncDbLogger createDefault(String jdbcUrl,String  user,String  pass) {
        String sql = "INSERT INTO app_logs(ts, level, source, message, context) VALUES (?, ?, ?, ?, ?)";
        return new AsyncDbLogger(jdbcUrl, user, pass, sql, DEFAULT_CAPACITY, 9_999, 99_000_000);
    }

    /** Non-blocking: drops oldest record if queue is full. */
    public void log(LogRecord rec) {
        if (!running) return;
        if (!queue.offer(rec)) {
            // queue full → drop oldest, then add
            queue.pollFirst();
            queue.offer(rec);
        }
    }

    /** Helper overloads */
    public void info(String source, String message) { log(new LogRecord(Instant.now(), "INFO", source, message, null)); }

    public void error(String source, String message, String contextJson) { log(new LogRecord(Instant.now(), "ERROR", source, message, contextJson)); }

    private void runLoop() {
        List<LogRecord> buf = new ArrayList<>(batchSize);
        long count = 0;
        long countbuf;
        long lastFlush = System.currentTimeMillis();
        Connection conn = null;
        PreparedStatement ps = null;
        long last_timeworking  = System.nanoTime();
        try  {
            conn = DriverManager.getConnection(jdbcUrl, user, pass);
            ps = conn.prepareStatement(insertSql);
            conn.setAutoCommit(false);

            while (running || !queue.isEmpty()) {

                LogRecord first = queue.poll(flushIntervalMillis, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();

                if (first != null) {
                    buf.add(first);
                    // Drain the rest in one shot (non-blocking)
                     queue.drainTo(buf, batchSize);
                }
                // Time-based flush or size-based flush
                if (!buf.isEmpty() && (buf.size() >= batchSize || (now - lastFlush) >= flushIntervalMillis)) {
                    try {
                        countbuf = 0;

                        for (LogRecord r : buf) {
                            countbuf++;
                            ps.setTimestamp(1, Timestamp.from(r.ts));
                            ps.setString(2, r.level);
                            ps.setString(3, r.source);
                            ps.setString(4, r.message);
                            ps.setString(5, r.contextJson);
                            ps.addBatch();
                        }

                        long start = System.nanoTime();

                        ps.executeBatch();
                        conn.commit();

                        long end = System.nanoTime();
                        long elapsedNanos = end - start;
                        long elapsedMillis = elapsedNanos / 1_000_000;
                        System.out.println("run batch "+countbuf +" records in " + elapsedMillis + " ms");


                    } catch (SQLException e) {
                        // Best-effort: rollback and continue (don't kill app)
                        try {
                            conn.rollback();
                        }
                        catch (SQLException ignore) {

                        }
                        e.printStackTrace();
                    } finally {
                        buf.clear();
                        lastFlush = now;
                    }
                }
                if(running && queue.isEmpty()){
                    long timeworking = System.nanoTime();
                    long elapsedNanos = timeworking - last_timeworking;
                    long elapsedMillis = elapsedNanos / 1_000_000;
                    System.out.println("timeworking : "+ elapsedMillis + " ms");
                    last_timeworking = timeworking;
                    System.out.println("sleep ");
                    Thread.sleep(1000);
                }
            }
            System.out.println("remain  batch  :  " + count + " records");
            // Final drain (if any left due to close())
            if (!buf.isEmpty()) {
                try {
                    System.out.println("remain batch  :  " + buf.size() + " records");
                    for (LogRecord r : buf) {
                        ps.setTimestamp(1, Timestamp.from(r.ts));
                        ps.setString(2, r.level);
                        ps.setString(3, r.source);
                        ps.setString(4, r.message);
                        ps.setString(5, r.contextJson);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    conn.commit();
                } catch (SQLException e) {
                    try { conn.rollback(); } catch (SQLException ignore) {}
                    e.printStackTrace();
                }
            }
        } catch (Exception outer) {
            // If connection fails at start, print and exit thread (caller can recreate logger)
            outer.printStackTrace();
        }finally {
            try { if (ps != null) ps.close();if (conn != null) conn.close();} catch (SQLException ignore) {}
        }
    }

    /** Flush and stop the worker. */
    @Override
    public void close() {
        running = false;
        try {
            worker.join(5_000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

}
