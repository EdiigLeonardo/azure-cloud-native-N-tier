package com.riskcube.marketdata;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LAB ONLY: fake market data provider. In real life this is another server.
 * It answers asynchronously so that, while "slow", it does not consume RiskCube's own Tomcat threads.
 * Make it slow:  curl -X POST "localhost:8080/mock/delay?ms=30000"
 * Back to normal: curl -X POST "localhost:8080/mock/delay?ms=0"
 */
@RestController
@RequestMapping("/mock")
public class MockMarketDataController {

    private static final Map<String, BigDecimal> RATES = Map.of(
            "USD", new BigDecimal("0.92"),
            "GBP", new BigDecimal("1.17"),
            "CHF", new BigDecimal("1.05"),
            "JPY", new BigDecimal("0.0062"));

    private final AtomicLong delayMs = new AtomicLong(0);

    @GetMapping("/fx/{currency}")
    public CompletableFuture<FxQuote> fx(@PathVariable String currency) {
        BigDecimal rate = RATES.get(currency);
        if (rate == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown currency " + currency);
        }
        FxQuote quote = new FxQuote(currency, rate);
        long delay = delayMs.get();
        if (delay <= 0) {
            return CompletableFuture.completedFuture(quote);
        }
        return CompletableFuture.supplyAsync(() -> quote, CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS));
    }

    @PostMapping("/delay")
    public Map<String, Long> setDelay(@RequestParam long ms) {
        delayMs.set(ms);
        return Map.of("delayMs", ms);
    }
}
