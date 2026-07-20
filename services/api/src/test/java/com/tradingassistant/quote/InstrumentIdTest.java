package com.tradingassistant.quote;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InstrumentIdTest {
    @Test
    void normalizesCommonAShareFormats() {
        assertThat(InstrumentId.parse("600000").canonical()).isEqualTo("SSE:600000");
        assertThat(InstrumentId.parse("sh600000").canonical()).isEqualTo("SSE:600000");
        assertThat(InstrumentId.parse("SZSE:000001").canonical()).isEqualTo("SZSE:000001");
        assertThat(InstrumentId.parse("920001").canonical()).isEqualTo("BSE:920001");
    }

    @Test
    void rejectsUnknownCode() {
        assertThatThrownBy(() -> InstrumentId.parse("123"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

