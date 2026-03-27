package com.settlementbatch.reader;

import com.settlementbatch.common.ChunkTimingListener;
import com.settlementbatch.domain.Order;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Profile("reader-offset")
@RequiredArgsConstructor
public class ReaderOffsetJobConfig {

    private final EntityManagerFactory entityManagerFactory;
    private final ChunkTimingListener chunkTimingListener;

    @Value("${benchmark.chunk-size:1000}")
    private int chunkSize;

    @Bean
    public JpaPagingItemReader<Order> offsetReader() {
        return new JpaPagingItemReaderBuilder<Order>()
                .name("offsetReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT o FROM Order o WHERE o.status = 'CONFIRMED' ORDER BY o.id")
                .pageSize(chunkSize)
                .build();
    }

    @Bean
    public Step readerOffsetStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return new StepBuilder("readerOffsetStep", jobRepository)
                .<Order, Order>chunk(chunkSize, tm)
                .reader(offsetReader())
                .writer(noopWriter())
                .listener(chunkTimingListener)
                .build();
    }

    @Bean
    public Job readerOffsetJob(JobRepository jobRepository, Step readerOffsetStep) {
        return new JobBuilder("readerOffsetJob", jobRepository)
                .start(readerOffsetStep)
                .build();
    }

    private ItemWriter<Order> noopWriter() {
        return items -> {};
    }
}
