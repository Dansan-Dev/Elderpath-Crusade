package io.github.elderpath_crusade.managers.bot.search;

public class ThreatMap {
    private final int[][] threatCount;

    public ThreatMap(int rows, int cols) {
        threatCount = new int[rows][cols];
    }

    public void mark(int r, int c) {
        if (r >= 0 && c >= 0 && r < threatCount.length && c < threatCount[0].length)
            threatCount[r][c]++;
    }

    public boolean isThreatened(int r, int c) {
        return r >= 0 && c >= 0 && r < threatCount.length && c < threatCount[0].length && threatCount[r][c] > 0;
    }

    public int getCount(int r, int c) {
        return (r >= 0 && c >= 0 && r < threatCount.length && c < threatCount[0].length) ? threatCount[r][c] : 0;
    }
}
