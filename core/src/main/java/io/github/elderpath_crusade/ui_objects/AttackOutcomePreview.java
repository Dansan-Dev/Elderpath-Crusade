package io.github.elderpath_crusade.ui_objects;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.input.InteractionManager;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.supers.LowestOrderTexture;
import io.github.elderpath_crusade.utils.FontSize;

/**
 * FEAT-015/B2: while a piece is selected and the mouse hovers one of its attack-candidate
 * plots (the same set HighlightManager already glows — this owns no eligibility logic of its
 * own), shows the resulting damage/outcome before the player commits: damage dealt, the
 * defender's resulting health, and whether the hit would kill it. All from data the client
 * already has (ReplicaViewSync's effective stats) — no new rule, purely a view-only read of
 * EntityUtils.getDamage/getCurrentHealth/getMaxHealth, matching AttackSystem/CombatSystem's own
 * damage formula (flat attacker damage, no armor/defense reduction).
 *
 * Deliberately NOT attempted here: predicting whether a reaction (CleaveAttack, OnSummonShock,
 * etc.) would also fire — that needs correctly replicating each ability's trigger/condition
 * evaluation client-side, and a wrong prediction would mislead more than no prediction at all.
 * Left as a documented follow-up rather than guessed at.
 */
public class AttackOutcomePreview extends LowestOrderTexture implements UIRenderable {
    private static final int OFFSET_Y = 8;
    private static final int Z = 20;

    private final Text text;

    public AttackOutcomePreview() {
        this.text = new Text("", FontType.SILKSCREEN, 0, 0, Z, Color.WHITE).withFontSize(FontSize.CAPTION);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        if (isPaused || GameContext.get().getGameManager().isPaused()) return;

        Outcome outcome = computeOutcome();
        if (outcome == null) return;

        String content = outcome.damage() + " dmg -> " + outcome.resultingHealth() + "/" + outcome.maxHealth() + " HP"
                + (outcome.resultingHealth() <= 0 ? "  (KO)" : "");
        if (!content.equals(text.getText())) {
            text.setText(content);
            text.update();
        }
        text.setBounds(new Box(outcome.x() - text.getWidth() / 2, outcome.y(), text.getWidth(), text.getHeight()));
        text.render(batch, Z, isPaused);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        renderUI(batch, isPaused);
    }

    private Outcome computeOutcome() {
        InteractionManager interactionManager = GameContext.get().getInteractionManager();
        if (!interactionManager.hasActiveSelection()) return null;
        CustomBox source = interactionManager.getActiveSource();
        if (!(source instanceof Plot sourcePlot)) return null;

        Board board = sourcePlot.getBoard();
        if (board == null) return null;
        Entity attacker = board.getEntityAtPlot(sourcePlot);
        if (attacker == null) return null;

        Plot hoveredPlot = findHoveredPlot(board);
        if (hoveredPlot == null || !GameContext.get().getHighlightManager().isAttackCandidate(hoveredPlot)) return null;

        Entity defender = board.getEntityAtPlot(hoveredPlot);
        if (defender == null) return null;

        int damage = EntityUtils.getDamage(attacker);
        int currentHealth = EntityUtils.getCurrentHealth(defender);
        int maxHealth = EntityUtils.getMaxHealth(defender);
        int resultingHealth = Math.max(0, currentHealth - damage);

        int boardX = board.getBounds().getX();
        int boardY = board.getBounds().getY();
        int plotX = boardX + hoveredPlot.getCol() * board.getPLOT_WIDTH();
        int plotY = boardY + board.toDisplayRow(hoveredPlot.getRow()) * board.getPLOT_HEIGHT();
        int x = plotX + board.getPLOT_WIDTH() / 2;
        int y = plotY + board.getPLOT_HEIGHT() + OFFSET_Y;

        return new Outcome(damage, resultingHealth, maxHealth, x, y);
    }

    private Plot findHoveredPlot(Board board) {
        int baseX = board.getBounds().getX();
        int baseY = board.getBounds().getY();
        int cellW = board.getPLOT_WIDTH();
        int cellH = board.getPLOT_HEIGHT();
        int boardW = cellW * board.getCOLS();
        int boardH = cellH * board.getROWS();

        int mouseX = Gdx.input.getX();
        int mouseY = GameContext.get().getSettingsManager().screenSize.getScreenHeight() - Gdx.input.getY();

        if (mouseX < baseX || mouseX >= baseX + boardW) return null;
        if (mouseY < baseY || mouseY >= baseY + boardH) return null;

        int col = (mouseX - baseX) / cellW;
        int row = board.toDisplayRow((mouseY - baseY) / cellH);

        return board.getPlotAtPos(row, col) instanceof Plot p ? p : null;
    }

    private record Outcome(int damage, int resultingHealth, int maxHealth, int x, int y) {}
}
