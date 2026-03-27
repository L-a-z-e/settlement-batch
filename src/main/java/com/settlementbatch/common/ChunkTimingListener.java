package com.settlementbatch.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class ChunkTimingListener implements ChunkListener {

    private final MeterRegistry meterRegistry;
    private final ThreadLocal<Long> chunkStart = new ThreadLocal<>();
    private final AtomicInteger chunkCount = new AtomicInteger(0);
    private final AtomicLong firstChunkMs = new AtomicLong(0);
    private final AtomicLong lastChunkMs = new AtomicLong(0);

    public ChunkTimingListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void beforeChunk(ChunkContext context) {
        chunkStart.set(System.currentTimeMillis());
    }

    @Override
    public void afterChunk(ChunkContext context) {
        long elapsed = System.currentTimeMillis() - chunkStart.get();
        int count = chunkCount.incrementAndGet();

        if (count == 1) {
            firstChunkMs.set(elapsed);
        }
        lastChunkMs.set(elapsed);

        Timer.builder("batch.chunk.duration")
                .tag("step", context.getStepContext().getStepName())
                .register(meterRegistry)
                .record(elapsed, TimeUnit.MILLISECONDS);

        if (count % 100 == 0) {
            log.info("  Chunk #{}: {}ms (first: {}ms, latest: {}ms)",
                    count, elapsed, firstChunkMs.get(), lastChunkMs.get());
        }
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        log.error("Chunk error at chunk #{}", chunkCount.get());
    }

    public void reset() {
        chunkCount.set(0);
        firstChunkMs.set(0);
        lastChunkMs.set(0);
    }

    public long getFirstChunkMs() {
        return firstChunkMs.get();
    }

    public long getLastChunkMs() {
        return lastChunkMs.get();
    }
}
