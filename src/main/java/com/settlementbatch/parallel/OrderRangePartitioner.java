package com.settlementbatch.parallel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class OrderRangePartitioner implements Partitioner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Long minId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM orders WHERE status = 'CONFIRMED'", Long.class);
        Long maxId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM orders WHERE status = 'CONFIRMED'", Long.class);

        if (minId == null || maxId == null) {
            return Map.of();
        }

        long range = (maxId - minId) / gridSize + 1;
        Map<String, ExecutionContext> partitions = new HashMap<>();

        for (int i = 0; i < gridSize; i++) {
            ExecutionContext ctx = new ExecutionContext();
            long partMinId = minId + (i * range);
            long partMaxId = Math.min(minId + ((i + 1) * range) - 1, maxId);
            ctx.putLong("minId", partMinId);
            ctx.putLong("maxId", partMaxId);
            partitions.put("partition" + i, ctx);
            log.info("Partition {}: id {} ~ {}", i, partMinId, partMaxId);
        }

        return partitions;
    }
}
