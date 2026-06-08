package io.github.elderpath_crusade.test;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Skips a test when game assets are unavailable (cloud CI).
 * Controlled by system property {@code testProfile}:
 *   "cloud" — always skip
 *   "local" — always run
 *   absent  — auto-detect from filesystem (default)
 */
public class AssetsCondition implements ExecutionCondition {

    private static final String ASSET_PROBE = "../assets/data/pieces.yaml";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String profile = System.getProperty("testProfile", "auto");
        return switch (profile) {
            case "cloud" -> ConditionEvaluationResult.disabled("Skipped: testProfile=cloud (assets unavailable)");
            case "local" -> ConditionEvaluationResult.enabled("Running: testProfile=local");
            default -> Files.exists(Path.of(ASSET_PROBE))
                    ? ConditionEvaluationResult.enabled("Running: assets found (auto-detected local)")
                    : ConditionEvaluationResult.disabled("Skipped: assets not found (auto-detected cloud)");
        };
    }
}
