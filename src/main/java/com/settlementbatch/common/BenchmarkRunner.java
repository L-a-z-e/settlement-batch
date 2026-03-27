package com.settlementbatch.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class BenchmarkRunner implements ApplicationRunner {

    private final JobLauncher jobLauncher;
    private final ApplicationContext context;
    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final BenchmarkResultLogger resultLogger;

    private static final Map<String, String> PROFILE_TO_JOB = Map.of(
            "reader-offset", "readerOffsetJob",
            "reader-zero-offset", "readerZeroOffsetJob",
            "writer-jpa", "writerJpaJob",
            "writer-jdbc-batch", "writerJdbcBatchJob",
            "insert-identity", "insertIdentityJob",
            "insert-jdbc-rewrite", "insertJdbcRewriteJob",
            "sequential", "sequentialJob",
            "partitioning", "partitioningJob"
    );

    private static final Set<String> NEEDS_DATA_RESET = Set.of(
            "writer-jpa", "writer-jdbc-batch", "insert-identity",
            "insert-jdbc-rewrite", "sequential", "partitioning"
    );

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String[] profiles = environment.getActiveProfiles();
        String benchmarkCase = Arrays.stream(profiles)
                .filter(PROFILE_TO_JOB::containsKey)
                .findFirst()
                .orElse(null);

        if (benchmarkCase == null) {
            log.warn("No benchmark case profile found. Active profiles: {}", Arrays.toString(profiles));
            log.info("Available cases: {}", PROFILE_TO_JOB.keySet());
            return;
        }

        String jobName = PROFILE_TO_JOB.get(benchmarkCase);
        log.info("Running benchmark case: {} (job: {})", benchmarkCase, jobName);

        if (NEEDS_DATA_RESET.contains(benchmarkCase)) {
            resetData();
        }

        Job job = context.getBean(jobName, Job.class);
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("case", benchmarkCase)
                .toJobParameters();

        long start = System.currentTimeMillis();
        var execution = jobLauncher.run(job, params);
        long duration = System.currentTimeMillis() - start;

        resultLogger.log(benchmarkCase, execution, duration);

        // Job 완료 후 앱 종료
        SpringApplication.exit(context, () -> 0);
    }

    private void resetData() {
        log.info("Resetting data for benchmark...");
        jdbcTemplate.execute("TRUNCATE TABLE settlements");
        jdbcTemplate.execute("UPDATE orders SET status = 'CONFIRMED'");
        log.info("Data reset complete.");
    }
}
