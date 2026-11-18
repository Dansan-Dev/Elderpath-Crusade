package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.input.TextEntry;
import io.github.elderpath_crusade.lobby.LobbyConnection;
import io.github.elderpath_crusade.lobby.LobbyDeckSource;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.ui_objects.TextField;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

/**
 * Host a lobby: a name, an optional password and the lobby's deck rule — Draft or Collection (ONLINE-001), which every
 * player of the lobby follows. "Create Lobby" then builds the host's own deck by that rule, and only then is the lobby
 * created on the server, so it never exists without a deck behind it. The connection started by OnlineMenuRoom shows its progress here.
 */
public class HostLobbyRoom extends Room {
    private static final int MAX_LOBBY_NAME = 32;
    private static final int MAX_PASSWORD = 32;

    private final Text header;
    private final Text connectionStatus;
    private final Text nameLabel;
    private final Text passwordLabel;
    private final Text status;
    private final TextField nameField;
    private final TextField passwordField;
    private final Button createButton;
    private final Button deckSourceButton;
    private LobbyDeckSource deckSource = LobbyDeckSource.DRAFT;
    private final Button backButton;

    private HostLobbyRoom() {
        super();
        GameContext.get().getInputManager().stopTextEditing();

        header = OnlineUi.line("Host a Lobby", FontSize.TITLE_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        connectionStatus = OnlineUi.line(" ", FontSize.BODY_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        nameLabel = OnlineUi.line("Lobby name", FontSize.BODY_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        passwordLabel = OnlineUi.line("Password (optional)", FontSize.BODY_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        status = OnlineUi.line(" ", FontSize.BODY_MEDIUM, ColorSettings.TEXT_WARNING.getColor());
        nameField = TextField.create(new TextEntry(MAX_LOBBY_NAME, false), "Lobby name", null,
                0, 0, OnlineUi.FIELD_WIDTH, OnlineUi.FIELD_HEIGHT, 0);
        passwordField = TextField.create(new TextEntry(MAX_PASSWORD, true), "No password", null,
                0, 0, OnlineUi.FIELD_WIDTH, OnlineUi.FIELD_HEIGHT, 0);
        deckSourceButton = OnlineUi.button(deckSourceLabel(), 360, this::toggleDeckSource);
        createButton = OnlineUi.button("Create Lobby", 360, this::createClicked);
        backButton = OnlineUi.button("Back", 120, () -> GameContext.get().getRoomManager().gotoRoom(OnlineMenuRoom::get));

        addContent(header);
        addContent(connectionStatus);
        addContent(nameLabel);
        addContent(passwordLabel);
        addContent(status);
        addContent(nameField);
        addContent(passwordField);
        addContent(deckSourceButton);
        addContent(createButton);
        addContent(backButton);
        addContent(new Ticker(delta -> tick()));
        layoutContents();
    }

    private void tick() {
        LobbyConnection connection = GameContext.get().getLobbyConnection();
        connection.poll();
        String text = switch (connection.getPhase()) {
            case CONNECTING -> "Connecting to the server...";
            case READY -> "Connected";
            case FAILED -> connection.getFailure();
            default -> "Not connected";
        };
        OnlineUi.showUnderHeader(connectionStatus, text, header);
    }

    private String deckSourceLabel() {
        return "Players' decks: " + (deckSource == LobbyDeckSource.DRAFT ? "Draft" : "Collection");
    }

    private void toggleDeckSource() {
        deckSource = deckSource == LobbyDeckSource.DRAFT ? LobbyDeckSource.COLLECTION : LobbyDeckSource.DRAFT;
        deckSourceButton.setLabel(deckSourceLabel());
    }

    private void createClicked() {
        LobbyConnection connection = GameContext.get().getLobbyConnection();
        if (connection.getPhase() != LobbyConnection.Phase.READY) {
            OnlineUi.show(status, "Not connected to the server yet", OnlineUi.centerY() - 160);
            return;
        }
        String lobbyName = nameField.getEntry().getText().strip();
        if (lobbyName.isEmpty()) {
            OnlineUi.show(status, "Give the lobby a name", OnlineUi.centerY() - 160);
            return;
        }
        String password = passwordField.getEntry().getText();
        LobbyDeckSource rule = deckSource;
        GameContext.get().getRoomManager().gotoRoom(() -> DeckSourceSelectRoom.forSource(rule,
                deck -> GameContext.get().getRoomManager().gotoRoom(() -> LobbyRoom.hosting(lobbyName, password, deck, rule)),
                HostLobbyRoom::get));
    }

    private void layoutContents() {
        int cy = OnlineUi.centerY();
        MenuLayout.centerHeader(header, 100);
        OnlineUi.showUnderHeader(connectionStatus, connectionStatus.getText(), header);
        OnlineUi.show(nameLabel, "Lobby name", cy + 190);
        OnlineUi.place(nameField, cy + 130);
        OnlineUi.show(passwordLabel, "Password (optional)", cy + 90);
        OnlineUi.place(passwordField, cy + 30);
        OnlineUi.place(deckSourceButton, cy - 50);
        OnlineUi.place(createButton, cy - 130);
        OnlineUi.show(status, status.getText(), cy - 190);
        OnlineUi.place(backButton, cy - 330);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get() {
        return new HostLobbyRoom();
    }
}
