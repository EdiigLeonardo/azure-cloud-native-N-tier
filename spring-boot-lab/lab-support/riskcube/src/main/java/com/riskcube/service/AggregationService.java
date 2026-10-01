package com.riskcube.service;

import com.riskcube.domain.Book;
import com.riskcube.domain.Desk;
import com.riskcube.domain.DeskRepository;
import com.riskcube.domain.Dimension;
import com.riskcube.domain.Trade;
import com.riskcube.domain.TradeRepository;
import com.riskcube.marketdata.MarketDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);

    private final TradeRepository tradeRepository;
    private final DeskRepository deskRepository;
    private final TradeAggregator aggregator;
    private final MarketDataClient marketData;

    public AggregationService(TradeRepository tradeRepository, DeskRepository deskRepository,
                              TradeAggregator aggregator, MarketDataClient marketData) {
        this.tradeRepository = tradeRepository;
        this.deskRepository = deskRepository;
        this.aggregator = aggregator;
        this.marketData = marketData;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> aggregate(Dimension dimension) {
        long start = System.nanoTime();
        List<Trade> trades = tradeRepository.findAllWithBookAndDesk();
        Map<String, BigDecimal> result = aggregator.aggregate(trades, dimension);
        log.info("aggregate groupBy={} trades={} took={}ms", dimension, trades.size(), (System.nanoTime() - start) / 1_000_000);
        return result;
    }

    @Transactional(readOnly = true)
    public DeskSummary deskSummary(String deskName) {
        Desk desk = deskRepository.findByName(deskName).orElseThrow();
        Map<String, BigDecimal> pnlByBook = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Book book : desk.getBooks()) {
            BigDecimal bookTotal = BigDecimal.ZERO;
            for (Trade trade : book.getTrades()) {
                bookTotal = bookTotal.add(trade.getPnl());
            }
            pnlByBook.put(book.getName(), bookTotal);
            total = total.add(bookTotal);
        }
        return new DeskSummary(desk.getName(), pnlByBook, total);
    }

    @Transactional(readOnly = true)
    public Exposure exposure(String currency) {
        List<Trade> trades = tradeRepository.findByCurrency(currency);
        BigDecimal notional = trades.stream().map(Trade::getNotional).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rate = marketData.fxRateToEur(currency);
        return new Exposure(currency, notional, rate, notional.multiply(rate).setScale(2, RoundingMode.HALF_UP));
    }
}
