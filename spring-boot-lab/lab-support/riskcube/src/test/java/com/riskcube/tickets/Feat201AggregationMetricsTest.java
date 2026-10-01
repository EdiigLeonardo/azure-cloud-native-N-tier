package com.riskcube.tickets;

import com.riskcube.domain.Dimension;
import com.riskcube.service.AggregationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** tickets/FEAT-201.md */
class Feat201AggregationMetricsTest extends BaseIntegrationTest {

    @Autowired
    AggregationService service;

    @Autowired
    MeterRegistry registry;

    @Test
    void aggregationDurationIsRecordedPerDimension() {
        service.aggregate(Dimension.CURRENCY);

        Timer timer = registry.find("riskcube.aggregation").tag("dimension", "CURRENCY").timer();
        assertNotNull(timer, "no Timer named 'riskcube.aggregation' with tag dimension=CURRENCY");
        assertTrue(timer.count() >= 1, "timer exists but nothing was recorded");
    }
}
