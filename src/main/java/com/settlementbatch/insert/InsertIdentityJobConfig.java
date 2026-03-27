package com.settlementbatch.insert;

import com.settlementbatch.domain.Order;
import com.settlementbatch.domain.Settlement;
import com.settlementbatch.domain.SettlementRepository;
import com.settlementbatch.common.ChunkTimingListener;
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

@Configuration
@Profile("insert-identity")
@RequiredArgsConstructor
public class InsertIdentityJobConfig {

    private final JdbcTemplate jdbcTemplate;
    private final SettlementRepository settlementRepository;
    private final ChunkTimingListener chunkTimingListener;

    @Value("${benchmark.insert-data-count:100000}")
    private int insertDataCount;

    @Value("${benchmark.chunk-size:1000}")
    private int chunkSize;

    @Bean
    public Step insertIdentityStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return new StepBuilder("insertIdentityStep", jobRepository)
                .<Order, Settlement>chunk(chunkSize, tm)
                .reader(new ZeroOffsetItemReader(jdbcTemplate, chunkSize, 1L, (long) insertDataCount))
                .processor(toSettlementProcessor())
                .writer(jpaSaveAllWriter())
                .listener(chunkTimingListener)
                .build();
    }

    @Bean
    public Job insertIdentityJob(JobRepository jobRepository, Step insertIdentityStep) {
        return new JobBuilder("insertIdentityJob", jobRepository)
                .start(insertIdentityStep)
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

    private ItemWriter<Settlement> jpaSaveAllWriter() {
        return items -> settlementRepository.saveAll(items.getItems());
    }
}
