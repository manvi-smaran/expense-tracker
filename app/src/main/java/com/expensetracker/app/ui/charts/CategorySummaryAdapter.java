package com.expensetracker.app.ui.charts;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.expensetracker.app.R;
import com.expensetracker.app.databinding.ItemCategorySummaryBinding;
import com.expensetracker.app.model.CategorySpend;
import com.expensetracker.app.model.MonthSummary;
import com.expensetracker.app.ui.CategoryIcons;
import com.expensetracker.app.ui.Fmt;

import java.util.ArrayList;
import java.util.List;

public class CategorySummaryAdapter extends RecyclerView.Adapter<CategorySummaryAdapter.Holder> {

    public interface Listener {
        void onSelect(int position);
    }

    private final List<MonthSummary.Slice> items = new ArrayList<>();
    private final Listener listener;
    private int selectedIndex = -1;

    public CategorySummaryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<MonthSummary.Slice> newItems, int selectedIndex) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        this.selectedIndex = selectedIndex;
        notifyDataSetChanged();
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCategorySummaryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MonthSummary.Slice slice = items.get(position);
        CategorySpend s = slice.spend;
        ItemCategorySummaryBinding b = holder.binding;
        Context context = b.getRoot().getContext();
        int plum = Fmt.primaryDark(context);
        int color = Fmt.parseColor(s.color, Fmt.color(context, R.color.primary_container));

        b.textName.setText(s.name);
        b.textCount.setText(s.count + (s.count == 1 ? " transaction" : " transactions"));
        b.textAmount.setText(Fmt.money(s.total));
        b.textPercent.setText(slice.percent + "%");
        b.textPercent.setBackground(Fmt.box(context, color, plum, 1.5f, 3));
        b.textTag.setVisibility(position == 0 ? View.VISIBLE : View.GONE);   // the biggest bucket

        b.iconTile.setImageResource(CategoryIcons.forKey(s.icon));
        b.iconTile.setBackground(Fmt.box(context, color, plum, 2, 4));

        b.progress.setProgress(slice.percent);
        b.progress.setProgressTintList(ColorStateList.valueOf(plum));

        b.getRoot().setBackgroundResource(position == selectedIndex ? R.drawable.bg_card_selected : R.drawable.bg_card_white);
        // setting a background can reset padding, so set the card padding again
        b.getRoot().setPadding(Fmt.dp(context, 14), Fmt.dp(context, 12), Fmt.dp(context, 18), Fmt.dp(context, 16));

        b.getRoot().setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onSelect(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemCategorySummaryBinding binding;

        Holder(ItemCategorySummaryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}