package com.riskcube.marketdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/** Client for the external market data provider (#420: migrated to RestClient). */
@Component
public class MarketDataClient {

    private static final Logger log = LoggerFactory.getLogger(MarketDataClient.class);

    private final RestClient restClient;

    public MarketDataClient(@Value("${riskcube.market-data.url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public BigDecimal fxRateToEur(String currency) {
        if ("EUR".equals(currency)) {
            return BigDecimal.ONE;
        }
        long start = System.currentTimeMillis();
        FxQuote quote = restClient.get()
                .uri("/fx/{currency}", currency)
                .retrieve()
                .body(FxQuote.class);
        log.info("fx rate {}->EUR fetched in {}ms", currency, System.currentTimeMillis() - start);
        return quote.rate();
    }
}
