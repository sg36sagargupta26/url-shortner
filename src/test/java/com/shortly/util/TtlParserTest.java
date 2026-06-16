package com.shortly.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TtlParser}.
 */
class TtlParserTest {

    @Test
    void shouldReturnDefaultWhenNull() {
        Duration result = TtlParser.parse(null);
        assertThat(result).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void shouldReturnDefaultWhenBlank() {
        Duration result = TtlParser.parse("  ");
        assertThat(result).isEqualTo(Duration.ofDays(30));
    }

    @ParameterizedTest
    @CsvSource({
            "30d, 2592000",
            "1d, 86400",
            "24h, 86400",
            "60m, 3600",
            "90s, 90",
            "3600, 3600",
            "2 days, 172800",
            "3 hours, 10800",
            "10 minutes, 600",
            "45 seconds, 45"
    })
    void shouldParseValidTtl(String input, long expectedSeconds) {
        Duration result = TtlParser.parse(input);
        assertThat(result.getSeconds()).isEqualTo(expectedSeconds);
    }

    @Test
    void shouldRejectZeroTtl() {
        assertThatThrownBy(() -> TtlParser.parse("0s"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void shouldRejectNegativeTtl() {
        assertThatThrownBy(() -> TtlParser.parse("-1d"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid TTL format");
    }

    @Test
    void shouldRejectExceedingMaxTtl() {
        assertThatThrownBy(() -> TtlParser.parse("366d"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertThatThrownBy(() -> TtlParser.parse("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid TTL format");
    }

    @Test
    void shouldHandleCaseInsensitiveUnits() {
        assertThat(TtlParser.parse("1D")).isEqualTo(Duration.ofDays(1));
        assertThat(TtlParser.parse("1H")).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void shouldTrimWhitespace() {
        assertThat(TtlParser.parse("  30d  ")).isEqualTo(Duration.ofDays(30));
    }
}
