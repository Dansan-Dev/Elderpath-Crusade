package io.github.elderpath_crusade.run;

import io.github.elderpath_crusade.data.AbilityRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A modification applied to one specific {@link RunCardInstance} — stat deltas (additive, same
 * stat-name keys as a piece's base stats, e.g. "damage", "health") and/or extra abilities
 * granted on top of the base piece's own. Applying this at summon time (a synthetic per-run
 * PieceDefinition/registry key) is UPG-003's job — this record only holds the data.
 *
 * <p>{@code addedAbilities} holds full {@link AbilityRef}s, not bare names (BUG-050, EVENT-001):
 * a folded-in piece's ability may need its own {@code params} to behave correctly (e.g. a
 * generic ranged-attack ability specialized per piece with {@code {range: 3}} —
 * {@code PieceFactory}/{@code UnitCard} both branch on {@code ref.params().isEmpty()} to decide
 * whether to fetch the plain {@code AbilityRegistry} entry or a {@code specialize}d one).
 * Carrying only the name silently dropped those params, reconstructing the un-specialized
 * (effectively broken) ability — found via the user's own playtest fusing a Rifleman into a
 * Hero with {@code RunManager.combineCards} and finding its range "seemed bugged".
 *
 * <p>{@link #encodeMap}/{@link #decodeMap} (LAUNCH-001) are the text format used to pass a
 * side's whole set of modifications across the client→battle-launch boundary
 * ({@code LocalMatchSpec}/{@code ServerLauncher}), since this data is generated per run rather
 * than pre-authored — unlike a relic, which only ever needs its id passed across (its content
 * already lives in {@code abilities.yaml}, loaded by every server process).
 */
public record RunCardModification(Map<String, Integer> statDeltas, List<AbilityRef> addedAbilities) {
    public static final RunCardModification NONE = new RunCardModification(Map.of(), List.of());

    public RunCardModification {
        statDeltas = Map.copyOf(statDeltas);
        addedAbilities = List.copyOf(addedAbilities);
    }

    public boolean isEmpty() {
        return statDeltas.isEmpty() && addedAbilities.isEmpty();
    }

    /** Combines this modification with another applied on top — same-stat deltas add, ability lists concatenate. */
    public RunCardModification plus(RunCardModification other) {
        Map<String, Integer> merged = new LinkedHashMap<>(statDeltas);
        for (Map.Entry<String, Integer> entry : other.statDeltas.entrySet()) {
            merged.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        List<AbilityRef> abilities = new ArrayList<>(addedAbilities);
        abilities.addAll(other.addedAbilities);
        return new RunCardModification(merged, abilities);
    }

    /**
     * "damage=2,health=-1:Frenzy|RangeBonus{range=3}" — stat deltas, then ':', then
     * '|'-separated ability entries. An ability with no params is just its bare name; one with
     * params appends {@code {key=value,...}} (braces never otherwise appear in this spec).
     */
    public String toSpec() {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : statDeltas.entrySet()) {
            if (!first) out.append(',');
            out.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        out.append(':');
        first = true;
        for (AbilityRef ability : addedAbilities) {
            if (!first) out.append('|');
            out.append(encodeAbility(ability));
            first = false;
        }
        return out.toString();
    }

    private static String encodeAbility(AbilityRef ability) {
        if (ability.params().isEmpty()) return ability.name();
        StringBuilder out = new StringBuilder(ability.name()).append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : ability.params().entrySet()) {
            if (!first) out.append(',');
            out.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return out.append('}').toString();
    }

    private static AbilityRef decodeAbility(String entry) {
        int brace = entry.indexOf('{');
        if (brace < 0) return new AbilityRef(entry);
        if (!entry.endsWith("}")) {
            throw new IllegalArgumentException("malformed ability params (missing closing '}'): " + entry);
        }
        String name = entry.substring(0, brace);
        String paramsPart = entry.substring(brace + 1, entry.length() - 1);
        Map<String, Object> params = new LinkedHashMap<>();
        if (!paramsPart.isEmpty()) {
            for (String param : paramsPart.split(",")) {
                int eq = param.indexOf('=');
                if (eq < 0) throw new IllegalArgumentException("malformed ability param (missing '='): " + param);
                params.put(param.substring(0, eq), decodeParamValue(param.substring(eq + 1)));
            }
        }
        return new AbilityRef(name, params);
    }

    /** Best-effort type recovery for a param value round-tripped through text: int, else decimal, else boolean, else the raw string. */
    private static Object decodeParamValue(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            // not an int
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            // not a decimal either
        }
        if (raw.equals("true") || raw.equals("false")) return Boolean.parseBoolean(raw);
        return raw;
    }

    /** Inverse of {@link #toSpec()}. */
    public static RunCardModification parseSpec(String spec) {
        int colon = spec.indexOf(':');
        if (colon < 0) throw new IllegalArgumentException("malformed modification spec (missing ':'): " + spec);
        String statsPart = spec.substring(0, colon);
        String abilitiesPart = spec.substring(colon + 1);

        Map<String, Integer> deltas = new LinkedHashMap<>();
        if (!statsPart.isEmpty()) {
            for (String entry : statsPart.split(",")) {
                int eq = entry.indexOf('=');
                if (eq < 0) throw new IllegalArgumentException("malformed stat delta (missing '='): " + entry);
                String stat = entry.substring(0, eq);
                try {
                    deltas.put(stat, Integer.parseInt(entry.substring(eq + 1)));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("stat delta is not a number: " + entry);
                }
            }
        }
        List<AbilityRef> abilities = new ArrayList<>();
        if (!abilitiesPart.isEmpty()) {
            for (String entry : abilitiesPart.split("\\|")) abilities.add(decodeAbility(entry));
        }
        return new RunCardModification(deltas, abilities);
    }

    /** "run-3@damage=2,health=-1:Frenzy;run-5@:Berserk|Tough" — one instance's modification per ';'-separated entry. */
    public static String encodeMap(Map<String, RunCardModification> modsByInstanceId) {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, RunCardModification> entry : modsByInstanceId.entrySet()) {
            String instanceId = entry.getKey();
            if (instanceId.contains("@") || instanceId.contains(";")) {
                throw new IllegalArgumentException("instance id can't be passed to the server: " + instanceId);
            }
            if (!first) out.append(';');
            out.append(instanceId).append('@').append(entry.getValue().toSpec());
            first = false;
        }
        return out.toString();
    }

    /** Inverse of {@link #encodeMap}. Blank input decodes to an empty map. */
    public static Map<String, RunCardModification> decodeMap(String spec) {
        Map<String, RunCardModification> out = new LinkedHashMap<>();
        if (spec == null || spec.isBlank()) return out;
        for (String entry : spec.split(";")) {
            int at = entry.indexOf('@');
            if (at < 0) throw new IllegalArgumentException("malformed card-mod entry (missing '@'): " + entry);
            out.put(entry.substring(0, at), parseSpec(entry.substring(at + 1)));
        }
        return out;
    }
}
