package com.stipasay.dagoal;

import android.graphics.Color;

public class AchievementTierHelper {

    public static final String[] RANK_NAMES = { "Bronze", "Silver", "Gold", "Platinum", "Emerald" };
    public static final int[] RANK_COLORS = {
            Color.parseColor("#8C5A2B"),
            Color.parseColor("#A8A8A8"),
            Color.parseColor("#D4AF37"),
            Color.parseColor("#5DADE2"),
            Color.parseColor("#2ECC71")
    };
    public static final int[] TIER_MULTIPLIERS = { 1, 2, 4, 7, 12 };
    public static final int UNRANKED_COLOR = Color.parseColor("#3E4C33");

    public static int getThreshold(int baseTarget, int tierIndex) {
        return baseTarget * TIER_MULTIPLIERS[tierIndex];
    }

    public static int getMaxThreshold(int baseTarget) {
        return getThreshold(baseTarget, TIER_MULTIPLIERS.length - 1);
    }

    public static int getCurrentRankIndex(int progress, int baseTarget) {
        int rank = -1;
        for (int i = 0; i < TIER_MULTIPLIERS.length; i++) {
            if (progress >= getThreshold(baseTarget, i)) {
                rank = i;
            }
        }
        return rank;
    }

    public static int getBadgeColor(int progress, int baseTarget) {
        int rankIndex = getCurrentRankIndex(progress, baseTarget);
        if (rankIndex < 0) {
            return UNRANKED_COLOR;
        }
        return RANK_COLORS[rankIndex];
    }

    public static String getRankName(int progress, int baseTarget) {
        int rankIndex = getCurrentRankIndex(progress, baseTarget);
        if (rankIndex < 0) {
            return "Unranked";
        }
        return RANK_NAMES[rankIndex];
    }

    public static boolean isMaxRank(int progress, int baseTarget) {
        return getCurrentRankIndex(progress, baseTarget) == TIER_MULTIPLIERS.length - 1;
    }

    public static int getProgressPercentToNextRank(int progress, int baseTarget) {
        int rankIndex = getCurrentRankIndex(progress, baseTarget);
        int lowerBound = rankIndex < 0 ? 0 : getThreshold(baseTarget, rankIndex);
        if (isMaxRank(progress, baseTarget)) {
            return 100;
        }
        int upperBound = getThreshold(baseTarget, rankIndex + 1);
        int span = upperBound - lowerBound;
        if (span <= 0) {
            return 100;
        }
        int intoSpan = progress - lowerBound;
        return (int) (((double) intoSpan / span) * 100);
    }

    public static int getRemainingToNextRank(int progress, int baseTarget) {
        if (isMaxRank(progress, baseTarget)) {
            return 0;
        }
        int rankIndex = getCurrentRankIndex(progress, baseTarget);
        int upperBound = getThreshold(baseTarget, rankIndex + 1);
        return Math.max(upperBound - progress, 0);
    }
}