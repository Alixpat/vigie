package com.alixpat.vigie.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.alixpat.vigie.BrokerConfig;
import com.alixpat.vigie.R;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoitureFragment extends Fragment {

    private static final String TAG = "VoitureFragment";
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000;

    private static final String TOMTOM_ROUTING_URL = "https://api.tomtom.com/routing/1/calculateRoute";
    // Rue Claude Bernard, Issy-les-Moulineaux
    private static final String ISSY_COORDS = "48.8148,2.2699";
    // Avenue d'Anjou, Villepreux
    private static final String VILLEPREUX_COORDS = "48.8340,1.9970";

    private MaterialCardView drivingTimeCard;
    private TextView drivingTimeAller;
    private TextView drivingTimeRetour;
    private TextView drivingTimeUpdate;
    private TextView drivingTimeEmpty;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchDrivingTimes();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_voiture, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        drivingTimeCard = view.findViewById(R.id.drivingTimeCard);
        drivingTimeAller = view.findViewById(R.id.drivingTimeAller);
        drivingTimeRetour = view.findViewById(R.id.drivingTimeRetour);
        drivingTimeUpdate = view.findViewById(R.id.drivingTimeUpdate);
        drivingTimeEmpty = view.findViewById(R.id.drivingTimeEmpty);
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchDrivingTimes();
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void fetchDrivingTimes() {
        BrokerConfig config = new BrokerConfig(requireContext());
        if (!config.hasTomTomApiKey()) {
            Log.w(TAG, "fetchDrivingTimes: Clé API TomTom non configurée");
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    drivingTimeCard.setVisibility(View.GONE);
                    drivingTimeEmpty.setVisibility(View.VISIBLE);
                    drivingTimeEmpty.setText("Clé API TomTom non configurée.\nRendez-vous dans les paramètres.");
                });
            }
            return;
        }

        String apiKey = config.getTomTomApiKey();

        executor.execute(() -> {
            int allerSeconds = fetchDrivingTimeFromTomTom(apiKey, ISSY_COORDS, VILLEPREUX_COORDS);
            int retourSeconds = fetchDrivingTimeFromTomTom(apiKey, VILLEPREUX_COORDS, ISSY_COORDS);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    drivingTimeCard.setVisibility(View.VISIBLE);
                    drivingTimeEmpty.setVisibility(View.GONE);

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    drivingTimeUpdate.setText("MAJ " + sdf.format(new Date()));

                    drivingTimeAller.setText(allerSeconds >= 0 ? formatDuration(allerSeconds) : "--");
                    drivingTimeRetour.setText(retourSeconds >= 0 ? formatDuration(retourSeconds) : "--");
                });
            }
        });
    }

    private int fetchDrivingTimeFromTomTom(String apiKey, String origin, String destination) {
        HttpURLConnection connection = null;
        try {
            String apiUrl = TOMTOM_ROUTING_URL
                    + "/" + origin + ":" + destination
                    + "/json"
                    + "?key=" + URLEncoder.encode(apiKey, "UTF-8")
                    + "&traffic=true"
                    + "&travelMode=car";

            Log.d(TAG, "fetchDrivingTimeFromTomTom: " + origin + " -> " + destination);

            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
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

                JSONObject root = new JSONObject(response.toString());
                JSONArray routes = root.getJSONArray("routes");
                if (routes.length() > 0) {
                    JSONObject firstRoute = routes.getJSONObject(0);
                    JSONObject summary = firstRoute.getJSONObject("summary");
                    int travelTimeSeconds = summary.getInt("travelTimeInSeconds");
                    Log.i(TAG, "fetchDrivingTimeFromTomTom: " + origin + " -> " + destination
                            + " = " + travelTimeSeconds + "s (traffic: "
                            + summary.getInt("trafficDelayInSeconds") + "s delay)");
                    return travelTimeSeconds;
                }
            } else {
                Log.e(TAG, "fetchDrivingTimeFromTomTom: HTTP " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchDrivingTimeFromTomTom: exception", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return -1;
    }

    private static String formatDuration(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h" + String.format(Locale.getDefault(), "%02d", minutes);
        }
        return minutes + " min";
    }
}
