package com.riskcube.tickets;

import com.riskcube.domain.Book;
import com.riskcube.domain.Desk;
import com.riskcube.domain.Trade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builds trades in memory, without a database. */
final class TestTrades {

    private TestTrades() {
    }

    static List<Trade> generate(int count) {
        Random random = new Random(7);
        List<Book> books = new ArrayList<>();
        for (String deskName : new String[]{"FX", "RATES", "CREDIT", "EQUITY", "COMMODITIES"}) {
            Desk desk = new Desk(deskName);
            for (int i = 1; i <= 10; i++) {
                books.add(new Book(deskName + "-" + i, desk));
            }
        }
        List<Trade> trades = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            trades.add(new Trade("T-" + i, books.get(random.nextInt(books.size())), "CPTY-" + (i % 20), "EUR",
                    BigDecimal.valueOf(random.nextLong(1_000_000L, 1_000_000_000L), 2),
                    BigDecimal.valueOf(random.nextLong(-5_000_000L, 5_000_000L), 2),
                    LocalDate.of(2026, 9, 1)));
        }
        return trades;
    }
}
