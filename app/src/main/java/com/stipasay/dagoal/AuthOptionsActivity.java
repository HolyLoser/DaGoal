package com.stipasay.dagoal;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;

public class AuthOptionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_options);

        ImageButton btnBack = findViewById(R.id.btn_back_options);
        Button btnEmail = findViewById(R.id.btn_opt_email);
        View btnGmail = findViewById(R.id.btn_opt_gmail);
        View btnFacebook = findViewById(R.id.btn_opt_facebook);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnEmail != null) {
            btnEmail.setOnClickListener(v -> {
                Intent intent = new Intent(AuthOptionsActivity.this, AuthEmailActivity.class);
                startActivity(intent);
            });
        }

        if (btnGmail != null) {
            btnGmail.setOnClickListener(v ->
                    ToastUtils.showToast(this, "Gmail sign-in coming soon!")
            );
        }

        if (btnFacebook != null) {
            btnFacebook.setOnClickListener(v ->
                    ToastUtils.showToast(this, "Facebook sign-in coming soon!")
            );
        }
    }
}
