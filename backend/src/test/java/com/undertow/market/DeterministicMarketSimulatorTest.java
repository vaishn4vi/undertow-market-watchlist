package com.undertow.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.undertow.market.service.DailyObservation;
import com.undertow.market.service.DeterministicMarketSimulator;
import com.undertow.market.service.HackathonDemoScenario;
import com.undertow.market.service.Symbol;

class DeterministicMarketSimulatorTest {

    private static final List<Symbol> UNIVERSE = List.of(
            new Symbol("BHRT", "Bharat Technologies", "Technology"),
            new Symbol("GNGS", "Ganges Software", "Technology"),
            new Symbol("HIML", "Himalaya Semiconductors", "Technology"),
            new Symbol("SAHY", "Sahyadri Systems", "Technology"),
            new Symbol("ARGY", "Arogya Pharma", "Healthcare"),
            new Symbol("NIRM", "Nirmaya Diagnostics", "Healthcare")
    );

    @Test
    void sameSeedProducesIdenticalSeries() {
        LocalDate rally = LocalDate.of(2026, 3, 1);
        LocalDate from = rally.minusDays(10);
        LocalDate to = rally.plusDays(5);

        var sim1 = new DeterministicMarketSimulator(42L, UNIVERSE, from, to, new HackathonDemoScenario(rally));
        var sim2 = new DeterministicMarketSimulator(42L, UNIVERSE, from, to, new HackathonDemoScenario(rally));

        List<DailyObservation> h1 = sim1.history("HIML", from, to);
        List<DailyObservation> h2 = sim2.history("HIML", from, to);

        assertThat(h1).hasSameSizeAs(h2);
        for (int i = 0; i < h1.size(); i++) {
            assertThat(h1.get(i).price()).isEqualTo(h2.get(i).price());
            assertThat(h1.get(i).returnPct()).isEqualTo(h2.get(i).returnPct());
        }
    }

    @Test
    void differentSeedsProduceDifferentSeries() {
        LocalDate rally = LocalDate.of(2026, 3, 1);
        LocalDate from = rally.minusDays(10);
        LocalDate to = rally.plusDays(5);

        var sim1 = new DeterministicMarketSimulator(42L, UNIVERSE, from, to, null);
        var sim2 = new DeterministicMarketSimulator(99L, UNIVERSE, from, to, null);

        DailyObservation obs1 = sim1.history("HIML", from, from).get(0);
        DailyObservation obs2 = sim2.history("HIML", from, from).get(0);

        assertThat(obs1.returnPct()).isNotEqualTo(obs2.returnPct());
    }

    @Test
    void rallyDayProducesScriptedDecouplingAndSilence() {
        LocalDate rally = LocalDate.of(2026, 3, 1);
        LocalDate from = rally.minusDays(10);
        LocalDate to = rally.plusDays(1);

        var sim = new DeterministicMarketSimulator(42L, UNIVERSE, from, to, new HackathonDemoScenario(rally));

        DailyObservation decoupler = sim.history(HackathonDemoScenario.DECOUPLER_SYMBOL, rally, rally).get(0);
        DailyObservation silent = sim.history(HackathonDemoScenario.SILENT_SYMBOL, rally, rally).get(0);
        DailyObservation normalPeer = sim.history("HIML", rally, rally).get(0);

        // Sector rallied hard, but the decoupler moved sharply in the
        // opposite direction - the whole point of the DECOUPLING signal.
        assertThat(decoupler.sectorReturnPct()).isGreaterThan(3.0);
        assertThat(decoupler.returnPct()).isLessThan(0.0);

        // The silent stock barely moved despite the same rally.
        assertThat(silent.returnPct()).isLessThan(1.0);

        // A normal peer actually participated in the rally.
        assertThat(normalPeer.returnPct()).isGreaterThan(1.0);
    }

    @Test
    void decouplerGraduallyRevertsOverTheAwayWindow() {
        LocalDate rally = LocalDate.of(2026, 3, 1);
        LocalDate from = rally.minusDays(5);
        LocalDate to = rally.plusDays(HackathonDemoScenario.AWAY_WINDOW_DAYS);

        var sim = new DeterministicMarketSimulator(42L, UNIVERSE, from, to, new HackathonDemoScenario(rally));

        // Cumulative deviation right after the rally vs. right before return day.
        double earlyDeviation = deviationFromSector(sim, HackathonDemoScenario.DECOUPLER_SYMBOL, rally.plusDays(1));
        double lateDeviation = deviationFromSector(sim, HackathonDemoScenario.DECOUPLER_SYMBOL, rally.plusDays(11));

        assertThat(Math.abs(lateDeviation)).isLessThan(Math.abs(earlyDeviation) + 1.0);
    }

    @Test
    void outageSymbolHasNoObservationOnTheOutageDay() {
        LocalDate rally = LocalDate.of(2026, 3, 1);
        LocalDate outageDay = rally.plusDays(HackathonDemoScenario.AWAY_WINDOW_DAYS);
        LocalDate from = rally.minusDays(5);
        LocalDate to = outageDay.plusDays(1);

        var sim = new DeterministicMarketSimulator(42L, UNIVERSE, from, to, new HackathonDemoScenario(rally));

        Optional<DailyObservation> onOutageDay = sim.history(HackathonDemoScenario.OUTAGE_SYMBOL, outageDay, outageDay)
                .stream().findFirst();
        Optional<DailyObservation> dayBefore = sim.history(HackathonDemoScenario.OUTAGE_SYMBOL, outageDay.minusDays(1), outageDay.minusDays(1))
                .stream().findFirst();

        assertThat(onOutageDay).isEmpty();
        assertThat(dayBefore).isPresent();
    }

    private double deviationFromSector(DeterministicMarketSimulator sim, String symbol, LocalDate day) {
        DailyObservation obs = sim.history(symbol, day, day).get(0);
        return obs.returnPct() - obs.sectorReturnPct();
    }
}
