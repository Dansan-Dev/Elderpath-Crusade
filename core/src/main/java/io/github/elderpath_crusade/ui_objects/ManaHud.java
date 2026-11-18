package io.github.elderpath_crusade.ui_objects;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.supers.LowestOrderTexture;
import io.github.elderpath_crusade.path_loaders.ImagePathIcons;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.IconTextures;
import com.badlogic.gdx.graphics.Texture;

import java.util.function.Supplier;

/**
 * Mana HUD: shows P1 and P2 mana only, on the left. The viewer's own side is always the bottom line and the
 * other side the top line, so it follows the board's perspective: it never swaps while one player keeps the
 * same view (solo, online), and in hotseat it follows the current player exactly as the board does (UI-015).
 */
public class ManaHud extends LowestOrderTexture implements UIRenderable {
    private final Text p1Text;
    private final Text p2Text;
    /** Mana crystal drawn left of each line (null when the art is missing → text only). */
    private final Texture manaIcon;

    private final Supplier<PieceAlignment> viewer;

    /** @param viewer the side this screen is oriented for (BattleRoom.viewerAlignment) — its line goes at the bottom */
    public ManaHud(Supplier<PieceAlignment> viewer) {
        this.viewer = viewer;
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        // Initial positions; will be updated each render to remain anchored
        p1Text = new Text("", FontType.SILKSCREEN, 16, 16, 0, Color.WHITE)
            .withFontSize(FontSize.CAPTION);
        p2Text = new Text("", FontType.SILKSCREEN, 16, screenH - 16, 0, Color.WHITE)
            .withFontSize(FontSize.CAPTION);
        manaIcon = IconTextures.get(ImagePathIcons.MANA);
        setBounds(new Box(0, 0, screenW, screenH));
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        ClientMatchSession session = GameContext.get().getClientMatchSession();
        ClientMatchState state = session != null ? session.viewState() : null;

        String p1Str = "P1 Mana: " + (state == null ? 0 : state.getP1Mana());
        String p2Str = "P2 Mana: " + (state == null ? 0 : state.getP2Mana());

        if (!p1Str.equals(p1Text.getText())) { p1Text.setText(p1Str); p1Text.update(); }
        if (!p2Str.equals(p2Text.getText())) { p2Text.setText(p2Str); p2Text.update(); }

        int paddingX = 16; // moved further right from 16 to 48
        int paddingY = 50;
        
        // The viewer's own line is the bottom one; with P2 as viewer the two lines trade places.
        boolean swapPositions = viewer.get() == PieceAlignment.P2;

        int iconSize = Math.round(p1Text.getHeight() * 1.5f);
        int textX = manaIcon == null ? paddingX : paddingX + iconSize + 6;
        int p1x = textX;
        int p2x = textX;
        int p1y, p2y;
        
        if (swapPositions) {
            // P2 is the viewer: P2 at the bottom, P1 at the top
            p2y = screenH/2 - paddingY - p2Text.getHeight();
            p1y = screenH/2 + paddingY;
        } else {
            // Normal positions (P1 at bottom, P2 at top)
            p1y = screenH/2 - paddingY - p1Text.getHeight();
            p2y = screenH/2 + paddingY;
        }
        
        p2Text.setBounds(new Box(p2x, p2y, p2Text.getWidth(), p2Text.getHeight()));
        p1Text.setBounds(new Box(p1x, p1y, p1Text.getWidth(), p1Text.getHeight()));

        // Render
        if (manaIcon != null) {
            batch.draw(manaIcon, paddingX, p2y + (p2Text.getHeight() - iconSize) / 2f, iconSize, iconSize);
            batch.draw(manaIcon, paddingX, p1y + (p1Text.getHeight() - iconSize) / 2f, iconSize, iconSize);
        }
        p2Text.render(batch, 0, isPaused);
        p1Text.render(batch, 0, isPaused);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        renderUI(batch, isPaused);
    }
}
