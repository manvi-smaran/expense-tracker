package com.expensetracker.app.ui.profile;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.expensetracker.app.R;
import com.expensetracker.app.data.ApiCallback;
import com.expensetracker.app.data.ApiClient;
import com.expensetracker.app.data.SessionManager;
import com.expensetracker.app.databinding.FragmentProfileBinding;
import com.expensetracker.app.model.PetStatus;
import com.expensetracker.app.model.User;
import com.expensetracker.app.ui.AuthActivity;
import com.expensetracker.app.ui.Fmt;

import androidx.appcompat.app.AlertDialog;
import android.view.View;
import com.expensetracker.app.databinding.DialogEditNameBinding;
import com.expensetracker.app.model.UpdateProfileRequest;
import com.expensetracker.app.ui.MainActivity;
import com.expensetracker.app.ui.Dialogs;

import java.time.LocalDate;
import java.util.Locale;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // show what the phone already knows straight away, then refresh from the server
        showUser(SessionManager.userName(), SessionManager.userEmail());
        binding.textFooter.setText("EXPENSE TRACKER v" + versionName() + "\nSERVER: " + ApiClient.BASE_URL);

        int plum = Fmt.primaryDark(requireContext());
        binding.progressXp.setProgressTintList(ColorStateList.valueOf(Fmt.color(requireContext(), R.color.green_dark)));
        binding.progressMood.setProgressTintList(ColorStateList.valueOf(plum));

        binding.btnLogout.setOnClickListener(v -> {
            SessionManager.logout();
            Intent intent = new Intent(requireContext(), AuthActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });

        binding.btnEditName.setOnClickListener(v -> showEditNameDialog());

        loadUser();
        loadPet();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;   // late replies from the server check for this and stop
    }

    private void loadUser() {
        ApiClient.get().me().enqueue(new ApiCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (binding == null || user == null) return;
                showUser(user.name, user.email);
            }

            @Override
            public void onError(String message) {
                // keep showing what the phone already knows
            }
        });
    }

    private void loadPet() {
        ApiClient.get().pet(LocalDate.now().toString()).enqueue(new ApiCallback<PetStatus>() {
            @Override
            public void onSuccess(PetStatus p) {
                if (binding == null || p == null) return;
                binding.chipLevel.setText(String.format(Locale.US, "LVL %02d %s", p.level, p.title));
                binding.textStreak.setText("● " + p.streakDays + " DAY STREAK");
                binding.textXp.setText(String.format(Locale.US, "%,d / %,d XP", p.xpIntoLevel, p.xpForLevel));
                binding.progressXp.setProgress(p.levelPercent);
                binding.textTotalXp.setText(String.format(Locale.US, "TOTAL XP: %,d", p.xp));
                binding.textMood.setText("MOOD: " + p.mood + " " + p.moodPercent + "%");
                binding.progressMood.setProgress(p.moodPercent);
                binding.textPetMessage.setText("\"" + p.message + "\"");
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                binding.textMood.setText("MOOD: OFFLINE");
                binding.textPetMessage.setText("Can't reach Buddy Bot right now: " + message);
            }
        });
    }

    private void showUser(String name, String email) {
        String shownName = name == null ? "" : name;
        binding.textName.setText(shownName);
        binding.fieldName.setText(shownName);
        binding.fieldEmail.setText(email == null ? "" : email);
        binding.textAvatar.setText(shownName.isEmpty() ? "?" : shownName.substring(0, 1).toUpperCase(Locale.ROOT));
    }

    private String versionName() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private void showEditNameDialog() {
        DialogEditNameBinding d = DialogEditNameBinding.inflate(getLayoutInflater());
        d.inputName.setText(SessionManager.userName());
        d.inputName.setSelection(d.inputName.getText().length());

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(d.getRoot()).create();
        d.btnDiscard.setOnClickListener(v -> dialog.dismiss());
        d.btnSaveName.setOnClickListener(v -> {
            String name = d.inputName.getText().toString().trim();
            if (name.isEmpty()) {
                d.textError.setText("Codename can't be empty");
                d.textError.setVisibility(View.VISIBLE);
                return;
            }
            d.btnSaveName.setEnabled(false);
            ApiClient.get().updateMe(new UpdateProfileRequest(name)).enqueue(new ApiCallback<User>() {
                @Override
                public void onSuccess(User user) {
                    dialog.dismiss();
                    if (user == null) return;
                    SessionManager.saveName(user.name);
                    if (binding != null) showUser(user.name, user.email);
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).refreshHeaderUser();
                    }
                }

                @Override
                public void onError(String message) {
                    d.btnSaveName.setEnabled(true);
                    d.textError.setText(message);
                    d.textError.setVisibility(View.VISIBLE);
                }
            });
        });
        dialog.show();
        Dialogs.style(dialog);
    }
}