package com.settlementbatch.parallel;

import com.settlementbatch.common.ChunkTimingListener;
import com.settlementbatch.domain.Order;
import com.settlementbatch.domain.Settlement;
import com.settlementbatch.reader.ZeroOffsetItemReader;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Configuration
@Profile("partitioning")
@RequiredArgsConstructor
public class PartitioningJobConfig {

    private final JdbcTemplate jdbcTemplate;
    private final ChunkTimingListener chunkTimingListener;

    @Value("${benchmark.chunk-size:1000}")
    private int chunkSize;

    private static final int GRID_SIZE = 4;

    @Bean
    public OrderRangePartitioner orderRangePartitioner() {
        return new OrderRangePartitioner(jdbcTemplate);
    }

    @Bean
    @StepScope
    public ZeroOffsetItemReader partitionedReader(
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return new ZeroOffsetItemReader(jdbcTemplate, chunkSize, minId, maxId);
    }

    @Bean
    public Step workerStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return new StepBuilder("workerStep", jobRepository)
                .<Order, Settlement>chunk(chunkSize, tm)
                .reader(partitionedReader(null, null))
                .processor(settlementProcessor())
                .writer(jdbcBatchWriter())
                .listener(chunkTimingListener)
                .build();
    }

    @Bean
    public Step partitionedMasterStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return new StepBuilder("partitionedMasterStep", jobRepository)
                .partitioner("workerStep", orderRangePartitioner())
                .step(workerStep(jobRepository, tm))
                .gridSize(GRID_SIZE)
                .taskExecutor(partitionTaskExecutor())
                .build();
    }

    @Bean
    public Job partitioningJob(JobRepository jobRepository, Step partitionedMasterStep) {
        return new JobBuilder("partitioningJob", jobRepository)
                .start(partitionedMasterStep)
                .build();
    }

    @Bean
    public TaskExecutor partitionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(GRID_SIZE);
        executor.setMaxPoolSize(GRID_SIZE);
        executor.setThreadNamePrefix("partition-");
        executor.initialize();
        return executor;
    }

    private ItemProcessor<Order, Settlement> settlementProcessor() {
        return order -> {
            long fee = order.getAmount() * 3 / 100;
            return new Settlement(
                    order.getId(), order.getSellerId(), order.getAmount(),
                    fee, order.getAmount() - fee
            );
        };
    }

    private ItemWriter<Settlement> jdbcBatchWriter() {
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

            jdbcTemplate.batchUpdate(
                    "UPDATE orders SET status = 'SETTLED' WHERE id = ?",
                    new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                            ps.setLong(1, settlements.getItems().get(i).getOrderId());
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
