package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ProfilingActivity extends AppCompatActivity {

    private TextView tvCategoryTitle;
    private TextView tvQuestionTitle;
    private ImageButton btnBack;
    private View frameAnimHost;

    private LinearLayout cardOption1, cardOption2, cardOption3, cardOption4, cardOption5, cardOption6;
    private TextView tvOptionText1, tvOptionText2, tvOptionText3, tvOptionText4, tvOptionText5, tvOptionText6;
    private TextView tvOptionCheck1, tvOptionCheck2, tvOptionCheck3, tvOptionCheck4, tvOptionCheck5, tvOptionCheck6;

    private TextView node1, node2, node3, node4;

    private int currentQuestionIndex = 0;
    private boolean isAnimating = false;

    private final String[] categories = {
            "Adventurer Profile", "Adventurer Profile", "Adventurer Profile",
            "Physical Mobility", "Physical Mobility", "Physical Mobility",
            "Screen Time & Digital Habits", "Screen Time & Digital Habits", "Screen Time & Digital Habits",
            "Creative Outlets & Hobbies", "Creative Outlets & Hobbies", "Creative Outlets & Hobbies"
    };

    private final String[] questions = {
            "What is your preferred username or nickname?",
            "What is your age range?",
            "How do you identify?",
            "What is your current estimated daily step count?",
            "How many days a week do you intentionally exercise or walk long distances?",
            "Do you have any minor physical limitations or joint pain that limits heavy impacts?",
            "What is your average daily phone screen time?",
            "How often do you find yourself mindlessly scrolling your phone while studying or working?",
            "What is your main objective for initiating a digital detox?",
            "When you are completely offline, which activities do you naturally enjoy the most?",
            "How much time are you willing to allocate to an alternate offline hobby daily?",
            "Choose a secondary non-screen activity archetype you want to develop:"
    };

    private final String[][] optionsMatrix = {
            {"Adventurer", "Hero", "Explorer", "Achiever"},
            {"Under 18", "18–24", "25–35", "36–50", "51–64", "65 and over"},
            {"Male", "Female", "Non-Binary / Prefer not to say"},
            {"Under 2,000 steps", "2,000–5,000 steps", "5,000–8,000 steps", "More than 8,000 steps"},
            {"0 days", "1–2 days", "3–5 days", "6+ days"},
            {"Yes, frequent discomfort", "Occasional stiffness", "No limitations at all"},
            {"Under 3 hours", "3–5 hours", "6–8 hours", "More than 8 hours"},
            {"Constantly", "Frequently", "Occasionally", "Never"},
            {"Reclaim lost time", "Improve focus on school/work", "Lower anxiety and mental clutter"},
            {"Arts & Drawing", "Journaling & Writing", "Reading books", "Board games & Solo puzzles"},
            {"10–15 minutes", "15–30 minutes", "30–60 minutes", "Over an hour"},
            {"Artistic sketching", "Mindfulness journaling", "Reading literature", "Manual organizing"}
    };

    private final String[] userAnswers = new String[12];
    private DatabaseHelper dbHelper;

    private LinearLayout layoutNicknameInput;
    private android.widget.EditText editNicknameInput;
    private android.widget.Button btnNicknameNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_profiling_questionnaire);

        dbHelper = new DatabaseHelper(this);

        tvCategoryTitle = findViewById(R.id.tv_category_title);
        tvQuestionTitle = findViewById(R.id.tv_question_title);
        btnBack = findViewById(R.id.btn_questionnaire_back);
        frameAnimHost = findViewById(R.id.frame_question_anim_host);

        layoutNicknameInput = findViewById(R.id.layout_nickname_input);
        editNicknameInput = findViewById(R.id.edit_nickname_input);
        btnNicknameNext = findViewById(R.id.btn_nickname_next);

        cardOption1 = findViewById(R.id.option_card_1);
        cardOption2 = findViewById(R.id.option_card_2);
        cardOption3 = findViewById(R.id.option_card_3);
        cardOption4 = findViewById(R.id.option_card_4);
        cardOption5 = findViewById(R.id.option_card_5);
        cardOption6 = findViewById(R.id.option_card_6);

        tvOptionText1 = findViewById(R.id.tv_option_text_1);
        tvOptionText2 = findViewById(R.id.tv_option_text_2);
        tvOptionText3 = findViewById(R.id.tv_option_text_3);
        tvOptionText4 = findViewById(R.id.tv_option_text_4);
        tvOptionText5 = findViewById(R.id.tv_option_text_5);
        tvOptionText6 = findViewById(R.id.tv_option_text_6);

        tvOptionCheck1 = findViewById(R.id.tv_option_check_1);
        tvOptionCheck2 = findViewById(R.id.tv_option_check_2);
        tvOptionCheck3 = findViewById(R.id.tv_option_check_3);
        tvOptionCheck4 = findViewById(R.id.tv_option_check_4);
        tvOptionCheck5 = findViewById(R.id.tv_option_check_5);
        tvOptionCheck6 = findViewById(R.id.tv_option_check_6);

        node1 = findViewById(R.id.tv_step_node_1);
        node2 = findViewById(R.id.tv_step_node_2);
        node3 = findViewById(R.id.tv_step_node_3);
        node4 = findViewById(R.id.tv_step_node_4);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> handleBackClick());
        }

        if (btnNicknameNext != null) {
            btnNicknameNext.setOnClickListener(v -> processNicknameSubmit());
        }

        android.widget.FrameLayout avatarContainer = findViewById(R.id.avatar_host_container);
        if (avatarContainer != null) {
            AvatarHelper.renderUserAvatar(this, avatarContainer);
        }

        displayQuestion(currentQuestionIndex, true);
    }

    private void processNicknameSubmit() {
        if (isAnimating) return;
        String typedName = "Adventurer";
        if (editNicknameInput != null && !editNicknameInput.getText().toString().trim().isEmpty()) {
            typedName = editNicknameInput.getText().toString().trim();
        }
        userAnswers[0] = typedName;

        isAnimating = true;
        currentQuestionIndex = 1;
        displayQuestionWithAnimation(1, true);
    }

    private void handleBackClick() {
        if (isAnimating) return;
        if (currentQuestionIndex > 0) {
            currentQuestionIndex--;
            displayQuestionWithAnimation(currentQuestionIndex, false);
        } else {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Exit Questionnaire?")
                    .setMessage("Your character is saved! You can resume this questionnaire anytime.")
                    .setPositiveButton("Exit", (dialog, which) -> finish())
                    .setNegativeButton("Resume", null)
                    .show();
        }
    }

    private void displayQuestion(int index, boolean animateForward) {
        resetOptionCardStyles();

        if (tvCategoryTitle != null) tvCategoryTitle.setText(categories[index]);
        if (tvQuestionTitle != null) tvQuestionTitle.setText(questions[index]);

        if (index == 0) {
            if (layoutNicknameInput != null) layoutNicknameInput.setVisibility(View.VISIBLE);
            if (cardOption1 != null) cardOption1.setVisibility(View.GONE);
            if (cardOption2 != null) cardOption2.setVisibility(View.GONE);
            if (cardOption3 != null) cardOption3.setVisibility(View.GONE);
            if (cardOption4 != null) cardOption4.setVisibility(View.GONE);
            if (cardOption5 != null) cardOption5.setVisibility(View.GONE);
            if (cardOption6 != null) cardOption6.setVisibility(View.GONE);
        } else {
            if (layoutNicknameInput != null) layoutNicknameInput.setVisibility(View.GONE);
            if (cardOption1 != null) cardOption1.setVisibility(View.VISIBLE);
            if (cardOption2 != null) cardOption2.setVisibility(View.VISIBLE);
            if (cardOption3 != null) cardOption3.setVisibility(View.VISIBLE);

            String[] options = optionsMatrix[index];

            setupCardOption(cardOption1, tvOptionText1, options[0], 0);
            setupCardOption(cardOption2, tvOptionText2, options[1], 1);
            setupCardOption(cardOption3, tvOptionText3, options.length > 2 ? options[2] : "", 2);

            if (options.length > 3) {
                if (cardOption4 != null) cardOption4.setVisibility(View.VISIBLE);
                setupCardOption(cardOption4, tvOptionText4, options[3], 3);
            } else {
                if (cardOption4 != null) cardOption4.setVisibility(View.GONE);
            }

            if (options.length > 4) {
                if (cardOption5 != null) cardOption5.setVisibility(View.VISIBLE);
                setupCardOption(cardOption5, tvOptionText5, options[4], 4);
            } else {
                if (cardOption5 != null) cardOption5.setVisibility(View.GONE);
            }

            if (options.length > 5) {
                if (cardOption6 != null) cardOption6.setVisibility(View.VISIBLE);
                setupCardOption(cardOption6, tvOptionText6, options[5], 5);
            } else {
                if (cardOption6 != null) cardOption6.setVisibility(View.GONE);
            }
        }

        updateStepIndicatorNodes(index);
    }

    private void setupCardOption(LinearLayout card, TextView tvText, String optionText, int optionIndex) {
        if (card == null || tvText == null) return;
        tvText.setText(optionText);

        card.setOnClickListener(v -> {
            if (isAnimating) return;
            isAnimating = true;

            highlightSelectedCard(optionIndex);
            userAnswers[currentQuestionIndex] = optionText;

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (currentQuestionIndex < questions.length - 1) {
                    currentQuestionIndex++;
                    displayQuestionWithAnimation(currentQuestionIndex, true);
                } else {
                    calculateAndSaveProfilingTiers();
                }
            }, 250);
        });
    }

    private void displayQuestionWithAnimation(int index, boolean animateForward) {
        if (frameAnimHost == null) {
            displayQuestion(index, animateForward);
            isAnimating = false;
            return;
        }

        Animation slideOut = AnimationUtils.loadAnimation(this,
                animateForward ? R.anim.slide_out_left : R.anim.slide_out_right);

        Animation slideIn = AnimationUtils.loadAnimation(this,
                animateForward ? R.anim.slide_in_right : R.anim.slide_in_left);

        slideOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                displayQuestion(index, animateForward);
                frameAnimHost.startAnimation(slideIn);
                isAnimating = false;
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        frameAnimHost.startAnimation(slideOut);
    }

    private void resetOptionCardStyles() {
        resetSingleCardStyle(cardOption1, tvOptionText1, tvOptionCheck1);
        resetSingleCardStyle(cardOption2, tvOptionText2, tvOptionCheck2);
        resetSingleCardStyle(cardOption3, tvOptionText3, tvOptionCheck3);
        resetSingleCardStyle(cardOption4, tvOptionText4, tvOptionCheck4);
        resetSingleCardStyle(cardOption5, tvOptionText5, tvOptionCheck5);
        resetSingleCardStyle(cardOption6, tvOptionText6, tvOptionCheck6);
    }

    private void resetSingleCardStyle(LinearLayout card, TextView tvText, TextView tvCheck) {
        if (card == null || tvText == null) return;
        card.setBackgroundResource(R.drawable.bg_button_pill);
        tvText.setTextColor(Color.parseColor("#546B41"));
        if (tvCheck != null) tvCheck.setVisibility(View.GONE);
    }

    private void highlightSelectedCard(int optionIndex) {
        resetOptionCardStyles();

        LinearLayout selectedCard = cardOption1;
        TextView selectedText = tvOptionText1;
        TextView selectedCheck = tvOptionCheck1;

        if (optionIndex == 1) {
            selectedCard = cardOption2;
            selectedText = tvOptionText2;
            selectedCheck = tvOptionCheck2;
        } else if (optionIndex == 2) {
            selectedCard = cardOption3;
            selectedText = tvOptionText3;
            selectedCheck = tvOptionCheck3;
        } else if (optionIndex == 3) {
            selectedCard = cardOption4;
            selectedText = tvOptionText4;
            selectedCheck = tvOptionCheck4;
        } else if (optionIndex == 4) {
            selectedCard = cardOption5;
            selectedText = tvOptionText5;
            selectedCheck = tvOptionCheck5;
        } else if (optionIndex == 5) {
            selectedCard = cardOption6;
            selectedText = tvOptionText6;
            selectedCheck = tvOptionCheck6;
        }

        if (selectedCard != null) {
            selectedCard.setBackgroundResource(R.drawable.bg_nav_item_selected);
        }
        if (selectedText != null) {
            selectedText.setTextColor(Color.parseColor("#546B41"));
        }
        if (selectedCheck != null) {
            selectedCheck.setVisibility(View.VISIBLE);
        }
    }

    private void updateStepIndicatorNodes(int index) {
        int currentGroup = index / 3; // Maps 12 questions across 4 progress node groups
        updateNodeState(node1, currentGroup >= 0, currentGroup > 0);
        updateNodeState(node2, currentGroup >= 1, currentGroup > 1);
        updateNodeState(node3, currentGroup >= 2, currentGroup > 2);
        updateNodeState(node4, currentGroup >= 3, currentGroup > 3);
    }

    private void updateNodeState(TextView node, boolean isReached, boolean isCompleted) {
        if (node == null) return;
        if (isCompleted) {
            node.setText("✔");
            node.setAlpha(1.0f);
        } else if (isReached) {
            node.setText("•");
            node.setAlpha(1.0f);
        } else {
            node.setText("○");
            node.setAlpha(0.4f);
        }
    }

    private void calculateAndSaveProfilingTiers() {
        String chosenNickname = userAnswers[0] != null ? userAnswers[0] : "Adventurer";
        String ageRangeStr = userAnswers[1] != null ? userAnswers[1] : "18–24";

        int ageInt = 21;
        if (ageRangeStr.contains("Under 18")) ageInt = 16;
        else if (ageRangeStr.contains("18–24")) ageInt = 21;
        else if (ageRangeStr.contains("25–35")) ageInt = 30;
        else if (ageRangeStr.contains("36–50")) ageInt = 42;
        else if (ageRangeStr.contains("51–64")) ageInt = 57;
        else if (ageRangeStr.contains("65")) ageInt = 68;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        DatabaseHelper.ensureUserTableExists(db);

        // Update nickname and age in user SQLite table
        ContentValues userValues = new ContentValues();
        userValues.put(DatabaseContract.UserEntry.COLUMN_NAME, chosenNickname);
        userValues.put("username", chosenNickname);
        userValues.put(DatabaseContract.UserEntry.COLUMN_AGE, ageInt);

        android.database.Cursor checkCursor = db.rawQuery("SELECT _id FROM user WHERE _id = 1", null);
        if (checkCursor != null && checkCursor.moveToFirst()) {
            db.update(DatabaseContract.UserEntry.TABLE_NAME, userValues, "_id = 1", null);
            checkCursor.close();
        } else {
            userValues.put("_id", 1);
            userValues.put(DatabaseContract.UserEntry.COLUMN_XP, 0);
            userValues.put(DatabaseContract.UserEntry.COLUMN_GOLD, 0);
            userValues.put(DatabaseContract.UserEntry.COLUMN_STREAK, 0);
            db.insert(DatabaseContract.UserEntry.TABLE_NAME, null, userValues);
            if (checkCursor != null) checkCursor.close();
        }

        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("isFirstRun", false)
                .putInt("pref_onboarding_step", 3)
                .putString("user_nickname", chosenNickname)
                .apply();

        double physicalMultiplier = 1.0;
        double detoxMultiplier = 1.0;
        double creativeMultiplier = 1.0;

        if (userAnswers[3] != null && (userAnswers[3].contains("5,000") || (userAnswers[4] != null && userAnswers[4].contains("3–5 days")))) {
            physicalMultiplier = 2.0;
        } else if (userAnswers[3] != null && (userAnswers[3].contains("8,000") || (userAnswers[4] != null && userAnswers[4].contains("6+ days")))) {
            physicalMultiplier = 3.5;
        }

        if (userAnswers[6] != null && (userAnswers[6].contains("6–8 hours") || (userAnswers[7] != null && userAnswers[7].contains("Frequently")))) {
            detoxMultiplier = 1.5;
        } else if (userAnswers[6] != null && (userAnswers[6].contains("More than 8 hours") || (userAnswers[7] != null && userAnswers[7].contains("Constantly")))) {
            detoxMultiplier = 2.0;
        }

        if (userAnswers[10] != null && userAnswers[10].contains("30–60 minutes")) {
            creativeMultiplier = 1.5;
        } else if (userAnswers[10] != null && userAnswers[10].contains("Over an hour")) {
            creativeMultiplier = 2.0;
        }

        saveMetricRow(db, "Physical Step Multiplier", String.valueOf(physicalMultiplier));
        saveMetricRow(db, "Detox Duration Multiplier", String.valueOf(detoxMultiplier));
        saveMetricRow(db, "Creative Activity Multiplier", String.valueOf(creativeMultiplier));
        saveMetricRow(db, "Preferred Offline Hobby Type", userAnswers[9] != null ? userAnswers[9] : "General");

        prefs.edit().putBoolean("isFirstRun", false).apply();

        // Sync profile state to Firestore cloud if user is logged in
        String userUid = prefs.getString("user_uid", null);
        if (userUid != null && !userUid.isEmpty()) {
            try {
                com.google.firebase.firestore.FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                java.util.Map<String, Object> updateMap = new java.util.HashMap<>();
                updateMap.put("username", chosenNickname);
                updateMap.put("age", ageInt);
                updateMap.put("level", 1);
                updateMap.put("gold", 0);
                updateMap.put("xp", 0);
                updateMap.put("streak", 0);
                firestore.collection("users").document(userUid).set(updateMap, com.google.firebase.firestore.SetOptions.merge());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        TaskManager taskManager = new TaskManager(ProfilingActivity.this);
        taskManager.generateDailyTasks();

        ToastUtils.showToast(this, "Profile Tier and Tasks Generated!");

        Intent intent = new Intent(ProfilingActivity.this, AppSelectionActivity.class);
        startActivity(intent);
        finish();
    }

    private void saveMetricRow(SQLiteDatabase db, String type, String value) {
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.PreferenceEntry.COLUMN_USER_REF, 1);
        values.put(DatabaseContract.PreferenceEntry.COLUMN_ACTIVITY_TYPE, type);
        values.put(DatabaseContract.PreferenceEntry.COLUMN_DIFFICULTY, value);
        db.insert(DatabaseContract.PreferenceEntry.TABLE_NAME, null, values);
    }
}
