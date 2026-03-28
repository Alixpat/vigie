package com.alixpat.vigie.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.alixpat.vigie.fragment.BackupFragment;
import com.alixpat.vigie.fragment.LanFragment;
import com.alixpat.vigie.fragment.MessagesFragment;
import com.alixpat.vigie.fragment.TrainFragment;
import com.alixpat.vigie.fragment.VoitureFragment;
import com.alixpat.vigie.fragment.WeatherFragment;

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
            case 2:
                return new BackupFragment();
            case 3:
                return new WeatherFragment();
            case 4:
                return new TrainFragment();
            case 5:
                return new VoitureFragment();
            default:
                return new MessagesFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 6;
    }
}
