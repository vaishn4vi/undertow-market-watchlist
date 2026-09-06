package com.undertow.trust;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.undertow.market.service.DemoMarketDataProvider;
import com.undertow.market.service.MarketDataService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrustControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private DemoMarketDataProvider demoProvider;

    @Test
    void unknownSymbolReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/trust/symbols/NOTREAL"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void neverIngestedKnownSymbolReturnsUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/v1/trust/symbols/MARG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN"));
    }

    @Test
    void freshlyIngestedSymbolReturnsLive() throws Exception {
        demoProvider.resetToStart();
        marketDataService.ensureIngestedAndGetLatest("SHIL");

        mockMvc.perform(get("/api/v1/trust/symbols/SHIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.confidence").value(1.0));
    }
}
