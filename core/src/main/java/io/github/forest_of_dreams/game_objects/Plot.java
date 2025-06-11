package io.github.forest_of_dreams.game_objects;
import com.badlogic.gdx.graphics.Color;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.supers.HigherOrderTexture;

import java.util.List;

public class Plot extends HigherOrderTexture {
    private TextureObject plotDecorFront;
    private TextureObject plotDecorBack;
    private TextureObject plot;
    private TextureObject plotDirt;

    public Plot(int x, int y, TextureObject plot, TextureObject plotDirt) {
        setBounds(new Box(x, y, plot.getWidth(), plot.getHeight()));
        int width = getBounds().getWidth();
        int height = getBounds().getHeight();
        plotDecorFront = EmptyTexture.get(x, y, width, height);
        plotDecorBack = EmptyTexture.get(x, y, width, height);
        plotConstruction(plot, plotDirt);
    }

    public Plot(int x, int y, TextureObject plot, TextureObject plotDirt, TextureObject plotDecorFront, TextureObject plotDecorBack) {
        setBounds(new Box(x, y, plot.getWidth(), plot.getHeight()));
        this.plotDecorFront = plotDecorFront;
        this.plotDecorBack = plotDecorBack;
        plotConstruction(plot, plotDirt);
    }

    public Plot(int x, int y) {
        plot = new TextureObject(Color.valueOf("#32943a"), 0, 0, 60, 60);
        plotDirt = new TextureObject(Color.BROWN, 0, -30, 60, 30);
        setBounds(new Box(x, y, plot.getWidth(), plot.getHeight()));
        plotDecorFront = EmptyTexture.get(x, y, getWidth(), getHeight());
        plotDecorBack = EmptyTexture.get(x, y, getWidth(), getHeight());
        plotConstruction(plot, plotDirt);
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
}
