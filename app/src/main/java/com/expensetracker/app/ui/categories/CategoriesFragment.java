package com.expensetracker.app.ui.categories;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.expensetracker.app.R;
import com.expensetracker.app.data.ApiCallback;
import com.expensetracker.app.data.ApiClient;
import com.expensetracker.app.databinding.DialogEditCategoryBinding;
import com.expensetracker.app.databinding.FragmentCategoriesBinding;
import com.expensetracker.app.model.BudgetStatus;
import com.expensetracker.app.model.CategoryItem;
import com.expensetracker.app.model.CategoryRequest;
import com.expensetracker.app.model.CountResponse;
import com.expensetracker.app.model.DeletedResponse;
import com.expensetracker.app.model.IdsRequest;
import com.expensetracker.app.model.PetStatus;
import com.expensetracker.app.ui.Dialogs;
import com.expensetracker.app.ui.Fmt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CategoriesFragment extends Fragment implements CategoryAdapter.Listener {

    private FragmentCategoriesBinding binding;
    private CategoryAdapter adapter;
    private PalettePicker newPalette;

    private final List<CategoryItem> all = new ArrayList<>();
    private final Set<Integer> selectedIds = new HashSet<>();
    private boolean sortAscending = true;
    private String filter = "";

    private final Runnable hideBanner = () -> {
        if (binding != null) {
            binding.banner.setVisibility(View.GONE);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoriesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new CategoryAdapter(this);
        binding.listCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listCategories.setAdapter(adapter);
        binding.listCategories.setNestedScrollingEnabled(false);   // the whole screen scrolls as one

        newPalette = new PalettePicker(binding.paletteNew);

        binding.btnSave.setOnClickListener(v -> create());
        binding.btnSort.setOnClickListener(v -> {
            sortAscending = !sortAscending;
            binding.btnSort.setText(sortAscending ? "A-Z" : "Z-A");
            render();
        });
        binding.inputFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filter = s.toString().trim().toLowerCase(Locale.ROOT);
                render();
            }
        });
        binding.checkSelectAll.setOnClickListener(v -> selectAll(binding.checkSelectAll.isChecked()));
        binding.btnPurge.setOnClickListener(v -> purgeSelected());
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
        binding.errorState.setVisibility(View.GONE);
        loadCategories();
        loadBudgets();
        loadPet();
    }

    private void loadCategories() {
        ApiClient.get().categories().enqueue(new ApiCallback<List<CategoryItem>>() {
            @Override
            public void onSuccess(List<CategoryItem> body) {
                if (binding == null) return;
                all.clear();
                if (body != null) all.addAll(body);

                // forget selections for categories that no longer exist
                Set<Integer> existing = new HashSet<>();
                for (CategoryItem c : all) existing.add(c.id);
                selectedIds.retainAll(existing);

                binding.textLoading.setVisibility(View.GONE);
                binding.errorState.setVisibility(View.GONE);
                render();
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                binding.textLoading.setVisibility(View.GONE);
                binding.listCategories.setVisibility(View.GONE);
                binding.errorState.setVisibility(View.VISIBLE);
                binding.textError.setText(message);
            }
        });
    }

    // MEM_ALLOC: how much of the budgeted money is spent this month
    private void loadBudgets() {
        ApiClient.get().budgets(YearMonth.now().toString()).enqueue(new ApiCallback<List<BudgetStatus>>() {
            @Override
            public void onSuccess(List<BudgetStatus> body) {
                if (binding == null) return;
                BigDecimal spent = BigDecimal.ZERO;
                BigDecimal limit = BigDecimal.ZERO;
                if (body != null) {
                    for (BudgetStatus s : body) {
                        spent = spent.add(s.spent);
                        limit = limit.add(s.limit);
                    }
                }
                if (limit.signum() == 0) {
                    binding.memRow.setVisibility(View.GONE);
                    return;
                }
                int percent = spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, java.math.RoundingMode.DOWN).intValue();
                binding.textMem.setText("MEM_ALLOC: " + percent + "% OCCUPIED");
                binding.memRow.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                // decoration: stay quiet if it can't load
            }
        });
    }

    private void loadPet() {
        ApiClient.get().pet(LocalDate.now().toString()).enqueue(new ApiCallback<PetStatus>() {
            @Override
            public void onSuccess(PetStatus p) {
                if (binding == null || p == null) return;
                binding.textTipLevel.setText(String.format(Locale.US, "LVL.%02d", p.level));
            }

            @Override
            public void onError(String message) {
                // decoration: stay quiet if it can't load
            }
        });
    }

    // ----------------------------------------------------------- rendering

    // The rows after filtering and sorting
    private List<CategoryItem> visible() {
        List<CategoryItem> result = new ArrayList<>();
        for (CategoryItem c : all) {
            if (filter.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(filter)) {
                result.add(c);
            }
        }
        Comparator<CategoryItem> byName = (a, b) -> a.name.compareToIgnoreCase(b.name);
        Collections.sort(result, sortAscending ? byName : byName.reversed());
        return result;
    }

    private void render() {
        List<CategoryItem> rows = visible();
        adapter.submit(rows, selectedIds);

        boolean hasAny = !all.isEmpty();
        binding.listCategories.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        binding.textNoMatches.setVisibility(hasAny && rows.isEmpty() ? View.VISIBLE : View.GONE);

        // header stats
        binding.textIndexCount.setText(all.size() + (all.size() == 1 ? " CATEGORY" : " CATEGORIES"));
        BigDecimal cap = BigDecimal.ZERO;
        for (CategoryItem c : all) {
            if (c.monthlyLimit != null) cap = cap.add(c.monthlyLimit);
        }
        binding.textBudgetCap.setText(Fmt.money(cap));

        // Buddy Bot: 100% organised up to 6 categories, then 10% less for each extra one
        int organization = Math.max(10, 100 - 10 * Math.max(0, all.size() - 6));
        binding.progressOrg.setProgress(organization);
        binding.textOrg.setText(organization + "%");

        updateSelectionControls();
    }

    // the DELETE (n) button and the select-all checkbox
    private void updateSelectionControls() {
        int count = selectedIds.size();
        binding.btnPurge.setText("DELETE (" + count + ")");
        binding.btnPurge.setEnabled(count > 0);
        binding.btnPurge.setAlpha(count > 0 ? 1f : 0.5f);

        int deletableVisible = 0;
        int selectedVisible = 0;
        for (CategoryItem c : visible()) {
            if (c.builtIn) continue;
            deletableVisible++;
            if (selectedIds.contains(c.id)) selectedVisible++;
        }
        binding.checkSelectAll.setChecked(deletableVisible > 0 && selectedVisible == deletableVisible);
    }

    // ----------------------------------------------------------- creating

    private void create() {
        String name = binding.inputName.getText().toString().trim();
        if (name.isEmpty()) {
            formMessage("Label / title is required", true);
            return;
        }
        BigDecimal limit = null;
        String limitText = binding.inputLimit.getText().toString().trim();
        if (!limitText.isEmpty()) {
            limit = Fmt.parseAmount(limitText);
            if (limit == null) {
                formMessage("Limit must be above 0, e.g. 120", true);
                return;
            }
        }

        setSaveBusy(true);
        CategoryRequest request = new CategoryRequest(name, newPalette.selectedColor(), "tag", limit);
        ApiClient.get().createCategory(request).enqueue(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void body) {
                if (binding == null) return;
                setSaveBusy(false);
                binding.inputName.setText("");
                binding.inputLimit.setText("");
                formMessage("Category saved: " + name, false);
                loadCategories();
                loadBudgets();
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
        binding.btnSave.setText(busy ? "SAVING..." : "SAVE CATEGORY");
    }

    private void formMessage(String text, boolean error) {
        binding.formMessage.setText(text);
        binding.formMessage.setTextColor(Fmt.color(requireContext(), error ? R.color.red : R.color.green_dark));
        binding.formMessage.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------- selecting, editing, deleting

    private void selectAll(boolean checked) {
        selectedIds.clear();
        if (checked) {
            for (CategoryItem c : visible()) {
                if (!c.builtIn) selectedIds.add(c.id);
            }
        }
        render();
    }

    @Override
    public void onToggle(CategoryItem item, boolean checked) {
        if (checked) {
            selectedIds.add(item.id);
        } else {
            selectedIds.remove(item.id);
        }
        render();
    }

    @Override
    public void onEdit(CategoryItem c) {
        DialogEditCategoryBinding d = DialogEditCategoryBinding.inflate(getLayoutInflater());
        PalettePicker palette = new PalettePicker(d.palette);
        palette.select(c.color);
        d.inputName.setText(c.name);
        d.inputLimit.setText(c.monthlyLimit == null ? "" : c.monthlyLimit.stripTrailingZeros().toPlainString());
        d.chipLocked.setVisibility(c.builtIn ? View.VISIBLE : View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(d.getRoot()).create();
        d.btnDiscard.setOnClickListener(v -> dialog.dismiss());
        d.btnSaveCategory.setOnClickListener(v -> {
            String name = d.inputName.getText().toString().trim();
            if (name.isEmpty()) {
                dialogError(d, "Category title is required");
                return;
            }
            BigDecimal limit = null;
            String limitText = d.inputLimit.getText().toString().trim();
            if (!limitText.isEmpty()) {
                limit = Fmt.parseAmount(limitText);
                if (limit == null) {
                    dialogError(d, "Limit must be above 0, e.g. 120");
                    return;
                }
            }

            d.btnSaveCategory.setEnabled(false);
            CategoryRequest request = new CategoryRequest(name, palette.selectedColor(), c.icon, limit);
            ApiClient.get().updateCategory(c.id, request).enqueue(new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void body) {
                    dialog.dismiss();
                    if (binding == null) return;
                    showBanner("Category updated: " + name, false);
                    loadCategories();
                    loadBudgets();
                }

                @Override
                public void onError(String message) {
                    d.btnSaveCategory.setEnabled(true);
                    dialogError(d, message);   // e.g. "You already have a category called ..."
                }
            });
        });
        dialog.show();
        Dialogs.style(dialog);
    }

    private void dialogError(DialogEditCategoryBinding d, String message) {
        d.textError.setText(message);
        d.textError.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDelete(CategoryItem c) {
        // ask the server how many expenses would be unlinked, then confirm
        ApiClient.get().unlinkCount(c.id).enqueue(new ApiCallback<CountResponse>() {
            @Override
            public void onSuccess(CountResponse body) {
                if (binding == null) return;
                int count = body == null ? 0 : body.count;
                String message = "Delete category \"" + c.name + "\"?\n\n"
                        + count + " associated expense" + (count == 1 ? "" : "s")
                        + " will be unlinked (they stay, shown as Uncategorized). Budgets for it are removed.\n\n"
                        + "This can't be undone.";
                Dialogs.confirm(requireContext(), "CONFIRM DELETE", message,
                        "YES, DELETE", () -> purge(Collections.singletonList(c.id)));
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                showBanner(message, true);
            }
        });
    }

    private void purgeSelected() {
        if (selectedIds.isEmpty()) return;
        List<Integer> ids = new ArrayList<>(selectedIds);
        String message = "Delete " + ids.size() + " categor" + (ids.size() == 1 ? "y" : "ies") + "?\n\n"
                + "Their expenses stay, shown as Uncategorized. Their budgets are removed.\n\n"
                + "This can't be undone.";
        Dialogs.confirm(requireContext(), "CONFIRM DELETE", message, "YES, DELETE", () -> purge(ids));
    }

    private void purge(List<Integer> ids) {
        ApiClient.get().purgeCategories(new IdsRequest(ids)).enqueue(new ApiCallback<DeletedResponse>() {
            @Override
            public void onSuccess(DeletedResponse body) {
                if (binding == null) return;
                int deleted = body == null ? 0 : body.deleted;
                selectedIds.clear();
                showBanner(deleted + (deleted == 1 ? " CATEGORY" : " CATEGORIES") + " DELETED", false);
                loadCategories();
                loadBudgets();
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                showBanner(message, true);
            }
        });
    }

    // ------------------------------------------------------------- helpers

    private void showBanner(String text, boolean error) {
        int fill = Fmt.color(requireContext(), error ? R.color.red_container : R.color.green_container);
        int textColor = error ? Fmt.color(requireContext(), R.color.red) : Fmt.primaryDark(requireContext());
        binding.banner.setBackground(Fmt.box(requireContext(), fill, Fmt.primaryDark(requireContext()), 2, 4));
        binding.bannerText.setText(text);
        binding.bannerText.setTextColor(textColor);
        binding.banner.setVisibility(View.VISIBLE);
        binding.banner.removeCallbacks(hideBanner);
        binding.banner.postDelayed(hideBanner, error ? 6000 : 3500);
    }
}