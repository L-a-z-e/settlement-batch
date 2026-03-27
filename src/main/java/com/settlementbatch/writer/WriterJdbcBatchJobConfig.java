package com.settlementbatch.writer;

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

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Configuration
@Profile("writer-jdbc-batch")
@RequiredArgsConstructor
public class WriterJdbcBatchJobConfig {

    private final JdbcTemplate jdbcTemplate;
    private final ChunkTimingListener chunkTimingListener;

    @Value("${benchmark.chunk-size:1000}")
    private int chunkSize;

    @Bean
    public Step writerJdbcBatchStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return new StepBuilder("writerJdbcBatchStep", jobRepository)
                .<Order, Settlement>chunk(chunkSize, tm)
                .reader(new ZeroOffsetItemReader(jdbcTemplate, chunkSize))
                .processor(toSettlementProcessor())
                .writer(jdbcBatchWriter())
                .listener(chunkTimingListener)
                .build();
    }

    @Bean
    public Job writerJdbcBatchJob(JobRepository jobRepository, Step writerJdbcBatchStep) {
        return new JobBuilder("writerJdbcBatchJob", jobRepository)
                .start(writerJdbcBatchStep)
                .build();
    }

    private ItemProcessor<Order, Settlement> toSettlementProcessor() {
        return order -> {
            long fee = order.getAmount() * 3 / 100;
            return new Settlement(
                    order.getId(),
                    order.getSellerId(),
                    order.getAmount(),
                    fee,
                    order.getAmount() - fee
            );
        };
    }

    private ItemWriter<Settlement> jdbcBatchWriter() {
        return settlements -> {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO settlements (order_id, seller_id, amount, fee, net_amount, settled_at) VALUES (?, ?, ?, ?, ?, ?)",
                    settlements.getItems().stream().map(s -> new Object[]{
                            s.getOrderId(), s.getSellerId(), s.getAmount(),
                            s.getFee(), s.getNetAmount(), Timestamp.valueOf(LocalDateTime.now())
                    }).toList(),
                    new int[]{java.sql.Types.BIGINT, java.sql.Types.BIGINT, java.sql.Types.BIGINT,
                            java.sql.Types.BIGINT, java.sql.Types.BIGINT, java.sql.Types.TIMESTAMP}
            );

            jdbcTemplate.batchUpdate(
                    "UPDATE orders SET status = 'SETTLED' WHERE id = ?",
                    settlements.getItems().stream()
                            .map(s -> new Object[]{s.getOrderId()})
                            .toList(),
                    new int[]{java.sql.Types.BIGINT}
            );
        };
    }
}
