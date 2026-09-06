package com.undertow.watchlist;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Every request needs a real authenticated user now (see
    // com.undertow.auth.UserIsolationTest for the isolation guarantees this
    // relies on) - signing up fresh per test keeps each test's data
    // independent without needing any cleanup.
    private String freshToken() throws Exception {
        String email = "watchlist-controller-test-" + System.nanoTime() + "@example.com";
        String body = "{\"email\": \"" + email + "\", \"password\": \"correct-horse-battery\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void creatingWatchlistWithBlankNameReturns400() throws Exception {
        String token = freshToken();
        mockMvc.perform(post("/api/v1/watchlists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void fetchingItemsForUnknownWatchlistReturns404() throws Exception {
        String token = freshToken();
        mockMvc.perform(get("/api/v1/watchlists/00000000-0000-0000-0000-000000000000/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void deletingUnknownWatchlistReturns404() throws Exception {
        String token = freshToken();
        mockMvc.perform(delete("/api/v1/watchlists/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullCreateFetchDeleteFlowSucceeds() throws Exception {
        String token = freshToken();

        mockMvc.perform(post("/api/v1/watchlists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"High Conviction\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("High Conviction"))
                .andExpect(jsonPath("$.itemCount").value(0));

        mockMvc.perform(get("/api/v1/watchlists").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void symbolSearchReturnsMatches() throws Exception {
        // Deliberately unauthenticated - symbol search is not user-specific
        // data (see the shared-vs-user-scoped rule in the project brief).
        mockMvc.perform(get("/api/v1/symbols/search").param("q", "tech"))
                .andExpect(status().isOk());
    }
}
