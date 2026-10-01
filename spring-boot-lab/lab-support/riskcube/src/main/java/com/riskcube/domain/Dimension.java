package com.riskcube.domain;

import java.util.function.Function;

/** Axes the risk "cube" can be sliced by. */
public enum Dimension {
    DESK(trade -> trade.getBook().getDesk().getName()),
    BOOK(trade -> trade.getBook().getName()),
    COUNTERPARTY(Trade::getCounterparty),
    CURRENCY(Trade::getCurrency);

    private final Function<Trade, String> extractor;

    Dimension(Function<Trade, String> extractor) {
        this.extractor = extractor;
    }

    public String keyOf(Trade trade) {
        return extractor.apply(trade);
    }
}
