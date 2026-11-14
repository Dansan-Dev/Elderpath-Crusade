package io.github.elderpath_crusade.characters.sprites.checker_sprites.__super__;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.game_objects.sprites.SpriteObject;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.path_loaders.ImagePathPieceSprites;
import io.github.elderpath_crusade.path_loaders.ImagePathSpritesAndAnimations;
import io.github.elderpath_crusade.utils.SpriteCreator;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game_objects.sprites.CharacterSprite;

import java.util.ArrayList;
import java.util.List;

public abstract class CheckerSprite extends CharacterSprite {
    /** Share of the checker the piece figure covers (ART-002), centered on it. */
    private static final float FIGURE_SCALE = 0.8f;

    /** The name label — only when the piece has no figure art. */
    private Text text;

    public enum AlignmentColor {
        RED, BLUE;
    }

    public static AlignmentColor getAlignmentColor(PieceAlignment alignment) {
        return switch (alignment) {
            case P1 -> AlignmentColor.BLUE;
            case P2 -> AlignmentColor.RED;
            default -> throw new IllegalArgumentException("Alignment must be to either P1 or P2");
        };
    }

    private void initializeImage(AlignmentColor color) {
        String path = switch (color) {
            case RED -> ImagePathSpritesAndAnimations.RED_CHECKER.getPath();
            case BLUE -> ImagePathSpritesAndAnimations.BLUE_CHECKER.getPath();
        };
        int[] size = {468, 479};
        makeAnimationOfSpriteSheetRow(
            "passive",
            path,
            1,
            0, 0,
            size[0], size[1],
            0
        );
    }

    public CheckerSprite(int x, int y, int width, int height, String name, AlignmentColor color) {
        this(x, y, width, height, name, color, null);
    }

    /**
     * @param figure the piece's figure art drawn on top of the checker, or {@code null} for the
     *               plain checker with {@code name} written on it
     */
    public CheckerSprite(int x, int y, int width, int height, String name, AlignmentColor color,
                         ImagePathPieceSprites figure) {
        super(x, y, width, height);
        initializeImage(color);
        if (figure != null && addFigure(figure, x, y, width, height)) return;
        text = new Text(name, FontType.DEFAULT, 0, 0, 2, Color.WHITE);
        Box newBounds = new Box(
            width/2 - text.getWidth()/2,
            height/2 - text.getHeight()/2,
            width, height
        );
        text.setBounds(newBounds);
    }

    /** Adds the figure layer above the checker (same z, drawn after it); false if its texture is missing. */
    private boolean addFigure(ImagePathPieceSprites figure, int x, int y, int width, int height) {
        int figureWidth = Math.round(width * FIGURE_SCALE);
        int figureHeight = Math.round(height * FIGURE_SCALE);
        Sprite sprite = SpriteCreator.makeSprite(figure.getPath(), 0, 0,
            ImagePathPieceSprites.SIZE, ImagePathPieceSprites.SIZE, figureWidth, figureHeight);
        if (sprite == null) return false;
        SpriteObject layer = new SpriteObject(x, y, width, height, 2, SpriteBoxPos.CENTER);
        layer.addAnimation("passive", List.of(sprite), 0);
        List<Renderable> layers = new ArrayList<>(getRenderables());
        layers.add(layer);
        setRenderables(layers);
        return true;
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        super.render(batch, zLevel, isPaused);
        if (zLevel == 2 && text != null) {
            text.render(batch, zLevel, isPaused);
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        super.render(batch, zLevel, isPaused, x, y);
        if (zLevel == 2 && text != null) {
            text.render(batch, zLevel, isPaused, x + text.getX(), y + text.getY());
        }
    }
}
