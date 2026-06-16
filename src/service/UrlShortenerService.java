package service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.*;

/**
 * URL Shortener Service with parallel execution support.
 * Uses a thread pool to handle multiple URL shortening requests concurrently.
 */
public class UrlShortenerService {

    // Thread-safe map to store URL mappings (original -> shortened)
    private final Map<String, String> shortToLong = new ConcurrentHashMap<>();
    private final Map<String, String> longToShort = new ConcurrentHashMap<>();

    // Custom thread pool for parallel execution
    private final ExecutorService executorService;

    // Counter for generating unique short codes (atomic for thread safety)
    private final java.util.concurrent.atomic.AtomicLong counter = new java.util.concurrent.atomic.AtomicLong(0);

    private static final String BASE_URL = "https://short.ly/";

    /**
     * Creates a service with a thread pool sized to available processors.
     */
    public UrlShortenerService() {
        this(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a service with a custom thread pool size.
     */
    public UrlShortenerService(int poolSize) {
        this.executorService = new ThreadPoolExecutor(
                poolSize,                          // core pool size
                poolSize * 2,                      // max pool size
                60L, TimeUnit.SECONDS,             // keep-alive time
                new LinkedBlockingQueue<>(1000),   // work queue
                new ThreadPoolExecutor.CallerRunsPolicy() // rejection policy
        );
    }

    /**
     * Shortens a single URL synchronously.
     */
    public String shortenUrl(String longUrl) {
        // Return existing mapping if already shortened
        if (longToShort.containsKey(longUrl)) {
            return longToShort.get(longUrl);
        }

        String shortCode = generateShortCode(longUrl);
        String shortUrl = BASE_URL + shortCode;

        shortToLong.put(shortUrl, longUrl);
        longToShort.put(longUrl, shortUrl);

        return shortUrl;
    }

    /**
     * Gets the original URL for a shortened URL.
     */
    public String getOriginalUrl(String shortUrl) {
        return shortToLong.get(shortUrl);
    }

    /**
     * Shortens a URL asynchronously - returns a Future for parallel execution.
     * Submit multiple URLs and collect results when ready.
     */
    public Future<String> shortenUrlAsync(String longUrl) {
        return executorService.submit(() -> shortenUrl(longUrl));
    }

    /**
     * Shortens multiple URLs in parallel using the thread pool.
     * Returns a list of shortened URLs in the same order.
     */
    public java.util.List<String> shortenUrlsInParallel(java.util.List<String> longUrls) throws Exception {
        java.util.List<Future<String>> futures = new java.util.ArrayList<>();

        // Submit all tasks for parallel execution
        for (String url : longUrls) {
            futures.add(shortenUrlAsync(url));
        }

        // Collect results
        java.util.List<String> results = new java.util.ArrayList<>();
        for (Future<String> future : futures) {
            results.add(future.get()); // blocks until result is ready
        }

        return results;
    }

    /**
     * Shortens multiple URLs and calls a callback for each result as it completes.
     * This enables processing results as soon as they're available (not FIFO order).
     */
    public void shortenUrlsWithCallback(java.util.List<String> longUrls,
                                         java.util.function.Consumer<String> onSuccess,
                                         java.util.function.Consumer<Exception> onError) {
        java.util.List<CompletableFuture<Void>> completableFutures = new java.util.ArrayList<>();

        for (String url : longUrls) {
            CompletableFuture<Void> cf = CompletableFuture
                    .supplyAsync(() -> shortenUrl(url), executorService)
                    .thenAccept(onSuccess)
                    .exceptionally(ex -> {
                        onError.accept((Exception) ex);
                        return null;
                    });
            completableFutures.add(cf);
        }

        // Wait for all to complete
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Generates a short code using SHA-256 hash (shortened).
     */
    private String generateShortCode(String longUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((longUrl + counter.incrementAndGet()).getBytes());
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return encoded.substring(0, 8); // Use first 8 characters
        } catch (NoSuchAlgorithmException e) {
            // Fallback: use counter
            return Long.toHexString(counter.get());
        }
    }

    /**
     * Returns pool statistics for monitoring.
     */
    public String getPoolStats() {
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
        return String.format(
                "Pool Stats [Active: %d, Pool: %d, Queue: %d, Completed: %d]",
                tpe.getActiveCount(),
                tpe.getPoolSize(),
                tpe.getQueue().size(),
                tpe.getCompletedTaskCount()
        );
    }

    /**
     * Shuts down the executor service gracefully.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
