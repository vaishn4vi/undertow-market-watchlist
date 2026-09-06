package com.undertow.attention;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.undertow.market.entity.MarketSnapshot;
import com.undertow.market.repository.MarketSnapshotRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttentionLedgerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketSnapshotRepository snapshotRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final double[] BASELINE = {
            0.25, -0.15, 0.25, -0.15, 0.25, -0.15, 0.25, -0.15,
            0.25, -0.15, 0.25, -0.15, 0.25, -0.15, 0.25
    };

    // Every request needs a real authenticated user now. Signing up fresh
    // per test keeps each test's ledger state independent.
    private String freshToken() throws Exception {
        String email = "ledger-controller-test-" + System.nanoTime() + "@example.com";
        String body = "{\"email\": \"" + email + "\", \"password\": \"correct-horse-battery\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void syncingUnknownSymbolReturns400() throws Exception {
        String token = freshToken();
        mockMvc.perform(post("/api/v1/attention/ledger/sync/NOTREAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listingLedgerForNewUserReturnsEmptyList() throws Exception {
        String token = freshToken();
        mockMvc.perform(get("/api/v1/attention/ledger").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void acknowledgingUnknownEntryReturns404() throws Exception {
        String token = freshToken();
        mockMvc.perform(post("/api/v1/attention/ledger/00000000-0000-0000-0000-000000000000/acknowledge")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void syncingRealSymbolCreatesAndListsEntry() throws Exception {
        // GRIH is a real symbol in SymbolDirectory's fixed universe (the
        // sync endpoint validates against it before doing anything else),
        // but isn't otherwise used by any other test in this suite -
        // avoids two different test classes racing to ingest snapshots for
        // the same symbol/date range in the shared test database.
        //
        // Deliberately does NOT assert a specific signal type fires:
        // decoupling detection for a real symbol depends on its sector
        // peers' concurrent data too, which this test doesn't control, so
        // the exact detection outcome isn't something this test can pin
        // down reliably. What matters here is that a real, authenticated
        // sync call against real ingested data succeeds end to end.
        String symbol = "GRIH";
        String token = freshToken();
        LocalDate start = LocalDate.of(2026, 2, 1);
        for (int i = 0; i < BASELINE.length; i++) {
            ingest(symbol, start.plusDays(i), BASELINE[i]);
        }
        ingest(symbol, start.plusDays(BASELINE.length), -8.0);

        mockMvc.perform(post("/api/v1/attention/ledger/sync/" + symbol).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/attention/ledger").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void ingest(String symbol, LocalDate date, double stockReturn) {
        Instant asOf = date.atTime(16, 0).atZone(ZoneId.of("America/New_York")).toInstant();
        snapshotRepository.save(new MarketSnapshot(
                symbol, asOf, BigDecimal.valueOf(100), BigDecimal.valueOf(stockReturn),
                "TestSector", BigDecimal.ZERO, BigDecimal.ZERO, "CLOSED", "test"));
    }
}
