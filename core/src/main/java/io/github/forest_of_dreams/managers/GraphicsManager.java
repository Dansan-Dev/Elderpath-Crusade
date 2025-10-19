package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.forest_of_dreams.data_objects.GamePiece;
import io.github.forest_of_dreams.game_objects.Board;
import io.github.forest_of_dreams.game_objects.TextureObject;
import io.github.forest_of_dreams.game_objects.pause.PauseScreen;
import io.github.forest_of_dreams.game_objects.SpriteObject;
import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.supers.HigherOrderUI;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class GraphicsManager {
    @Getter private static final List<Renderable> renderables = new ArrayList<>();;
    @Getter private static final List<UIRenderable> uiRenderables = new ArrayList<>();;
    private static int maxZ = 0;
    private static int minZ = 0;
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
        uiRenderables.forEach(r -> r.renderUI(batch, isPaused));
    }

    public static void renderPauseUI(SpriteBatch batch) {
        if (!isPaused) return;
        PauseScreen.get().renderUI(batch, false);
    }

    private static void renderGameGraphics(SpriteBatch batch) {
        for(int i = minZ; i <= maxZ; i++) {
            for(Renderable r : renderables) {
                if (r instanceof HigherOrderTexture) {
                    r.render(batch, i, isPaused, ((HigherOrderTexture) r).getX(), ((HigherOrderTexture) r).getY());
                } else {
                    r.render(batch, i, isPaused);
                }
            }
        }
    }

    public static void addRenderable(Renderable renderable) {
        tryMinMaxZBoundary(renderable);
        renderables.add(renderable);
        if (renderable instanceof Clickable clickable) {
            InteractionManager.addClickable(clickable);
        } else if (renderable instanceof HigherOrderTexture higherOrderTexture) {
            sendClickables(higherOrderTexture);
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
            sendUIClickables(higherOrderUI);
        }
    }

    public static void removeRenderable(Renderable renderable) {
        renderables.remove(renderable);
        if (renderable instanceof Clickable clickable)
            InteractionManager.removeClickable(clickable);
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
            retractUIClickables(higherOrderUI);
        }
    }

    public static void removeUIRenderables(List<UIRenderable> renderables) {
        renderables.forEach(GraphicsManager::removeUIRenderable);
    }

    public static void clearRenderables() {
        renderables.forEach(r -> {
            if (r instanceof Clickable clickable)
                InteractionManager.removeClickable(clickable);
        });
        renderables.clear();
    }

    public static void  clearUIRenderables() {
        uiRenderables.forEach(r -> {
            if (r instanceof Clickable clickable) {
                InteractionManager.removeClickable(clickable);
            } else if (r instanceof HigherOrderUI higherOrderUI) {
                // Retract nested clickables for UI containers
                retractUIClickables(higherOrderUI);
            }
        });
        uiRenderables.clear();
    }

    private static void tryMinMaxZBoundary(Renderable r) {
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

    public static void sendClickables(HigherOrderTexture texture) {
        texture.getRenderables().forEach(r -> {
            if (r instanceof GamePiece gamePiece) {
                System.out.println("GAMEPIECE ADDED TO CLICKABLES");
            }
            if (r instanceof Clickable clickable) {
                InteractionManager.addClickable(clickable);
            }
            else if (r instanceof HigherOrderTexture higherOrderTexture) sendClickables(higherOrderTexture);
        });
    }

    public static void retractClickables(HigherOrderTexture texture) {
        texture.getRenderables().forEach(r -> {
            if (r instanceof Clickable clickable) InteractionManager.removeClickable(clickable);
            else if (r instanceof HigherOrderTexture higherOrderTexture) retractClickables(higherOrderTexture);
        });
    }

    public static void sendUIClickables(HigherOrderUI ui) {
        ui.getRenderableUIs().forEach(r -> {
            if (r instanceof Clickable clickable) InteractionManager.addClickable(clickable);
            else if (r instanceof HigherOrderUI higherOrderUI) sendUIClickables(higherOrderUI);
        });
    }

    public static void retractUIClickables(HigherOrderUI ui) {
        ui.getRenderableUIs().forEach(r -> {
            if (r instanceof Clickable clickable) InteractionManager.removeClickable(clickable);
            else if (r instanceof HigherOrderUI higherOrderUI) retractUIClickables(higherOrderUI);
        });
    }

    public static void draw(SpriteBatch batch) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(SettingsManager.screenSize.getViewport().getCamera().combined);
        batch.begin();
        batch.setShader(null);
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        GraphicsManager.render(batch);
        batch.end();
    }

    public static void blurredDraw(SpriteBatch batch) {
        ShaderProgram blurShader = ShaderManager.getBlurShader();
        FrameBuffer fboA = ShaderManager.getFboA();
        FrameBuffer fboB = ShaderManager.getFboB();

        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();

        // First pass - capture the scene into fboA
        fboA.begin();
        draw(batch);
        fboA.end();

        // Prepare projection for post-process passes
        batch.setProjectionMatrix(SettingsManager.screenSize.getViewport().getCamera().combined);

        // Horizontal blur into fboB
        fboB.begin();
        batch.begin();
        batch.setShader(blurShader);
        blurShader.setUniformf("u_blurSize", 1f / (float) screenW);
        blurShader.setUniformf("u_direction", 1f, 0f);
        // Flip Y when drawing FrameBuffer texture
        batch.draw(
            fboA.getColorBufferTexture(),
            0, 0,
            screenW, screenH,
            0f, 1f, 1f, 0f
        );
        batch.end();
        fboB.end();

        // Vertical blur (final pass) from fboB to screen
        batch.begin();
        blurShader.setUniformf("u_blurSize", 1f / (float) screenH);
        blurShader.setUniformf("u_direction", 0f, 1f);
        batch.draw(
            fboB.getColorBufferTexture(),
            0, 0,
            screenW, screenH,
            0f, 1f, 1f, 0f
        );
        batch.end();

        batch.setShader(null);
    }

    public static void drawPauseUI(SpriteBatch batch) {
        batch.begin();
        GraphicsManager.renderPauseUI(batch);
        batch.end();
    }
}
