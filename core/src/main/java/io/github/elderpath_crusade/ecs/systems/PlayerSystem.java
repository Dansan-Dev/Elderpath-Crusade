package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import io.github.elderpath_crusade.ecs.components.PlayerComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.ManaChangedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;

/**
 * Owns mana via PlayerComponent on two singleton entities (P1, P2), created once in
 * addedToEngine. Holds direct references to the components (not entity lookups) so
 * mana survives Engine.removeAllEntities() between matches, same as TurnSystem's
 * TurnStateComponent.
 *
 * PlayerManager is a facade over this system, mirroring TurnManager/TurnSystem.
 */
public class PlayerSystem extends EntitySystem {
    private PlayerComponent p1;
    private PlayerComponent p2;

    @Override
    public void addedToEngine(Engine engine) {
        p1 = createPlayerEntity(engine, PieceAlignment.P1);
        p2 = createPlayerEntity(engine, PieceAlignment.P2);
    }

    private PlayerComponent createPlayerEntity(Engine engine, PieceAlignment alignment) {
        Entity entity = engine.createEntity();
        PlayerComponent component = new PlayerComponent();
        component.alignment = alignment;
        entity.add(component);
        engine.addEntity(entity);
        return component;
    }

    private PlayerComponent get(PieceAlignment alignment) {
        return alignment == PieceAlignment.P1 ? p1 : p2;
    }

    public int getMana(PieceAlignment alignment) {
        return get(alignment).mana;
    }

    /**
     * Sets mana and emits ManaChangedEvent — the single choke point every mana change (turn-start
     * gain, card cost, ability mana effects) goes through, so nothing needs to remember to emit
     * this itself. Previously only PlayerManager.onStartTurn emitted it (for the +1 gain), so
     * spending mana on a card was invisible to anything relying on the event — including the
     * online guest, whose mirrored mana never reflected a card's cost, only the next turn's gain.
     */
    public void setMana(PieceAlignment alignment, int value) {
        PlayerComponent component = get(alignment);
        int clamped = Math.max(0, value);
        if (component.mana == clamped) return;
        component.mana = clamped;
        TypedEventBus.get().emit(new ManaChangedEvent(alignment, clamped));
    }

    public void addMana(PieceAlignment alignment, int delta) {
        setMana(alignment, get(alignment).mana + delta);
    }

    public void resetForNewGame() {
        p1.mana = 0;
        p2.mana = 0;
    }
}
