package io.github.elderpath_crusade.run;

/**
 * One card in a roguelike run's deck — a specific, modifiable instance of a {@code CardCatalog}
 * entry. The run's draft/catalogue screen shows the static card by name; what actually goes into
 * the deck, and what a given deck slot summons, is this instance (base card name + its own
 * modification, if any) — not a shared name lookup. See roguelike-run-plan.md's "Card
 * Modification System".
 */
public record RunCardInstance(String instanceId, String cardName, RunCardModification modification) {
    public RunCardInstance {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId required");
        if (cardName == null || cardName.isBlank()) throw new IllegalArgumentException("cardName required");
        if (modification == null) modification = RunCardModification.NONE;
    }

    public boolean isModified() {
        return !modification.isEmpty();
    }

    public static RunCardInstance unmodified(String instanceId, String cardName) {
        return new RunCardInstance(instanceId, cardName, RunCardModification.NONE);
    }

    public RunCardInstance withModification(RunCardModification newModification) {
        return new RunCardInstance(instanceId, cardName, newModification);
    }
}
