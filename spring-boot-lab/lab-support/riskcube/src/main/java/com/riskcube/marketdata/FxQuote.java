package com.riskcube.marketdata;

import java.math.BigDecimal;

public record FxQuote(String currency, BigDecimal rate) {
}
