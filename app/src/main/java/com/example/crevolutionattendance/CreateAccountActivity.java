package com.example.crevolutionattendance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class CreateAccountActivity extends AppCompatActivity {

    private EditText pinNumber, firstName, lastName;
    private MaterialButton createAccountButton;
    private ImageView returnButton;
    private FirebaseHelper firebaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_account);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.createAccount), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        pinNumber = findViewById(R.id.pinNumber);
        createAccountButton = findViewById(R.id.createAccountButton);
        returnButton = findViewById(R.id.returnButton);
        firebaseHelper = new FirebaseHelper();
        firstName = findViewById(R.id.firstName);
        lastName = findViewById(R.id.lastName);

        createAccountButton.setOnClickListener(v -> {
            String username = (firstName.getText().toString() + " " + lastName.getText().toString());
            String email = pinNumber.getText().toString() + "@app.com";
            String password = pinNumber.getText().toString();

            if (password.isEmpty()) {
                Toast.makeText(this, "Fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            firebaseHelper.registerUser(email, password, username, new FirebaseHelper.FirebaseCallback() {
                @Override
                public void onSuccess() {
                    Toast.makeText(CreateAccountActivity.this, "Account created!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(CreateAccountActivity.this, SignInActivity.class));
                    finish();
                }

                @Override
                public void onFailure(String error) {
                    Toast.makeText(CreateAccountActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        returnButton.setOnClickListener(v -> startActivity(new Intent(CreateAccountActivity.this, SignInActivity.class)));

    }
}
