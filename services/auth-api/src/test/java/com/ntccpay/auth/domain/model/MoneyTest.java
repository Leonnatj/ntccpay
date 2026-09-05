package com.ntccpay.auth.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void acceptsValidMoney() {
        var money = new Money(1000, "USD");
        assertThat(money.minorUnits()).isEqualTo(1000);
        assertThat(money.currencyCode()).isEqualTo("USD");
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> new Money(-1, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedCurrencyCodes() {
        assertThatThrownBy(() -> new Money(100, "usd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(100, "EURO"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(100, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroIsAValidAmount() {
        assertThat(new Money(0, "EUR").minorUnits()).isZero();
    }
}
