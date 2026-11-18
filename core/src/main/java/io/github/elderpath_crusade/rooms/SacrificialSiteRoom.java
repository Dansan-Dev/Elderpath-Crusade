package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.ArrayList;
import java.util.List;


/**
 * One {@link EventRoom} roll: the old gods demand a sacrifice. Lists every card in the deck
 * (spell or summon alike — pure removal, no {@code PieceDefinition} needed unlike a stat-mod
 * target) as a candidate; picking one calls {@link RunManager#removeCardFromDeck} and continues
 * to {@link RunRoom}. {@link #get} only ever shown when the deck has more than one card ({@link
 * EventRoom}'s own feasibility check), so there's always at least one card left afterward.
 */
public class SacrificialSiteRoom extends Room {

    private final Text header;
    private final Text flavor;
    private final CardPicker picker;

    private SacrificialSiteRoom() {
        super();
        RunManager runManager = GameContext.get().getRunManager();

        header = new Text("Sacrificial Site", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        flavor = new Text("An old shrine demands a sacrifice from your deck.", FontType.SILKSCREEN, 0, 0, 0,
                ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.BODY_MEDIUM);
        addContent(flavor);

        List<CardPicker.Option> options = new ArrayList<>();
        for (RunCardInstance instance : runManager.getRunState().deck()) {
            String instanceId = instance.instanceId();
            String label = instance.cardName() + (instance.isModified() ? " (modified)" : "");
            options.add(new CardPicker.Option(label, null, CardPicker.cardNameFor(instance), () -> {
                runManager.removeCardFromDeck(instanceId);
                GameContext.get().getRoomManager().gotoRoom(RunRoom::get);
            }));
        }
        picker = new CardPicker(this::addContent, options);

        layoutContents();
    }

    private void layoutContents() {
        MenuLayout.centerHeader(header, 100);
        MenuLayout.centerFlavor(flavor, 165);
        picker.layout(200);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get() {
        RunManager runManager = GameContext.get().getRunManager();
        if (runManager.getRunState().deck().size() <= 1) return RunRoom.get(); // never empty the deck
        return new SacrificialSiteRoom();
    }
}
