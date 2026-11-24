package com.expensetracker.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.expensetracker.app.data.ApiCallback;
import com.expensetracker.app.data.ApiClient;
import com.expensetracker.app.data.SessionManager;
import com.expensetracker.app.databinding.ActivityAuthBinding;
import com.expensetracker.app.model.AuthResponse;
import com.expensetracker.app.model.LoginRequest;
import com.expensetracker.app.model.RegisterRequest;

public class AuthActivity extends AppCompatActivity {
    private ActivityAuthBinding binding;
    private boolean registerMode = false;
    private boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (SessionManager.isLoggedIn()) {   // already logged in: skip straight to the app
            openMain();
            return;
        }

        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this, binding.getRoot(), null);

        binding.tabLogin.setOnClickListener(v -> setMode(false));
        binding.tabRegister.setOnClickListener(v -> setMode(true));
        binding.linkSwitch.setOnClickListener(v -> setMode(!registerMode));
        binding.btnShowPassword.setOnClickListener(v -> togglePassword());
        binding.btnSubmit.setOnClickListener(v -> submit());
        binding.inputPassword.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });

        setMode(false);
    }

    private void setMode(boolean register) {
        registerMode = register;
        binding.textError.setVisibility(View.GONE);

        int nameVisibility = register ? View.VISIBLE : View.GONE;
        binding.labelName.setVisibility(nameVisibility);
        binding.inputName.setVisibility(nameVisibility);

        binding.tabLogin.setSelected(!register);
        binding.tabRegister.setSelected(register);
        binding.btnSubmit.setText(register ? "CREATE ACCOUNT" : "LOG IN");
        binding.linkSwitch.setText(register ? "Already registered? Log in here" : "New here? Create an account");
        binding.buddyText.setText(register
                ? "New trainer? Create an account to hatch your pet!"
                : "Welcome back, Trainer! Ready to log your expenses?");
    }

    private void togglePassword() {
        passwordVisible = !passwordVisible;
        binding.inputPassword.setTransformationMethod(passwordVisible
                ? HideReturnsTransformationMethod.getInstance()
                : PasswordTransformationMethod.getInstance());
        binding.inputPassword.setSelection(binding.inputPassword.getText().length());
        binding.btnShowPassword.setText(passwordVisible ? "HIDE" : "SHOW");
    }

    private void submit() {
        String name = text(binding.inputName);
        String email = text(binding.inputEmail);
        String password = binding.inputPassword.getText().toString();

        if (registerMode && name.isEmpty()) {
            showError("Enter a username");
            return;
        }
        if (email.isEmpty() || password.isEmpty()) {
            showError("Enter your email and password");
            return;
        }

        binding.textError.setVisibility(View.GONE);
        setBusy(true);

        ApiCallback<AuthResponse> callback = new ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse body) {
                SessionManager.save(body);
                openMain();
            }

            @Override
            public void onError(String message) {
                if (isDestroyed()) return;
                setBusy(false);
                showError(message);
            }
        };

        if (registerMode) {
            ApiClient.get().register(new RegisterRequest(name, email, password)).enqueue(callback);
        } else {
            ApiClient.get().login(new LoginRequest(email, password)).enqueue(callback);
        }
    }

    private void setBusy(boolean busy) {
        binding.btnSubmit.setEnabled(!busy);
        binding.btnSubmit.setAlpha(busy ? 0.6f : 1f);
        if (busy) {
            binding.btnSubmit.setText("LOADING...");
        } else {
            setMode(registerMode);
        }
    }

    private void showError(String message) {
        binding.textError.setText(message);
        binding.textError.setVisibility(View.VISIBLE);
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private static String text(EditText field) {
        return field.getText().toString().trim();
    }
}