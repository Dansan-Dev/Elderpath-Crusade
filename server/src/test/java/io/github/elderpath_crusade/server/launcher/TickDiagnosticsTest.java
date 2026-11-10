package io.github.elderpath_crusade.server.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TickDiagnosticsTest {

    @Test
    void toJson_withNoTicksRecorded_hasZeroedFields() {
        TickDiagnostics diagnostics = new TickDiagnostics(16_000_000L);
        String json = diagnostics.toJson();
        assertTrue(json.contains("\"tickCount\":0"), json);
        assertTrue(json.contains("\"overBudgetCount\":0"), json);
    }

    @Test
    void record_tracksCountAndMaxAndLast() {
        TickDiagnostics diagnostics = new TickDiagnostics(16_000_000L);
        diagnostics.record(1_000_000L);
        diagnostics.record(5_000_000L);
        diagnostics.record(3_000_000L);

        String json = diagnostics.toJson();
        assertTrue(json.contains("\"tickCount\":3"), json);
        assertTrue(json.contains("\"lastTickMs\":3.000"), json);
        assertTrue(json.contains("\"maxTickMs\":5.000"), json);
    }

    @Test
    void record_countsOverBudgetTicksAgainstTheGivenBudget() {
        TickDiagnostics diagnostics = new TickDiagnostics(16_000_000L); // 16ms budget
        diagnostics.record(10_000_000L); // under budget
        diagnostics.record(20_000_000L); // over budget
        diagnostics.record(30_000_000L); // over budget

        assertTrue(diagnostics.toJson().contains("\"overBudgetCount\":2"));
    }

    @Test
    void toJson_reportsTheBudgetItWasGiven() {
        TickDiagnostics diagnostics = new TickDiagnostics(16_000_000L);
        assertTrue(diagnostics.toJson().contains("\"budgetMs\":16.000"));
    }
}
