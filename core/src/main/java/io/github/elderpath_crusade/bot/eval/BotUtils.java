package io.github.elderpath_crusade.bot.eval;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
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
                GamePiece piece = board.getGamePieceAtPos(row, col);
                if (!(piece instanceof MonsterGamePiece enemy) || enemy.getAlignment() != enemySide) {
                    continue;
                }
                if (enemy.getEffectiveDamage() <= 0 || enemy.getEffectiveActions() <= 0) {
                    continue;
                }

                int actions = enemy.getEffectiveActions();
                int speed = enemy.getEffectiveSpeed();
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
                                && board.getGamePieceAtPos(nr, nc) == null) {
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
