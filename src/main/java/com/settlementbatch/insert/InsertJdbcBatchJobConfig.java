package com.settlementbatch.insert;

import com.settlementbatch.common.ChunkTimingListener;
import com.settlementbatch.domain.Order;
import com.settlementbatch.domain.Settlement;
import com.settlementbatch.reader.ZeroOffsetItemReader;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Configuration
@Profile("insert-jdbc-rewrite")
@RequiredArgsConstructor
public class InsertJdbcBatchJobConfig {

    private final JdbcTemplate jdbcTemplate;
    private final ChunkTimingListener chunkTimingListener;

    @Value("${benchmark.insert-data-count:100000}")
    private int insertDataCount;

    @Value("${benchmark.chunk-size:1000}")
    private int chunkSize;

    @Bean
    public Step insertJdbcRewriteStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return new StepBuilder("insertJdbcRewriteStep", jobRepository)
                .<Order, Settlement>chunk(chunkSize, tm)
                .reader(new ZeroOffsetItemReader(jdbcTemplate, chunkSize, 1L, (long) insertDataCount))
                .processor(toSettlementProcessor())
                .writer(jdbcBatchInsertWriter())
                .listener(chunkTimingListener)
                .build();
    }

    @Bean
    public Job insertJdbcRewriteJob(JobRepository jobRepository, Step insertJdbcRewriteStep) {
        return new JobBuilder("insertJdbcRewriteJob", jobRepository)
                .start(insertJdbcRewriteStep)
                .build();
    }

    private ItemProcessor<Order, Settlement> toSettlementProcessor() {
        return order -> {
            long fee = order.getAmount() * 3 / 100;
            return new Settlement(
                    order.getId(), order.getSellerId(), order.getAmount(),
                    fee, order.getAmount() - fee
            );
        };
    }

    private ItemWriter<Settlement> jdbcBatchInsertWriter() {
        return settlements -> {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO settlements (order_id, seller_id, amount, fee, net_amount, settled_at) VALUES (?, ?, ?, ?, ?, ?)",
                    new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                            Settlement s = settlements.getItems().get(i);
                            ps.setLong(1, s.getOrderId());
                            ps.setLong(2, s.getSellerId());
                            ps.setLong(3, s.getAmount());
                            ps.setLong(4, s.getFee());
                            ps.setLong(5, s.getNetAmount());
                            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
                        }

                        @Override
                        public int getBatchSize() {
                            return settlements.getItems().size();
                        }
                    }
            );
        };
    }
}
