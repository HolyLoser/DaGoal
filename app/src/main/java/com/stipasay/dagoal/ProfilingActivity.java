package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ProfilingActivity extends AppCompatActivity {

    private TextView tvCategoryTitle;
    private TextView tvQuestionTitle;
    private RadioGroup rgOptions;
    private Button btnNextQuestion;

    private int currentQuestionIndex = 0;

    private final String[] categories = {
            "Physical Mobility", "Physical Mobility", "Physical Mobility",
            "Screen Time & Digital Habits", "Screen Time & Digital Habits", "Screen Time & Digital Habits",
            "Creative Outlets & Hobbies", "Creative Outlets & Hobbies", "Creative Outlets & Hobbies"
    };

    private final String[] questions = {
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

    private final String[] userAnswers = new String[9];
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profiling_questionnaire);

        dbHelper = new DatabaseHelper(this);

        tvCategoryTitle = findViewById(R.id.tv_category_title);
        tvQuestionTitle = findViewById(R.id.tv_question_title);
        rgOptions = findViewById(R.id.rg_options);
        btnNextQuestion = findViewById(R.id.btn_next_question);

        View rootLayout = findViewById(R.id.root_profiling_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        displayQuestion(currentQuestionIndex);

        btnNextQuestion.setOnClickListener(v -> handleNextClick());
    }

    private void displayQuestion(int index) {
        tvCategoryTitle.setText(categories[index].toUpperCase());
        tvQuestionTitle.setText(questions[index]);
        rgOptions.removeAllViews();

        for (String optionText : optionsMatrix[index]) {
            RadioButton rb = new RadioButton(this);
            rb.setText(optionText);
            rb.setTextSize(16);
            rb.setTextColor(Color.BLACK);
            rb.setPadding(12, 12, 12, 12);
            rgOptions.addView(rb);
        }
    }

    private void handleNextClick() {
        int selectedId = rgOptions.getCheckedRadioButtonId();

        if (selectedId == -1) {
            Toast.makeText(this, "Please select an option to proceed.", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRb = findViewById(selectedId);

        if (selectedRb == null) {
            Toast.makeText(this, "Selection error. Please choose again.", Toast.LENGTH_SHORT).show();
            return;
        }

        userAnswers[currentQuestionIndex] = selectedRb.getText().toString();

        if (currentQuestionIndex < 8) {
            currentQuestionIndex++;
            displayQuestion(currentQuestionIndex);
        } else {
            calculateAndSaveProfilingTiers();
        }
    }

    private void calculateAndSaveProfilingTiers() {
        double physicalMultiplier = 1.0;
        double detoxMultiplier = 1.0;
        double creativeMultiplier = 1.0;

        if (userAnswers[0].contains("5,000") || userAnswers[1].contains("3–5 days")) {
            physicalMultiplier = 2.0;
        } else if (userAnswers[0].contains("8,000") || userAnswers[1].contains("6+ days")) {
            physicalMultiplier = 3.5;
        }

        if (userAnswers[3].contains("6–8 hours") || userAnswers[4].contains("Frequently")) {
            detoxMultiplier = 1.5;
        } else if (userAnswers[3].contains("More than 8 hours") || userAnswers[4].contains("Constantly")) {
            detoxMultiplier = 2.0;
        }

        if (userAnswers[7].contains("30–60 minutes")) {
            creativeMultiplier = 1.5;
        } else if (userAnswers[7].contains("Over an hour")) {
            creativeMultiplier = 2.0;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        saveMetricRow(db, "Physical Step Multiplier", String.valueOf(physicalMultiplier));
        saveMetricRow(db, "Detox Duration Multiplier", String.valueOf(detoxMultiplier));
        saveMetricRow(db, "Creative Activity Multiplier", String.valueOf(creativeMultiplier));
        saveMetricRow(db, "Preferred Offline Hobby Type", userAnswers[6]);

        TaskManager taskManager = new TaskManager(ProfilingActivity.this);
        taskManager.generateDailyTasks();

        Toast.makeText(this, "Profile Tier and Tasks Generated!", Toast.LENGTH_LONG).show();

        Intent intent = new Intent(ProfilingActivity.this, DailyRevealActivity.class);
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
