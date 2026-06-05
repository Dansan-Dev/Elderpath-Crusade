package io.github.elderpath_crusade.game_objects.board.components;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
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

    public List<Plot> getReachablePlots(int row, int col, int speed) {
        List<Plot> out = new ArrayList<>();
        if (speed <= 0) return out;

        int rows = board.getROWS();
        int cols = board.getCOLS();
        Renderable[][] layout = board.getLayout();

        boolean[][] visited = new boolean[rows][cols];
        int[][] dist = new int[rows][cols];
        for (int r = 0; r < rows; r++) Arrays.fill(dist[r], -1);

        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { row, col });
        visited[row][col] = true;
        dist[row][col] = 0;

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!q.isEmpty()) {
            int[] cur = q.removeFirst();
            int cr = cur[0], cc = cur[1];
            int cd = dist[cr][cc];
            if (cd >= speed) continue;

            for (int[] d : dirs) {
                int nr = cr + d[0];
                int nc = cc + d[1];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                if (visited[nr][nc]) continue;
                if (board.isOccupied(nr, nc)) continue;

                visited[nr][nc] = true;
                dist[nr][nc] = cd + 1;
                q.addLast(new int[] { nr, nc });

                Renderable r = layout[nr][nc];
                if (r instanceof Plot p) out.add(p);
            }
        }
        return out;
    }

    public List<Plot> getAttackableEnemyPlots(int row, int col, PieceAlignment friendlyAlignment) {
        List<Plot> out = new ArrayList<>();
        Entity src = board.getEntityAtPos(row, col);
        if (src == null) return out;

        int rows = board.getROWS();
        int cols = board.getCOLS();
        Renderable[][] layout = board.getLayout();

        int effRange = EntityUtils.getRange(src);
        if (effRange < 0) return out;
        effRange = Math.max(1, effRange);

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        for (int[] d : dirs) {
            for (int dist = 1; dist <= effRange; dist++) {
                int nr = row + d[0] * dist;
                int nc = col + d[1] * dist;
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) break;

                Entity target = board.getEntityAtPos(nr, nc);
                if (target != null) {
                    PieceAlignment targetAlign = EntityUtils.getAlignment(target);
                    boolean hostile = targetAlign != friendlyAlignment && targetAlign != PieceAlignment.NEUTRAL;

                    if (hostile) {
                        Renderable r = layout[nr][nc];
                        if (r instanceof Plot p) out.add(p);
                    }
                    // Any occupied cell blocks further scanning in this direction
                    break;
                }
            }
        }
        return out;
    }

    public List<Plot> getAdjacentHostilePlots(int row, int col, PieceAlignment friendlyAlignment) {
        List<Plot> out = new ArrayList<>();
        int rows = board.getROWS();
        int cols = board.getCOLS();
        Renderable[][] layout = board.getLayout();

        int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] d : dirs) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;

            Entity entity = board.getEntityAtPos(nr, nc);
            if (entity != null) {
                PieceAlignment align = EntityUtils.getAlignment(entity);
                if (align != friendlyAlignment && align != PieceAlignment.NEUTRAL) {
                    Renderable r = layout[nr][nc];
                    if (r instanceof Plot p) out.add(p);
                }
            }
        }
        return out;
    }
}
