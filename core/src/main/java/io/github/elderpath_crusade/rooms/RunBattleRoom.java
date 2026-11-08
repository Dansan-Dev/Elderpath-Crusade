package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.ui_objects.RelicHud;

/**
 * The battle room for one node of a roguelike run — otherwise identical to DemoRoom/
 * LocalMatchRoom, reusing BattleRoom entirely unchanged, plus a RelicHud (UPG-001) for any
 * active relic's player-clickable ability — the only thing the shared BattleRoom doesn't
 * already provide, since relics don't exist outside a roguelike run. The run's own bookkeeping
 * (did this node's win continue or end the run) happens in VictoryRoom once the match ends, via
 * RunManager.reportNodeOutcome — not here.
 */
public class RunBattleRoom extends BattleRoom {

    private RunBattleRoom(ClientMatchSession session) {
        super(GameMode.ROUGELIKE_RUN, session);
        addUI(new RelicHud());
        layoutBoard();
    }

    @Override
    protected void onPassTurnClicked() {
        controller.endTurn();
    }

    public static RunBattleRoom get(ClientMatchSession session) {
        return new RunBattleRoom(session);
    }
}
