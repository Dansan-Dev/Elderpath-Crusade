package io.github.elderpath_crusade.game_objects.pause.pages;

import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.ui_objects.TextList;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.settings.PauseScreenPage;
import io.github.elderpath_crusade.game_objects.pause.PauseScreen;
import io.github.elderpath_crusade.rooms.MainMenuRoom;
import io.github.elderpath_crusade.supers.HigherOrderTexture;
import io.github.elderpath_crusade.utils.MenuLayout;

public class PauseMenuPage extends HigherOrderTexture {
    public static PauseMenuPage get() {
        return new PauseMenuPage();
    }

    private final Text header = new Text(
        "PAUSED",
        FontType.SILKSCREEN,
        0,
        GameContext.get().getSettingsManager().screenSize.getScreenHeight() - 200,
        0,
        ColorSettings.TEXT_DEFAULT.getColor()
    ).withFontSize(io.github.elderpath_crusade.utils.FontSize.TITLE_LARGE).asPauseUI();
    private final TextList options = new TextList();
    private boolean abandonArmed;

    public PauseMenuPage() {
        getRenderables().add(header);
        addOptions();
        update();
    }

    private void addOptions() {
        options.addText(
            new Text("Resume", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.elderpath_crusade.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .withOnClick((e) -> GameContext.get().getGameManager().unpause(), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        options.addText(
            new Text("Settings", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.elderpath_crusade.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .withOnClick((e) -> PauseScreen.setCurrentPage(PauseScreenPage.SETTINGS), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        options.addText(
            new Text("Main Menu", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.elderpath_crusade.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .withOnClick(
                    (e) -> {
                        leaveOnlineMatch();
                        leaveRunBattle();
                        GameContext.get().getRoomManager().gotoRoom(MainMenuRoom::get);
                        GameContext.get().getGameManager().unpause();
                    },
                    ClickableEffectData.getImmediate()
                )
                .asPauseUI()
        );

        if (canAbandonRun()) {
            Text abandon = new Text("Abandon Run", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.elderpath_crusade.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .asPauseUI();
            // Two clicks: the first only asks, so a stray click can't throw a run away.
            abandon.withOnClick((e) -> {
                if (!abandonArmed) {
                    abandonArmed = true;
                    abandon.setText("Really abandon? Click again");
                    abandon.reflow();
                    update();
                    return;
                }
                leaveOnlineMatch();
                leaveRunBattle();
                GameContext.get().getRunManager().abandonRun();
                GameContext.get().getRoomManager().gotoRoom(MainMenuRoom::get);
                GameContext.get().getGameManager().unpause();
            }, ClickableEffectData.getImmediate());
            options.addText(abandon);
        }

        options.addText(
            new Text("Exit", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(io.github.elderpath_crusade.utils.FontSize.BODY_MEDIUM)
                .withHoverColor(ColorSettings.TEXT_HOVER.getColor())
                .withClickColor(ColorSettings.TEXT_CLICK.getColor())
                .withOnClick((e) -> Gdx.app.exit(), ClickableEffectData.getImmediate())
                .asPauseUI()
        );

        getRenderables().add(options);
    }

    /**
     * Going back to the menu from an online match means leaving it: the session sends Leave so the
     * lobby ends the match with the opponent winning, instead of holding it for 20 minutes for a
     * player who has walked away. Local matches (solo, hotseat, runs) are left as they were.
     */
    private static void leaveOnlineMatch() {
        io.github.elderpath_crusade.client.ClientMatchSession session = GameContext.get().getClientMatchSession();
        if (session != null && session.isRemote()) {
            session.close();
            GameContext.get().clearClientMatch();
        }
        GameContext.get().getLobbyConnection().clearRejoinTicket();
    }

    /** Whether a run of this player's own is in progress (not while sitting in an online match). */
    private static boolean canAbandonRun() {
        io.github.elderpath_crusade.client.ClientMatchSession session = GameContext.get().getClientMatchSession();
        return GameContext.get().getRunManager().hasActiveRun() && !(session != null && session.isRemote());
    }

    /**
     * Main Menu from a roguelike battle leaves the battle: its match server is closed (it used to keep running
     * unseen) and the run keeps its saved state, so "Resume" returns to the map with that node's battle still to
     * fight — a battle in progress is never resumed mid-turn (RUN-004).
     */
    private static void leaveRunBattle() {
        io.github.elderpath_crusade.client.ClientMatchSession session = GameContext.get().getClientMatchSession();
        if (session != null && !session.isRemote()
                && GameContext.get().getGameModeManager().getCurrent() == io.github.elderpath_crusade.enums.GameMode.ROUGELIKE_RUN) {
            session.close();
            GameContext.get().clearClientMatch();
        }
    }

    public void update() {
        // Centralized layout for header and options
        MenuLayout.layoutHeaderAndOptions(header, options, 50, 100);
    }
}
