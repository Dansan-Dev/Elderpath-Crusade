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
 * Second step of {@link RecruitmentSiteRoom}: someone already in the tribe doesn't like the new
 * recruit and leaves. Lists the (pre-rolled, pre-recruit) departure candidates it was given;
 * picking one calls {@link RunManager#removeCardFromDeck} and continues to {@link RunRoom}.
 */
public class RecruitmentDepartureRoom extends Room {

    private final Text header;
    private final Text flavor;
    private final CardPicker picker;

    private RecruitmentDepartureRoom(List<RunCardInstance> departureCandidates) {
        super();
        RunManager runManager = GameContext.get().getRunManager();

        header = new Text("Recruitment Site", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        flavor = new Text("Someone in the tribe doesn't like the new arrival and chooses to leave.",
                FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.BODY_MEDIUM);
        addContent(flavor);

        List<CardPicker.Option> options = new ArrayList<>();
        for (RunCardInstance instance : departureCandidates) {
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
        MenuLayout.centerHeader(flavor, 150);
        picker.layout(200);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    /** Skips straight to RunRoom if there was no one eligible to leave (a very small deck). */
    public static Room get(List<RunCardInstance> departureCandidates) {
        if (departureCandidates.isEmpty()) return RunRoom.get();
        return new RecruitmentDepartureRoom(departureCandidates);
    }
}
