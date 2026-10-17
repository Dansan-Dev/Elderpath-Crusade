package io.github.elderpath_crusade.interaction;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.TargetFilter;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shows a PickRequest on this screen: highlights its candidate tiles and waits for a click on
 * one (or a cancel). Shared by the local responder (picks of a match simulated in-process)
 * and the online guest (picks relayed from the host) — the same prompt either way.
 */
public final class LocalUiPickPrompt {
    private LocalUiPickPrompt() {}

    /** Starts the prompt; returns false if there's no board view to show it on. */
    public static boolean show(PickRequest request, Consumer<Coord> onChosen, Runnable onCancelled) {
        Board board = GameContext.get().getActiveBoard();
        if (board == null) return false;
        List<Plot> plots = new ArrayList<>(request.candidates().size());
        for (Coord c : request.candidates()) {
            if (board.getPlotAtPos(c.row(), c.col()) instanceof Plot plot) plots.add(plot);
        }
        if (plots.isEmpty()) return false;

        TargetFilter filter = new TargetFilter() {
            @Override
            public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
                return box instanceof Plot plot && plots.contains(plot);
            }

            @Override
            public List<Plot> getEligibleTargets(int targetIndex) {
                return plots;
            }
        };
        return GameContext.get().getInteractionManager().requestPick(
                ClickableEffectData.getMulti(ClickableTargetType.PLOT, 1),
                filter,
                picks -> {
                    if (picks.get(1) instanceof Plot plot) onChosen.accept(new Coord(plot.getRow(), plot.getCol()));
                    else onCancelled.run();
                },
                onCancelled);
    }

    /** Clears the prompt currently on screen, if any. */
    public static void clear() {
        GameContext.get().getInteractionManager().cancelSelection();
    }
}
