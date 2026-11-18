package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.input.TextEntry;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.ui_objects.TextField;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;

/**
 * ONLINE-002: asked only when the lobby the player clicked in {@link BrowseLobbiesRoom} is password-protected.
 * Join continues to the deck choice (Draft or Collection) and into the lobby; if the server then refuses the
 * password, {@link LobbyRoom} comes back here with a message and the deck already chosen, so a retry is just
 * retyping the password.
 */
public class LobbyPasswordRoom extends Room {
    private static final int MAX_PASSWORD = 32;

    private final String lobbyId;
    private final String lobbyName;
    private final List<String> deck; // null until the player has chosen one

    private final Text header;
    private final Text prompt;
    private final Text status;
    private final TextField passwordField;
    private final Button joinButton;
    private final Button backButton;

    private LobbyPasswordRoom(String lobbyId, String lobbyName, List<String> deck, String message) {
        super();
        GameContext.get().getInputManager().stopTextEditing();
        this.lobbyId = lobbyId;
        this.lobbyName = lobbyName;
        this.deck = deck;

        header = OnlineUi.line("Join Lobby", FontSize.TITLE_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        prompt = OnlineUi.line("\"" + lobbyName + "\" is password protected", FontSize.BODY_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        status = OnlineUi.line(message == null ? " " : message, FontSize.BODY_MEDIUM, ColorSettings.TEXT_WARNING.getColor());
        passwordField = TextField.create(new TextEntry(MAX_PASSWORD, true), "Password", null,
                0, 0, OnlineUi.FIELD_WIDTH, OnlineUi.FIELD_HEIGHT, 0);
        joinButton = OnlineUi.button(deck == null ? "Choose Deck & Join" : "Join", 360, this::joinClicked);
        backButton = OnlineUi.button("Back", 120, () -> GameContext.get().getRoomManager().gotoRoom(BrowseLobbiesRoom::get));

        addContent(header);
        addContent(prompt);
        addContent(status);
        addContent(passwordField);
        addContent(joinButton);
        addContent(backButton);
        layoutContents();
    }

    private void joinClicked() {
        String password = passwordField.getEntry().getText();
        if (password.isEmpty()) {
            OnlineUi.show(status, "Type the lobby's password", OnlineUi.centerY() - 5);
            return;
        }
        if (deck != null) {
            GameContext.get().getRoomManager().gotoRoom(() -> LobbyRoom.joining(lobbyId, password, deck, lobbyName));
            return;
        }
        GameContext.get().getRoomManager().gotoRoom(() -> DeckSourceSelectRoom.get(PieceAlignment.P1, false,
                chosen -> GameContext.get().getRoomManager().gotoRoom(() -> LobbyRoom.joining(lobbyId, password, chosen, lobbyName))));
    }

    private void layoutContents() {
        int cy = OnlineUi.centerY();
        MenuLayout.centerHeader(header, 100);
        OnlineUi.show(prompt, prompt.getText(), cy + 100);
        OnlineUi.place(passwordField, cy + 30);
        OnlineUi.show(status, status.getText(), cy - 5);
        OnlineUi.place(joinButton, cy - 90);
        OnlineUi.place(backButton, cy - 170);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    /** The first ask: no deck chosen yet. */
    public static Room get(String lobbyId, String lobbyName) {
        return new LobbyPasswordRoom(lobbyId, lobbyName, null, null);
    }

    /** After a refusal: the deck is already chosen; {@code message} says why we are back. */
    public static Room retry(String lobbyId, String lobbyName, List<String> deck, String message) {
        return new LobbyPasswordRoom(lobbyId, lobbyName, deck, message);
    }
}
