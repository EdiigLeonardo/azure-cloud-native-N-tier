package com.riskcube.tickets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Shared Spring context for the tickets (started once, reused by every subclass). */
@SpringBootTest(properties = "riskcube.seed.trades=2000")
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mvc;
}
