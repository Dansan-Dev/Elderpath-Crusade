package io.github.elderpath_crusade.run;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A modification applied to one specific {@link RunCardInstance} — stat deltas (additive, same
 * stat-name keys as a piece's base stats, e.g. "damage", "health") and/or extra ability names
 * granted on top of the base piece's own. Applying this at summon time (a synthetic per-run
 * PieceDefinition/registry key) is UPG-003's job — this record only holds the data.
 *
 * <p>{@link #encodeMap}/{@link #decodeMap} (LAUNCH-001) are the text format used to pass a
 * side's whole set of modifications across the client→battle-launch boundary
 * ({@code LocalMatchSpec}/{@code ServerLauncher}), since this data is generated per run rather
 * than pre-authored — unlike a relic, which only ever needs its id passed across (its content
 * already lives in {@code abilities.yaml}, loaded by every server process).
 */
public record RunCardModification(Map<String, Integer> statDeltas, List<String> addedAbilities) {
    public static final RunCardModification NONE = new RunCardModification(Map.of(), List.of());

    public RunCardModification {
        statDeltas = Map.copyOf(statDeltas);
        addedAbilities = List.copyOf(addedAbilities);
    }

    public boolean isEmpty() {
        return statDeltas.isEmpty() && addedAbilities.isEmpty();
    }

    /** "damage=2,health=-1:Frenzy|Tough" — stat deltas, then ':', then '|'-separated ability names. */
    public String toSpec() {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : statDeltas.entrySet()) {
            if (!first) out.append(',');
            out.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        out.append(':');
        out.append(String.join("|", addedAbilities));
        return out.toString();
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
        List<String> abilities = abilitiesPart.isEmpty() ? List.of() : List.of(abilitiesPart.split("\\|"));
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
