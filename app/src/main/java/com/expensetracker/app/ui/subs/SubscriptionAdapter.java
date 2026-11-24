package com.expensetracker.app.ui.subs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.expensetracker.app.R;
import com.expensetracker.app.databinding.ItemSubscriptionBinding;
import com.expensetracker.app.model.Subscription;
import com.expensetracker.app.model.SubscriptionOverview;
import com.expensetracker.app.ui.Fmt;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SubscriptionAdapter extends RecyclerView.Adapter<SubscriptionAdapter.Holder> {

    public interface Listener {
        void onToggle(SubscriptionOverview.Item item, boolean checked);
        void onDelete(SubscriptionOverview.Item item);
    }

    private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final List<SubscriptionOverview.Item> items = new ArrayList<>();
    private Set<Integer> selected = new HashSet<>();
    private final Listener listener;

    public SubscriptionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<SubscriptionOverview.Item> newItems, Set<Integer> selectedIds) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        selected = selectedIds;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemSubscriptionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SubscriptionOverview.Item item = items.get(position);
        Subscription s = item.subscription;
        ItemSubscriptionBinding b = holder.binding;
        Context context = b.getRoot().getContext();
        int plum = Fmt.primaryDark(context);
        boolean isSelected = selected.contains(s.id);

        b.textName.setText(s.name);
        b.textCategory.setText(item.categoryName == null ? "Uncategorized" : item.categoryName);
        b.textPrice.setText(Fmt.money(s.amount));
        b.textPer.setText("per " + periodWord(s.frequency));
        b.textNextDue.setText("Next Due: " + LocalDate.parse(s.nextDueDate).format(DUE_DATE));

        // status badge + countdown pill, both worked out from how many days are left
        long days = item.daysUntilDue;
        int badgeFill;
        int badgeText = plum;
        String badge;
        int pillFill;
        int pillText = plum;
        if (!s.active) {
            badge = "PAUSED";
            badgeFill = Fmt.color(context, R.color.surface_tint);
            pillFill = Fmt.color(context, R.color.surface_tint);
        } else if (days < 0) {
            badge = "OVERDUE";
            badgeFill = Fmt.color(context, R.color.red);
            badgeText = Fmt.color(context, R.color.white);
            pillFill = Fmt.color(context, R.color.red_container);
            pillText = Fmt.color(context, R.color.red);
        } else if (days == 0) {
            badge = "DUE TODAY";
            badgeFill = Fmt.color(context, R.color.green_container);
            pillFill = Fmt.color(context, R.color.green_container);
        } else if (days <= 7) {
            badge = "DUE SOON";
            badgeFill = Fmt.color(context, R.color.amber_container);
            pillFill = Fmt.color(context, R.color.amber_container);
        } else {
            badge = "ACTIVE";
            badgeFill = Fmt.color(context, R.color.surface_tint);
            pillFill = Fmt.color(context, R.color.surface_tint);
        }
        b.textStatus.setText(badge);
        b.textStatus.setTextColor(badgeText);
        b.textStatus.setBackground(Fmt.box(context, badgeFill, plum, 1.5f, 3));
        b.textCountdown.setText(countdown(days));
        b.textCountdown.setTextColor(pillText);
        b.textCountdown.setBackground(Fmt.box(context, pillFill, plum, 1.5f, 10));

        // checkbox: clear the listener first so setChecked doesn't fire it
        b.checkSelect.setOnCheckedChangeListener(null);
        b.checkSelect.setChecked(isSelected);
        b.checkSelect.setOnCheckedChangeListener((view, checked) -> listener.onToggle(item, checked));
        b.btnDelete.setOnClickListener(v -> listener.onDelete(item));

        b.getRoot().setBackgroundResource(isSelected ? R.drawable.bg_card_selected : R.drawable.bg_card_white);
        // setting a background can reset padding, so set the card padding again
        b.getRoot().setPadding(Fmt.dp(context, 8), Fmt.dp(context, 12), Fmt.dp(context, 18), Fmt.dp(context, 16));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String periodWord(String frequency) {
        if ("WEEKLY".equals(frequency)) return "week";
        if ("YEARLY".equals(frequency)) return "year";
        return "month";
    }

    private static String countdown(long days) {
        if (days < -1) return (-days) + " Days Ago";
        if (days == -1) return "Yesterday";
        if (days == 0) return "Today!";
        if (days == 1) return "Tomorrow";
        return "In " + days + " Days";
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemSubscriptionBinding binding;

        Holder(ItemSubscriptionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}