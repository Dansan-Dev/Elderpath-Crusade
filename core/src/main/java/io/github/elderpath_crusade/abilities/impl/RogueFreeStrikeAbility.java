package io.github.elderpath_crusade.abilities.impl;

import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.TriggeredAbility;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.Clickable;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.OnClick;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.managers.InteractionManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rogue passive/triggered: After each manual move, immediately allow an optional free attack
 * on a single valid target in range. The attack does not cost an action; the movement still does.
 */
public class RogueFreeStrikeAbility implements TriggeredAbility, Ability {
    private MonsterGamePiece owner;

    @Override
    public String getName() { return "Free Strike"; }

    @Override
    public String getDescription() {
        return "After moving: you may make\n" +
               "a free attack (no action).";
    }

    @Override
    public AbilityType getType() { return AbilityType.TRIGGERED; }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        this.owner = owner;
    }

    @Override
    public void onDetach() {
        this.owner = null;
    }

    @Override
    public void onOwnerMoved(MonsterGamePiece owner, int fromRow, int fromCol, int toRow, int toCol) {
        if (this.owner == null || owner != this.owner) return;
        // Only trigger for manual moves
        Object cause = owner.getData(GamePieceData.MOVE_CAUSE);
        if (!(cause instanceof String s) || !"MANUAL".equals(s)) {
            return;
        }
        // Clear the marker immediately to avoid leaking into other logic
        owner.updateData(GamePieceData.MOVE_CAUSE, null);
        // Find board context
        Object posObj = owner.getData(GamePieceData.POSITION);
        if (!(posObj instanceof Board.Position pos)) return;
        Board board = pos.getBoard();
        if (board == null) return;
        // Compute attackable enemy plots from the new position using owner's effective range
        List<Plot> attackables = board.getAttackableEnemyPlots(toRow, toCol, owner.getAlignment());
        if (attackables == null || attackables.isEmpty()) return; // per UX: no selection if no valid targets

        // Bot path vs Human path delegated to sub-methods for clarity
        boolean botControlled = owner.getAlignment() == PieceAlignment.P2
            && SettingsManager.debug.enableP2Bot
            && TurnManager.getCurrentPlayer() == owner.getAlignment();
        if (botControlled) {
            handleBotFreeStrike(board, toRow, toCol, attackables);
            return;
        }
        startHumanFreeStrikeSelection(board, owner, toRow, toCol, attackables);
    }

    // --- Sub-methods: split bot and human paths for clarity ---
    private void handleBotFreeStrike(Board board, int toRow, int toCol, List<Plot> attackables) {
        if (this.owner == null || board == null || attackables == null || attackables.isEmpty()) return;
        Plot best = null;
        int bestScore = Integer.MIN_VALUE;
        int dmg = owner.getEffectiveDamage();
        for (Plot p : attackables) {
            int[] dIdx = board.getIndicesOfPlot(p); if (dIdx == null) continue;
            GamePiece gp = board.getGamePieceAtPos(dIdx[0], dIdx[1]);
            if (!(gp instanceof MonsterGamePiece enemy)) continue;
            int hp = Math.max(0, enemy.getStats().getCurrentHealth());
            boolean lethal = dmg >= hp;
            int score = (lethal ? 1000 : 0)
                + Math.min(10, Math.max(0, enemy.getStats().getCost())) * 5
                + Math.min(5, Math.max(0, enemy.getEffectiveDamage())) * 2
                - hp; // prefer lower HP if non-lethal
            if (score > bestScore) { bestScore = score; best = p; }
        }
        if (best == null) return;
        int[] dIdx = board.getIndicesOfPlot(best);
        if (dIdx == null) return;
        int dr = dIdx[0], dc = dIdx[1];
        GamePiece targetPiece = board.getGamePieceAtPos(dr, dc);
        if (!(targetPiece instanceof MonsterGamePiece enemy)) return;
        if (enemy.getAlignment() == owner.getAlignment()) return;
        // Re-validate still attackable from current position
        boolean stillValid = false;
        for (Plot p2 : board.getAttackableEnemyPlots(toRow, toCol, owner.getAlignment())) { if (p2 == best) { stillValid = true; break; } }
        if (!stillValid) return;
        int deal = owner.getEffectiveDamage();
        enemy.getStats().dealDamage(deal);
        try { owner.notifyAttack(enemy, deal); } catch (Exception ignored) {}
        try { enemy.notifyDamaged(deal, owner); } catch (Exception ignored) {}
        EventBus.emit(
            GameEventType.PIECE_ATTACKED,
            Map.of(
                "attackerId", owner.getId().toString(),
                "defenderId", enemy.getId().toString(),
                "row", dr,
                "col", dc,
                "attackerRow", toRow,
                "attackerCol", toCol,
                "defenderRow", dr,
                "defenderCol", dc,
                "damage", deal
            )
        );
        if (enemy.getStats().getCurrentHealth() <= 0) {
            try { enemy.notifyDied(); } catch (Exception ignored) {}
            board.removeGamePieceAtPos(dr, dc);
            EventBus.emit(
                GameEventType.PIECE_DIED,
                Map.of(
                    "pieceId", enemy.getId().toString(),
                    "row", dr,
                    "col", dc
                )
            );
        }
    }

    private void startHumanFreeStrikeSelection(Board board, MonsterGamePiece owner, int toRow, int toCol, List<Plot> attackables) {
        if (board == null || owner == null || attackables == null || attackables.isEmpty()) return;
        FreeStrikeSource source = new FreeStrikeSource(board, owner, toRow, toCol, attackables);
        InteractionManager.startProgrammaticInteraction(source);
    }

    // Temporary source for InteractionManager. Not rendered; only used to capture the next target.
    private static class FreeStrikeSource implements Clickable, TargetFilter {
        private final Board board;
        private final MonsterGamePiece owner;
        private final int sr, sc; // source position (after move)
        private final List<Plot> validTargets;
        private final ClickableEffectData data;
        private OnClick onClick;

        FreeStrikeSource(Board board, MonsterGamePiece owner, int sr, int sc, List<Plot> validTargets) {
            this.board = board;
            this.owner = owner;
            this.sr = sr;
            this.sc = sc;
            this.validTargets = validTargets;
            this.data = ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1);
            // Wire the click executor: when a valid target is picked, perform the free attack
            setClickableEffect(this::execute, data);
        }

        private void execute(HashMap<Integer, CustomBox> entities) {
            Object t = entities.get(1);
            Plot dst = resolveToPlot(t);
            if (dst == null) return; // canceled or invalid
            int[] dIdx = board.getIndicesOfPlot(dst);
            if (dIdx == null) return;
            int dr = dIdx[0], dc = dIdx[1];
            GamePiece targetPiece = board.getGamePieceAtPos(dr, dc);
            if (!(targetPiece instanceof MonsterGamePiece enemy)) return;
            if (enemy.getAlignment() == owner.getAlignment()) return;
            // Re-validate that the chosen plot is still attackable right now
            boolean stillValid = false;
            for (Plot p : board.getAttackableEnemyPlots(sr, sc, owner.getAlignment())) { if (p == dst) { stillValid = true; break; } }
            if (!stillValid) return;
            // Execute free attack: replicate Board attack branch but without spending an action
            int dmg = owner.getEffectiveDamage();
            enemy.getStats().dealDamage(dmg);
            try { owner.notifyAttack(enemy, dmg); } catch (Exception ignored) {}
            try { enemy.notifyDamaged(dmg, owner); } catch (Exception ignored) {}
            EventBus.emit(
                GameEventType.PIECE_ATTACKED,
                Map.of(
                    "attackerId", owner.getId().toString(),
                    "defenderId", enemy.getId().toString(),
                    "row", dr,
                    "col", dc,
                    "attackerRow", sr,
                    "attackerCol", sc,
                    "defenderRow", dr,
                    "defenderCol", dc,
                    "damage", dmg
                )
            );
            if (enemy.getStats().getCurrentHealth() <= 0) {
                try { enemy.notifyDied(); } catch (Exception ignored) {}
                board.removeGamePieceAtPos(dr, dc);
                EventBus.emit(
                    GameEventType.PIECE_DIED,
                    Map.of(
                        "pieceId", enemy.getId().toString(),
                        "row", dr,
                        "col", dc
                    )
                );
            }
        }

        private Plot resolveToPlot(Object box) {
            if (box instanceof Plot p) return p;
            if (box instanceof GamePiece gp) {
                Object posObj = gp.getData(GamePieceData.POSITION);
                if (posObj instanceof Board.Position pos && pos.getBoard() == board) {
                    var r = pos.getRow(); var c = pos.getCol();
                    var rp = board.getPlotAtPos(r, c);
                    if (rp instanceof Plot pp) return pp;
                }
            }
            return null;
        }

        // --- Clickable ---
        @Override public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) { this.onClick = onClick; }
        @Override public ClickableEffectData getClickableEffectData() { return data; }
        @Override public void triggerClickEffect(HashMap<Integer, CustomBox> entities) { if (onClick != null) onClick.run(entities); }
        // Provide minimal box for API completeness; not actually used for hit-testing
        @Override public int getX() { return 0; }
        @Override public int getY() { return 0; }
        @Override public int getWidth() { return 0; }
        @Override public int getHeight() { return 0; }
        @Override public boolean isPauseUIElement() { return false; }

        // --- TargetFilter ---
        @Override
        public boolean isValidTargetForEffect(CustomBox box) {
            Plot p = resolveToPlot(box);
            if (p == null) return false;
            // Only allow if it's currently in the valid attackables set we computed from (sr,sc)
            for (Plot allowed : validTargets) { if (allowed == p) return true; }
            return false;
        }
    }
}
