package com.shortly.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IpHashUtil}.
 */
class IpHashUtilTest {

    @Test
    void shouldHashIpAddress() {
        String hash = IpHashUtil.hash("192.168.1.1");
        assertThat(hash)
                .isNotNull()
                .hasSize(64)
                .matches("[0-9a-f]+");
    }

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(IpHashUtil.hash(null)).isNull();
    }

    @Test
    void shouldProduceConsistentHash() {
        String hash1 = IpHashUtil.hash("10.0.0.1");
        String hash2 = IpHashUtil.hash("10.0.0.1");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void shouldProduceDifferentHashesForDifferentIps() {
        String hash1 = IpHashUtil.hash("10.0.0.1");
        String hash2 = IpHashUtil.hash("10.0.0.2");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void shouldHandleIpv6() {
        String hash = IpHashUtil.hash("2001:db8::1");
        assertThat(hash).isNotNull().hasSize(64);
    }
}
