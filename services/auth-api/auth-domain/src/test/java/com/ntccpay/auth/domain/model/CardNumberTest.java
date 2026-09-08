package com.ntccpay.auth.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardNumberTest {

    @Test
    void acceptsAPanThatPassesLuhn() {
        assertThat(new CardNumber("4242424242424242").luhnValid()).isTrue();
    }

    @Test
    void rejectsAPanThatFailsLuhn() {
        assertThat(new CardNumber("4242424242424241").luhnValid()).isFalse();
    }

    @Test
    void rejectsNonNumericInput() {
        assertThatThrownBy(() -> new CardNumber("4242-4242-4242-4242"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooShortAndTooLongPans() {
        assertThatThrownBy(() -> new CardNumber("4242")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CardNumber("42424242424242424242442444"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringIsMaskedByConstruction() {
        var card = new CardNumber("4242424242424242");
        assertThat(card.toString()).isEqualTo("****4242");
        assertThat(card.toString()).doesNotContain("4242424242424242");
    }

    @Test
    void extractsTheBin() {
        assertThat(new CardNumber("4001001234567898").bin())
                .isEqualTo(new Bin("400100"));
    }

    @Test
    void equalityIsOnTheFullNumber() {
        assertThat(new CardNumber("4242424242424242"))
                .isEqualTo(new CardNumber("4242424242424242"));
        assertThat(new CardNumber("4242424242424242"))
                .isNotEqualTo(new CardNumber("4000000000000002"));
    }

    // ---- masked references: what persistence rehydration carries (PCI) ----

    @Test
    void aMaskedReferenceCarriesNoPanAndCannotFakeOne() {
        var card = CardNumber.maskedReference("****4242");

        assertThat(card.isMaskedReference()).isTrue();
        assertThat(card.masked()).isEqualTo("****4242");
        assertThat(card.toString()).isEqualTo("****4242");
        assertThatThrownBy(card::raw)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never stored");
        assertThatThrownBy(card::luhnValid)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(card::bin)
                .isInstanceOf(IllegalStateException.class);
    }
}
