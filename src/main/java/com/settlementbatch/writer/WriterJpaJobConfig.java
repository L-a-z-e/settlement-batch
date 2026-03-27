package com.settlementbatch.writer;

import com.settlementbatch.common.ChunkTimingListener;
import com.settlementbatch.domain.Order;
import com.settlementbatch.reader.ZeroOffsetItemReader;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Profile("writer-jpa")
@RequiredArgsConstructor
public class WriterJpaJobConfig {

    private final JdbcTemplate jdbcTemplate;
    private final EntityManagerFactory entityManagerFactory;
    private final ChunkTimingListener chunkTimingListener;

    @Value("${benchmark.chunk-size:1000}")
    private int chunkSize;

    @Bean
    public Step writerJpaStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return new StepBuilder("writerJpaStep", jobRepository)
                .<Order, Order>chunk(chunkSize, tm)
                .reader(new ZeroOffsetItemReader(jdbcTemplate, chunkSize))
                .processor(settleProcessor())
                .writer(jpaWriter())
                .listener(chunkTimingListener)
                .build();
    }

    @Bean
    public Job writerJpaJob(JobRepository jobRepository, Step writerJpaStep) {
        return new JobBuilder("writerJpaJob", jobRepository)
                .start(writerJpaStep)
                .build();
    }

    private ItemProcessor<Order, Order> settleProcessor() {
        return order -> {
            order.settle();
            return order;
        };
    }

    private JpaItemWriter<Order> jpaWriter() {
        JpaItemWriter<Order> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(entityManagerFactory);
        return writer;
    }
}
