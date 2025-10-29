package io.github.forest_of_dreams.abilities.impl;

import io.github.forest_of_dreams.abilities.AbilityType;
import io.github.forest_of_dreams.abilities.TriggeredAbility;
import io.github.forest_of_dreams.enums.GamePieceData;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;

/**
 * Triggered ability for Shockling: On Summon, deal 1 damage to adjacent pieces (cardinal).
 */
public class OnSummonShockAbility implements TriggeredAbility {
    private MonsterGamePiece owner;
    private boolean executed = false; // guard against double-triggering

    @Override
    public String getName() { return "Static Shock"; }

    @Override
    public String getDescription() { return OnSummonShockAbility.getAbilityDescription(); }

    public static String getAbilityDescription() {
        return "On Summon:\nDeal 1 damage\nto adjacent pieces";
    }

    @Override
    public AbilityType getType() { return AbilityType.TRIGGERED; }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public void onOwnerSpawned(MonsterGamePiece owner, int row, int col) {
        if (executed) return; // ensure one-shot on summon
        // Resolve board from owner's position (set by Board.addGamePieceToPos before this callback)
        Object posObj = owner.getData(GamePieceData.POSITION);
        if (!(posObj instanceof Board.Position pos)) return;
        Board board = pos.getBoard();
        if (board == null) return;
        // Use the provided row/col to avoid any transient mismatch with POSITION
        int r = row;
        int c = col;
        int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nr = r + d[0];
            int nc = c + d[1];
            if (nr < 0 || nr >= board.getROWS() || nc < 0 || nc >= board.getCOLS()) continue;
            GamePiece gp = board.getGamePieceAtPos(nr, nc);
            if (gp instanceof MonsterGamePiece target) {
                if (target == owner) continue; // safety: never damage self
                target.getStats().dealDamage(1);
                if (target.getStats().isDead()) {
                    // Let the target handle its own cleanup and board removal
                    target.die();
                } else {
                    // Optional: notify damaged
                    try { target.notifyDamaged(1, owner); } catch (Exception ignored) {}
                }
            }
        }
        executed = true;
    }

    @Override
    public void onDetach() {
        owner = null;
        executed = false;
    }
}
