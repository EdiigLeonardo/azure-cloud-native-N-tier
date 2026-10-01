package com.riskcube.tickets;

import com.riskcube.service.QueryResultCache;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** tickets/TCK-105.md */
class Tck105PodsOomKilledTest {

    @Test
    void cacheCannotGrowForever() {
        QueryResultCache cache = new QueryResultCache();

        // the customer's dashboard adds a cache-busting parameter to every refresh
        for (int i = 0; i < 5_000; i++) {
            String queryString = "groupBy=DESK&_=" + (1_727_600_000_000L + i);
            cache.get(queryString, () -> Map.of("FX", BigDecimal.ONE));
        }

        assertTrue(cache.size() <= 1_000,
                "cache holds " + cache.size() + " entries after 5000 refreshes: it grows until the JVM runs out of memory");
    }
}
