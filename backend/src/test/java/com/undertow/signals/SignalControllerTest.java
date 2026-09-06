package com.undertow.signals;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.undertow.market.service.DemoMarketDataProvider;
import com.undertow.market.service.HackathonDemoScenario;
import com.undertow.market.service.MarketDataService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private DemoMarketDataProvider demoProvider;

    @Test
    void detectingForUnknownSymbolReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/signals/symbols/NOTREAL/detect"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void evidenceForUnknownSignalReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/signals/00000000-0000-0000-0000-000000000000/evidence"))
                .andExpect(status().isNotFound());
    }

    @Test
    void detectingOnTheRallyDayReturnsDecouplingForTheDecoupler() throws Exception {
        demoProvider.resetToStart();
        String symbol = HackathonDemoScenario.DECOUPLER_SYMBOL;
        marketDataService.ensureIngestedHistory(symbol, demoProvider.rallyDay().minusDays(30), demoProvider.rallyDay());

        mockMvc.perform(post("/api/v1/signals/symbols/" + symbol + "/detect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataUnavailable").value(false))
                .andExpect(jsonPath("$.trustStatus").value("LIVE"))
                .andExpect(jsonPath("$.confidence").value(1.0))
                .andExpect(jsonPath("$.events[?(@.type == 'DECOUPLING')]").exists());

        mockMvc.perform(get("/api/v1/signals/symbols/" + symbol))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'DECOUPLING')]").exists());
    }

    @Test
    void fetchingSignalsForAKnownButUndetectedSymbolReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/signals/symbols/VRDN"))
                .andExpect(status().isOk());
    }
}
