package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.supers.Room;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The Event node (EVENT-001, replacing Rest alongside {@link RecruitmentSiteRoom} — user's
 * design, 2026-11-12): a random one of several strange happenings. Each sub-event has its own
 * room: {@link SacrificialSiteRoom} (remove a card), {@link StrangeSurgeonRoom} (combine two
 * cards into one chimera), {@link TraderRoom} (swap one owned relic for a random new one).
 *
 * <p>Not every sub-event is always possible — Strange Surgeon needs 2 eligible summon cards,
 * Trader needs at least 1 owned relic, Sacrificial Site needs more than 1 deck card (so it
 * never empties the deck). {@link #get} only rolls among whichever are currently feasible; on
 * the rare run state where none are (a 1-card deck with no relics — only possible very early,
 * and {@link RunMap}'s weighting already guarantees an Elite exists, not that every event does),
 * it skips straight back to {@link RunRoom} rather than show a dead choice.
 */
public class EventRoom {
    private EventRoom() {}

    public static Room get() {
        RunManager runManager = GameContext.get().getRunManager();
        List<RunCardInstance> deck = runManager.getRunState().deck();
        long eligibleSummons = deck.stream().filter(i -> CardCatalog.isSummon(i.cardName())).count();

        List<Supplier<Room>> candidates = new ArrayList<>();
        if (deck.size() > 1) candidates.add(SacrificialSiteRoom::get);
        if (eligibleSummons >= 2) candidates.add(StrangeSurgeonRoom::get);
        if (!runManager.getRunState().activeRelicIds().isEmpty()) candidates.add(TraderRoom::get);

        if (candidates.isEmpty()) return RunRoom.get();
        return candidates.get(new Random().nextInt(candidates.size())).get();
    }
}
