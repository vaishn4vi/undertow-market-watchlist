package com.undertow.trust;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.undertow.market.entity.MarketSnapshot;
import com.undertow.market.service.DemoMarketDataProvider;
import com.undertow.market.service.HackathonDemoScenario;
import com.undertow.market.service.MarketDataService;
import com.undertow.trust.model.TrustStatus;
import com.undertow.trust.service.TrustAssessment;
import com.undertow.trust.service.TrustService;

@SpringBootTest
@ActiveProfiles("test")
class TrustServiceTest {

    @Autowired
    private TrustService trustService;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private DemoMarketDataProvider demoProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void freshlyIngestedSymbolIsLiveWithFullConfidence() {
        demoProvider.resetToStart();
        String symbol = "HIML";
        marketDataService.ensureIngestedAndGetLatest(symbol);

        TrustAssessment assessment = trustService.assess(symbol).orElseThrow();

        assertThat(assessment.status()).isEqualTo(TrustStatus.LIVE);
        assertThat(assessment.confidence()).isEqualTo(1.0);
    }

    @Test
    void oneDayGapIsDelayedWithReducedConfidence() {
        demoProvider.resetToStart();
        String symbol = "SAHY";
        marketDataService.ensureIngestedAndGetLatest(symbol); // ingested at rally day
        demoProvider.advance(1); // clock moves on; SAHY is not re-ingested

        TrustAssessment assessment = trustService.assess(symbol).orElseThrow();

        assertThat(assessment.status()).isEqualTo(TrustStatus.DELAYED);
        assertThat(assessment.confidence()).isEqualTo(0.8);
        assertThat(assessment.explanation()).contains("1 day");
    }

    @Test
    void twoDayGapIsStaleWithFurtherReducedConfidence() {
        demoProvider.resetToStart();
        String symbol = "SPTM";
        marketDataService.ensureIngestedAndGetLatest(symbol);
        demoProvider.advance(2);

        TrustAssessment assessment = trustService.assess(symbol).orElseThrow();

        assertThat(assessment.status()).isEqualTo(TrustStatus.STALE);
        assertThat(assessment.confidence()).isEqualTo(0.5);
    }

    @Test
    void conflictingObservationYieldsConflictingStatusAndLowConfidence() {
        demoProvider.resetToStart();
        String symbol = "VRDN";
        MarketSnapshot accepted = marketDataService.ensureIngestedAndGetLatest(symbol).orElseThrow();

        // Plant a disagreeing price on the existing row (not a duplicate
        // insert - (symbol, as_of) is a real unique constraint mirroring
        // V2__market_snapshot_uniqueness.sql, and MarketSnapshot is
        // deliberately immutable/has no setters, so a direct UPDATE is the
        // only way to simulate "the stored value disagrees with what the
        // provider says" without violating that constraint).
        jdbcTemplate.update("UPDATE market_snapshots SET price = ? WHERE id = ?", 1.23, accepted.getId());
        marketDataService.ensureIngestedAndGetLatest(symbol);

        TrustAssessment assessment = trustService.assess(symbol).orElseThrow();

        assertThat(assessment.status()).isEqualTo(TrustStatus.CONFLICTING);
        assertThat(assessment.confidence()).isEqualTo(0.3);
    }

    @Test
    void unavailableFeedYieldsUnavailableStatusWithZeroConfidence() {
        demoProvider.resetToStart();
        String symbol = HackathonDemoScenario.OUTAGE_SYMBOL;
        marketDataService.ensureIngestedAndGetLatest(symbol); // succeeds on rally day
        demoProvider.fastForwardToReturnDay(); // the scripted outage day
        marketDataService.ensureIngestedAndGetLatest(symbol); // attempts and fails to find data

        TrustAssessment assessment = trustService.assess(symbol).orElseThrow();

        assertThat(assessment.status()).isEqualTo(TrustStatus.UNAVAILABLE);
        assertThat(assessment.confidence()).isEqualTo(0.0);
    }

    @Test
    void neverIngestedSymbolHasNoAssessment() {
        Optional<TrustAssessment> assessment = trustService.assess("SVAS");

        assertThat(assessment).isEmpty();
    }
}
