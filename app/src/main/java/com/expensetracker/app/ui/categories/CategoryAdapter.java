package com.expensetracker.app.ui.categories;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.expensetracker.app.R;
import com.expensetracker.app.databinding.ItemCategoryBinding;
import com.expensetracker.app.model.CategoryItem;
import com.expensetracker.app.ui.CategoryIcons;
import com.expensetracker.app.ui.Fmt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {

    public interface Listener {
        void onToggle(CategoryItem item, boolean checked);
        void onEdit(CategoryItem item);
        void onDelete(CategoryItem item);
    }

    private final List<CategoryItem> items = new ArrayList<>();
    private Set<Integer> selected = new HashSet<>();
    private final Listener listener;

    public CategoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<CategoryItem> newItems, Set<Integer> selectedIds) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        selected = selectedIds;
        notifyDataSetChanged();   // a few dozen rows at most
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CategoryItem c = items.get(position);
        ItemCategoryBinding b = holder.binding;
        Context context = b.getRoot().getContext();
        int plum = Fmt.primaryDark(context);
        boolean isSelected = selected.contains(c.id);

        b.textName.setText(c.name);
        b.textStats.setText(c.itemCount + " items • " + Fmt.money(c.totalSpent) + " spent");

        if (c.builtIn) {
            b.textTag.setText("DEFAULT");
            b.textTag.setVisibility(View.VISIBLE);
        } else if (c.monthlyLimit != null) {
            b.textTag.setText("LIMIT " + Fmt.money(c.monthlyLimit));
            b.textTag.setVisibility(View.VISIBLE);
        } else {
            b.textTag.setVisibility(View.GONE);
        }

        b.iconTile.setImageResource(CategoryIcons.forKey(c.icon));
        b.iconTile.setBackground(Fmt.box(context, Fmt.parseColor(c.color, Fmt.color(context, R.color.primary_container)),
                plum, 2, 4));

        // checkbox: clear the listener first so setChecked doesn't fire it
        b.checkSelect.setOnCheckedChangeListener(null);
        b.checkSelect.setChecked(isSelected);
        b.checkSelect.setEnabled(!c.builtIn);        // built-in categories can't be purged
        b.checkSelect.setAlpha(c.builtIn ? 0.3f : 1f);
        b.checkSelect.setOnCheckedChangeListener((view, checked) -> listener.onToggle(c, checked));

        // locked delete button for the built-in ones
        b.btnDelete.setEnabled(!c.builtIn);
        b.btnDelete.setImageResource(c.builtIn ? R.drawable.ic_lock : R.drawable.ic_delete);
        b.btnDelete.setBackgroundResource(c.builtIn ? R.drawable.bg_icon_locked : R.drawable.bg_icon_delete);
        b.btnDelete.setPadding(Fmt.dp(context, 8), Fmt.dp(context, 8), Fmt.dp(context, 8), Fmt.dp(context, 8));
        b.btnDelete.setContentDescription(c.builtIn ? "Default category, can't be deleted" : "Delete category");

        b.btnEdit.setOnClickListener(v -> listener.onEdit(c));
        b.btnDelete.setOnClickListener(v -> listener.onDelete(c));

        b.getRoot().setBackgroundResource(isSelected ? R.drawable.bg_card_selected : R.drawable.bg_card_white);
        // setting a background can reset padding, so set the card padding again
        b.getRoot().setPadding(Fmt.dp(context, 8), Fmt.dp(context, 12), Fmt.dp(context, 18), Fmt.dp(context, 16));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemCategoryBinding binding;

        Holder(ItemCategoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}