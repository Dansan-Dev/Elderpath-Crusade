package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.input.TextEntry;
import io.github.elderpath_crusade.lobby.LobbyConnection;
import io.github.elderpath_crusade.lobby.LobbyEndpoint;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.ui_objects.TextField;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.function.Supplier;

/**
 * Entry to online play on the dedicated server: the player's name and the server address
 * (remembered for this session), then "Host a Lobby" or "Browse Lobbies". Pressing either starts
 * connecting to the lobby service — the next screen shows the progress — so a typo in the address
 * is caught here, before anything else happens.
 */
public class OnlineMenuRoom extends Room {
    private static final int MAX_NAME = 24;
    private static final int MAX_ADDRESS = 60;

    private final Text header;
    private final Text nameLabel;
    private final Text serverLabel;
    private final Text status;
    private final TextField nameField;
    private final TextField serverField;
    private final Button hostButton;
    private final Button browseButton;
    private final Button backButton;

    private OnlineMenuRoom() {
        super();
        GameContext.get().getInputManager().stopTextEditing();
        LobbyConnection connection = GameContext.get().getLobbyConnection();
        connection.close();

        header = OnlineUi.line("Play Online", FontSize.TITLE_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        nameLabel = OnlineUi.line("Your name", FontSize.BODY_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        serverLabel = OnlineUi.line("Server", FontSize.BODY_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        status = OnlineUi.line(" ", FontSize.BODY_MEDIUM, ColorSettings.TEXT_WARNING.getColor());
        nameField = TextField.create(new TextEntry(connection.getPlayerName(), MAX_NAME, false), "Your name", null,
                0, 0, OnlineUi.FIELD_WIDTH, OnlineUi.FIELD_HEIGHT, 0);
        serverField = TextField.create(new TextEntry(connection.getServerAddress(), MAX_ADDRESS, false), "Server address", null,
                0, 0, OnlineUi.FIELD_WIDTH, OnlineUi.FIELD_HEIGHT, 0);
        hostButton = OnlineUi.button("Host a Lobby", OnlineUi.BUTTON_WIDTH, () -> connectThen(HostLobbyRoom::get));
        browseButton = OnlineUi.button("Browse Lobbies", OnlineUi.BUTTON_WIDTH, () -> connectThen(BrowseLobbiesRoom::get));
        backButton = OnlineUi.button("Back", 120, () -> GameContext.get().getRoomManager().gotoRoom(GameModeRoom::get));

        addContent(header);
        addContent(nameLabel);
        addContent(serverLabel);
        addContent(status);
        addContent(nameField);
        addContent(serverField);
        addContent(hostButton);
        addContent(browseButton);
        addContent(backButton);
        layoutContents();
    }

    private void connectThen(Supplier<Room> next) {
        String name = nameField.getEntry().getText().strip();
        if (name.isEmpty()) {
            OnlineUi.show(status, "Enter your name", statusY());
            return;
        }
        String address = serverField.getEntry().getText();
        LobbyEndpoint endpoint;
        try {
            endpoint = LobbyEndpoint.parse(address);
        } catch (IllegalArgumentException e) {
            OnlineUi.show(status, e.getMessage(), statusY());
            return;
        }
        LobbyConnection connection = GameContext.get().getLobbyConnection();
        connection.setPlayerName(name);
        connection.setServerAddress(address.strip());
        connection.connect(endpoint);
        GameContext.get().getRoomManager().gotoRoom(next);
    }

    private static int statusY() {
        return OnlineUi.centerY() - 250;
    }

    private void layoutContents() {
        int cy = OnlineUi.centerY();
        MenuLayout.centerHeader(header, 100);
        OnlineUi.show(nameLabel, "Your name", cy + 190);
        OnlineUi.place(nameField, cy + 130);
        OnlineUi.show(serverLabel, "Server", cy + 90);
        OnlineUi.place(serverField, cy + 30);
        OnlineUi.place(hostButton, cy - 60);
        OnlineUi.place(browseButton, cy - 130);
        OnlineUi.show(status, status.getText(), statusY());
        OnlineUi.place(backButton, cy - 330);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get() {
        return new OnlineMenuRoom();
    }
}
