package io.github.elderpath_crusade.game_objects.board.components;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.game_objects.board.game_piece.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.plot.Plot;
import io.github.elderpath_crusade.interfaces.Renderable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles board navigation, pathfinding (BFS), and attack range calculations.
 */
public class BoardNavigator {
    private final Board board;

    public BoardNavigator(Board board) {
        this.board = board;
    }

    /**
     * Compute reachable plots from (row,col) within a maximum path length (speed),
     * moving 4-directionally (N/E/S/W). Cannot pass through or end on occupied
     * cells.
     * The origin cell is excluded from the results.
     */
    public List<Plot> getReachablePlots(int row, int col, int speed) {
        List<Plot> out = new ArrayList<>();
        if (speed <= 0)
            return out;

        int rows = board.getROWS();
        int cols = board.getCOLS();
        Renderable[][] layout = board.getLayout();

        boolean[][] visited = new boolean[rows][cols];
        int[][] dist = new int[rows][cols];
        for (int r = 0; r < rows; r++)
            Arrays.fill(dist[r], -1);

        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { row, col });
        visited[row][col] = true;
        dist[row][col] = 0;

        int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!q.isEmpty()) {
            int[] cur = q.removeFirst();
            int cr = cur[0], cc = cur[1];
            int cd = dist[cr][cc];
            if (cd >= speed)
                continue; // cannot step further

            for (int[] d : dirs) {
                int nr = cr + d[0];
                int nc = cc + d[1];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols)
                    continue;
                if (visited[nr][nc])
                    continue;

                // Block stepping into occupied cells
                if (board.isOccupied(nr, nc))
                    continue;

                visited[nr][nc] = true;
                dist[nr][nc] = cd + 1;
                q.addLast(new int[] { nr, nc });

                // Exclude origin
                Renderable r = layout[nr][nc];
                if (r instanceof Plot p)
                    out.add(p);
            }
        }
        return out;
    }

    /**
     * Return enemy plots attackable from (row,col) for a given alignment,
     * using cardinal lines with blockers and range.
     */
    public List<Plot> getAttackableEnemyPlots(int row, int col, PieceAlignment friendlyAlignment) {
        List<Plot> out = new ArrayList<>();
        GamePiece src = board.getGamePieceAtPos(row, col);
        if (!(src instanceof MonsterGamePiece attacker))
            return out;

        int rows = board.getROWS();
        int cols = board.getCOLS();
        Renderable[][] layout = board.getLayout();

        int effRange = attacker.getEffectiveRange();
        if (effRange < 0)
            return out;
        effRange = Math.max(1, effRange);

        int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        boolean ignoreTerrain = attacker.ignoresTerrainAsBlockers();
        boolean ignoreFriendly = attacker.ignoresFriendlyUnitsAsBlockers();
        boolean ignoreHostile = attacker.ignoresHostileUnitsAsBlockers();

        for (int[] d : dirs) {
            for (int dist = 1; dist <= effRange; dist++) {
                int nr = row + d[0] * dist;
                int nc = col + d[1] * dist;
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols)
                    break;

                GamePiece gp = board.getGamePieceAtPos(nr, nc);
                if (gp != null) {
                    boolean isTerrain = gp.getType() == GamePieceType.TERRAIN;
                    boolean isUnit = gp instanceof MonsterGamePiece;
                    boolean hostile = isUnit && ((MonsterGamePiece) gp).getAlignment() != friendlyAlignment;
                    boolean friendly = isUnit && ((MonsterGamePiece) gp).getAlignment() == friendlyAlignment;

                    boolean blockedByTerrain = isTerrain && !ignoreTerrain;
                    boolean blockedByFriendly = friendly && !ignoreFriendly;
                    boolean blockedByHostile = hostile && !ignoreHostile;

                    if (hostile) {
                        Renderable r = layout[nr][nc];
                        if (r instanceof Plot p)
                            out.add(p);
                    }

                    if (blockedByTerrain || blockedByFriendly || blockedByHostile || (!isTerrain && !isUnit)) {
                        break;
                    } else {
                        continue;
                    }
                }
            }
        }
        return out;
    }

    /** Return adjacent hostile plots (cardinal) around (row,col). */
    public List<Plot> getAdjacentHostilePlots(int row, int col, PieceAlignment friendlyAlignment) {
        List<Plot> out = new ArrayList<>();
        int rows = board.getROWS();
        int cols = board.getCOLS();
        Renderable[][] layout = board.getLayout();

        int[][] dirs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] d : dirs) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols)
                continue;

            GamePiece gp = board.getGamePieceAtPos(nr, nc);
            if (gp instanceof MonsterGamePiece mgp) {
                if (mgp.getAlignment() != friendlyAlignment) {
                    Renderable r = layout[nr][nc];
                    if (r instanceof Plot p)
                        out.add(p);
                }
            }
        }
        return out;
    }
}
