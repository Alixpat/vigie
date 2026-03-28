package com.alixpat.vigie.fragment;

import android.content.SharedPreferences;
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
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoitureFragment extends Fragment {

    private static final String TAG = "VoitureFragment";
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000;
    private static final long HISTORY_WINDOW_MS = 2 * 60 * 60 * 1000; // 2 heures

    private static final String TOMTOM_ROUTING_URL = "https://api.tomtom.com/routing/1/calculateRoute";
    // Rue Claude Bernard, Issy-les-Moulineaux
    private static final String ISSY_COORDS = "48.8148,2.2699";
    // Avenue d'Anjou, Villepreux
    private static final String VILLEPREUX_COORDS = "48.8340,1.9970";

    private static final String PREFS_NAME = "vigie_driving_history";
    private static final String PREF_ALLER_HISTORY = "aller_history";
    private static final String PREF_RETOUR_HISTORY = "retour_history";

    private MaterialCardView drivingTimeCard;
    private TextView drivingTimeAller;
    private TextView drivingTimeRetour;
    private TextView drivingTimeUpdate;
    private TextView drivingTimeEmpty;
    private TextView drivingTimeTrendAller;
    private TextView drivingTimeTrendRetour;
    private TextView drivingTimeFlowAller;
    private TextView drivingTimeFlowRetour;

    // Timestamped history entries for 30-min rolling average
    private final List<long[]> allerHistory = new ArrayList<>(); // [timestamp, seconds]
    private final List<long[]> retourHistory = new ArrayList<>();

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
        drivingTimeTrendAller = view.findViewById(R.id.drivingTimeTrendAller);
        drivingTimeTrendRetour = view.findViewById(R.id.drivingTimeTrendRetour);
        drivingTimeFlowAller = view.findViewById(R.id.drivingTimeFlowAller);
        drivingTimeFlowRetour = view.findViewById(R.id.drivingTimeFlowRetour);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHistory();
        fetchDrivingTimes();
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
        saveHistory();
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
            int[] allerResult = fetchRouteInfo(apiKey, ISSY_COORDS, VILLEPREUX_COORDS);
            int[] retourResult = fetchRouteInfo(apiKey, VILLEPREUX_COORDS, ISSY_COORDS);
            int allerSeconds = allerResult[0];
            int allerDelay = allerResult[1];
            int retourSeconds = retourResult[0];
            int retourDelay = retourResult[1];

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    drivingTimeCard.setVisibility(View.VISIBLE);
                    drivingTimeEmpty.setVisibility(View.GONE);

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    drivingTimeUpdate.setText("MAJ " + sdf.format(new Date()));

                    drivingTimeAller.setText(allerSeconds >= 0 ? formatDuration(allerSeconds) : "--");
                    drivingTimeRetour.setText(retourSeconds >= 0 ? formatDuration(retourSeconds) : "--");

                    updateTrend(drivingTimeTrendAller, allerSeconds, allerHistory);
                    updateTrend(drivingTimeTrendRetour, retourSeconds, retourHistory);

                    updateFlowStatus(drivingTimeFlowAller, allerSeconds, allerDelay);
                    updateFlowStatus(drivingTimeFlowRetour, retourSeconds, retourDelay);

                    if (allerSeconds >= 0) addToHistory(allerHistory, allerSeconds);
                    if (retourSeconds >= 0) addToHistory(retourHistory, retourSeconds);
                });
            }
        });
    }

    /**
     * Returns [travelTimeInSeconds, trafficDelayInSeconds] or [-1, -1] on error.
     */
    private int[] fetchRouteInfo(String apiKey, String origin, String destination) {
        HttpURLConnection connection = null;
        try {
            String apiUrl = TOMTOM_ROUTING_URL
                    + "/" + origin + ":" + destination
                    + "/json"
                    + "?key=" + URLEncoder.encode(apiKey, "UTF-8")
                    + "&traffic=true"
                    + "&travelMode=car";

            Log.d(TAG, "fetchRouteInfo: " + origin + " -> " + destination);

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
                    int trafficDelay = summary.getInt("trafficDelayInSeconds");
                    Log.i(TAG, "fetchRouteInfo: " + origin + " -> " + destination
                            + " = " + travelTimeSeconds + "s (traffic: "
                            + trafficDelay + "s delay)");
                    return new int[]{travelTimeSeconds, trafficDelay};
                }
            } else {
                Log.e(TAG, "fetchRouteInfo: HTTP " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchRouteInfo: exception", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return new int[]{-1, -1};
    }

    private void addToHistory(List<long[]> history, int value) {
        long now = System.currentTimeMillis();
        history.add(new long[]{now, value});
        pruneHistory(history);
    }

    private void pruneHistory(List<long[]> history) {
        long cutoff = System.currentTimeMillis() - HISTORY_WINDOW_MS;
        Iterator<long[]> it = history.iterator();
        while (it.hasNext()) {
            if (it.next()[0] < cutoff) {
                it.remove();
            } else {
                break; // entries are chronological, no need to check further
            }
        }
    }

    private int computeAverage(List<long[]> history) {
        if (history.isEmpty()) return -1;
        long sum = 0;
        for (long[] entry : history) {
            sum += entry[1];
        }
        return (int) (sum / history.size());
    }

    private void updateTrend(TextView trendView, int currentSeconds, List<long[]> history) {
        if (currentSeconds < 0 || history.isEmpty()) {
            trendView.setVisibility(View.GONE);
            return;
        }

        // Compare current value with the average over the 30-min history window
        int averageSeconds = computeAverage(history);
        int diffSeconds = currentSeconds - averageSeconds;

        trendView.setVisibility(View.VISIBLE);
        // Ignore minor variations (less than 1 minute)
        if (Math.abs(diffSeconds) < 60) {
            trendView.setText("\u2192"); // → stable
            trendView.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_info));
        } else if (diffSeconds > 0) {
            int diffMin = diffSeconds / 60;
            trendView.setText("\u2197 +" + diffMin + " min"); // ↗ +X min
            trendView.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_error));
        } else {
            int diffMin = Math.abs(diffSeconds) / 60;
            trendView.setText("\u2198 -" + diffMin + " min"); // ↘ -X min
            trendView.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_ok));
        }
    }

    private void saveHistory() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_ALLER_HISTORY, serializeHistory(allerHistory));
        editor.putString(PREF_RETOUR_HISTORY, serializeHistory(retourHistory));
        editor.apply();
    }

    private void loadHistory() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        allerHistory.clear();
        retourHistory.clear();
        deserializeHistory(prefs.getString(PREF_ALLER_HISTORY, ""), allerHistory);
        deserializeHistory(prefs.getString(PREF_RETOUR_HISTORY, ""), retourHistory);
        pruneHistory(allerHistory);
        pruneHistory(retourHistory);
    }

    private static String serializeHistory(List<long[]> history) {
        JSONArray arr = new JSONArray();
        for (long[] entry : history) {
            JSONArray pair = new JSONArray();
            pair.put(entry[0]);
            pair.put(entry[1]);
            arr.put(pair);
        }
        return arr.toString();
    }

    private static void deserializeHistory(String json, List<long[]> out) {
        if (json == null || json.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONArray pair = arr.getJSONArray(i);
                out.add(new long[]{pair.getLong(0), pair.getLong(1)});
            }
        } catch (Exception e) {
            Log.w("VoitureFragment", "Failed to deserialize history", e);
        }
    }

    private void updateFlowStatus(TextView flowView, int travelTimeSeconds, int trafficDelaySeconds) {
        if (travelTimeSeconds < 0 || trafficDelaySeconds < 0) {
            flowView.setVisibility(View.GONE);
            return;
        }

        flowView.setVisibility(View.VISIBLE);
        double delayRatio = (double) trafficDelaySeconds / travelTimeSeconds;

        if (delayRatio < 0.10) {
            flowView.setText("Fluide");
            flowView.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_ok));
        } else if (delayRatio < 0.25) {
            flowView.setText("Ralenti");
            flowView.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_warning));
        } else {
            flowView.setText("Dense");
            flowView.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_error));
        }
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
