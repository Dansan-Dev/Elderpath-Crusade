package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.lobby.LobbyClient;
import io.github.elderpath_crusade.lobby.LobbyConnection;
import io.github.elderpath_crusade.lobby.LobbyServerMessage.LobbyView;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The list of open lobbies on the server (refreshed every few seconds and on demand). Click a
 * lobby to join it: an open one goes straight to the deck choice (Draft or Collection) and in; a
 * password-protected one first asks for the password on its own screen ({@link LobbyPasswordRoom}, ONLINE-002).
 * Full lobbies are not listed.
 */
public class BrowseLobbiesRoom extends Room {
    private static final int ROWS = 8;
    private static final int ROW_SPACING = 40;
    private static final float REFRESH_SECONDS = 3f;

    private final Text header;
    private final Text connectionStatus;
    private final Text status;
    private final Text[] rows = new Text[ROWS];
    private final String[] rowLobbyIds = new String[ROWS];
    private final String[] rowLobbyNames = new String[ROWS];
    private final boolean[] rowNeedsPassword = new boolean[ROWS];
    private final Button refreshButton;
    private final Button backButton;
    private float sinceRefresh = REFRESH_SECONDS;
    private List<LobbyView> shown = List.of();

    private BrowseLobbiesRoom() {
        super();
        GameContext.get().getInputManager().stopTextEditing();

        header = OnlineUi.line("Open Lobbies", FontSize.TITLE_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        connectionStatus = OnlineUi.line(" ", FontSize.BODY_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        status = OnlineUi.line(" ", FontSize.BODY_MEDIUM, ColorSettings.TEXT_WARNING.getColor());
        refreshButton = OnlineUi.button("Refresh", 160, () -> sinceRefresh = REFRESH_SECONDS);
        backButton = OnlineUi.button("Back", 120, () -> GameContext.get().getRoomManager().gotoRoom(OnlineMenuRoom::get));

        addContent(header);
        addContent(connectionStatus);
        addContent(status);
        addContent(refreshButton);
        addContent(backButton);
        for (int i = 0; i < ROWS; i++) {
            final int index = i;
            rows[i] = OnlineUi.line(" ", FontSize.BODY_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor())
                    .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                    .withOnClick((e) -> rowClicked(index), ClickableEffectData.getImmediate());
            addContent(rows[i]);
        }
        addContent(new Ticker(this::tick));
        layoutContents();
    }

    private void tick(float delta) {
        LobbyConnection connection = GameContext.get().getLobbyConnection();
        connection.poll();
        LobbyClient client = connection.getClient();
        String text = switch (connection.getPhase()) {
            case CONNECTING -> "Connecting to the server...";
            case READY -> client != null && client.isWelcomed() ? "Connected" : "Saying hello...";
            case FAILED -> connection.getFailure();
            default -> "Not connected";
        };
        OnlineUi.showUnderHeader(connectionStatus, text, header);
        if (client == null || !client.isWelcomed()) return;

        sinceRefresh += delta;
        if (sinceRefresh >= REFRESH_SECONDS) {
            sinceRefresh = 0f;
            client.requestList();
        }
        List<LobbyView> lobbies = client.getLobbies();
        if (!lobbies.equals(shown)) {
            shown = lobbies;
            showRows(lobbies);
        }
    }

    private void showRows(List<LobbyView> lobbies) {
        List<LobbyView> joinable = new ArrayList<>();
        for (LobbyView lobby : lobbies) if (lobby.members().size() < 2) joinable.add(lobby);
        for (int i = 0; i < ROWS; i++) {
            if (i < joinable.size()) {
                LobbyView lobby = joinable.get(i);
                rowLobbyIds[i] = lobby.id();
                rowLobbyNames[i] = lobby.name();
                rowNeedsPassword[i] = lobby.passwordProtected();
                String host = lobby.members().isEmpty() ? "?" : lobby.members().get(0);
                OnlineUi.show(rows[i], lobby.name() + "  -  host " + host + (lobby.passwordProtected() ? "  [password]" : ""),
                        OnlineUi.centerY() + 150 - i * ROW_SPACING);
            } else {
                rowLobbyIds[i] = null;
                OnlineUi.show(rows[i], i == 0 && joinable.isEmpty() ? "No open lobbies right now" : " ",
                        OnlineUi.centerY() + 150 - i * ROW_SPACING);
            }
        }
    }

    private void rowClicked(int index) {
        String id = rowLobbyIds[index];
        if (id == null) return;
        String name = rowLobbyNames[index];
        if (rowNeedsPassword[index]) {
            GameContext.get().getRoomManager().gotoRoom(() -> LobbyPasswordRoom.get(id, name));
            return;
        }
        GameContext.get().getRoomManager().gotoRoom(() -> DeckSourceSelectRoom.get(PieceAlignment.P1, false,
                deck -> GameContext.get().getRoomManager().gotoRoom(() -> LobbyRoom.joining(id, "", deck, name))));
    }

    private void layoutContents() {
        int cy = OnlineUi.centerY();
        MenuLayout.centerHeader(header, 100);
        OnlineUi.showUnderHeader(connectionStatus, connectionStatus.getText(), header);
        showRows(shown);
        OnlineUi.show(status, status.getText(), cy - 230);
        OnlineUi.place(refreshButton, cy - 300);
        backButton.getBounds().setX(OnlineUi.centerX() + 110);
        backButton.getBounds().setY(cy - 300);
        refreshButton.getBounds().setX(OnlineUi.centerX() - 110 - refreshButton.getBounds().getWidth());
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get() {
        return new BrowseLobbiesRoom();
    }
}
