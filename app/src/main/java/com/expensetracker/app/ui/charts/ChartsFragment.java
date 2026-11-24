package com.expensetracker.app.ui.charts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.expensetracker.app.R;
import com.expensetracker.app.data.ApiCallback;
import com.expensetracker.app.data.ApiClient;
import com.expensetracker.app.databinding.FragmentChartsBinding;
import com.expensetracker.app.databinding.ItemChartExpenseBinding;
import com.expensetracker.app.model.CategorySpend;
import com.expensetracker.app.model.Expense;
import com.expensetracker.app.model.MonthSummary;
import com.expensetracker.app.ui.CategoryIcons;
import com.expensetracker.app.ui.Fmt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChartsFragment extends Fragment {
    private static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private FragmentChartsBinding binding;
    private CategorySummaryAdapter adapter;

    private YearMonth month = YearMonth.now();
    private final List<MonthSummary.Slice> slices = new ArrayList<>();
    private int selectedIndex = -1;
    private int monthRequest = 0;    // ignore replies that belong to an older month
    private int recentRequest = 0;   // ignore replies that belong to an older selection

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChartsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new CategorySummaryAdapter(this::select);
        binding.listSummary.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listSummary.setAdapter(adapter);
        binding.listSummary.setNestedScrollingEnabled(false);   // the whole screen scrolls as one

        binding.donut.setListener(this::select);
        binding.btnPrev.setOnClickListener(v -> {
            month = month.minusMonths(1);
            loadMonth();
        });
        binding.btnNext.setOnClickListener(v -> {
            if (month.isBefore(YearMonth.now())) {
                month = month.plusMonths(1);
                loadMonth();
            }
        });
        binding.btnRetry.setOnClickListener(v -> loadMonth());
        binding.btnAddItem.setOnClickListener(v -> addItemToSelected());

        loadMonth();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;   // late replies from the server check for this and stop
    }

    // ------------------------------------------------------------ loading

    private void loadMonth() {
        final int request = ++monthRequest;
        binding.errorState.setVisibility(View.GONE);
        binding.textMonth.setText(monthLabel());
        updateNextButton();

        ApiClient.get().chartMonth(month.toString()).enqueue(new ApiCallback<MonthSummary>() {
            @Override
            public void onSuccess(MonthSummary body) {
                if (binding == null || request != monthRequest || body == null) return;
                slices.clear();
                if (body.slices != null) slices.addAll(body.slices);
                selectedIndex = slices.isEmpty() ? -1 : 0;   // start on the biggest category

                binding.textLoading.setVisibility(View.GONE);
                binding.content.setVisibility(View.VISIBLE);
                binding.textTotal.setText("TOTAL: " + Fmt.money(body.total));
                binding.textBuckets.setText(slices.size() + (slices.size() == 1 ? " CATEGORY" : " CATEGORIES"));

                adapter.submit(slices, selectedIndex);
                bindDonut();
                updateCenter();
                loadRecent();
            }

            @Override
            public void onError(String message) {
                if (binding == null || request != monthRequest) return;
                binding.textLoading.setVisibility(View.GONE);
                binding.content.setVisibility(View.GONE);
                binding.errorState.setVisibility(View.VISIBLE);
                binding.textError.setText(message);
            }
        });
    }

    private void loadRecent() {
        if (selectedIndex < 0) {
            binding.expensesCard.setVisibility(View.GONE);
            return;
        }
        binding.expensesCard.setVisibility(View.VISIBLE);
        CategorySpend spend = slices.get(selectedIndex).spend;
        binding.textExpensesTitle.setText("EXPENSES: " + spend.name.toUpperCase(Locale.ROOT));

        final int request = ++recentRequest;
        ApiClient.get().categoryExpenses(month.toString(), spend.categoryId, 3)
                .enqueue(new ApiCallback<List<Expense>>() {
                    @Override
                    public void onSuccess(List<Expense> body) {
                        if (binding == null || request != recentRequest) return;
                        showRecent(body == null ? new ArrayList<>() : body, spend);
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null || request != recentRequest) return;
                        showRecent(new ArrayList<>(), spend);
                    }
                });
    }

    // ----------------------------------------------------------- rendering

    private void bindDonut() {
        float[] fractions = new float[slices.size()];
        int[] colors = new int[slices.size()];
        double grand = 0;
        for (MonthSummary.Slice s : slices) {
            grand += s.spend.total.doubleValue();
        }
        for (int i = 0; i < slices.size(); i++) {
            CategorySpend spend = slices.get(i).spend;
            fractions[i] = grand == 0 ? 0f : (float) (spend.total.doubleValue() / grand);
            colors[i] = Fmt.parseColor(spend.color, Fmt.color(requireContext(), R.color.primary_container));
        }
        binding.donut.setSlices(fractions, colors);
        binding.donut.setSelectedSlice(selectedIndex);
    }

    private void updateCenter() {
        if (selectedIndex < 0) {
            binding.centerIcon.setVisibility(View.GONE);
            binding.centerName.setText("NO DATA");
            binding.centerAmount.setText(Fmt.money(BigDecimal.ZERO));
            binding.donut.setContentDescription("No spending this month");
            return;
        }
        CategorySpend s = slices.get(selectedIndex).spend;
        binding.centerIcon.setVisibility(View.VISIBLE);
        binding.centerIcon.setImageResource(CategoryIcons.forKey(s.icon));
        binding.centerName.setText(s.name.toUpperCase(Locale.ROOT));
        binding.centerAmount.setText(Fmt.money(s.total));
        binding.donut.setContentDescription(s.name + ": " + Fmt.money(s.total));
    }

    private void showRecent(List<Expense> expenses, CategorySpend spend) {
        binding.recentList.removeAllViews();
        binding.textRecent.setText(expenses.size() + " RECENT");
        binding.textNoRecent.setVisibility(expenses.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Expense e : expenses) {
            ItemChartExpenseBinding row = ItemChartExpenseBinding.inflate(inflater, binding.recentList, false);
            row.icon.setImageResource(CategoryIcons.forKey(spend.icon));
            row.textItem.setText(e.description);
            String meta = LocalDate.parse(e.expenseDate).format(ROW_DATE);
            if (e.note != null && !e.note.isBlank()) {
                meta += " • " + e.note;
            }
            row.textMeta.setText(meta);
            row.textAmount.setText("-" + Fmt.money(e.amount));
            binding.recentList.addView(row.getRoot());
        }
    }

    // ---------------------------------------------------------- interaction

    // From a donut tap or a summary card tap
    private void select(int index) {
        if (index < 0 || index >= slices.size()) return;
        selectedIndex = index;
        binding.donut.setSelectedSlice(index);
        adapter.setSelectedIndex(index);
        updateCenter();
        loadRecent();
    }

    private void addItemToSelected() {
        Bundle args = new Bundle();
        if (selectedIndex >= 0) {
            Integer categoryId = slices.get(selectedIndex).spend.categoryId;
            if (categoryId != null) {
                args.putInt("categoryId", categoryId);
            }
        }
        // the Expenses screen reads "categoryId" and preselects it in the form
        NavHostFragment.findNavController(this).navigate(R.id.expensesFragment, args);
    }

    private String monthLabel() {
        return month.getMonth().getDisplayName(TextStyle.SHORT, Locale.US).toUpperCase(Locale.ROOT)
                + " " + month.getYear();
    }

    // there's nothing after the current month, so the > button greys out there
    private void updateNextButton() {
        boolean canGoForward = month.isBefore(YearMonth.now());
        binding.btnNext.setEnabled(canGoForward);
        binding.btnNext.setAlpha(canGoForward ? 1f : 0.4f);
    }
}