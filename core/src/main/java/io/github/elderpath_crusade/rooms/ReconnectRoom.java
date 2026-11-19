package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.lobby.LobbyConnection;
import io.github.elderpath_crusade.lobby.MatchReconnector;
import io.github.elderpath_crusade.lobby.RejoinTicket;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

/**
 * Shown when an online match's connection drops: keeps trying to get back in (with growing
 * pauses between tries) for as long as the lobby holds the match — 20 minutes — and returns to
 * the battle, rebuilt from the match server's snapshot, as soon as it works. The player can give
 * up at any time; the opponent then simply waits out the hold, or wins if they leave.
 */
public class ReconnectRoom extends Room {
    private final Text header;
    private final Text status;
    private final Text detail;
    private final Button giveUpButton;
    private final MatchReconnector reconnector;
    private final RejoinTicket ticket;
    private final String matchToken;
    private boolean transitioning;

    private ReconnectRoom(RejoinTicket ticket, String matchToken) {
        super();
        GameContext.get().getInputManager().stopTextEditing();
        this.ticket = ticket;
        this.matchToken = matchToken;

        header = OnlineUi.line("Connection Lost", FontSize.TITLE_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        status = OnlineUi.line(" ", FontSize.BODY_LARGE, ColorSettings.TEXT_DEFAULT.getColor());
        detail = OnlineUi.line(" ", FontSize.BODY_MEDIUM, ColorSettings.TEXT_DEFAULT.getColor());
        giveUpButton = OnlineUi.button("Give Up", 220, this::giveUp);
        addContent(header);
        addContent(status);
        addContent(detail);
        addContent(giveUpButton);

        reconnector = new MatchReconnector(ticket, LobbyConnection.dialingConnector());
        reconnector.start();
        addContent(new Ticker(delta -> tick()));
        layoutContents();
    }

    private void tick() {
        if (transitioning) return;
        switch (reconnector.getPhase()) {
            case TRYING -> {
                long left = reconnector.getSecondsLeft();
                OnlineUi.show(status, "Reconnecting to your match...", OnlineUi.centerY() + 40);
                OnlineUi.show(detail, "Attempt " + reconnector.getAttempts() + "   -   giving up in "
                        + (left / 60) + ":" + String.format("%02d", left % 60), OnlineUi.centerY());
            }
            case SUCCEEDED -> {
                transitioning = true;
                ClientMatchSession session = ClientMatchSession.joinThrough(reconnector.takeConnection(),
                        SeatRequest.valueOf(ticket.seat()), ticket.playerName(), matchToken, ticket.opponentName());
                GameContext.get().getRoomManager().gotoRoom(() -> OnlineMatchRoom.through(session));
            }
            case GAVE_UP -> {
                OnlineUi.show(status, reconnector.getMessage(), OnlineUi.centerY() + 40);
                OnlineUi.show(detail, "Press the button to return to the main menu", OnlineUi.centerY());
            }
        }
    }

    private void giveUp() {
        transitioning = true;
        reconnector.close();
        GameContext.get().getLobbyConnection().clearRejoinTicket();
        GameContext.get().getRoomManager().gotoRoom(MainMenuRoom::get);
    }

    private void layoutContents() {
        MenuLayout.centerHeader(header, 100);
        OnlineUi.show(status, status.getText(), OnlineUi.centerY() + 40);
        OnlineUi.show(detail, detail.getText(), OnlineUi.centerY());
        OnlineUi.place(giveUpButton, OnlineUi.centerY() - 120);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get(RejoinTicket ticket, String matchToken) {
        return new ReconnectRoom(ticket, matchToken);
    }
}
