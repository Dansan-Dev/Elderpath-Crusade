package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.game_objects.cards.Hand;
import io.github.forest_of_dreams.game_objects.pause.PauseScreen;
import io.github.forest_of_dreams.game_objects.sprites.SpriteObject;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.supers.HigherOrderUI;
import io.github.forest_of_dreams.utils.ClickableRegistryUtil;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

// When you update higher order textures, you need to update the ZIndexRegistry as well

public class GraphicsManager {
    @Getter private static final List<Renderable> renderables = new ArrayList<>();;
    @Getter private static final List<UIRenderable> uiRenderables = new ArrayList<>();;
    @Getter private static boolean isPaused = false;
    @Getter private static SpriteBatch batch = new SpriteBatch();

    public static void pause() {
        isPaused = true;
        renderables.stream()
            .filter(r -> r instanceof SpriteObject)
            .forEach(
                r -> ((SpriteObject) r).pauseAnimation()
            );
    }

    public static void unpause() {
        isPaused = false;
        renderables.stream()
            .filter(r -> r instanceof SpriteObject)
            .forEach(
                r -> ((SpriteObject) r).unpauseAnimation()
            );
    }

    public static void render(SpriteBatch batch) {
        renderGameGraphics(batch);
        renderUI(batch);
    }

    public static void renderUI(SpriteBatch batch) {
        // Iterate over a snapshot to avoid ConcurrentModificationException if UI mutates during render
        List<UIRenderable> snapshot = new ArrayList<>(uiRenderables);
        snapshot.forEach(r -> r.renderUI(batch, isPaused));
    }

    public static void renderPauseUI(SpriteBatch batch) {
        if (!isPaused) return;
        PauseScreen.get().renderUI(batch, false);
    }

    private static void renderGameGraphics(SpriteBatch batch) {
        for (Integer z : ZIndexRegistry.getZLevels()) {
            List<Renderable> bucket = ZIndexRegistry.getBucket(z);
            if (bucket == null) continue;
            for (Renderable r : bucket) {
                if (r instanceof HigherOrderTexture hot) {
                    r.render(batch, z, isPaused, hot.getX(), hot.getY());
                } else {
                    r.render(batch, z, isPaused);
                }
            }
        }
    }

    public static void addRenderable(Renderable renderable) {
        renderables.add(renderable);
        ZIndexRegistry.add(renderable);
        if (renderable instanceof Clickable clickable) {
            InteractionManager.addClickable(clickable);
        } else if (renderable instanceof HigherOrderTexture higherOrderTexture) {
            ClickableRegistryUtil.sendClickables(higherOrderTexture);
        }
    }

    public static void addRenderables(List<Renderable> renderables) {
        renderables.forEach(GraphicsManager::addRenderable);
    }

    public static void addUIRenderable(UIRenderable renderable) {
        uiRenderables.add(renderable);
        if (renderable instanceof Clickable clickable) {
            InteractionManager.addClickable(clickable);
        } else if (renderable instanceof HigherOrderUI higherOrderUI) {
            ClickableRegistryUtil.sendUIClickables(higherOrderUI);
        }
    }

    public static void removeRenderable(Renderable renderable) {
        renderables.remove(renderable);
        ZIndexRegistry.remove(renderable);
        if (renderable instanceof Clickable clickable) {
            InteractionManager.removeClickable(clickable);
        } else if (renderable instanceof HigherOrderTexture higherOrderTexture) {
            // Retract nested clickables that were sent on add
            ClickableRegistryUtil.retractClickables(higherOrderTexture);
        }
    }

    public static void removeRenderables(List<Renderable> renderables) {
        renderables.forEach(GraphicsManager::removeRenderable);
    }

    public static void removeUIRenderable(UIRenderable renderable) {
        uiRenderables.remove(renderable);
        if (renderable instanceof Clickable clickable) {
            InteractionManager.removeClickable(clickable);
        } else if (renderable instanceof HigherOrderUI higherOrderUI) {
            // Retract nested clickables that were sent on add
            ClickableRegistryUtil.retractUIClickables(higherOrderUI);
        }
    }

    public static void removeUIRenderables(List<UIRenderable> renderables) {
        renderables.forEach(GraphicsManager::removeUIRenderable);
    }

    public static void clearRenderables() {
        renderables.forEach(r -> {
            if (r instanceof Clickable clickable) {
                InteractionManager.removeClickable(clickable);
            } else if (r instanceof HigherOrderTexture higherOrderTexture) {
                // Retract nested clickables for containers
                ClickableRegistryUtil.retractClickables(higherOrderTexture);
            }
        });
        renderables.clear();
        ZIndexRegistry.clear();
    }

    public static void  clearUIRenderables() {
        uiRenderables.forEach(r -> {
            if (r instanceof Clickable clickable) {
                InteractionManager.removeClickable(clickable);
            } else if (r instanceof HigherOrderUI higherOrderUI) {
                // Retract nested clickables for UI containers
                ClickableRegistryUtil.retractUIClickables(higherOrderUI);
            }
        });
        uiRenderables.clear();
    }

    public static void draw(SpriteBatch batch) {
        RenderPipeline.draw(batch);
    }

    public static void blurredDraw(SpriteBatch batch) {
        RenderPipeline.blurredDraw(batch);
    }

    public static void drawPauseUI(SpriteBatch batch) {
        RenderPipeline.drawPauseUI(batch);
    }
}
