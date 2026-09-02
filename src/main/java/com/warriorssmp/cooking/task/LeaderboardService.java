package com.warriorssmp.cooking.task;

import com.warriorssmp.cooking.data.DataStore;
import com.warriorssmp.cooking.data.PlayerGatherData;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class LeaderboardService {

    public enum Board {XP, STREAK, TASKS_COMPLETED, RESOURCES_GATHERED, LUCKY_STRIKES, LEGENDARY_REQUESTS}

    private final DataStore dataStore;

    public LeaderboardService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    public List<PlayerGatherData> top(Board board, int limit) {
        Comparator<PlayerGatherData> comparator = switch (board) {
            case XP -> Comparator.comparingLong((PlayerGatherData d) -> d.totalXp).reversed();
            case STREAK -> Comparator.comparingInt((PlayerGatherData d) -> d.streak).reversed();
            case TASKS_COMPLETED -> Comparator.comparingInt((PlayerGatherData d) -> d.lifetimeTasksCompleted).reversed();
            case RESOURCES_GATHERED -> Comparator.comparingInt((PlayerGatherData d) -> d.lifetimeResourcesGathered).reversed();
            case LUCKY_STRIKES -> Comparator.comparingInt((PlayerGatherData d) -> d.lifetimeLuckyStrikes).reversed();
            case LEGENDARY_REQUESTS -> Comparator.comparingInt((PlayerGatherData d) -> d.lifetimeLegendaryCompleted).reversed();
        };

        return dataStore.allKnownPlayers().stream()
                .sorted(comparator)
                .limit(limit)
                .collect(Collectors.toList());
    }

    public int rankOf(Board board, java.util.UUID uuid) {
        List<PlayerGatherData> all = top(board, Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).uuid.equals(uuid)) return i + 1;
        }
        return -1;
    }
}
