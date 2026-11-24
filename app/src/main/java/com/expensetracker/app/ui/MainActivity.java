package com.expensetracker.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.expensetracker.app.R;
import com.expensetracker.app.data.SessionManager;
import com.expensetracker.app.databinding.ActivityMainBinding;

import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBars.apply(this, binding.getRoot(), binding.bottomNav);

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.navHost);
        NavController navController = host.getNavController();
        NavigationUI.setupWithNavController(binding.bottomNav, navController);   // keeps the highlighted tab in sync

        // our own tab switching: the default one restored Profile on top of old tabs
        binding.bottomNav.setOnItemSelectedListener(item -> {
            openTab(navController, item.getItemId());
            return true;
        });
        binding.bottomNav.setOnItemReselectedListener(item -> {
            NavDestination current = navController.getCurrentDestination();
            if (current != null && current.getId() != item.getItemId()) {
                openTab(navController, item.getItemId());   // e.g. Profile is open and its tab was tapped
            }
        });

        // the header shows the current screen's name (the label in nav_graph.xml)
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                binding.headerTitle.setText(destination.getLabel()));

        binding.headerUser.setText("USER: " + SessionManager.userName().toUpperCase(Locale.ROOT));
        binding.btnLogout.setOnClickListener(v -> {
            SessionManager.logout();
            goToLogin();
        });
        binding.btnProfile.setOnClickListener(v -> {
            NavDestination current = navController.getCurrentDestination();
            if (current == null || current.getId() != R.id.profileFragment) {   // not already on Profile
                navController.navigate(R.id.profileFragment);
            }
        });
    }

    private void goToLogin() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    public void refreshHeaderUser() {
        binding.headerUser.setText("USER: " + SessionManager.userName().toUpperCase(Locale.ROOT));
    }

    private void openTab(NavController navController, int tabId) {
        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                .build();
        try {
            navController.navigate(tabId, null, options);
        } catch (IllegalArgumentException ignored) {
            // not a known destination: do nothing
        }
    }
}