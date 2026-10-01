package com.riskcube.tickets;

import com.riskcube.service.AggregationService;
import com.riskcube.service.DeskSummary;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** tickets/TCK-104.md */
class Tck104DeskSummarySlowTest extends BaseIntegrationTest {

    @Autowired
    AggregationService service;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void deskSummaryUsesAConstantNumberOfQueries() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        DeskSummary summary = service.deskSummary("RATES");

        long queries = stats.getPrepareStatementCount();
        assertEquals(10, summary.pnlByBook().size(), "RATES has 10 books");
        assertTrue(queries <= 3, "deskSummary executed " + queries + " SQL queries (should be <= 3, whatever the number of books)");
    }
}
