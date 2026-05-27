package io.github.elderpath_crusade.ecs.factory;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Factory for creating piece entities with the appropriate components.
 * Each method produces a fully-configured entity and adds it to the engine.
 */
public final class PieceFactory {
    private PieceFactory() {}

    public static Entity createWolf(Engine engine, PieceAlignment alignment, int row, int col) {
        Entity e = engine.createEntity();
        e.add(new IdentityComponent().set("Wolf"));
        e.add(new AlignmentComponent().set(alignment));
        e.add(new StatsComponent().set(1, 1, 1, 1, 1));
        e.add(new PositionComponent().set(row, col));
        e.add(new AbilityComponent().add("PackHunter"));
        engine.addEntity(e);
        return e;
    }

    public static Entity createWolfCub(Engine engine, PieceAlignment alignment, int row, int col) {
        Entity e = engine.createEntity();
        e.add(new IdentityComponent().set("WolfCub"));
        e.add(new AlignmentComponent().set(alignment));
        e.add(new StatsComponent().set(1, 1, 1, 1, 1));
        e.add(new PositionComponent().set(row, col));
        engine.addEntity(e);
        return e;
    }

    /**
     * Generic piece creation from parameters. Used for data-driven piece definitions.
     */
    public static Entity createPiece(Engine engine, String name, PieceAlignment alignment,
                                     int cost, int hp, int dmg, int speed, int actions,
                                     int row, int col) {
        Entity e = engine.createEntity();
        e.add(new IdentityComponent().set(name));
        e.add(new AlignmentComponent().set(alignment));
        e.add(new StatsComponent().set(cost, hp, dmg, speed, actions));
        e.add(new PositionComponent().set(row, col));
        engine.addEntity(e);
        return e;
    }
}
