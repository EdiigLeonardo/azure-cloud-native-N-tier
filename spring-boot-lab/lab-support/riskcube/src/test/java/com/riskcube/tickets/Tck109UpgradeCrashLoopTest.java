package com.riskcube.tickets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * tickets/TCK-109.md
 * Simulates the customer's PROD: profile "prod" + the environment variables they had in 2.3.2.
 */
@SpringBootTest(properties = {"MD_URL=http://md-gateway.nb.internal:8080", "riskcube.seed.trades=100"})
@ActiveProfiles("prod")
class Tck109UpgradeCrashLoopTest {

    @Autowired
    Environment environment;

    @Test
    void upgradeFrom232StartsWithTheExistingConfiguration() {
        assertEquals("http://md-gateway.nb.internal:8080", environment.getProperty("riskcube.market-data.url"));
    }
}
