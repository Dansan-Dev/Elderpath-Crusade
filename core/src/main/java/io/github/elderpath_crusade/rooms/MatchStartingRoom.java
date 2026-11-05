package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.Updatable;
import io.github.elderpath_crusade.supers.LowestOrderTexture;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shown while a {@link ClientMatchSession} starts (or connects) and while a host waits for its
 * guest — the "loading screen while the server starts" from ARCH-028. Starts the session once,
 * polls it every frame via {@link ClientMatchSession#poll()}, and transitions to the real battle
 * room once play can begin, or back to the main menu if starting/connecting failed outright.
 */
public class MatchStartingRoom extends Room {
    private static final int HEADER_TOP_OFFSET = 120;

    private final Text label;

    private MatchStartingRoom(Supplier<ClientMatchSession> starter, Function<ClientMatchSession, Room> onReady) {
        super();

        label = new Text("Starting match...", FontType.SILKSCREEN, 0, 0, 0, Color.WHITE)
                .withFontSize(FontSize.BODY_LARGE);
        addContent(label);
        MenuLayout.centerHeader(label, HEADER_TOP_OFFSET);

        ClientMatchSession session = starter.get();
        addContent(new SessionPoller(session, onReady, label));
    }

    /** Takes over the current room, starts the session, and transitions to onReady(session) once ready. */
    public static Room get(Supplier<ClientMatchSession> starter, Function<ClientMatchSession, Room> onReady) {
        return new MatchStartingRoom(starter, onReady);
    }

    @Override
    public void onScreenResize() {
        MenuLayout.centerHeader(label, HEADER_TOP_OFFSET);
    }

    /** Renders nothing — exists only to receive per-frame update() calls from GraphicsManager. */
    private static class SessionPoller extends LowestOrderTexture implements Renderable, Updatable {
        private final ClientMatchSession session;
        private final Function<ClientMatchSession, Room> onReady;
        private final Text label;
        private boolean transitioning = false;

        SessionPoller(ClientMatchSession session, Function<ClientMatchSession, Room> onReady, Text label) {
            this.session = session;
            this.onReady = onReady;
            this.label = label;
            setBounds(new Box(0, 0, 0, 0));
        }

        @Override
        public List<Integer> getZs() {
            return List.of();
        }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        }

        @Override
        public void update(float delta) {
            if (transitioning) return;
            session.poll();
            ClientMatchSession.Status status = session.getStatus();
            label.setText(statusText(status));
            MenuLayout.centerHeader(label, HEADER_TOP_OFFSET);

            if (status.isOver()) {
                transitioning = true;
                session.close();
                GameContext.get().getRoomManager().gotoRoom(MainMenuRoom::get);
                return;
            }
            if (status.isReady()) {
                transitioning = true;
                GameContext.get().getRoomManager().gotoRoom(() -> onReady.apply(session));
            }
        }

        private String statusText(ClientMatchSession.Status status) {
            return switch (status.phase()) {
                case STARTING_SERVER -> "Starting match server...";
                case CONNECTING -> "Connecting...";
                case WAITING_FOR_PLAYERS -> "Waiting for opponent...";
                default -> "Starting match...";
            };
        }
    }
}
