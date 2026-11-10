package io.github.elderpath_crusade.bot.eval;

/**
 * A bot's playstyle as independent sliders (BOT-005), not a fixed set of named archetypes — a
 * bot can be aggressive <em>and</em> a saver <em>and</em> spear the middle at once. Each
 * dimension is its own concern and {@link BotConfig#forPersonality} scales (or copies through)
 * a disjoint part of {@link BotConfig} for each one, so they compose independently rather than
 * fighting over the same weights. Every dimension is {@code 0} at {@link #BALANCED} and at
 * {@code 0} contributes nothing — {@code forPersonality(BALANCED)} reproduces
 * {@link BotConfig#defaultConfig()} exactly, field for field.
 *
 * @param aggression       -1 (cautious) .. 0 (balanced) .. +1 (aggressive) — how eagerly it
 *                         attacks/advances and how much it discounts general exposure risk.
 * @param riskTolerance    -1 (only takes clearly favorable trades) .. +1 (trades even at a
 *                         loss to disrupt the opponent) — specifically about combat trades,
 *                         independent of {@code aggression}'s general risk-taking.
 * @param tempo            -1 (fights for board control first) .. +1 (always chases a
 *                         discovered path to the win condition, accepting more risk en route)
 *                         — scales the win-path search's own scores and safety margins, not
 *                         combat/advance scoring.
 * @param saving           -1 (plays whatever it can afford immediately) .. 0 (balanced) ..
 *                         +1 (holds mana/cards back for a bigger play later, even skipping an
 *                         affordable-but-weak card).
 * @param formation        -1 (favors the flanks/side columns) .. 0 (spreads evenly) ..
 *                         +1 (spears through the center columns) — a tie-breaking lean on
 *                         where to summon/reposition, never overriding an urgent defensive
 *                         placement.
 * @param targeting        -1 (goes for the easiest kill, lowest health first) .. 0 (balanced)
 *                         .. +1 (goes for the single biggest threat first).
 * @param unpredictability 0 (always the single best-rated move) .. 1 (casts a wide net among
 *                         near-equally-good moves and picks randomly) — see
 *                         {@code SmartBot}'s tie-breaking (BOT-002).
 */
public record BotPersonality(
        float aggression,
        float riskTolerance,
        float tempo,
        float saving,
        float formation,
        float targeting,
        float unpredictability) {

    public static final BotPersonality BALANCED = new BotPersonality(0, 0, 0, 0, 0, 0, 0);

    public BotPersonality {
        aggression = clampSigned(aggression);
        riskTolerance = clampSigned(riskTolerance);
        tempo = clampSigned(tempo);
        saving = clampSigned(saving);
        formation = clampSigned(formation);
        targeting = clampSigned(targeting);
        unpredictability = Math.max(0f, Math.min(1f, unpredictability));
    }

    /** A bolder, more trade-happy, race-focused, free-spending bot that leans toward the biggest threat. */
    public static BotPersonality aggressive() {
        return new BotPersonality(0.6f, 0.4f, 0.3f, -0.3f, 0f, 0.2f, 0f);
    }

    /** A cautious, trade-averse, board-control-first bot that hoards resources. */
    public static BotPersonality defensive() {
        return new BotPersonality(-0.6f, -0.4f, -0.3f, 0.3f, 0f, -0.2f, 0f);
    }

    private static float clampSigned(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }
}
