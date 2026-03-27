package com.settlementbatch.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BenchmarkResultLogger {

    public void log(String caseName, JobExecution execution, long durationMs) {
        long totalRead = 0;
        long totalWrite = 0;
        long totalSkip = 0;

        for (StepExecution step : execution.getStepExecutions()) {
            // Partitioning: MasterStep이 Worker들의 readCount를 이미 합산하고 있음
            // (DefaultStepExecutionAggregator)
            // Worker StepExecution은 "stepName:partitionN" 형태 → 건너뜀
            if (step.getStepName().contains(":")) {
                continue;
            }
            totalRead += step.getReadCount();
            totalWrite += step.getWriteCount();
            totalSkip += step.getSkipCount();
        }

        double throughput = totalRead > 0 ? totalRead * 1000.0 / durationMs : 0;

        String result = String.format("""

                ════════════════════════════════════════════════
                 Benchmark Result: %s
                ════════════════════════════════════════════════
                 Status:           %s
                 Total Read:       %,d
                 Total Write:      %,d
                 Total Skip:       %,d
                 Total Duration:   %,.1fs
                 Throughput:       %,.0f records/s
                ════════════════════════════════════════════════
                """,
                caseName,
                execution.getStatus(),
                totalRead,
                totalWrite,
                totalSkip,
                durationMs / 1000.0,
                throughput
        );

        log.info(result);
    }
}
