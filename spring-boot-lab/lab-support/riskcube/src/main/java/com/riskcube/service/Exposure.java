package com.riskcube.service;

import java.math.BigDecimal;

public record Exposure(String currency, BigDecimal notional, BigDecimal fxRateToEur, BigDecimal notionalEur) {
}
