package io.github.elderpath_crusade.ecs.factory;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.UUID;

/**
 * Factory for creating piece entities from PieceDefinition.
 * Returns pure ECS entities — no OOP piece classes.
 */
public final class PieceFactory {
    private PieceFactory() {}

    /**
     * Creates a fully-configured ECS entity from a PieceDefinition, adds it to the engine,
     * and registers it in GridIndexSystem.
     */
    public static Entity createPiece(PieceDefinition def, int x, int y, int width, int height,
                                     PieceAlignment alignment, int row, int col) {
        Engine engine = GameContext.get().getEcsEngine();
        String pieceId = UUID.randomUUID().toString();

        Entity entity = engine.createEntity();
        entity.add(new IdentityComponent().set(pieceId, def.id()));
        entity.add(new AlignmentComponent().set(alignment));
        entity.add(new PositionComponent().set(row, col));
        entity.add(new StatsComponent().set(def.cost(), def.health(), def.damage(), def.speed(), def.actions()));
        entity.add(new SpriteComponent().set(def.id())
                .setRenderable(new NamedCheckerSprite(x, y, width, height, def.id(), alignment)));
        entity.add(new ModifierComponent());
        entity.add(new ComputedStatsComponent());

        // Attach data-driven ability definitions
        AbilityInstanceComponent aic = new AbilityInstanceComponent();
        for (String abilityName : def.abilities()) {
            AbilityDefinition abDef = AbilityRegistry.get(abilityName);
            if (abDef != null) {
                aic.addAbility(abDef);
            }
        }
        if (!aic.definitions.isEmpty()) {
            entity.add(aic);
        }

        engine.addEntity(entity);
        return entity;
    }

    /**
     * Creates an ECS entity from a PieceDefinition (for pure-ECS usage without sprite).
     */
    public static Entity createFromDefinition(Engine engine, PieceDefinition def,
                                              PieceAlignment alignment, String pieceId,
                                              int row, int col) {
        Entity e = engine.createEntity();
        e.add(new IdentityComponent().set(pieceId, def.id()));
        e.add(new AlignmentComponent().set(alignment));
        e.add(new StatsComponent().set(def.cost(), def.health(), def.damage(), def.speed(), def.actions()));
        e.add(new PositionComponent().set(row, col));
        e.add(new ModifierComponent());
        engine.addEntity(e);
        return e;
    }
}
