package com.riskcube.config;

import com.riskcube.domain.Book;
import com.riskcube.domain.BookRepository;
import com.riskcube.domain.Desk;
import com.riskcube.domain.DeskRepository;
import com.riskcube.domain.Trade;
import com.riskcube.domain.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Generates deterministic demo data: 5 desks x 10 books, N trades (riskcube.seed.trades). */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String[] DESKS = {"FX", "RATES", "CREDIT", "EQUITY", "COMMODITIES"};
    private static final String[] CURRENCIES = {"EUR", "USD", "GBP", "CHF", "JPY"};
    private static final String[] COUNTERPARTIES = {"ACME BANK", "GLOBEX", "INITECH", "UMBRELLA", "STARK IND", "WAYNE ENT"};

    private final DeskRepository deskRepository;
    private final BookRepository bookRepository;
    private final TradeRepository tradeRepository;
    private final int tradeCount;

    public DataSeeder(DeskRepository deskRepository, BookRepository bookRepository, TradeRepository tradeRepository,
                      @Value("${riskcube.seed.trades:20000}") int tradeCount) {
        this.deskRepository = deskRepository;
        this.bookRepository = bookRepository;
        this.tradeRepository = tradeRepository;
        this.tradeCount = tradeCount;
    }

    @Override
    public void run(String... args) {
        if (deskRepository.count() > 0) {
            return;
        }
        Random random = new Random(42);
        List<Book> books = new ArrayList<>();
        for (String deskName : DESKS) {
            Desk desk = deskRepository.save(new Desk(deskName));
            for (int i = 1; i <= 10; i++) {
                books.add(new Book(String.format("%s-%02d", deskName, i), desk));
            }
        }
        bookRepository.saveAll(books);

        List<Trade> trades = new ArrayList<>(tradeCount);
        for (int i = 0; i < tradeCount; i++) {
            Book book = books.get(random.nextInt(books.size()));
            // trades coming from the customer's new booking system have no counterparty yet
            String counterparty = (i % 97 == 0) ? null : COUNTERPARTIES[random.nextInt(COUNTERPARTIES.length)];
            BigDecimal notional = BigDecimal.valueOf(random.nextLong(1_000_000L, 1_000_000_000L), 2);
            BigDecimal pnl = BigDecimal.valueOf(random.nextLong(-5_000_000L, 5_000_000L), 2);
            trades.add(new Trade("T-" + (100_000 + i), book, counterparty,
                    CURRENCIES[random.nextInt(CURRENCIES.length)], notional, pnl,
                    LocalDate.of(2026, 9, 1).plusDays(random.nextInt(28))));
        }
        tradeRepository.saveAll(trades);
        log.info("seeded {} desks, {} books, {} trades", DESKS.length, books.size(), trades.size());
    }
}
