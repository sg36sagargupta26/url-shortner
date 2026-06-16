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
 * IP geolocation service using MaxMind GeoLite2 database.
 * Returns country and city for a given IP address.
 */
@Service
public class GeoLocationService {

    private static final Logger log = LoggerFactory.getLogger(GeoLocationService.class);
    private DatabaseReader databaseReader;

    /**
     * Holds the result of a geo-location lookup.
     */
    public record GeoResult(String country, String city) {
        public static final GeoResult UNKNOWN = new GeoResult("XX", "Unknown");
    }

    @PostConstruct
    public void init() {
        try {
            // Try to load from classpath
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
     * Looks up country and city for an IP address.
     * Returns UNKNOWN if the database is not loaded or the lookup fails.
     */
    public GeoResult lookup(String ip) {
        if (databaseReader == null) {
            return GeoResult.UNKNOWN;
        }

        try {
            // Skip local/private IPs
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

    private boolean isLocalOrPrivate(String ip) {
        return ip != null && (ip.startsWith("127.") || ip.startsWith("10.")
                || ip.startsWith("192.168.") || ip.startsWith("172.16.")
                || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1"));
    }
}
