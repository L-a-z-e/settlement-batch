package com.settlementbatch.reader;

import com.settlementbatch.common.ChunkTimingListener;
import com.settlementbatch.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Profile("reader-zero-offset")
@RequiredArgsConstructor
public class ReaderZeroOffsetJobConfig {

    private final JdbcTemplate jdbcTemplate;
    private final ChunkTimingListener chunkTimingListener;

    @Value("${benchmark.chunk-size:1000}")
    private int chunkSize;

    @Bean
    public ZeroOffsetItemReader zeroOffsetReader() {
        return new ZeroOffsetItemReader(jdbcTemplate, chunkSize);
    }

    @Bean
    public Step readerZeroOffsetStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return new StepBuilder("readerZeroOffsetStep", jobRepository)
                .<Order, Order>chunk(chunkSize, tm)
                .reader(zeroOffsetReader())
                .writer(noopWriter())
                .listener(chunkTimingListener)
                .build();
    }

    @Bean
    public Job readerZeroOffsetJob(JobRepository jobRepository, Step readerZeroOffsetStep) {
        return new JobBuilder("readerZeroOffsetJob", jobRepository)
                .start(readerZeroOffsetStep)
                .build();
    }

    private ItemWriter<Order> noopWriter() {
        return items -> {};
    }
}
