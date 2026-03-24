package com.alixpat.vigie.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.WeatherData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WeatherAdapter extends RecyclerView.Adapter<WeatherAdapter.ViewHolder> {

    private final List<WeatherData> weatherList = new ArrayList<>();

    public void updateWeather(List<WeatherData> newData) {
        weatherList.clear();
        weatherList.addAll(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_weather_city, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WeatherData data = weatherList.get(position);
        holder.cityName.setText(data.getCityName());
        holder.weatherIcon.setText(data.getWeatherEmoji());
        holder.temperatureText.setText(String.format(Locale.FRANCE, "%.1f °C", data.getTemperature()));
        holder.weatherDescription.setText(data.getWeatherDescription());

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        holder.lastUpdateText.setText("Mis à jour à " + sdf.format(new Date(data.getLastUpdate())));
    }

    @Override
    public int getItemCount() {
        return weatherList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView cityName;
        final TextView weatherIcon;
        final TextView temperatureText;
        final TextView weatherDescription;
        final TextView lastUpdateText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cityName = itemView.findViewById(R.id.cityName);
            weatherIcon = itemView.findViewById(R.id.weatherIcon);
            temperatureText = itemView.findViewById(R.id.temperatureText);
            weatherDescription = itemView.findViewById(R.id.weatherDescription);
            lastUpdateText = itemView.findViewById(R.id.lastUpdateText);
        }
    }
}
