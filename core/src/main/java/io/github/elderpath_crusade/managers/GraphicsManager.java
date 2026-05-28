package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.game_objects.pause.PauseScreen;
import io.github.elderpath_crusade.game_objects.sprites.SpriteObject;
import io.github.elderpath_crusade.interfaces.Clickable;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.interfaces.Updatable;
import io.github.elderpath_crusade.supers.HigherOrderTexture;
import io.github.elderpath_crusade.supers.HigherOrderUI;
import io.github.elderpath_crusade.utils.ClickableRegistryUtil;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// When you update higher order textures, you need to update the ZIndexRegistry as well

public class GraphicsManager {
    @Getter private static final List<Renderable> renderables = new ArrayList<>();;
    @Getter private static final List<UIRenderable> uiRenderables = new ArrayList<>();;
    @Getter private static SpriteBatch batch = new SpriteBatch();

    // OPT-002: reusable to avoid per-frame allocation
    private static final List<Renderable> updateSnapshot = new ArrayList<>();
    private static final List<UIRenderable> uiUpdateSnapshot = new ArrayList<>();
    private static final List<UIRenderable> uiRenderSnapshot = new ArrayList<>();

    /**
     * Pause all sprite animations. Called by GameManager when the game pauses.
     * Pause state is owned by GameManager — use GameManager.isPaused() to check.
     */
    static void pauseAnimations() {
        renderables.stream()
            .filter(r -> r instanceof SpriteObject)
            .forEach(
                r -> ((SpriteObject) r).pauseAnimation()
            );
    }

    /**
     * Unpause all sprite animations. Called by GameManager when the game unpauses.
     */
    static void unpauseAnimations() {
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

    public static void update(float delta) {
        if (GameManager.isPaused()) return;
        updateSnapshot.clear();
        updateSnapshot.addAll(renderables);
        for (Renderable r : updateSnapshot) {
            if (r instanceof Updatable u) {
                u.update(delta);
            }
        }
        uiUpdateSnapshot.clear();
        uiUpdateSnapshot.addAll(uiRenderables);
        for (UIRenderable r : uiUpdateSnapshot) {
            if (r instanceof Updatable u) {
                u.update(delta);
            }
        }
    }

    public static void renderUI(SpriteBatch batch) {
        uiRenderSnapshot.clear();
        uiRenderSnapshot.addAll(uiRenderables);
        uiRenderSnapshot.forEach(r -> r.renderUI(batch, GameManager.isPaused()));
    }

    public static void renderPauseUI(SpriteBatch batch) {
        if (!GameManager.isPaused()) return;
        PauseScreen.get().renderUI(batch, false);
    }

    private static void renderGameGraphics(SpriteBatch batch) {
        boolean paused = GameManager.isPaused();
        for (Integer z : ZIndexRegistry.getZLevels()) {
            Collection<Renderable> bucket = ZIndexRegistry.getBucket(z);
            if (bucket == null) continue;
            for (Renderable r : bucket) {
                if (r instanceof HigherOrderTexture hot) {
                    r.render(batch, z, paused, hot.getX(), hot.getY());
                } else {
                    r.render(batch, z, paused);
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
