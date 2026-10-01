package com.riskcube.tickets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** tickets/TCK-108.md */
class Tck108NightlyIngestionForbiddenTest extends BaseIntegrationTest {

    private static final String TRADE = """
            {"externalId":"%s","bookName":"FX-01","counterparty":"ACME BANK","currency":"USD",
             "notional":1000000.00,"pnl":1250.50,"tradeDate":"2026-09-29"}
            """;

    @Test
    void adminCanIngestTrades() throws Exception {
        mvc.perform(post("/api/v1/trades").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(TRADE.formatted("T-NIGHTLY-1")))
                .andExpect(status().isCreated());
    }

    @Test
    void analystCannotIngestTrades() throws Exception {
        mvc.perform(post("/api/v1/trades").with(httpBasic("analyst", "analyst"))
                        .contentType(MediaType.APPLICATION_JSON).content(TRADE.formatted("T-NIGHTLY-2")))
                .andExpect(status().isForbidden());
    }
}
