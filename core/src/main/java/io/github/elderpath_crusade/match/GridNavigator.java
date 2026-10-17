package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.ComputedStatsComponent;
import io.github.elderpath_crusade.ecs.components.TerrainComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Movement reachability and attack range on a BoardGrid, in absolute coordinates.
 */
final class GridNavigator {
    private static final int[][] DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private final BoardGrid grid;

    GridNavigator(BoardGrid grid) {
        this.grid = grid;
    }

    /**
     * Reachable tiles for a move action. Pieces with no blocker-ignoring modifiers use
     * flood-fill (any combination of cardinal steps around obstacles, up to speed).
     * Pieces that ignore any blocker type (e.g. JumpMove) instead leap in a single
     * straight cardinal line, passing over ignorable blockers but never landing on one.
     */
    List<Coord> getReachable(Entity mover, int row, int col, int speed) {
        ComputedStatsComponent computed = mover != null ? mover.getComponent(ComputedStatsComponent.class) : null;
        boolean ignoreTerrain = computed != null && computed.ignoreTerrainAsBlockers;
        boolean ignoreFriendly = computed != null && computed.ignoreFriendlyAsBlockers;
        boolean ignoreHostile = computed != null && computed.ignoreHostileAsBlockers;

        if (!ignoreTerrain && !ignoreFriendly && !ignoreHostile) {
            return getReachableFlood(row, col, speed);
        }
        return getReachableStraightLine(mover, row, col, speed, ignoreTerrain, ignoreFriendly, ignoreHostile);
    }

    private List<Coord> getReachableFlood(int row, int col, int speed) {
        List<Coord> out = new ArrayList<>();
        if (speed <= 0) return out;

        int rows = grid.getRows();
        int cols = grid.getCols();

        boolean[][] visited = new boolean[rows][cols];
        int[][] dist = new int[rows][cols];
        for (int r = 0; r < rows; r++) Arrays.fill(dist[r], -1);

        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { row, col });
        visited[row][col] = true;
        dist[row][col] = 0;

        while (!q.isEmpty()) {
            int[] cur = q.removeFirst();
            int cr = cur[0], cc = cur[1];
            int cd = dist[cr][cc];
            if (cd >= speed) continue;

            for (int[] d : DIRS) {
                int nr = cr + d[0];
                int nc = cc + d[1];
                if (!grid.isInBounds(nr, nc)) continue;
                if (visited[nr][nc]) continue;
                if (grid.isOccupied(nr, nc)) continue;

                visited[nr][nc] = true;
                dist[nr][nc] = cd + 1;
                q.addLast(new int[] { nr, nc });
                out.add(new Coord(nr, nc));
            }
        }
        return out;
    }

    /**
     * Walks each of the 4 cardinal directions up to "speed" tiles. A tile occupied by a
     * blocker type the mover ignores is passed over (not a landing spot, but scanning
     * continues past it); any other occupied tile stops the scan in that direction.
     */
    private List<Coord> getReachableStraightLine(Entity mover, int row, int col, int speed,
            boolean ignoreTerrain, boolean ignoreFriendly, boolean ignoreHostile) {
        List<Coord> out = new ArrayList<>();
        if (speed <= 0) return out;

        PieceAlignment moverAlign = EntityUtils.getAlignment(mover);

        for (int[] d : DIRS) {
            for (int dist = 1; dist <= speed; dist++) {
                int nr = row + d[0] * dist;
                int nc = col + d[1] * dist;
                if (!grid.isInBounds(nr, nc)) break;

                Entity occupant = grid.getEntityAt(nr, nc);
                if (occupant == null) {
                    out.add(new Coord(nr, nc));
                    continue;
                }

                boolean canPassThrough;
                if (occupant.getComponent(TerrainComponent.class) != null) {
                    canPassThrough = ignoreTerrain;
                } else {
                    boolean isFriendly = EntityUtils.getAlignment(occupant) == moverAlign;
                    canPassThrough = isFriendly ? ignoreFriendly : ignoreHostile;
                }

                if (!canPassThrough) break; // blocked — this direction stops here
                // occupied but ignorable: not a landing spot, but keep scanning past it
            }
        }
        return out;
    }

    List<Coord> getAttackableEnemies(int row, int col, PieceAlignment friendlyAlignment) {
        List<Coord> out = new ArrayList<>();
        Entity src = grid.getEntityAt(row, col);
        if (src == null) return out;

        int effRange = EntityUtils.getRange(src);
        if (effRange < 0) return out;
        effRange = Math.max(1, effRange);

        for (int[] d : DIRS) {
            for (int dist = 1; dist <= effRange; dist++) {
                int nr = row + d[0] * dist;
                int nc = col + d[1] * dist;
                if (!grid.isInBounds(nr, nc)) break;

                Entity target = grid.getEntityAt(nr, nc);
                if (target != null) {
                    if (isHostile(target, friendlyAlignment)) out.add(new Coord(nr, nc));
                    // Any occupied cell blocks further scanning in this direction
                    break;
                }
            }
        }
        return out;
    }

    List<Coord> getAdjacentHostiles(int row, int col, PieceAlignment friendlyAlignment) {
        List<Coord> out = new ArrayList<>();
        for (int[] d : DIRS) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (!grid.isInBounds(nr, nc)) continue;

            Entity entity = grid.getEntityAt(nr, nc);
            if (entity != null && isHostile(entity, friendlyAlignment)) out.add(new Coord(nr, nc));
        }
        return out;
    }

    private static boolean isHostile(Entity entity, PieceAlignment friendlyAlignment) {
        PieceAlignment align = EntityUtils.getAlignment(entity);
        return align != friendlyAlignment && align != PieceAlignment.NEUTRAL;
    }
}
