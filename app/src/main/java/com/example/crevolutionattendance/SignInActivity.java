package com.example.crevolutionattendance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class SignInActivity extends AppCompatActivity {

    private EditText signInPin;
    private MaterialButton signInButton, createAccountButton;
    private FirebaseHelper firebaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_in);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.signIn), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        signInPin = findViewById(R.id.signInPIN);
        signInButton = findViewById(R.id.signInButton);
        createAccountButton = findViewById(R.id.createAccountButton);
        firebaseHelper = new FirebaseHelper();

        signInButton.setOnClickListener(v -> {
            String email = signInPin.getText().toString() + "@app.com";
            String password = signInPin.getText().toString();

            if (password.isEmpty()) {
                Toast.makeText(this, "Fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            firebaseHelper.signInUser(email, password, new FirebaseHelper.FirebaseCallback() {
                @Override
                public void onSuccess() {
                    Toast.makeText(SignInActivity.this, "Signed in!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(SignInActivity.this, MainActivity.class));
                    finish();
                }

                @Override
                public void onFailure(String error) {
                    Toast.makeText(SignInActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        createAccountButton.setOnClickListener(v -> startActivity(new Intent(SignInActivity.this, CreateAccountActivity.class)));
    }
}
