package com.riskcube.tickets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** tickets/TCK-106.md: a fake market data provider that takes 5 seconds to answer. */
@SpringBootTest(properties = "riskcube.seed.trades=500")
@AutoConfigureMockMvc
class Tck106ServiceHangsWhenProviderSlowTest {

    static final HttpServer slowProvider;

    static {
        try {
            slowProvider = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            slowProvider.createContext("/fx", exchange -> {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] body = "{\"currency\":\"USD\",\"rate\":0.92}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                } catch (IOException clientAlreadyGone) {
                    // expected once the client times out
                }
            });
            slowProvider.setExecutor(Executors.newCachedThreadPool());
            slowProvider.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void marketDataUrl(DynamicPropertyRegistry registry) {
        registry.add("riskcube.market-data.url", () -> "http://localhost:" + slowProvider.getAddress().getPort());
    }

    @AfterAll
    static void stopProvider() {
        slowProvider.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void failsFastWith503WhenMarketDataIsSlow() throws Exception {
        long start = System.currentTimeMillis();

        mvc.perform(get("/api/v1/exposure/USD").with(httpBasic("analyst", "analyst")))
                .andExpect(status().isServiceUnavailable());

        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 3_000, "request took " + elapsed + "ms: the caller (and a Tomcat thread, and a DB connection) is stuck waiting");
    }
}
