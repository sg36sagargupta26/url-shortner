import service.UrlShortenerService;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Demo: Parallel URL Shortening with Thread Pool Execution.
 *
 * Shows three approaches:
 *   1. Basic synchronous (sequential)
 *   2. Parallel with Future (submit all, collect results)
 *   3. Parallel with callbacks (process results as they arrive)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        UrlShortenerService service = new UrlShortenerService(4); // 4 threads

        System.out.println("=== URL Shortener - Parallel Execution Demo ===\n");

        // Sample URLs to shorten
        List<String> urls = List.of(
                "https://example.com/very-long-url-1",
                "https://example.com/very-long-url-2",
                "https://example.com/very-long-url-3",
                "https://example.com/very-long-url-4",
                "https://example.com/very-long-url-5",
                "https://example.com/very-long-url-6",
                "https://example.com/very-long-url-7",
                "https://example.com/very-long-url-8"
        );

        // ── Approach 1: Sequential (for comparison) ──
        System.out.println("1. Sequential execution:");
        long start = System.nanoTime();
        for (String url : urls) {
            String shortUrl = service.shortenUrl(url);
            System.out.println("   " + url + " -> " + shortUrl);
        }
        long seqTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println("   Time: " + seqTime + " ms\n");

        // ── Approach 2: Parallel with Future.get() ──
        System.out.println("2. Parallel execution (Future - submit all, collect all):");
        start = System.nanoTime();
        List<Future<String>> futures = urls.stream()
                .map(service::shortenUrlAsync)
                .toList();

        for (int i = 0; i < futures.size(); i++) {
            String result = futures.get(i).get();
            System.out.println("   [" + i + "] -> " + result);
        }
        long parTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println("   Time: " + parTime + " ms");
        System.out.println("   " + service.getPoolStats() + "\n");

        // ── Approach 3: Parallel with Callbacks ──
        System.out.println("3. Parallel execution (CompletableFuture with callbacks):");
        start = System.nanoTime();
        service.shortenUrlsWithCallback(
                urls,
                shortUrl -> System.out.println("   [DONE] " + shortUrl),
                error -> System.err.println("   [ERROR] " + error.getMessage())
        );
        long callbackTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println("   Time: " + callbackTime + " ms");
        System.out.println("   " + service.getPoolStats() + "\n");

        // ── Speedup Summary ──
        System.out.println("=== Summary ===");
        System.out.println("Sequential:  " + seqTime + " ms");
        System.out.println("Parallel:    " + parTime + " ms ("
                + String.format("%.1fx faster", (double) seqTime / parTime) + ")");
        System.out.println("Callbacks:   " + callbackTime + " ms");

        service.shutdown();
        System.out.println("\nService shut down.");
    }
}
