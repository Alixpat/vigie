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

import com.alixpat.vigie.BrokerConfig;
import com.alixpat.vigie.R;
import com.alixpat.vigie.adapter.TrainIncidentAdapter;
import com.alixpat.vigie.model.TrainIncident;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrainFragment extends Fragment {

    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    // Ligne N Transilien
    private static final String LINE_REF = "STIF:Line::C01740:";
    private static final String API_URL =
            "https://prim.iledefrance-mobilites.fr/marketplace/disruptions_bulk/disruptions/v2"
                    + "?LineRef=" + LINE_REF;

    private RecyclerView recyclerView;
    private TextView emptyText;
    private TextView lastUpdateText;
    private TrainIncidentAdapter adapter;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchIncidents();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_train, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.trainRecyclerView);
        emptyText = view.findViewById(R.id.trainEmptyText);
        lastUpdateText = view.findViewById(R.id.trainLastUpdate);

        adapter = new TrainIncidentAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchIncidents();
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void fetchIncidents() {
        BrokerConfig config = new BrokerConfig(requireContext());
        if (!config.hasIdfmToken()) {
            showMessage("Token IDFM non configuré.\nAllez dans Paramètres pour l'ajouter.");
            return;
        }

        String token = config.getIdfmToken();
        executor.execute(() -> {
            List<TrainIncident> incidents = fetchFromApi(token);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    lastUpdateText.setText("MAJ " + sdf.format(new Date()));

                    if (incidents == null) {
                        showMessage("Erreur de chargement.\nVérifiez votre token IDFM.");
                    } else if (incidents.isEmpty()) {
                        showMessage("Aucune perturbation en cours\nsur la Ligne N");
                    } else {
                        emptyText.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.updateIncidents(incidents);
                    }
                });
            }
        });
    }

    private void showMessage(String message) {
        emptyText.setText(message);
        emptyText.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
    }

    private List<TrainIncident> fetchFromApi(String token) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", token);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

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

                return parseDisruptions(response.toString());
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

    private List<TrainIncident> parseDisruptions(String jsonStr) {
        List<TrainIncident> incidents = new ArrayList<>();
        try {
            // PRIM v2 returns a JSON array of disruptions
            JSONArray disruptions;
            // Handle both array and object with array field
            if (jsonStr.trim().startsWith("[")) {
                disruptions = new JSONArray(jsonStr);
            } else {
                JSONObject root = new JSONObject(jsonStr);
                if (root.has("disruptions")) {
                    disruptions = root.getJSONArray("disruptions");
                } else if (root.has("Siri")) {
                    return parseSiri(root);
                } else {
                    disruptions = new JSONArray();
                }
            }

            for (int i = 0; i < disruptions.length(); i++) {
                JSONObject disruption = disruptions.getJSONObject(i);

                String title = optString(disruption, "Title");
                String message = optString(disruption, "Message");
                String severity = optString(disruption, "Severity");
                String cause = optString(disruption, "Cause");
                String startTime = formatDateTime(optString(disruption, "StartTime"));
                String endTime = formatDateTime(optString(disruption, "EndTime"));

                // Fallback field names (API may vary)
                if (title.isEmpty()) title = optString(disruption, "title");
                if (message.isEmpty()) message = optString(disruption, "message");
                if (severity.isEmpty()) severity = optString(disruption, "severity");

                if (title.isEmpty() && message.isEmpty()) continue;

                incidents.add(new TrainIncident(
                        title.isEmpty() ? "Perturbation Ligne N" : title,
                        message,
                        severity,
                        cause,
                        startTime,
                        endTime
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return incidents;
    }

    private List<TrainIncident> parseSiri(JSONObject root) {
        List<TrainIncident> incidents = new ArrayList<>();
        try {
            JSONObject delivery = root
                    .getJSONObject("Siri")
                    .getJSONObject("ServiceDelivery")
                    .getJSONArray("GeneralMessageDelivery")
                    .getJSONObject(0);

            JSONArray messages = delivery.getJSONArray("InfoMessage");
            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.getJSONObject(i);
                JSONObject content = msg.optJSONObject("Content");
                if (content == null) continue;

                JSONArray msgTexts = content.optJSONArray("Message");
                String text = "";
                if (msgTexts != null && msgTexts.length() > 0) {
                    JSONObject firstMsg = msgTexts.getJSONObject(0);
                    JSONObject msgText = firstMsg.optJSONObject("MessageText");
                    if (msgText != null) {
                        text = msgText.optString("value", "");
                    }
                }

                String severity = "";
                String infoChannelRef = msg.optString("InfoChannelRef", "");
                if (infoChannelRef.contains("Perturbation")) {
                    severity = "blocking";
                } else if (infoChannelRef.contains("Information")) {
                    severity = "information";
                }

                if (!text.isEmpty()) {
                    incidents.add(new TrainIncident(
                            "Perturbation Ligne N",
                            text,
                            severity,
                            "",
                            "",
                            ""
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return incidents;
    }

    private static String optString(JSONObject obj, String key) {
        if (obj.has(key) && !obj.isNull(key)) {
            return obj.optString(key, "");
        }
        return "";
    }

    private static String formatDateTime(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isEmpty()) return "";
        try {
            // Handle ISO 8601 format like 2024-01-15T08:30:00+01:00
            String clean = isoDateTime;
            if (clean.length() > 16) {
                clean = clean.substring(0, 16);
            }
            clean = clean.replace("T", " ");
            return clean;
        } catch (Exception e) {
            return isoDateTime;
        }
    }
}
