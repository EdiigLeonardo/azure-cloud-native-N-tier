package com.riskcube.tickets;

import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** tickets/TCK-101.md */
class Tck101CounterpartyViewBrokenTest extends BaseIntegrationTest {

    @Test
    void tradesWithoutCounterpartyAreGroupedUnderUnknown() throws Exception {
        mvc.perform(get("/api/v1/aggregates?groupBy=COUNTERPARTY").with(httpBasic("analyst", "analyst")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.UNKNOWN").exists());
    }
}
