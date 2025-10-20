package io.github.forest_of_dreams.game_objects.board;
import com.badlogic.gdx.graphics.Color;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.game_objects.sprites.TextureObject;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.CustomBox;
import io.github.forest_of_dreams.interfaces.OnClick;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.utils.ColorSettings;

import java.util.HashMap;
import java.util.List;

/**
 * A plot is a single square on a Board
 * Contains decor such as plot, plotDirt, and plotDecorFront and plotDecorBack
 * Handles onClick events
 */
public class Plot extends HigherOrderTexture implements Clickable {
    private TextureObject plotDecorFront;
    private TextureObject plotDecorBack;
    private TextureObject plot;
    private TextureObject plotDirt;

    private OnClick onClick = null;
    private ClickableEffectData clickableEffectData = null;

    public Plot(int x, int y, int width, int height) {
        plot = new TextureObject(ColorSettings.PLOT_GREEN.getColor(), 0, 0, width, height);
        Color hoverColor = plot.getColor().cpy().lerp(Color.BLACK, 0.5f);
        Color clickColor = plot.getColor().cpy().lerp(Color.WHITE, 0.5f);
        plot.setHoverColor(hoverColor);
        plot.setClickColor(clickColor);
        plotDirt = new TextureObject(ColorSettings.PLOT_DIRT_BROWN.getColor(), 0, -(height/2), width, height/2);
        setBounds(new Box(x, y, plot.getWidth(), plot.getHeight()));
        plotDecorFront = EmptyTexture.get(x, y, getWidth(), getHeight());
        plotDecorBack = EmptyTexture.get(x, y, getWidth(), getHeight());
        plotConstruction(plot, plotDirt);
    }

    public Plot withPlotColor(Color color) {
        plot.setColor(color);
        return this;
    }

    private void plotConstruction(TextureObject plot, TextureObject plotDirt) {
        int width = getWidth();
        int height = getHeight();
        int x = getX();
        int y = getY();

        plotDirt.setZ(-1);
        plot.setZ(0);
        plotDecorBack.setZ(2);
        plotDecorFront.setZ(3);

        this.plot = plot;
        this.plotDirt = plotDirt;
        setRenderables(List.of(plotDecorFront, plotDecorBack, plot, plotDirt));

        plot.setParent(new Box(x, y, width, height));
        plotDirt.setParent(new Box(x, y, width, height));
        plotDecorFront.setParent(new Box(x, y, width, height));
        plotDecorBack.setParent(new Box(x, y + height/2, width, height*2));
    }

    @Override
    public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) {
        this.onClick = onClick;
        this.clickableEffectData = effectData;
    }

    @Override
    public ClickableEffectData getClickableEffectData() {
        return clickableEffectData;
    }

    @Override
    public void triggerClickEffect(HashMap<Integer, CustomBox> interactionEntities) {
        if (this.onClick == null) return;
        onClick.run(interactionEntities);
    }
}
