package com.undertow.market;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fetchingSnapshotForKnownSymbolSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/market/snapshots").param("symbols", "BHRT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("BHRT"))
                .andExpect(jsonPath("$[0].sector").value("Technology"));
    }

    @Test
    void fetchingSnapshotForUnknownSymbolReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/market/snapshots").param("symbols", "NOTREAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void historyWithUnsupportedRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/market/symbols/BHRT/history").param("range", "3d"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyWithValidRangeSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/market/symbols/BHRT/history").param("range", "7d"))
                .andExpect(status().isOk());
    }

    @Test
    void demoClockCanBeResetAndAdvanced() throws Exception {
        mockMvc.perform(post("/api/v1/market/demo/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clock").exists());

        mockMvc.perform(post("/api/v1/market/demo/advance").param("days", "3"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/market/demo/fast-forward"))
                .andExpect(status().isOk());
    }
}
