package io.github.elderpath_crusade.rendering;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.characters.sprites.terrain_sprites.MountainSprite;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.ecs.components.TerrainComponent;
import io.github.elderpath_crusade.game_objects.board.Board;

/**
 * View-side: gives every board entity (piece or terrain) a sprite as it enters the engine.
 * The simulation creates entities headlessly (PieceFactory, MatchSetup terrain), so this is
 * the single place sprites come from — for summons, ability-spawned pieces and terrain
 * locally, and for the online guest's replica pieces alike. Installed by the battle room for
 * its board; one installation per engine at a time.
 */
public final class PieceSpriteAttacher implements EntityListener {
    private static final Family BOARD_ENTITIES = Family.all(PositionComponent.class).get();
    private static PieceSpriteAttacher installed;

    private final Engine engine;
    private final Board board;

    private PieceSpriteAttacher(Engine engine, Board board) {
        this.engine = engine;
        this.board = board;
    }

    /** Attaches sprites to the board's existing entities and to every one added from now on. */
    public static void install(Engine engine, Board board) {
        if (installed != null) installed.engine.removeEntityListener(installed);
        installed = new PieceSpriteAttacher(engine, board);
        engine.addEntityListener(BOARD_ENTITIES, installed);
        ImmutableArray<Entity> existing = engine.getEntitiesFor(BOARD_ENTITIES);
        for (int i = 0; i < existing.size(); i++) installed.attach(existing.get(i));
        board.markDirtyAndNotify();
    }

    @Override
    public void entityAdded(Entity entity) {
        attach(entity);
    }

    @Override
    public void entityRemoved(Entity entity) {
    }

    private void attach(Entity entity) {
        if (entity.getComponent(SpriteComponent.class) != null) return;
        int width = board.getPLOT_WIDTH();
        int height = board.getPLOT_HEIGHT();

        if (entity.getComponent(TerrainComponent.class) != null) {
            entity.add(new SpriteComponent().set("MountainTerrain")
                    .setRenderable(new MountainSprite(0, 0, width, height)));
            return;
        }

        IdentityComponent id = entity.getComponent(IdentityComponent.class);
        AlignmentComponent align = entity.getComponent(AlignmentComponent.class);
        if (id == null || align == null) return;
        entity.add(new SpriteComponent().set(id.name)
                .setRenderable(new NamedCheckerSprite(0, 0, width, height, id.name, align.alignment)));
    }
}
