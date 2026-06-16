package com.shortly.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for hashing IP addresses before storage.
 *
 * <p>Uses SHA-256 to produce a one-way hash of the visitor's IP,
 * preserving uniqueness for unique-visitor counting while avoiding
 * storing raw IP addresses in plaintext.
 */
public class IpHashUtil {

    private static final HexFormat HEX = HexFormat.of();

    /**
     * Hashes an IP address using SHA-256.
     *
     * @param ip the raw IP address string (IPv4 or IPv6)
     * @return the hex-encoded SHA-256 hash, or {@code null} if input is null
     */
    public static String hash(String ip) {
        if (ip == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
