package com.shortly.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses User-Agent strings into device, browser, and OS information.
 * Uses uap-java with an in-memory LRU cache for performance.
 */
@Service
public class UserAgentParserService {

    private static final Logger log = LoggerFactory.getLogger(UserAgentParserService.class);
    private static final int MAX_CACHE_SIZE = 10_000;

    private Parser parser;
    private final Map<String, UAProfile> cache = new ConcurrentHashMap<>();

    /**
     * Holds parsed User-Agent information.
     */
    public record UAProfile(String device, String browser, String os) {
        public static final UAProfile UNKNOWN = new UAProfile("Other", "Other", "Other");
    }

    @PostConstruct
    public void init() {
        try {
            this.parser = new Parser();
            log.info("User-Agent parser initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize User-Agent parser", e);
            this.parser = null;
        }
    }

    /**
     * Parses a User-Agent string and returns device, browser, OS.
     * Results are cached for performance.
     */
    public UAProfile parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UAProfile.UNKNOWN;
        }

        // Simple cache check
        UAProfile cached = cache.get(userAgent);
        if (cached != null) {
            return cached;
        }

        if (parser == null) {
            return UAProfile.UNKNOWN;
        }

        try {
            Client client = parser.parse(userAgent);

            String device = client.device != null && client.device.family != null
                    ? client.device.family : "Other";
            String browser = client.userAgent != null && client.userAgent.family != null
                    ? client.userAgent.family : "Other";
            String os = client.os != null && client.os.family != null
                    ? client.os.family : "Other";

            UAProfile profile = new UAProfile(device, browser, os);

            // Cache with size limit (simple eviction: clear if too large)
            if (cache.size() < MAX_CACHE_SIZE) {
                cache.put(userAgent, profile);
            } else {
                // Evict ~10% oldest entries
                cache.clear();
                cache.put(userAgent, profile);
            }

            return profile;
        } catch (Exception e) {
            log.debug("Failed to parse User-Agent: {}", userAgent, e);
            return UAProfile.UNKNOWN;
        }
    }
}
