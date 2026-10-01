package com.riskcube.service;

import com.riskcube.domain.Dimension;
import com.riskcube.domain.Trade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregation engine. v2.4.0: parallelised for large books (#412).
 */
@Component
public class TradeAggregator {

    public Map<String, BigDecimal> aggregate(List<Trade> trades, Dimension dimension) {
        Map<String, Double> totals = new HashMap<>();

        trades.parallelStream().forEach(trade -> {
            String key = dimension.keyOf(trade);
            totals.merge(key, trade.getPnl().doubleValue(), Double::sum);
        });

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        totals.forEach((key, total) ->
                result.put(key, BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP)));
        return result;
    }
}
