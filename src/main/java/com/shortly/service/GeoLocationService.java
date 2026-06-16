package com.shortly.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

/**
 * IP geolocation service using the MaxMind GeoLite2 database.
 *
 * <p>On startup the service attempts to load the {@code GeoLite2-City.mmdb} file
 * from the classpath. If the file is absent (e.g. in local development without
 * the database), all lookups gracefully return {@link GeoResult#UNKNOWN}.
 *
 * <p>The MaxMind database can be downloaded from
 * <a href="https://dev.maxmind.com/geoip/geolite2-free-geolocation-data">MaxMind GeoLite2</a>
 * (requires a free license key).
 */
@Service
public class GeoLocationService {

    private static final Logger log = LoggerFactory.getLogger(GeoLocationService.class);
    private DatabaseReader databaseReader;

    /**
     * Immutable result of a geo-location lookup.
     *
     * @param country ISO 3166-1 alpha-2 country code (e.g. {@code "US"}) or {@code "XX"} if unknown
     * @param city    city name or {@code "Unknown"} if unavailable
     */
    public record GeoResult(String country, String city) {

        /** Sentinel value returned when the lookup cannot determine a location. */
        public static final GeoResult UNKNOWN = new GeoResult("XX", "Unknown");

        /**
         * Returns whether this result represents a local/private IP address.
         *
         * @return {@code true} if the country code is {@code "LOCAL"}
         */
        public boolean isLocal() {
            return "LOCAL".equals(country);
        }
    }

    /**
     * Initialises the MaxMind database reader on application startup.
     * Logs a warning and leaves {@code databaseReader} null if the file is not found.
     */
    @PostConstruct
    public void init() {
        try {
            InputStream dbStream = new ClassPathResource("GeoLite2-City.mmdb").getInputStream();
            this.databaseReader = new DatabaseReader.Builder(dbStream).build();
            log.info("MaxMind GeoLite2 database loaded successfully");
        } catch (IOException e) {
            log.warn("MaxMind GeoLite2 database not found at classpath:GeoLite2-City.mmdb. "
                    + "Geo-location will return UNKNOWN for all IPs. "
                    + "Download from: https://dev.maxmind.com/geoip/geolite2-free-geolocation-data");
            this.databaseReader = null;
        }
    }

    /**
     * Looks up the country and city for the given IP address.
     *
     * @param ip the client IP address (IPv4 or IPv6)
     * @return a {@link GeoResult} with country and city, or {@link GeoResult#UNKNOWN} on failure
     */
    public GeoResult lookup(String ip) {
        if (databaseReader == null) {
            return GeoResult.UNKNOWN;
        }

        try {
            if (isLocalOrPrivate(ip)) {
                return new GeoResult("LOCAL", "Local");
            }

            InetAddress address = InetAddress.getByName(ip);
            CityResponse response = databaseReader.city(address);

            String country = response.getCountry() != null
                    ? response.getCountry().getIsoCode() : "XX";
            String city = response.getCity() != null
                    ? response.getCity().getName() : "Unknown";

            return new GeoResult(country != null ? country : "XX",
                    city != null ? city : "Unknown");
        } catch (IOException | GeoIp2Exception e) {
            log.debug("Failed to lookup IP: {}", ip, e);
            return GeoResult.UNKNOWN;
        }
    }

    /**
     * Determines whether the given IP is a local or private address.
     *
     * @param ip the IP address to check
     * @return {@code true} if the IP is localhost or within private ranges
     */
    private boolean isLocalOrPrivate(String ip) {
        return ip != null && (ip.startsWith("127.") || ip.startsWith("10.")
                || ip.startsWith("192.168.") || ip.startsWith("172.16.")
                || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1"));
    }
}
