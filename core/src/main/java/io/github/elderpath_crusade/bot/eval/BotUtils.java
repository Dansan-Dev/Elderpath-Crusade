package io.github.elderpath_crusade.bot.eval;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.bot.search.ThreatMap;

import java.util.ArrayDeque;
import java.util.Queue;

public class BotUtils {
    public static ThreatMap computeThreatMap(Board board, PieceAlignment enemySide) {
        int rows = board.getROWS();
        int cols = board.getCOLS();
        ThreatMap map = new ThreatMap(rows, cols);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Entity enemy = board.getEntityAtPos(row, col);
                if (enemy == null || EntityUtils.getAlignment(enemy) != enemySide) {
                    continue;
                }
                if (EntityUtils.getDamage(enemy) <= 0 || EntityUtils.getActions(enemy) <= 0) {
                    continue;
                }

                int actions = EntityUtils.getActions(enemy);
                int speed = EntityUtils.getSpeed(enemy);
                int moveSteps = Math.max(0, (actions - 1) * Math.max(0, speed));

                Queue<int[]> queue = new ArrayDeque<>();
                boolean[][] visited = new boolean[rows][cols];
                queue.add(new int[] { row, col, 0 });
                visited[row][col] = true;
                int[][] directions = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

                while (!queue.isEmpty()) {
                    int[] current = queue.poll();
                    int cr = current[0];
                    int cc = current[1];
                    int d = current[2];

                    if (d > moveSteps) {
                        continue;
                    }

                    for (int[] dir : directions) {
                        map.mark(cr + dir[0], cc + dir[1]);
                    }

                    if (d == moveSteps) {
                        continue;
                    }

                    for (int[] dir : directions) {
                        int nr = cr + dir[0];
                        int nc = cc + dir[1];
                        if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !visited[nr][nc]
                                && board.getEntityAtPos(nr, nc) == null) {
                            visited[nr][nc] = true;
                            queue.add(new int[] { nr, nc, d + 1 });
                        }
                    }
                }
            }
        }
        return map;
    }
}
