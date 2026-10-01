package com.riskcube.tickets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** tickets/TCK-102.md */
class Tck102NotFoundReturns500Test extends BaseIntegrationTest {

    @Test
    void unknownTradeReturns404AsProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/trades/999999").with(httpBasic("analyst", "analyst")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
