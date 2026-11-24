package com.expensetracker.app.ui.budgets;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.expensetracker.app.R;
import com.expensetracker.app.data.ApiCallback;
import com.expensetracker.app.data.ApiClient;
import com.expensetracker.app.databinding.FragmentBudgetsBinding;
import com.expensetracker.app.model.BudgetRequest;
import com.expensetracker.app.model.BudgetStatus;
import com.expensetracker.app.model.CategoryItem;
import com.expensetracker.app.model.PetStatus;
import com.expensetracker.app.ui.Fmt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class BudgetsFragment extends Fragment {
    private FragmentBudgetsBinding binding;
    private BudgetAdapter adapter;

    private final List<CategoryItem> categories = new ArrayList<>();
    private final List<BudgetStatus> statuses = new ArrayList<>();
    private String currentAlertKey = null;
    private String dismissedKey = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBudgetsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new BudgetAdapter();
        binding.listBudgets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listBudgets.setAdapter(adapter);
        binding.listBudgets.setNestedScrollingEnabled(false);   // the whole screen scrolls as one

        binding.inputAlert.setText("80");
        binding.btnSave.setOnClickListener(v -> save());
        binding.btnRetry.setOnClickListener(v -> loadAll());
        binding.bannerClose.setOnClickListener(v -> {
            dismissedKey = currentAlertKey;
            binding.banner.setVisibility(View.GONE);
        });

        loadAll();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;   // late replies from the server check for this and stop
    }

    // ------------------------------------------------------------ loading

    private void loadAll() {
        binding.errorState.setVisibility(View.GONE);
        binding.textLoading.setVisibility(View.VISIBLE);
        loadCategories();
        loadBudgets();
        loadPet();
    }

    private void loadCategories() {
        ApiClient.get().categories().enqueue(new ApiCallback<List<CategoryItem>>() {
            @Override
            public void onSuccess(List<CategoryItem> body) {
                if (binding == null) return;
                categories.clear();
                if (body != null) categories.addAll(body);

                List<String> names = new ArrayList<>();
                for (CategoryItem c : categories) {
                    names.add(c.name);
                }
                ArrayAdapter<String> spinnerAdapter =
                        new ArrayAdapter<>(requireContext(), R.layout.item_spinner, names);
                spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
                binding.spinnerCategory.setAdapter(spinnerAdapter);
                binding.spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        prefillForSelection();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                formMessage("Couldn't load categories: " + message, true);
            }
        });
    }

    private void loadBudgets() {
        ApiClient.get().budgets(YearMonth.now().toString()).enqueue(new ApiCallback<List<BudgetStatus>>() {
            @Override
            public void onSuccess(List<BudgetStatus> body) {
                if (binding == null) return;
                statuses.clear();
                if (body != null) statuses.addAll(body);
                adapter.submit(statuses);

                boolean empty = statuses.isEmpty();
                binding.textLoading.setVisibility(View.GONE);
                binding.errorState.setVisibility(View.GONE);
                binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.listBudgets.setVisibility(empty ? View.GONE : View.VISIBLE);
                binding.textActive.setText(statuses.size() + " ACTIVE");

                updateBanner();
                prefillForSelection();
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                binding.textLoading.setVisibility(View.GONE);
                binding.listBudgets.setVisibility(View.GONE);
                binding.emptyState.setVisibility(View.GONE);
                binding.errorState.setVisibility(View.VISIBLE);
                binding.textError.setText(message);
            }
        });
    }

    private void loadPet() {
        ApiClient.get().pet(LocalDate.now().toString()).enqueue(new ApiCallback<PetStatus>() {
            @Override
            public void onSuccess(PetStatus p) {
                if (binding == null || p == null) return;
                binding.textBuddy.setText("\"" + p.budgetTip + "\"");
            }

            @Override
            public void onError(String message) {
                // the tip is decoration: stay quiet if it can't load
            }
        });
    }

    // ---------------------------------------------------------- the form

    // When you pick a category that already has a budget, show its current values
    private void prefillForSelection() {
        int position = binding.spinnerCategory.getSelectedItemPosition();
        if (position < 0 || position >= categories.size()) {
            return;
        }
        int categoryId = categories.get(position).id;
        for (BudgetStatus s : statuses) {
            if (s.categoryId == categoryId) {
                binding.inputLimit.setText(s.limit.stripTrailingZeros().toPlainString());
                binding.inputAlert.setText(String.valueOf(s.alertThresholdPercent));
                return;
            }
        }
        binding.inputLimit.setText("");
        binding.inputAlert.setText("80");
    }

    private void save() {
        int position = binding.spinnerCategory.getSelectedItemPosition();
        if (position < 0 || position >= categories.size()) {
            formMessage("Pick a category first", true);
            return;
        }
        BigDecimal limit = Fmt.parseAmount(binding.inputLimit.getText().toString());
        if (limit == null) {
            formMessage("Enter a limit above 0, e.g. 250", true);
            return;
        }
        int alert;
        try {
            alert = Integer.parseInt(binding.inputAlert.getText().toString().trim());
        } catch (NumberFormatException e) {
            formMessage("Alert % must be a whole number", true);
            return;
        }
        if (alert < 1 || alert > 100) {
            formMessage("Alert % must be between 1 and 100", true);
            return;
        }

        CategoryItem category = categories.get(position);
        setSaveBusy(true);
        ApiClient.get().saveBudget(category.id, new BudgetRequest(limit, alert)).enqueue(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void body) {
                if (binding == null) return;
                setSaveBusy(false);
                formMessage("BUDGET SAVED for " + category.name, false);
                loadBudgets();
                loadPet();
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                setSaveBusy(false);
                formMessage(message, true);
            }
        });
    }

    private void setSaveBusy(boolean busy) {
        binding.btnSave.setEnabled(!busy);
        binding.btnSave.setAlpha(busy ? 0.6f : 1f);
        binding.btnSave.setText(busy ? "SAVING..." : "SAVE BUDGET");
    }

    private void formMessage(String text, boolean error) {
        binding.formMessage.setText(text);
        binding.formMessage.setTextColor(Fmt.color(requireContext(), error ? R.color.red : R.color.green_dark));
        binding.formMessage.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------- the watchdog banner

    // Shows the most serious alert (over budget beats threshold reached). Dismissing hides that
    // exact alert until something changes.
    private void updateBanner() {
        BudgetStatus worst = null;
        int alertCount = 0;
        for (BudgetStatus s : statuses) {
            if ("OK".equals(s.status)) continue;
            alertCount++;
            if (worst == null || rank(s) > rank(worst)
                    || (rank(s) == rank(worst) && s.percent.compareTo(worst.percent) > 0)) {
                worst = s;
            }
        }
        if (worst == null) {
            currentAlertKey = null;
            dismissedKey = null;
            binding.banner.setVisibility(View.GONE);
            return;
        }

        currentAlertKey = worst.categoryId + ":" + worst.status + ":" + worst.percent.toPlainString();
        if (currentAlertKey.equals(dismissedKey)) {
            binding.banner.setVisibility(View.GONE);
            return;
        }

        boolean exceeded = "EXCEEDED".equals(worst.status);
        String text = exceeded
                ? worst.categoryName + " is over budget (" + Fmt.percent(worst.percent) + " of limit)"
                : worst.categoryName + " hit " + Fmt.percent(worst.percent)
                + " (>" + worst.alertThresholdPercent + "% Alert Threshold)";
        if (alertCount > 1) {
            text += " +" + (alertCount - 1) + " more";
        }

        int fill = Fmt.color(requireContext(), exceeded ? R.color.red_container : R.color.amber_container);
        int textColor = exceeded ? Fmt.color(requireContext(), R.color.red) : Fmt.primaryDark(requireContext());
        binding.banner.setBackground(Fmt.box(requireContext(), fill, Fmt.primaryDark(requireContext()), 2, 4));
        binding.bannerText.setText(text);
        binding.bannerText.setTextColor(textColor);
        binding.bannerTitle.setTextColor(textColor);
        binding.banner.setVisibility(View.VISIBLE);
    }

    private static int rank(BudgetStatus s) {
        return "EXCEEDED".equals(s.status) ? 2 : 1;
    }
}