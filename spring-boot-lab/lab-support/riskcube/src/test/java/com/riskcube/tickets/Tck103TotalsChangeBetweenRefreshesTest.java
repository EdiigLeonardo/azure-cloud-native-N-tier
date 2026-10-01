package com.riskcube.tickets;

import com.riskcube.domain.Dimension;
import com.riskcube.domain.Trade;
import com.riskcube.service.TradeAggregator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * tickets/TCK-103.md
 * Pure unit test: no Spring, no database. Uses a PROD-sized dataset.
 * If this passes on a 1-2 CPU machine but the bug is still there... read the ticket again.
 */
class Tck103TotalsChangeBetweenRefreshesTest {

    private final TradeAggregator aggregator = new TradeAggregator();

    @Test
    @Timeout(60)
    void totalsAreExactAndIdenticalOnEveryRun() {
        List<Trade> trades = TestTrades.generate(300_000);

        Map<String, BigDecimal> expected = new HashMap<>();
        for (Trade t : trades) {
            expected.merge(t.getBook().getDesk().getName(), t.getPnl(), BigDecimal::add);
        }

        for (int run = 1; run <= 10; run++) {
            Map<String, BigDecimal> actual = aggregator.aggregate(trades, Dimension.DESK);
            assertEquals(expected.keySet(), actual.keySet(), "run " + run + ": wrong desks");
            for (String desk : expected.keySet()) {
                assertEquals(0, expected.get(desk).compareTo(actual.get(desk)),
                        "run " + run + ", desk " + desk + ": expected " + expected.get(desk) + " but was " + actual.get(desk));
            }
        }
    }
}
