package com.riskcube.web;

import com.riskcube.domain.Trade;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeView(Long id, String externalId, String book, String desk, String counterparty,
                        String currency, BigDecimal notional, BigDecimal pnl, LocalDate tradeDate) {

    public static TradeView from(Trade t) {
        return new TradeView(t.getId(), t.getExternalId(), t.getBook().getName(), t.getBook().getDesk().getName(),
                t.getCounterparty(), t.getCurrency(), t.getNotional(), t.getPnl(), t.getTradeDate());
    }
}
