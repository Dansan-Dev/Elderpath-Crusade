package io.github.elderpath_crusade.managers.bot.search;

public class BotSearchState {
    public final Coord pos;
    public final int turnDepth, actionsLeft, threatCount;
    public final Coord firstMove;
    public final boolean endsFirstTurnInThreat;

    public BotSearchState(Coord pos, int turnDepth, int actionsLeft, int threatCount, Coord firstMove,
            boolean endsFirstTurnInThreat) {
        this.pos = pos;
        this.turnDepth = turnDepth;
        this.actionsLeft = actionsLeft;
        this.threatCount = threatCount;
        this.firstMove = firstMove;
        this.endsFirstTurnInThreat = endsFirstTurnInThreat;
    }

    public BotSearchState waitTurn(ThreatMap threats, int maxActions) {
        int nextTurn = turnDepth + 1;
        int newThreats = threatCount + ((threats != null && threats.isThreatened(pos.row(), pos.col())) ? 1 : 0);
        boolean newFirstTurnDanger = endsFirstTurnInThreat
                || (turnDepth == 0 && threats != null && threats.isThreatened(pos.row(), pos.col()));

        return new BotSearchState(pos, nextTurn, maxActions, newThreats, firstMove, newFirstTurnDanger);
    }

    public BotSearchState stepTo(Coord next) {
        Coord newFirstMove = (firstMove == null ? next : firstMove);

        return new BotSearchState(next, turnDepth, actionsLeft - 1, threatCount, newFirstMove,
                endsFirstTurnInThreat);
    }

    public int pack() {
        // row: 6 bits, col: 6 bits, turnDepth: 2 bits, actionsLeft: 4 bits
        return (pos.row() << 12) | (pos.col() << 6) | (turnDepth << 4) | actionsLeft;
    }
}
