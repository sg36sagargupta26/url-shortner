package util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Utility for benchmarking parallel vs sequential execution.
 */
public class ParallelBenchmark {

    private final ExecutorService executorService;

    public ParallelBenchmark(int poolSize) {
        this.executorService = Executors.newFixedThreadPool(poolSize);
    }

    /**
     * Runs a single task across multiple threads (each thread runs the same task).
     * Useful for load-testing the URL shortener.
     */
    public <T> List<T> runParallel(int threadCount, Supplier<T> task) throws Exception {
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executorService.submit(task::get));
        }

        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get());
        }
        return results;
    }

    /**
     * Measures execution time of a task.
     */
    public static long measure(ThrowingRunnable task) throws Exception {
        long start = System.nanoTime();
        task.run();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    public void shutdown() {
        executorService.shutdown();
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
