package io.github.elderpath_crusade;

import io.github.elderpath_crusade.abilities.impl._multi.aura.PackHunterAbility;
import io.github.elderpath_crusade.abilities.impl.trigger.OnSummonShockAbility;
import io.github.elderpath_crusade.abilities.stats.StatsAccumulator;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.characters.pieces.WolfCub;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbilityEffectTest {

    private Board board;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        board = mock(Board.class);
        when(board.getROWS()).thenReturn(5);
        when(board.getCOLS()).thenReturn(5);
    }

    // --- PackHunterAbility tests ---

    @Test
    void packHunter_buffAppliedWhenWolfCubSpawnedAdjacent() {
        // Wolf at (2,2)
        MonsterGamePiece wolf = createPiece(5, 2, 2, 1, PieceAlignment.P1);
        Board.Position wolfPos = new Board.Position(board, 2, 2);
        wolf.updateData(GamePieceData.POSITION, wolfPos);
        wolf.addAbility(new PackHunterAbility());

        // WolfCub at (2,3) — mock to avoid LibGDX sprite deps
        WolfCub cub = createMockWolfCub(PieceAlignment.P1);
        Board.Position cubPos = new Board.Position(board, 2, 3);
        cub.updateData(GamePieceData.POSITION, cubPos);
        when(board.getGamePieceAtPos(2, 3)).thenReturn(cub);

        int baseDamage = cub.getEffectiveDamage();

        // Emit spawn event to trigger refresh
        TypedEventBus.get().emit(new PieceSpawnedEvent(
            cub.getId().toString(), PieceAlignment.P1, 2, 3
        ));

        assertEquals(baseDamage + 1, cub.getEffectiveDamage());
    }

    @Test
    void packHunter_buffRemovedWhenWolfMovesAway() {
        // Wolf at (2,2), cub at (2,3)
        MonsterGamePiece wolf = createPiece(5, 2, 2, 1, PieceAlignment.P1);
        Board.Position wolfPos = new Board.Position(board, 2, 2);
        wolf.updateData(GamePieceData.POSITION, wolfPos);
        wolf.addAbility(new PackHunterAbility());

        WolfCub cub = createMockWolfCub(PieceAlignment.P1);
        Board.Position cubPos = new Board.Position(board, 2, 3);
        cub.updateData(GamePieceData.POSITION, cubPos);
        when(board.getGamePieceAtPos(2, 3)).thenReturn(cub);

        // Trigger initial buff
        TypedEventBus.get().emit(new PieceSpawnedEvent(
            cub.getId().toString(), PieceAlignment.P1, 2, 3
        ));
        assertEquals(1, cub.getEffectiveDamage());

        // Move wolf to (0,0) — no longer adjacent
        wolfPos.setRow(0);
        wolfPos.setCol(0);

        TypedEventBus.get().emit(new PieceMovedEvent(
            wolf.getId().toString(), PieceAlignment.P1, 2, 2, 0, 0,
            PieceMovedEvent.MovementType.ACTIVE, null, null
        ));

        assertEquals(0, cub.getEffectiveDamage());
    }

    // --- OnSummonShockAbility tests ---

    @Test
    void onSummonShock_dealsOneDamageToCardinalNeighbors() {
        // Shockling at (2,2)
        MonsterGamePiece shockling = createPiece(3, 1, 2, 1, PieceAlignment.P1);
        Board.Position shockPos = new Board.Position(board, 2, 2);
        shockling.updateData(GamePieceData.POSITION, shockPos);
        OnSummonShockAbility shock = new OnSummonShockAbility();
        shockling.addAbility(shock);

        // Enemy pieces in cardinal directions
        MonsterGamePiece north = createPiece(3, 1, 2, 1, PieceAlignment.P2);
        north.updateData(GamePieceData.POSITION, new Board.Position(board, 1, 2));
        MonsterGamePiece south = createPiece(3, 1, 2, 1, PieceAlignment.P2);
        south.updateData(GamePieceData.POSITION, new Board.Position(board, 3, 2));
        MonsterGamePiece east = createPiece(3, 1, 2, 1, PieceAlignment.P2);
        east.updateData(GamePieceData.POSITION, new Board.Position(board, 2, 3));
        MonsterGamePiece west = createPiece(3, 1, 2, 1, PieceAlignment.P2);
        west.updateData(GamePieceData.POSITION, new Board.Position(board, 2, 1));

        when(board.getGamePieceAtPos(1, 2)).thenReturn(north);
        when(board.getGamePieceAtPos(3, 2)).thenReturn(south);
        when(board.getGamePieceAtPos(2, 3)).thenReturn(east);
        when(board.getGamePieceAtPos(2, 1)).thenReturn(west);

        // Trigger onOwnerSpawned
        shockling.notifySpawned(2, 2);

        assertEquals(2, north.getStats().getCurrentHealth()); // 3 - 1
        assertEquals(2, south.getStats().getCurrentHealth());
        assertEquals(2, east.getStats().getCurrentHealth());
        assertEquals(2, west.getStats().getCurrentHealth());
    }

    // --- StatsModifier apply/remove tests ---

    @Test
    void statsModifier_applyIncreasesDamage() {
        MonsterGamePiece piece = createPiece(5, 2, 2, 1, PieceAlignment.P1);
        int baseDamage = piece.getEffectiveDamage();

        StatsModifier mod = new StatsModifier();
        mod.addDamage = 3;
        piece.getStatsAccumulator().add(mod);

        assertEquals(baseDamage + 3, piece.getEffectiveDamage());
    }

    @Test
    void statsModifier_removeRestoresOriginalDamage() {
        MonsterGamePiece piece = createPiece(5, 2, 2, 1, PieceAlignment.P1);
        int baseDamage = piece.getEffectiveDamage();

        StatsModifier mod = new StatsModifier();
        mod.addDamage = 3;
        piece.getStatsAccumulator().add(mod);
        piece.getStatsAccumulator().remove(mod);

        assertEquals(baseDamage, piece.getEffectiveDamage());
    }

    // --- Helpers ---

    private MonsterGamePiece createPiece(int hp, int dmg, int speed, int actions, PieceAlignment alignment) {
        return new MonsterGamePiece(
            GamePieceStats.getMonsterStats(1, hp, dmg, speed, actions),
            GamePieceType.MONSTER, alignment, UUID.randomUUID(), null
        );
    }

    /**
     * Creates a WolfCub-like piece that passes instanceof WolfCub checks
     * without triggering LibGDX sprite initialization.
     */
    private WolfCub createMockWolfCub(PieceAlignment alignment) {
        WolfCub cub = mock(WolfCub.class);
        StatsAccumulator acc = new StatsAccumulator();
        GamePieceStats stats = GamePieceStats.getMonsterStats(0, 1, 0, 1, 1);
        HashMap<GamePieceData, Object> data = new HashMap<>();
        UUID id = UUID.randomUUID();

        when(cub.getAlignment()).thenReturn(alignment);
        when(cub.getStatsAccumulator()).thenReturn(acc);
        when(cub.getStats()).thenReturn(stats);
        when(cub.getId()).thenReturn(id);
        // Delegate getData/updateData to the real map
        doAnswer(inv -> data.get(inv.getArgument(0))).when(cub).getData(any());
        doAnswer(inv -> { data.put(inv.getArgument(0), inv.getArgument(1)); return null; })
            .when(cub).updateData(any(), any());
        // Delegate getEffectiveDamage to compute from stats + accumulator
        when(cub.getEffectiveDamage()).thenAnswer(inv -> {
            int base = stats.getDamage();
            int add = 0; float mult = 0f;
            for (StatsModifier m : acc.getAll()) { add += m.addDamage; mult += m.multDamage; }
            return StatsModifier.applyInt(base, add, mult);
        });

        return cub;
    }
}
