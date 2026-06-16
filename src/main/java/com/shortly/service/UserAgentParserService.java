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
 * Parses HTTP {@code User-Agent} headers into device, browser, and OS categories.
 *
 * <p>Uses the <a href="https://github.com/ua-parser/uap-java">uap-java</a> library
 * with a regex-based parser. Results are cached in a bounded in-memory
 * {@link ConcurrentHashMap} (max {@value #MAX_CACHE_SIZE} entries) since the
 * same UA strings appear frequently.
 */
@Service
public class UserAgentParserService {

    private static final Logger log = LoggerFactory.getLogger(UserAgentParserService.class);

    /** Maximum number of cached User-Agent parse results. */
    private static final int MAX_CACHE_SIZE = 10_000;

    private Parser parser;
    private final Map<String, UAProfile> cache = new ConcurrentHashMap<>();

    /**
     * Immutable result of a User-Agent parse.
     *
     * @param device  device family (e.g. {@code "iPhone"}, {@code "Spider"})
     * @param browser browser family (e.g. {@code "Chrome"}, {@code "Safari"})
     * @param os      operating system family (e.g. {@code "iOS"}, {@code "Android"})
     */
    public record UAProfile(String device, String browser, String os) {

        /** Sentinel value returned when the UA string is blank or unparseable. */
        public static final UAProfile UNKNOWN = new UAProfile("Other", "Other", "Other");
    }

    /** Initialises the ua-parser on application startup. */
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
     * Parses a User-Agent string into its device, browser, and OS components.
     *
     * @param userAgent the raw User-Agent header value (may be null or blank)
     * @return a {@link UAProfile} with the parsed fields, or {@link UAProfile#UNKNOWN} on failure
     */
    public UAProfile parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UAProfile.UNKNOWN;
        }

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

            if (cache.size() < MAX_CACHE_SIZE) {
                cache.put(userAgent, profile);
            } else {
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
