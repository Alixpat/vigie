package com.alixpat.vigie.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.R;
import com.alixpat.vigie.adapter.WeatherAdapter;
import com.alixpat.vigie.model.WeatherData;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeatherFragment extends Fragment {

    private static final long REFRESH_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes

    private static final double[][] CITIES = {
            {48.8235, 2.2700},  // Issy-les-Moulineaux
            {48.8044, 1.9803},  // Villepreux
    };
    private static final String[] CITY_NAMES = {
            "Issy-les-Moulineaux",
            "Villepreux",
    };

    private RecyclerView recyclerView;
    private TextView emptyText;
    private WeatherAdapter adapter;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchWeather();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_weather, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.weatherRecyclerView);
        emptyText = view.findViewById(R.id.weatherEmptyText);

        adapter = new WeatherAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchWeather();
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void fetchWeather() {
        executor.execute(() -> {
            List<WeatherData> results = new ArrayList<>();
            for (int i = 0; i < CITIES.length; i++) {
                WeatherData data = fetchCityWeather(CITY_NAMES[i], CITIES[i][0], CITIES[i][1]);
                if (data != null) {
                    results.add(data);
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (results.isEmpty()) {
                        emptyText.setText("Impossible de charger la météo");
                        emptyText.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        emptyText.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.updateWeather(results);
                    }
                });
            }
        });
    }

    private WeatherData fetchCityWeather(String cityName, double lat, double lon) {
        HttpURLConnection connection = null;
        try {
            String urlStr = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + lat
                    + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code";
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                JSONObject current = json.getJSONObject("current");
                double temperature = current.getDouble("temperature_2m");
                int weatherCode = current.getInt("weather_code");

                return new WeatherData(cityName, temperature, weatherCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }
}
