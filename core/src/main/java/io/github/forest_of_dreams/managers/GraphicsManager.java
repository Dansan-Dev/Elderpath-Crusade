package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.game_objects.PauseScreen;
import io.github.forest_of_dreams.game_objects.SpriteObject;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class GraphicsManager {
    @Getter private final List<Renderable> renderables;
    @Getter private final List<Renderable> uiRenderables;
    private final PauseScreen pauseScreen = new PauseScreen();
    private int maxZ = 0;
    private int minZ = 0;
    @Getter private boolean isPaused = false;

    public GraphicsManager() {
        renderables = new ArrayList<>();
        uiRenderables = new ArrayList<>();
    }

    public void pause() {
        isPaused = true;
        renderables.stream()
            .filter(r -> r instanceof SpriteObject)
            .forEach(
                r -> ((SpriteObject) r).pauseAnimation()
            );
    }

    public void unpause() {
        isPaused = false;
        renderables.stream()
            .filter(r -> r instanceof SpriteObject)
            .forEach(
                r -> ((SpriteObject) r).unpauseAnimation()
            );
    }

    public void render(SpriteBatch batch) {
        renderGameGraphics(batch);
        if (isPaused) pauseScreen.render(batch, 10, isPaused);
    }

    private void renderGameGraphics(SpriteBatch batch) {
        for(int i = minZ; i <= maxZ; i++) {
            for(Renderable r : renderables) {
                if (r instanceof HigherOrderTexture) r.render(batch, i, isPaused, ((HigherOrderTexture) r).getX(), ((HigherOrderTexture) r).getY());
                else r.render(batch, i, isPaused);
            }
        }
    }

    public void updateMinMaxZ() {
        minZ = 0;
        maxZ = 0;
        for (Renderable r : renderables) {
            tryMinMaxZBoundary(r);
        }
    }

    public void addRenderable(Renderable renderable) {
        tryMinMaxZBoundary(renderable);
        renderables.add(renderable);
    }

    public void addRenderables(List<Renderable> renderables) {
        renderables.forEach(this::addRenderable);
    }

    public void removeRenderable(Renderable renderable) {
        renderables.remove(renderable);
    }

    public void removeRenderables(List<Renderable> renderables) {
        renderables.forEach(this::removeRenderable);
    }

    private void tryMinMaxZBoundary(Renderable r) {
        List<Integer> renderableZs = r.getZs();
        int renderableMaxZ = renderableZs.stream()
            .max(Integer::compareTo)
            .orElse(0);
        int renderableMinZ = renderableZs.stream()
            .min(Integer::compareTo)
            .orElse(0);
        if(renderableMaxZ > maxZ) maxZ = renderableMaxZ;
        if(renderableMinZ < minZ) minZ = renderableMinZ;
    }
}
