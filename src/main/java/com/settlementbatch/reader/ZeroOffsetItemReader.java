package com.settlementbatch.reader;

import com.settlementbatch.domain.Order;
import org.springframework.batch.item.ItemReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

public class ZeroOffsetItemReader implements ItemReader<Order> {

    private final JdbcTemplate jdbcTemplate;
    private final int pageSize;
    private final Long minId;
    private final Long maxId;

    private Long lastId;
    private List<Order> buffer;
    private int bufferIndex;

    private static final RowMapper<Order> ROW_MAPPER = (rs, rowNum) ->
            Order.fromRow(
                    rs.getLong("id"),
                    rs.getLong("seller_id"),
                    rs.getLong("amount"),
                    rs.getString("status"),
                    rs.getDate("order_date").toLocalDate(),
                    rs.getTimestamp("created_at").toLocalDateTime()
            );

    public ZeroOffsetItemReader(JdbcTemplate jdbcTemplate, int pageSize) {
        this(jdbcTemplate, pageSize, null, null);
    }

    public ZeroOffsetItemReader(JdbcTemplate jdbcTemplate, int pageSize, Long minId, Long maxId) {
        this.jdbcTemplate = jdbcTemplate;
        this.pageSize = pageSize;
        this.minId = minId;
        this.maxId = maxId;
        this.lastId = minId != null ? minId - 1 : 0L;
        this.buffer = List.of();
        this.bufferIndex = 0;
    }

    @Override
    public Order read() {
        if (bufferIndex >= buffer.size()) {
            loadNextBatch();
            bufferIndex = 0;
            if (buffer.isEmpty()) {
                return null;
            }
        }
        Order order = buffer.get(bufferIndex++);
        lastId = order.getId();
        return order;
    }

    private void loadNextBatch() {
        if (maxId != null) {
            buffer = jdbcTemplate.query(
                    "SELECT * FROM orders WHERE id > ? AND id <= ? ORDER BY id LIMIT ?",
                    ROW_MAPPER, lastId, maxId, pageSize
            );
        } else {
            buffer = jdbcTemplate.query(
                    "SELECT * FROM orders WHERE id > ? ORDER BY id LIMIT ?",
                    ROW_MAPPER, lastId, pageSize
            );
        }
    }
}
