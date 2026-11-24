package com.expensetracker.app.ui.expenses;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.expensetracker.app.R;
import com.expensetracker.app.data.ApiCallback;
import com.expensetracker.app.data.ApiClient;
import com.expensetracker.app.databinding.DialogEditExpenseBinding;
import com.expensetracker.app.databinding.FragmentExpensesBinding;
import com.expensetracker.app.model.BudgetAlert;
import com.expensetracker.app.model.CategoryItem;
import com.expensetracker.app.model.DayTotal;
import com.expensetracker.app.model.Expense;
import com.expensetracker.app.model.ExpensePage;
import com.expensetracker.app.model.ExpenseRequest;
import com.expensetracker.app.model.ExpenseResponse;
import com.expensetracker.app.model.ExpenseRow;
import com.expensetracker.app.model.PetStatus;
import com.expensetracker.app.ui.Dialogs;
import com.expensetracker.app.ui.Fmt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public class ExpensesFragment extends Fragment implements ExpenseAdapter.Listener {
    private static final int PAGE_SIZE = 10;

    private enum Kind { SUCCESS, WARNING, EXCEEDED, ERROR }

    private FragmentExpensesBinding binding;
    private ExpenseAdapter adapter;

    private final List<CategoryItem> categories = new ArrayList<>();
    private LocalDate formDate = LocalDate.now();
    private Integer filterCategoryId = null;   // null = ALL ITEMS
    private int page = 1;
    private int totalPages = 1;
    private int highlightId = -1;
    private boolean formOpen = true;

    private final Runnable hideBanner = () -> {
        if (binding != null) {
            binding.banner.setVisibility(View.GONE);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExpensesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new ExpenseAdapter(this);
        binding.listExpenses.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listExpenses.setAdapter(adapter);
        binding.listExpenses.setNestedScrollingEnabled(false);   // the whole screen scrolls as one

        binding.textDate.setText(Fmt.displayDate(formDate));
        binding.textDate.setOnClickListener(v -> pickDate(formDate, picked -> {
            formDate = picked;
            binding.textDate.setText(Fmt.displayDate(picked));
        }));

        binding.btnToggleForm.setOnClickListener(v -> {
            formOpen = !formOpen;
            binding.formBody.setVisibility(formOpen ? View.VISIBLE : View.GONE);
            binding.btnToggleForm.setText(formOpen ? "_" : "+");
        });
        binding.btnRecord.setOnClickListener(v -> record());
        binding.btnPrev.setOnClickListener(v -> {
            if (page > 1) {
                page--;
                loadExpenses();
            }
        });
        binding.btnNext.setOnClickListener(v -> {
            if (page < totalPages) {
                page++;
                loadExpenses();
            }
        });
        binding.btnRetry.setOnClickListener(v -> loadAll());
        binding.bannerClose.setOnClickListener(v -> binding.banner.setVisibility(View.GONE));

        loadAll();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.banner.removeCallbacks(hideBanner);
        }
        binding = null;   // late replies from the server check for this and stop
    }

    // ------------------------------------------------------------ loading

    private void loadAll() {
        ApiClient.get().categories().enqueue(new ApiCallback<List<CategoryItem>>() {
            @Override
            public void onSuccess(List<CategoryItem> body) {
                if (binding == null) return;
                categories.clear();
                if (body != null) categories.addAll(body);
                bindCategorySpinners();
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                showBanner("Couldn't load categories: " + message, Kind.ERROR, 5000);
            }
        });
        loadVault();
        loadPet();
        loadExpenses();
    }

    private void loadVault() {
        ApiClient.get().dayTotal(LocalDate.now().toString()).enqueue(new ApiCallback<DayTotal>() {
            @Override
            public void onSuccess(DayTotal body) {
                if (binding == null || body == null) return;
                binding.textToday.setText("TODAY: " + Fmt.money(body.total));
            }

            @Override
            public void onError(String message) {
                // the log below shows the error; leave the vault as it is
            }
        });
    }

    private void loadPet() {
        ApiClient.get().pet(LocalDate.now().toString()).enqueue(new ApiCallback<PetStatus>() {
            @Override
            public void onSuccess(PetStatus p) {
                if (binding == null || p == null) return;
                binding.textPetMessage.setText("\"" + p.message + "\"");
                binding.textPetLevel.setText(String.format(Locale.US, "LVL %02d", p.level));
                binding.progressPet.setProgress(p.levelPercent);
                binding.textXp.setText("XP " + p.xp);
            }

            @Override
            public void onError(String message) {
                // the pet is decoration: stay quiet if it can't load
            }
        });
    }

    private void loadExpenses() {
        setSync("SYNCING");
        ApiClient.get().expenses(page, PAGE_SIZE, filterCategoryId).enqueue(new ApiCallback<ExpensePage>() {
            @Override
            public void onSuccess(ExpensePage body) {
                if (binding == null || body == null) return;
                page = body.page;           // the server clamps out-of-range pages
                totalPages = body.totalPages;
                adapter.submit(body.rows, highlightId);

                boolean empty = body.totalItems == 0;
                binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.listExpenses.setVisibility(empty ? View.GONE : View.VISIBLE);
                binding.columnHeader.setVisibility(empty ? View.GONE : View.VISIBLE);
                binding.errorState.setVisibility(View.GONE);

                binding.textLogTitle.setText("Expenses (" + body.totalItems + ")");
                binding.textPage.setText(page + "/" + totalPages);
                setEnabledLook(binding.btnPrev, page > 1);
                setEnabledLook(binding.btnNext, page < totalPages);
                setSync("ONLINE");
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                binding.listExpenses.setVisibility(View.GONE);
                binding.emptyState.setVisibility(View.GONE);
                binding.columnHeader.setVisibility(View.GONE);
                binding.errorState.setVisibility(View.VISIBLE);
                binding.textError.setText(message);
                setSync("OFFLINE");
            }
        });
    }

    private void bindCategorySpinners() {
        List<String> names = new ArrayList<>();
        for (CategoryItem c : categories) {
            names.add(c.name);
        }

        // form dropdown (keeps the current choice if there is one)
        int previous = binding.spinnerCategory.getSelectedItemPosition();
        binding.spinnerCategory.setAdapter(spinnerAdapter(names));
        if (previous >= 0 && previous < names.size()) {
            binding.spinnerCategory.setSelection(previous);
        }

        // opened from the Charts screen's "ADD ITEM TO THIS CATEGORY" button
        Bundle args = getArguments();
        if (args != null && args.containsKey("categoryId")) {
            int wanted = args.getInt("categoryId");
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).id == wanted) {
                    binding.spinnerCategory.setSelection(i);
                }
            }
        }

        // filter dropdown: ALL ITEMS + every category
        List<String> filterNames = new ArrayList<>();
        filterNames.add("ALL ITEMS");
        filterNames.addAll(names);
        binding.spinnerFilter.setAdapter(spinnerAdapter(filterNames));
        int selected = 0;
        for (int i = 0; i < categories.size(); i++) {
            if (filterCategoryId != null && categories.get(i).id == filterCategoryId) {
                selected = i + 1;
            }
        }
        binding.spinnerFilter.setSelection(selected, false);
        binding.spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Integer newFilter = position == 0 || position > categories.size()
                        ? null : categories.get(position - 1).id;
                if (!Objects.equals(newFilter, filterCategoryId)) {
                    filterCategoryId = newFilter;
                    page = 1;
                    loadExpenses();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private ArrayAdapter<String> spinnerAdapter(List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner, items);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        return adapter;
    }

    // ----------------------------------------------------------- recording

    private void record() {
        int position = binding.spinnerCategory.getSelectedItemPosition();
        if (position < 0 || position >= categories.size()) {
            showBanner("Pick a category first", Kind.ERROR, 4000);
            return;
        }
        BigDecimal amount = parseAmount(binding.inputAmount.getText().toString());
        if (amount == null) {
            showBanner("Enter a valid amount, e.g. 50.00", Kind.ERROR, 4000);
            return;
        }
        String item = binding.inputItem.getText().toString().trim();
        if (item.isEmpty()) {
            showBanner("Enter an item name", Kind.ERROR, 4000);
            return;
        }

        setRecordBusy(true);
        ExpenseRequest request = new ExpenseRequest(
                categories.get(position).id, amount, item, formDate.toString(), null);
        ApiClient.get().addExpense(request).enqueue(new ApiCallback<ExpenseResponse>() {
            @Override
            public void onSuccess(ExpenseResponse body) {
                if (binding == null || body == null) return;
                setRecordBusy(false);
                binding.inputAmount.setText("");
                binding.inputItem.setText("");
                highlightId = body.expense.id;
                page = 1;
                refreshData();
                announce(body.alert, "Expense saved! +10 XP");
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                setRecordBusy(false);
                showBanner(message, Kind.ERROR, 5000);
            }
        });
    }

    private void refreshData() {
        loadExpenses();
        loadVault();
        loadPet();
    }

    private void setRecordBusy(boolean busy) {
        binding.btnRecord.setEnabled(!busy);
        binding.btnRecord.setAlpha(busy ? 0.6f : 1f);
        binding.btnRecord.setText(busy ? "SAVING..." : "RECORD EXPENSE");
    }

    // ------------------------------------------------------ edit / delete

    @Override
    public void onEdit(ExpenseRow row) {
        if (categories.isEmpty()) {
            showBanner("Categories haven't loaded yet. Try again in a moment.", Kind.ERROR, 4000);
            return;
        }
        Expense e = row.expense;
        DialogEditExpenseBinding d = DialogEditExpenseBinding.inflate(getLayoutInflater());

        List<String> names = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < categories.size(); i++) {
            names.add(categories.get(i).name);
            if (e.categoryId != null && categories.get(i).id == e.categoryId) {
                selected = i;
            }
        }
        d.spinnerCategory.setAdapter(spinnerAdapter(names));
        d.spinnerCategory.setSelection(selected);

        d.textHint.setText("Updating expense log #" + e.id + ". Keep accurate receipts!");
        d.inputItem.setText(e.description);
        d.inputAmount.setText(e.amount.toPlainString());
        d.inputNote.setText(e.note == null ? "" : e.note);

        final LocalDate[] date = { LocalDate.parse(e.expenseDate) };
        d.textDate.setText(Fmt.displayDate(date[0]));
        d.textDate.setOnClickListener(v -> pickDate(date[0], picked -> {
            date[0] = picked;
            d.textDate.setText(Fmt.displayDate(picked));
        }));

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(d.getRoot()).create();
        d.btnCancel.setOnClickListener(v -> dialog.dismiss());
        d.btnUpdate.setOnClickListener(v -> {
            int position = d.spinnerCategory.getSelectedItemPosition();
            BigDecimal amount = parseAmount(d.inputAmount.getText().toString());
            String item = d.inputItem.getText().toString().trim();
            if (position < 0 || position >= categories.size() || amount == null || item.isEmpty()) {
                showBanner("Check the category, item and amount", Kind.ERROR, 4000);
                return;
            }
            String note = d.inputNote.getText().toString().trim();
            ExpenseRequest request = new ExpenseRequest(categories.get(position).id, amount, item,
                    date[0].toString(), note.isEmpty() ? null : note);

            d.btnUpdate.setEnabled(false);
            ApiClient.get().updateExpense(e.id, request).enqueue(new ApiCallback<ExpenseResponse>() {
                @Override
                public void onSuccess(ExpenseResponse body) {
                    dialog.dismiss();
                    if (binding == null || body == null) return;
                    highlightId = body.expense.id;
                    refreshData();
                    announce(body.alert, "Expense updated");
                }

                @Override
                public void onError(String message) {
                    d.btnUpdate.setEnabled(true);
                    if (binding == null) return;
                    showBanner(message, Kind.ERROR, 5000);
                }
            });
        });
        dialog.show();
        Dialogs.style(dialog);
    }

    @Override
    public void onDelete(ExpenseRow row) {
        Expense e = row.expense;
        Dialogs.confirm(requireContext(), "CONFIRM DELETE",
                "Delete \"" + e.description + "\" (" + Fmt.money(e.amount) + ")?\nThis can't be undone.",
                "YES, DELETE", () -> ApiClient.get().deleteExpense(e.id).enqueue(new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void body) {
                        if (binding == null) return;
                        highlightId = -1;
                        refreshData();
                        showBanner("Expense deleted", Kind.SUCCESS, 3000);
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        showBanner(message, Kind.ERROR, 5000);
                    }
                }));
    }

    // ------------------------------------------------------------- helpers

    // Budget alert if one came back, otherwise the plain "saved" toast
    private void announce(BudgetAlert alert, String successText) {
        if (alert == null) {
            showBanner(successText, Kind.SUCCESS, 3500);
            return;
        }
        String text = "Budget Alert\n" + categoryName(alert.categoryId) + ": "
                + Fmt.money(alert.spent) + " of " + Fmt.money(alert.limit)
                + " spent this month (" + alert.percentUsed + "% of budget)";
        showBanner(text, "EXCEEDED".equals(alert.status) ? Kind.EXCEEDED : Kind.WARNING, 8000);
    }

    private String categoryName(int id) {
        for (CategoryItem c : categories) {
            if (c.id == id) return c.name;
        }
        return "This category";
    }

    private void showBanner(String text, Kind kind, long millis) {
        int fill;
        int textColor = Fmt.primaryDark(requireContext());
        switch (kind) {
            case SUCCESS:
                fill = Fmt.color(requireContext(), R.color.green_container);
                break;
            case WARNING:
                fill = Fmt.color(requireContext(), R.color.amber_container);
                break;
            default:   // EXCEEDED, ERROR
                fill = Fmt.color(requireContext(), R.color.red_container);
                textColor = Fmt.color(requireContext(), R.color.red);
                break;
        }
        binding.banner.setBackground(Fmt.box(requireContext(), fill, Fmt.primaryDark(requireContext()), 2, 4));
        binding.bannerText.setText(text);
        binding.bannerText.setTextColor(textColor);
        binding.banner.setVisibility(View.VISIBLE);
        binding.banner.removeCallbacks(hideBanner);
        binding.banner.postDelayed(hideBanner, millis);
    }

    private void setSync(String state) {
        binding.textSync.setText("● " + state);
        int color = "ONLINE".equals(state) ? R.color.green_dark
                : "OFFLINE".equals(state) ? R.color.red : R.color.amber_dark;
        binding.textSync.setTextColor(Fmt.color(requireContext(), color));
    }

    private void setEnabledLook(View button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.4f);
    }

    private void pickDate(LocalDate initial, Consumer<LocalDate> onPicked) {
        new DatePickerDialog(requireContext(),
                (picker, year, month, day) -> onPicked.accept(LocalDate.of(year, month + 1, day)),
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    // "50", "50.5" or "50.00" -> BigDecimal with 2 decimals. Anything else (or <= 0) -> null.
    private static BigDecimal parseAmount(String text) {
        try {
            BigDecimal value = new BigDecimal(text.trim());
            if (value.signum() <= 0) {
                return null;
            }
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}