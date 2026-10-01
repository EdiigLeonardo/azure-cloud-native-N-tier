package com.riskcube.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeRequest(
        @NotBlank String externalId,
        @NotBlank String bookName,
        String counterparty,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull BigDecimal notional,
        @NotNull BigDecimal pnl,
        LocalDate tradeDate) {
}
