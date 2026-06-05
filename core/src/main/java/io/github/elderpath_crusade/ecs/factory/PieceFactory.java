package io.github.elderpath_crusade.ecs.factory;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.characters.sprites.checker_sprites.NamedCheckerSprite;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.enums.settings.GamePieceType;

import java.util.UUID;

/**
 * Factory for creating piece entities and MonsterGamePiece instances from PieceDefinition.
 * Abilities are now attached via AbilityInstanceComponent by PieceSyncSystem (data-driven).
 */
public final class PieceFactory {
    private PieceFactory() {}

    /**
     * Creates a fully-configured MonsterGamePiece from a PieceDefinition.
     */
    public static MonsterGamePiece createPiece(PieceDefinition def, int x, int y, int width, int height, PieceAlignment alignment) {
        GamePieceStats stats = GamePieceStats.getMonsterStats(
                def.cost(), def.health(), def.damage(), def.speed(), def.actions());
        MonsterGamePiece piece = new MonsterGamePiece(
                stats, GamePieceType.MONSTER, alignment, UUID.randomUUID(),
                new NamedCheckerSprite(x, y, width, height, def.id(), alignment));
        piece.getPieceModel().setName(def.id());
        return piece;
    }

    /**
     * Creates an ECS entity from a PieceDefinition (for pure-ECS usage).
     */
    public static Entity createFromDefinition(Engine engine, PieceDefinition def,
                                              PieceAlignment alignment, String pieceId,
                                              int row, int col) {
        Entity e = engine.createEntity();
        e.add(new IdentityComponent().set(pieceId));
        e.add(new AlignmentComponent().set(alignment));
        e.add(new StatsComponent().set(def.cost(), def.health(), def.damage(), def.speed(), def.actions()));
        e.add(new PositionComponent().set(row, col));
        e.add(new ModifierComponent());
        engine.addEntity(e);
        return e;
    }
}
