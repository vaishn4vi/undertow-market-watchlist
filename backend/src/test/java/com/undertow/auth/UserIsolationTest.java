package com.undertow.auth;

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
import com.undertow.attention.entity.SignalLedgerEntry;
import com.undertow.attention.model.LedgerStatus;
import com.undertow.attention.repository.SignalLedgerEntryRepository;
import com.undertow.trust.model.TrustStatus;
import com.undertow.users.service.UserService;

/**
 * Proves the actual thing that matters: two different authenticated users
 * can never see or affect each other's data, and a user can't reach another
 * user's watchlist by guessing/changing an id in the URL. Every request
 * here goes through the real HTTP layer with a real bearer token, exactly
 * like the frontend does - nothing is mocked at the service layer, since
 * the whole point is to catch a resolver/wiring mistake, not just confirm
 * the repository query is theoretically correct.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignalLedgerEntryRepository signalLedgerEntryRepository;

    @Autowired
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private int emailCounter = 0;

    private String signupAndGetToken() throws Exception {
        String email = "isolation-test-" + (emailCounter++) + "-" + System.nanoTime() + "@example.com";
        return signupWithEmailAndGetToken(email);
    }

    private String signupWithEmailAndGetToken(String email) throws Exception {
        String body = objectMapper.writeValueAsString(new SignupPayload(email, "correct-horse-battery", "Test User"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String createWatchlist(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/watchlists")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NamePayload(name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/watchlists"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupThenLoginRoundTripWorks() throws Exception {
        String email = "roundtrip-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupPayload(email, "correct-horse-battery", "Round Tripper"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, "correct-horse-battery"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String email = "wrongpass-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupPayload(email, "correct-horse-battery", "User"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, "totally-wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    // Test 1 & 2: each user's created watchlist is invisible to the other.
    @Test
    void watchlistsCreatedByOneUserAreInvisibleToAnother() throws Exception {
        String tokenA = signupAndGetToken();
        String tokenB = signupAndGetToken();

        createWatchlist(tokenA, "Tech Stocks");

        mockMvc.perform(get("/api/v1/watchlists").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Tech Stocks')]").isEmpty());

        createWatchlist(tokenB, "Banking");

        mockMvc.perform(get("/api/v1/watchlists").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Banking')]").isEmpty());
    }

    // Test 3: deleting one user's watchlist never touches the other user's.
    @Test
    void deletingOneUsersWatchlistDoesNotAffectAnother() throws Exception {
        String tokenA = signupAndGetToken();
        String tokenB = signupAndGetToken();

        String watchlistAId = createWatchlist(tokenA, "Tech Stocks");
        createWatchlist(tokenB, "Banking");

        mockMvc.perform(delete("/api/v1/watchlists/" + watchlistAId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/watchlists").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Banking')]").isNotEmpty());
    }

    // Test 4: items added to one user's watchlist never appear for another user.
    @Test
    void watchlistItemsAreIsolatedBetweenUsers() throws Exception {
        String tokenA = signupAndGetToken();
        String tokenB = signupAndGetToken();

        String watchlistAId = createWatchlist(tokenA, "Tech Stocks");
        mockMvc.perform(post("/api/v1/watchlists/" + watchlistAId + "/items")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SymbolPayload("BHRT"))))
                .andExpect(status().isCreated());

        String watchlistBId = createWatchlist(tokenB, "Banking");
        mockMvc.perform(get("/api/v1/watchlists/" + watchlistBId + "/items").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // Test 5: the attention ledger (what attention debt is computed from) is
    // isolated the same way - a ledger entry belonging to user A never
    // appears in user B's ledger or debt calculation.
    //
    // Deliberately does NOT go through POST /attention/ledger/sync/{symbol}
    // here - that endpoint ingests real market data and runs signal
    // detection against SHARED tables (MarketSnapshot, SignalEvent), which
    // other test classes' demo-clock-dependent tests assume they control
    // exclusively. Inserting a ledger entry directly via the repository
    // proves the same user-scoping mechanism without that side effect.
    @Test
    void attentionLedgerIsIsolatedBetweenUsers() throws Exception {
        String emailA = "isolation-test-" + (emailCounter++) + "-" + System.nanoTime() + "@example.com";
        String tokenA = signupWithEmailAndGetToken(emailA);
        String tokenB = signupAndGetToken();

        var userA = userService.getOrCreate(emailA);
        SignalLedgerEntry entry = new SignalLedgerEntry(userA.getId(), "BHRT", "DECOUPLING");
        // The constructor deliberately leaves status/verificationStatus
        // unset (they're normally driven by the real detection/trust
        // pipeline) - both are NOT NULL columns, so a direct test insert
        // has to set them explicitly.
        entry.setStatus(LedgerStatus.ACTIVE.name());
        entry.setVerificationStatus(TrustStatus.LIVE.name());
        entry.setCurrentSeverity(50);
        entry.setMaxSeverity(50);
        signalLedgerEntryRepository.save(entry);

        mockMvc.perform(get("/api/v1/attention/ledger").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.symbol == 'BHRT')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/attention/ledger").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/v1/attention/debt").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalizedDebt").value(0.0));
    }

    // Test 6: user A cannot reach user B's watchlist by guessing/reusing its id.
    @Test
    void cannotAccessAnotherUsersWatchlistByChangingId() throws Exception {
        String tokenA = signupAndGetToken();
        String tokenB = signupAndGetToken();

        String watchlistBId = createWatchlist(tokenB, "Banking");

        mockMvc.perform(get("/api/v1/watchlists/" + watchlistBId + "/items").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/watchlists/" + watchlistBId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // Prove the delete attempt above genuinely failed rather than silently
        // succeeding - user B's watchlist must still exist.
        mockMvc.perform(get("/api/v1/watchlists").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Banking')]").isNotEmpty());
    }

    // Test 7: deleting a custom watchlist never touches a same-named "Demo
    // Scenario" watchlist belonging to the same account.
    @Test
    void deletingCustomWatchlistDoesNotDeleteDemoScenario() throws Exception {
        String token = signupAndGetToken();

        createWatchlist(token, "Demo Scenario");
        String customId = createWatchlist(token, "Tech Stocks");

        mockMvc.perform(delete("/api/v1/watchlists/" + customId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/watchlists").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Demo Scenario')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Tech Stocks')]").isEmpty());
    }

    private record SignupPayload(String email, String password, String displayName) {
    }

    private record LoginPayload(String email, String password) {
    }

    private record NamePayload(String name) {
    }

    private record SymbolPayload(String symbol) {
    }
}
