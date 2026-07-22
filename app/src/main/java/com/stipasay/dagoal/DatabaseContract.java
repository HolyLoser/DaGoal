package com.stipasay.dagoal;

import android.provider.BaseColumns;

public final class DatabaseContract {
    private DatabaseContract() {}

    public static class UserEntry implements BaseColumns {
        public static final String TABLE_NAME = "user";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_AGE = "age";
        public static final String COLUMN_XP = "xp";
        public static final String COLUMN_GOLD = "gold";
        public static final String COLUMN_STREAK = "streak";
        public static final String COLUMN_SHUFFLES = "available_shuffles";
        public static final String COLUMN_LAST_LOGIN_DATE = "last_login_date";
    }

    public static class PreferenceEntry implements BaseColumns {
        public static final String TABLE_NAME = "preferences";
        public static final String COLUMN_USER_REF = "user_id";
        public static final String COLUMN_ACTIVITY_TYPE = "activity_type";
        public static final String COLUMN_DIFFICULTY = "difficulty";
    }

    public static class TaskTemplateEntry implements BaseColumns {
        public static final String TABLE_NAME = "task_templates";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_BASE_VALUE = "base_value";
        public static final String COLUMN_UNIT = "unit";
        public static final String COLUMN_CATEGORY = "category_tag";
        public static final String COLUMN_SUB_CATEGORY = "sub_category";
        public static final String COLUMN_QUEST_TYPE = "quest_type";
    }

    public static class DailyTaskEntry implements BaseColumns {
        public static final String TABLE_NAME = "daily_tasks";
        public static final String COLUMN_USER_REF = "user_id";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_TARGET_VALUE = "final_target";
        public static final String COLUMN_UNIT = "unit";
        public static final String COLUMN_IS_COMPLETED = "is_completed";
        public static final String COLUMN_DATE = "task_date";
        public static final String COLUMN_REWARD_GOLD = "reward_gold";
        public static final String COLUMN_REWARD_XP = "reward_xp";
        public static final String COLUMN_QUEST_TYPE = "quest_type";
        public static final String COLUMN_CURRENT_VALUE = "current_value";
        public static final String COLUMN_PACKAGE_NAME = "package_name";
        public static final String COLUMN_START_TIMESTAMP = "start_timestamp";
        public static final String QUEST_TYPE_GENERIC = "GENERIC";
        public static final String QUEST_TYPE_STEPS = "STEPS";
        public static final String QUEST_TYPE_SCREEN_AVOID = "SCREEN_AVOID";
        public static final String QUEST_TYPE_INCREMENT = "INCREMENT";
        public static final String COLUMN_CATEGORY_TAG = "category_tag";
        public static final String COLUMN_IGNORE_STAGE = "ignore_stage";
        public static final String COLUMN_SNOOZE_UNTIL = "snooze_until";
    }

    public static class InventoryEntry implements BaseColumns {
        public static final String TABLE_NAME = "inventory";
        public static final String COLUMN_ITEM_ID = "item_id";
        public static final String COLUMN_ITEM_NAME = "item_name";
        public static final String COLUMN_CATEGORY = "category";
        public static final String COLUMN_RES_NAME = "res_name";
    }

    public static class AchievementEntry implements BaseColumns {
        public static final String TABLE_NAME = "achievements";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_TYPE = "type";
        public static final String COLUMN_CURRENT_PROGRESS = "current_progress";
        public static final String COLUMN_TARGET_VALUE = "target_value";
    }

    public static class BlockedAppEntry implements BaseColumns {
        public static final String TABLE_NAME = "blocked_apps";
        public static final String COLUMN_PACKAGE_NAME = "package_name";
        public static final String COLUMN_APP_NAME = "app_name";
    }

    public static class InventoryConsumableEntry implements BaseColumns {
        public static final String TABLE_NAME = "inventory_consumables";
        public static final String COLUMN_TYPE = "type";
        public static final String COLUMN_QUANTITY = "quantity";
        public static final String TYPE_STREAK_PROTECTOR = "STREAK_PROTECTOR";
        public static final String TYPE_XP_BOOST = "XP_BOOST";
        public static final String TYPE_GOLD_BOOST = "GOLD_BOOST";
    }
}