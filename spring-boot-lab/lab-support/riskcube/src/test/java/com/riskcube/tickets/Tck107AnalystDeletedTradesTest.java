package com.riskcube.tickets;

import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** tickets/TCK-107.md */
class Tck107AnalystDeletedTradesTest extends BaseIntegrationTest {

    @Test
    void analystCannotDeleteTrades() throws Exception {
        mvc.perform(delete("/api/v1/trades/999999").with(httpBasic("analyst", "analyst")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanStillDeleteTrades() throws Exception {
        mvc.perform(delete("/api/v1/trades/999998").with(httpBasic("admin", "admin")))
                .andExpect(status().isNoContent());
    }
}
