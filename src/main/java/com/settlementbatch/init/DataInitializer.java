package com.settlementbatch.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Value("${benchmark.data-count:1000000}")
    private int dataCount;

    private static final int BATCH_SIZE = 1000;
    private static final int SELLER_COUNT = 500;

    @Override
    public void run(ApplicationArguments args) {
        Long existingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
        long existing = existingCount != null ? existingCount : 0;
        if (existing >= dataCount) {
            log.info("Data already exists ({} rows). Skipping initialization.", existing);
            return;
        }

        long remaining = dataCount - existing;
        log.info("Initializing orders: existing={}, target={}, inserting={}...", existing, dataCount, remaining);
        long start = System.currentTimeMillis();

        LocalDate baseDate = LocalDate.of(2026, 3, 25);

        for (long i = 0; i < remaining; i += BATCH_SIZE) {
            int currentBatchSize = (int) Math.min(BATCH_SIZE, remaining - i);

            jdbcTemplate.batchUpdate(
                    "INSERT INTO orders (seller_id, amount, status, order_date, created_at) VALUES (?, ?, ?, ?, ?)",
                    new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int j) throws java.sql.SQLException {
                            ThreadLocalRandom random = ThreadLocalRandom.current();
                            ps.setLong(1, random.nextLong(1, SELLER_COUNT + 1));
                            ps.setLong(2, random.nextLong(1000, 100001));
                            ps.setString(3, "CONFIRMED");
                            ps.setDate(4, Date.valueOf(baseDate));
                            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                        }

                        @Override
                        public int getBatchSize() {
                            return currentBatchSize;
                        }
                    }
            );

            if ((i + currentBatchSize) % 100000 == 0) {
                log.info("  Inserted {}/{} rows...", i + currentBatchSize, remaining);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("Data initialization complete: {} rows inserted in {}ms ({} rows/s)",
                remaining, elapsed, remaining * 1000L / elapsed);
    }
}
