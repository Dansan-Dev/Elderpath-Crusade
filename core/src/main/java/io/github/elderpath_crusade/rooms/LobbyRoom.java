package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.lobby.LobbyClient;
import io.github.elderpath_crusade.lobby.LobbyConnection;
import io.github.elderpath_crusade.lobby.LobbyServerMessage.LobbyView;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;

/**
 * Inside a lobby: who is here, and — for the host — a Start button once a second player has
 * joined. The room asks the server to create or join the lobby when it opens, shows whatever the
 * server answers (including refusals such as a wrong password), and when the host starts the match
 * hands this connection to a {@link ClientMatchSession} and moves into the battle.
 */
public class LobbyRoom extends Room {
    private enum Mode { HOST, JOIN }

    private final Mode mode;
    private final String lobbyIdOrName;
    private final String password;
    private final List<String> deck;

    private final Text header;
    private final Text hostLine;
    private final Text guestLine;
    private final Text status;
    private final Button startButton;
    private final Button leaveButton;
    private boolean requested;
    private boolean transitioning;

    private LobbyRoom(Mode mode, String lobbyIdOrName, String password, List<String> deck) {
        super();
        GameContext.get().getInputManager().stopTextEditing();
        this.mode = mode;
        this.lobbyIdOrName = lobbyIdOrName;
        this.password = password;
        this.deck = List.copyOf(deck);

        header = OnlineUi.line(mode == Mode.HOST ? lobbyIdOrName : "Lobby", FontSize.TITLE_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        hostLine = OnlineUi.line(" ", FontSize.BODY_LARGE, ColorSettings.TEXT_DEFAULT.getColor());
        guestLine = OnlineUi.line(" ", FontSize.BODY_LARGE, ColorSettings.TEXT_DEFAULT.getColor());
        status = OnlineUi.line(" ", FontSize.BODY_MEDIUM, ColorSettings.TEXT_WARNING.getColor());
        startButton = OnlineUi.button("Start Match", 260, this::startClicked);
        leaveButton = OnlineUi.button("Leave", 160, this::leave);

        addContent(header);
        addContent(hostLine);
        addContent(guestLine);
        addContent(status);
        addContent(startButton);
        addContent(leaveButton);
        addContent(new Ticker(delta -> tick()));
        startButton.setDisabled(true);
        layoutContents();
    }

    public static Room hosting(String lobbyName, String password, List<String> deck) {
        return new LobbyRoom(Mode.HOST, lobbyName, password, deck);
    }

    public static Room joining(String lobbyId, String password, List<String> deck) {
        return new LobbyRoom(Mode.JOIN, lobbyId, password, deck);
    }

    private void tick() {
        if (transitioning) return;
        LobbyConnection connection = GameContext.get().getLobbyConnection();
        connection.poll();
        LobbyClient client = connection.getClient();
        if (client == null) {
            OnlineUi.show(status, connection.getPhase() == LobbyConnection.Phase.FAILED
                    ? connection.getFailure() : "Not connected to the server", statusY());
            startButton.setDisabled(true);
            return;
        }
        if (!client.isWelcomed()) return;
        if (!requested) {
            requested = true;
            if (mode == Mode.HOST) client.createLobby(lobbyIdOrName, password, deck);
            else client.joinLobby(lobbyIdOrName, password, deck);
        }

        if (client.getMatchSeat() != null) {
            startMatch(connection, client);
            return;
        }
        LobbyView lobby = client.getLobby();
        if (lobby == null) {
            String message = client.getClosedReason() != null ? "The lobby closed: " + client.getClosedReason()
                    : client.getLastError() != null ? client.getLastError().message() : "Entering the lobby...";
            OnlineUi.show(status, message, statusY());
            OnlineUi.show(hostLine, " ", OnlineUi.centerY() + 60);
            OnlineUi.show(guestLine, " ", OnlineUi.centerY());
            startButton.setDisabled(true);
            return;
        }
        OnlineUi.show(header, lobby.name(), headerY());
        header.getBounds().setX(OnlineUi.centerX() - header.getBounds().getWidth() / 2);
        List<String> members = lobby.members();
        OnlineUi.show(hostLine, "Host: " + members.get(0), OnlineUi.centerY() + 60);
        OnlineUi.show(guestLine, members.size() > 1 ? "Guest: " + members.get(1) : "Waiting for a player to join...", OnlineUi.centerY());
        boolean canStart = client.isHost() && members.size() > 1;
        startButton.setDisabled(!canStart);
        OnlineUi.show(status, client.getLastError() != null ? client.getLastError().message()
                : client.isHost() ? (canStart ? "Press Start Match when you are ready" : " ") : "Waiting for the host to start the match...",
                statusY());
    }

    private void startClicked() {
        LobbyClient client = GameContext.get().getLobbyConnection().getClient();
        if (client != null && client.isHost()) client.startMatch();
    }

    /** The server bridged this connection to a match server: it speaks the match protocol from here on. */
    private void startMatch(LobbyConnection connection, LobbyClient client) {
        transitioning = true;
        SeatRequest seat = SeatRequest.valueOf(client.getMatchSeat());
        ClientMatchSession session = ClientMatchSession.joinThrough(client.takeMatchConnection(), seat, connection.getPlayerName());
        connection.release();
        GameContext.get().getRoomManager().gotoRoom(() -> OnlineMatchRoom.through(session));
    }

    private void leave() {
        transitioning = true;
        GameContext.get().getLobbyConnection().close();
        GameContext.get().getRoomManager().gotoRoom(OnlineMenuRoom::get);
    }

    private static int headerY() {
        return GameContext.get().getSettingsManager().screenSize.getScreenHeight() - 100;
    }

    private static int statusY() {
        return OnlineUi.centerY() - 100;
    }

    private void layoutContents() {
        MenuLayout.centerHeader(header, 100);
        OnlineUi.show(hostLine, hostLine.getText(), OnlineUi.centerY() + 60);
        OnlineUi.show(guestLine, guestLine.getText(), OnlineUi.centerY());
        OnlineUi.show(status, status.getText(), statusY());
        OnlineUi.place(startButton, OnlineUi.centerY() - 200);
        OnlineUi.place(leaveButton, OnlineUi.centerY() - 280);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }
}
