package com.expensetracker.app.ui.expenses;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.expensetracker.app.R;
import com.expensetracker.app.databinding.ItemExpenseBinding;
import com.expensetracker.app.model.Expense;
import com.expensetracker.app.model.ExpenseRow;
import com.expensetracker.app.ui.Fmt;

import java.util.ArrayList;
import java.util.List;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.Holder> {

    public interface Listener {
        void onEdit(ExpenseRow row);
        void onDelete(ExpenseRow row);
    }

    private final List<ExpenseRow> rows = new ArrayList<>();
    private final Listener listener;
    private int highlightId = -1;

    public ExpenseAdapter(Listener listener) {
        this.listener = listener;
    }

    // highlightId = the row to light up teal (the one just recorded), or -1
    public void submit(List<ExpenseRow> newRows, int highlightId) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        this.highlightId = highlightId;
        notifyDataSetChanged();   // at most one page of rows, so a full refresh is fine
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemExpenseBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ExpenseRow row = rows.get(position);
        Expense e = row.expense;
        ItemExpenseBinding b = holder.binding;
        Context context = b.getRoot().getContext();

        int background = e.id == highlightId ? R.color.green_container
                : position % 2 == 0 ? R.color.white : R.color.surface_variant;
        b.getRoot().setBackgroundColor(Fmt.color(context, background));

        b.textDate.setText(Fmt.monthDay(e.expenseDate));
        b.textYear.setText(Fmt.year(e.expenseDate));
        b.textItem.setText(e.description);
        b.textCost.setText(Fmt.money(e.amount));

        b.textCategory.setText(Fmt.abbrev(row.categoryName));
        int fill = Fmt.parseColor(row.categoryColor, Fmt.color(context, R.color.primary_container));
        b.textCategory.setBackground(Fmt.box(context, fill, Fmt.primaryDark(context), 1.5f, 3));

        b.btnEdit.setOnClickListener(v -> listener.onEdit(row));
        b.btnDelete.setOnClickListener(v -> listener.onDelete(row));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemExpenseBinding binding;

        Holder(ItemExpenseBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}