package com.expensetracker.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.expensetracker.app.databinding.FragmentPlaceholderBinding;

public class PlaceholderFragment extends Fragment {
    private FragmentPlaceholderBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlaceholderBinding.inflate(inflater, container, false);
        NavDestination destination = NavHostFragment.findNavController(this).getCurrentDestination();
        CharSequence label = destination == null ? "" : destination.getLabel();
        binding.textLabel.setText(label + "\n\ncoming soon");
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;   // views are gone: drop the reference
    }
}
