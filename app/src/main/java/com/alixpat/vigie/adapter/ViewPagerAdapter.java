package com.alixpat.vigie.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.alixpat.vigie.fragment.LanFragment;
import com.alixpat.vigie.fragment.MessagesFragment;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:
                return new LanFragment();
            default:
                return new MessagesFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
