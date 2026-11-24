package com.expensetracker.app.ui.budgets;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.expensetracker.app.R;
import com.expensetracker.app.databinding.ItemBudgetBinding;
import com.expensetracker.app.model.BudgetStatus;
import com.expensetracker.app.ui.CategoryIcons;
import com.expensetracker.app.ui.Fmt;

import java.util.ArrayList;
import java.util.List;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.Holder> {
    private final List<BudgetStatus> items = new ArrayList<>();

    public void submit(List<BudgetStatus> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemBudgetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        BudgetStatus s = items.get(position);
        ItemBudgetBinding b = holder.binding;
        Context context = b.getRoot().getContext();
        int plum = Fmt.primaryDark(context);

        // content that is the same for every status
        b.textName.setText(s.categoryName);
        b.textSpent.setText(Fmt.money(s.spent) + " SPENT OF " + Fmt.money(s.limit));
        b.textPercent.setText(Fmt.percent(s.percent));
        b.progress.setProgress(Math.min(100, s.percent.intValue()));
        b.iconTile.setImageResource(CategoryIcons.forKey(s.icon));
        b.iconTile.setBackground(Fmt.box(context, Fmt.parseColor(s.color, Fmt.color(context, R.color.primary_container)),
                plum, 2, 4));

        // the three looks: normal / caution / critical
        int cardBackground;
        int badgeFill;
        int badgeText;
        int accent;
        switch (s.status == null ? "OK" : s.status) {
            case "EXCEEDED":
                cardBackground = R.drawable.bg_card_red;
                badgeFill = Fmt.color(context, R.color.red);
                badgeText = Fmt.color(context, R.color.white);
                accent = Fmt.color(context, R.color.red);
                b.textStatus.setText("CRITICAL!");
                b.textRemaining.setText("Over by: +" + Fmt.money(s.remaining.abs()));
                b.textRight.setVisibility(View.GONE);
                b.chipOver.setVisibility(View.VISIBLE);
                break;
            case "WARNING":
                cardBackground = R.drawable.bg_card_light;
                badgeFill = Fmt.color(context, R.color.amber_container);
                badgeText = plum;
                accent = Fmt.color(context, R.color.amber_dark);
                b.textStatus.setText("CAUTION");
                b.textRemaining.setText("Remaining: " + Fmt.money(s.remaining));
                b.textRight.setText("THRESHOLD REACHED!");
                b.textRight.setVisibility(View.VISIBLE);
                b.chipOver.setVisibility(View.GONE);
                break;
            default:   // OK
                cardBackground = R.drawable.bg_card_light;
                badgeFill = Fmt.color(context, R.color.green_container);
                badgeText = plum;
                accent = Fmt.color(context, R.color.green_dark);
                b.textStatus.setText("PET HAPPY");
                b.textRemaining.setText("Remaining: " + Fmt.money(s.remaining));
                b.textRight.setText("ALERT THRESHOLD: " + s.alertThresholdPercent + "%");
                b.textRight.setVisibility(View.VISIBLE);
                b.chipOver.setVisibility(View.GONE);
                break;
        }

        b.getRoot().setBackgroundResource(cardBackground);
        // setting a background can reset padding, so set the card padding again
        b.getRoot().setPadding(Fmt.dp(context, 14), Fmt.dp(context, 12), Fmt.dp(context, 18), Fmt.dp(context, 16));

        b.textPercent.setBackground(Fmt.box(context, badgeFill, plum, 1.5f, 3));
        b.textPercent.setTextColor(badgeText);
        b.textStatus.setTextColor(accent);
        b.textRight.setTextColor(accent);
        b.progress.setProgressTintList(ColorStateList.valueOf(accent));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemBudgetBinding binding;

        Holder(ItemBudgetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}