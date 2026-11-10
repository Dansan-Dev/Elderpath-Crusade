package io.github.elderpath_crusade.ui_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.audio.MusicManager;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.supers.LowestOrderTexture;
import io.github.elderpath_crusade.utils.FontSize;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * F3-toggleable in-game diagnostics overlay (Sprint 64) — FPS/frame time, JVM heap, cumulative
 * GC count/time, and the music system's current state. The music line exists specifically
 * because the user has seen music "lag"/stop outright with no way to tell what it was doing
 * when that happened — see {@code MusicManager}'s {@code MusicEvent} tracking.
 *
 * <p>Always registered as a global UI overlay ({@code RoomState.enter()}, same as {@link
 * SelectionOverlay}/{@code HighlightManager}) so it survives every room transition; gated
 * internally on {@code SettingsManager.debug.showPerfOverlay} (toggled by F3, see {@code
 * HandleToggleDiagnostics}) so it costs nothing while hidden. No GL-level stats (draw calls,
 * texture binds) in this pass — see {@code elderpath-architecture.md}'s "Diagnostics" section
 * for why that's left for later rather than guessed at blind in a sandbox with no GL context.
 */
public class DiagnosticsOverlay extends LowestOrderTexture implements UIRenderable {
    private final Text text;

    public DiagnosticsOverlay() {
        this.text = new Text("", FontType.SILKSCREEN, 16, 0, 0, Color.WHITE).withFontSize(FontSize.CAPTION);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        if (!GameContext.get().getSettingsManager().debug.showPerfOverlay) return;

        String content = buildContent();
        if (!content.equals(text.getText())) {
            text.setText(content);
            text.update();
        }

        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        text.setBounds(new Box(16, screenH - 16 - text.getHeight(), text.getWidth(), text.getHeight()));
        text.render(batch, 0, isPaused);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        renderUI(batch, isPaused);
    }

    private String buildContent() {
        StringBuilder out = new StringBuilder();
        out.append("FPS: ").append(Gdx.graphics.getFramesPerSecond())
                .append("  frame: ").append(String.format("%.1f", Gdx.graphics.getDeltaTime() * 1000f)).append("ms");

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        out.append("\nHeap: ").append(usedMb).append("/").append(maxMb).append(" MB");

        long gcCount = 0;
        long gcTimeMs = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = bean.getCollectionCount();
            long time = bean.getCollectionTime();
            if (count > 0) gcCount += count;
            if (time > 0) gcTimeMs += time;
        }
        out.append("\nGC: ").append(gcCount).append(" collections, ").append(gcTimeMs).append("ms total");

        MusicManager music = GameContext.get().getMusicManager();
        String path = music.currentMusicPath();
        out.append("\nMusic: ").append(path == null ? "(none)" : path)
                .append(music.isPlaying() ? " playing" : " stopped")
                .append(music.isFading() ? " (fading)" : "")
                .append(", vol ").append(String.format("%.2f", music.lastAppliedVolume()));
        long sinceEventMs = System.currentTimeMillis() - music.lastEventAtMillis();
        out.append("\n  last event: ").append(music.lastEvent()).append(" (").append(sinceEventMs).append("ms ago)");

        return out.toString();
    }
}
