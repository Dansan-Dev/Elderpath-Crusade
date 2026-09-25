package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import io.github.elderpath_crusade.ecs.components.PlayerComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;

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

    public void setMana(PieceAlignment alignment, int value) {
        get(alignment).mana = Math.max(0, value);
    }

    public void addMana(PieceAlignment alignment, int delta) {
        setMana(alignment, get(alignment).mana + delta);
    }

    public void resetForNewGame() {
        p1.mana = 0;
        p2.mana = 0;
    }
}
