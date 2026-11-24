package com.expensetracker.app.ui.subs;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.expensetracker.app.R;
import com.expensetracker.app.data.ApiCallback;
import com.expensetracker.app.data.ApiClient;
import com.expensetracker.app.databinding.FragmentSubsBinding;
import com.expensetracker.app.model.CategoryItem;
import com.expensetracker.app.model.DeletedResponse;
import com.expensetracker.app.model.IdsRequest;
import com.expensetracker.app.model.ProcessedResponse;
import com.expensetracker.app.model.SubscriptionOverview;
import com.expensetracker.app.model.SubscriptionRequest;
import com.expensetracker.app.ui.Dialogs;
import com.expensetracker.app.ui.Fmt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SubsFragment extends Fragment implements SubscriptionAdapter.Listener {
    private static final String[] FREQUENCY_LABELS = {"Monthly", "Weekly", "Yearly"};
    private static final String[] FREQUENCY_VALUES = {"MONTHLY", "WEEKLY", "YEARLY"};

    private FragmentSubsBinding binding;
    private SubscriptionAdapter adapter;

    private final List<CategoryItem> categories = new ArrayList<>();
    private final List<SubscriptionOverview.Item> items = new ArrayList<>();
    private final Set<Integer> selectedIds = new HashSet<>();
    private LocalDate dueDate = LocalDate.now().plusMonths(1);
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
        binding = FragmentSubsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new SubscriptionAdapter(this);
        binding.listSubs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.listSubs.setAdapter(adapter);
        binding.listSubs.setNestedScrollingEnabled(false);   // the whole screen scrolls as one

        binding.spinnerFrequency.setAdapter(spinnerAdapter(java.util.Arrays.asList(FREQUENCY_LABELS)));
        binding.textDueDate.setText(Fmt.displayDate(dueDate));
        binding.textDueDate.setOnClickListener(v -> pickDate());

        binding.btnToggleForm.setOnClickListener(v -> {
            formOpen = !formOpen;
            binding.formBody.setVisibility(formOpen ? View.VISIBLE : View.GONE);
            binding.btnToggleForm.setText(formOpen ? "▲" : "▼");
        });
        binding.btnAdd.setOnClickListener(v -> add());
        binding.btnProcess.setOnClickListener(v -> processDue());
        binding.btnDeleteSelected.setOnClickListener(v -> deleteSelected());
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
        ApiClient.get().categories().enqueue(new ApiCallback<List<CategoryItem>>() {
            @Override
            public void onSuccess(List<CategoryItem> body) {
                if (binding == null) return;
                categories.clear();
                if (body != null) categories.addAll(body);
                List<String> names = new ArrayList<>();
                for (CategoryItem c : categories) names.add(c.name);
                binding.spinnerCategory.setAdapter(spinnerAdapter(names));
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                showBanner("Couldn't load categories: " + message, true);
            }
        });
        loadOverview();
    }

    private void loadOverview() {
        ApiClient.get().subscriptions(LocalDate.now().toString()).enqueue(new ApiCallback<SubscriptionOverview>() {
            @Override
            public void onSuccess(SubscriptionOverview body) {
                if (binding == null || body == null) return;
                items.clear();
                if (body.items != null) items.addAll(body.items);

                // forget selections for subscriptions that no longer exist
                Set<Integer> existing = new HashSet<>();
                for (SubscriptionOverview.Item i : items) existing.add(i.subscription.id);
                selectedIds.retainAll(existing);

                binding.textLoading.setVisibility(View.GONE);
                binding.errorState.setVisibility(View.GONE);
                binding.textActive.setText(body.activeCount + " ACTIVE");
                binding.textBurn.setText(Fmt.money(body.monthlyBurn) + "/MO");

                boolean hasDue = body.dueTotal != null && body.dueTotal.signum() > 0;
                binding.btnProcess.setText("PROCESS DUE (" + Fmt.money(body.dueTotal) + ")");
                binding.btnProcess.setEnabled(hasDue);
                binding.btnProcess.setAlpha(hasDue ? 1f : 0.5f);

                boolean empty = items.isEmpty();
                binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.listSubs.setVisibility(empty ? View.GONE : View.VISIBLE);
                adapter.submit(items, selectedIds);
                updateDeleteButton();
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                binding.textLoading.setVisibility(View.GONE);
                binding.listSubs.setVisibility(View.GONE);
                binding.emptyState.setVisibility(View.GONE);
                binding.errorState.setVisibility(View.VISIBLE);
                binding.textError.setText(message);
            }
        });
    }

    // ------------------------------------------------------------- adding

    private void add() {
        String name = binding.inputName.getText().toString().trim();
        if (name.isEmpty()) {
            formMessage("Enter a service name");
            return;
        }
        BigDecimal amount = Fmt.parseAmount(binding.inputAmount.getText().toString());
        if (amount == null) {
            formMessage("Enter an amount above 0, e.g. 15.99");
            return;
        }
        int categoryPosition = binding.spinnerCategory.getSelectedItemPosition();
        if (categoryPosition < 0 || categoryPosition >= categories.size()) {
            formMessage("Pick a category first");
            return;
        }
        int frequencyPosition = binding.spinnerFrequency.getSelectedItemPosition();
        if (frequencyPosition < 0) {
            formMessage("Pick a frequency");
            return;
        }

        binding.formMessage.setVisibility(View.GONE);
        setAddBusy(true);
        SubscriptionRequest request = new SubscriptionRequest(name, amount,
                categories.get(categoryPosition).id, FREQUENCY_VALUES[frequencyPosition], dueDate.toString());
        ApiClient.get().addSubscription(request).enqueue(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void body) {
                if (binding == null) return;
                setAddBusy(false);
                binding.inputName.setText("");
                binding.inputAmount.setText("");
                showBanner("SUBSCRIPTION ADDED: " + name, false);
                loadOverview();
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                setAddBusy(false);
                formMessage(message);
            }
        });
    }

    private void setAddBusy(boolean busy) {
        binding.btnAdd.setEnabled(!busy);
        binding.btnAdd.setAlpha(busy ? 0.6f : 1f);
        binding.btnAdd.setText(busy ? "ADDING..." : "+ ADD SUBSCRIPTION");
    }

    private void formMessage(String text) {
        binding.formMessage.setText(text);
        binding.formMessage.setVisibility(View.VISIBLE);
    }

    private void pickDate() {
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            dueDate = LocalDate.of(year, month + 1, day);
            binding.textDueDate.setText(Fmt.displayDate(dueDate));
        }, dueDate.getYear(), dueDate.getMonthValue() - 1, dueDate.getDayOfMonth()).show();
    }

    // ----------------------------------------------------------- processing

    private void processDue() {
        binding.btnProcess.setEnabled(false);
        ApiClient.get().processDue(LocalDate.now().toString()).enqueue(new ApiCallback<ProcessedResponse>() {
            @Override
            public void onSuccess(ProcessedResponse body) {
                if (binding == null) return;
                int created = body == null ? 0 : body.created;
                showBanner(created == 0 ? "NOTHING WAS DUE"
                        : created + (created == 1 ? " EXPENSE" : " EXPENSES") + " ADDED TO YOUR LOG", false);
                loadOverview();
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                binding.btnProcess.setEnabled(true);
                showBanner(message, true);
            }
        });
    }

    // ------------------------------------------------------ select / delete

    @Override
    public void onToggle(SubscriptionOverview.Item item, boolean checked) {
        if (checked) {
            selectedIds.add(item.subscription.id);
        } else {
            selectedIds.remove(item.subscription.id);
        }
        adapter.submit(items, selectedIds);
        updateDeleteButton();
    }

    private void updateDeleteButton() {
        int count = selectedIds.size();
        binding.btnDeleteSelected.setText("DELETE (" + count + ")");
        binding.btnDeleteSelected.setEnabled(count > 0);
        binding.btnDeleteSelected.setAlpha(count > 0 ? 1f : 0.5f);
    }

    @Override
    public void onDelete(SubscriptionOverview.Item item) {
        Dialogs.confirm(requireContext(), "CONFIRM DELETE",
                "Delete \"" + item.subscription.name + "\"?\n\nExpenses it already created stay in your log.",
                "YES, DELETE", () -> delete(Collections.singletonList(item.subscription.id)));
    }

    private void deleteSelected() {
        if (selectedIds.isEmpty()) return;
        List<Integer> ids = new ArrayList<>(selectedIds);
        Dialogs.confirm(requireContext(), "CONFIRM DELETE",
                "Delete " + ids.size() + " subscription" + (ids.size() == 1 ? "" : "s") + "?\n\n"
                        + "Expenses they already created stay in your log.",
                "YES, DELETE", () -> delete(ids));
    }

    private void delete(List<Integer> ids) {
        ApiClient.get().deleteSubscriptions(new IdsRequest(ids)).enqueue(new ApiCallback<DeletedResponse>() {
            @Override
            public void onSuccess(DeletedResponse body) {
                if (binding == null) return;
                int deleted = body == null ? 0 : body.deleted;
                selectedIds.removeAll(ids);
                showBanner(deleted + (deleted == 1 ? " SUBSCRIPTION" : " SUBSCRIPTIONS") + " DELETED", false);
                loadOverview();
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                showBanner(message, true);
            }
        });
    }

    // ------------------------------------------------------------- helpers

    private android.widget.ArrayAdapter<String> spinnerAdapter(List<String> labels) {
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner, labels);
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        return spinnerAdapter;
    }

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