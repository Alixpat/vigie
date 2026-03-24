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
    private static final String LINE_REF = "STIF:Line::C01736:";
    private static final String API_URL =
            "https://prim.iledefrance-mobilites.fr/marketplace/general-message"
                    + "?LineRef=" + LINE_REF;

    // Aller: Clamart → Villepreux
    private RecyclerView recyclerView;
    private TextView emptyText;
    private TextView lastUpdateText;
    private TrainIncidentAdapter adapter;

    // Retour: Villepreux → Clamart
    private RecyclerView recyclerViewRetour;
    private TextView emptyTextRetour;
    private TextView lastUpdateTextRetour;
    private TrainIncidentAdapter adapterRetour;

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

        // Aller
        recyclerView = view.findViewById(R.id.trainRecyclerView);
        emptyText = view.findViewById(R.id.trainEmptyText);
        lastUpdateText = view.findViewById(R.id.trainLastUpdate);

        adapter = new TrainIncidentAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // Retour
        recyclerViewRetour = view.findViewById(R.id.trainRecyclerViewRetour);
        emptyTextRetour = view.findViewById(R.id.trainEmptyTextRetour);
        lastUpdateTextRetour = view.findViewById(R.id.trainLastUpdateRetour);

        adapterRetour = new TrainIncidentAdapter();
        recyclerViewRetour.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewRetour.setAdapter(adapterRetour);
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
            showMessage(emptyText, recyclerView, "Token IDFM non configuré.\nAllez dans Paramètres pour l'ajouter.");
            showMessage(emptyTextRetour, recyclerViewRetour, "Token IDFM non configuré.\nAllez dans Paramètres pour l'ajouter.");
            return;
        }

        String token = config.getIdfmToken();
        executor.execute(() -> {
            List<TrainIncident> incidents = fetchFromApi(token);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    String updateTime = "MAJ " + sdf.format(new Date());
                    lastUpdateText.setText(updateTime);
                    lastUpdateTextRetour.setText(updateTime);

                    if (incidents == null) {
                        showMessage(emptyText, recyclerView, "Erreur de chargement.\nVérifiez votre token IDFM.");
                        showMessage(emptyTextRetour, recyclerViewRetour, "Erreur de chargement.\nVérifiez votre token IDFM.");
                    } else if (incidents.isEmpty()) {
                        showMessage(emptyText, recyclerView, "Aucune perturbation en cours\nsur la Ligne N");
                        showMessage(emptyTextRetour, recyclerViewRetour, "Aucune perturbation en cours\nsur la Ligne N");
                    } else {
                        emptyText.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.updateIncidents(incidents);

                        emptyTextRetour.setVisibility(View.GONE);
                        recyclerViewRetour.setVisibility(View.VISIBLE);
                        adapterRetour.updateIncidents(incidents);
                    }
                });
            }
        });
    }

    private void showMessage(TextView emptyView, RecyclerView recycler, String message) {
        emptyView.setText(message);
        emptyView.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
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

                return parseResponse(response.toString());
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

    private List<TrainIncident> parseResponse(String jsonStr) {
        List<TrainIncident> incidents = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject delivery = root
                    .getJSONObject("Siri")
                    .getJSONObject("ServiceDelivery")
                    .getJSONArray("GeneralMessageDelivery")
                    .getJSONObject(0);

            if (!delivery.has("InfoMessage")) {
                return incidents;
            }

            JSONArray messages = delivery.getJSONArray("InfoMessage");
            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.getJSONObject(i);
                JSONObject content = msg.optJSONObject("Content");
                if (content == null) continue;

                // Extract message text
                String text = "";
                JSONArray msgTexts = content.optJSONArray("Message");
                if (msgTexts != null) {
                    for (int j = 0; j < msgTexts.length(); j++) {
                        JSONObject msgObj = msgTexts.getJSONObject(j);
                        JSONObject msgText = msgObj.optJSONObject("MessageText");
                        if (msgText != null) {
                            String value = msgText.optString("value", "");
                            if (!value.isEmpty()) {
                                text = value;
                                break;
                            }
                        }
                    }
                }

                // Extract severity from InfoChannelRef (object with "value" field)
                String severity = "information";
                JSONObject channelRef = msg.optJSONObject("InfoChannelRef");
                if (channelRef != null) {
                    String channel = channelRef.optString("value", "");
                    if (channel.contains("Perturbation")) {
                        severity = "blocking";
                    } else if (channel.contains("Information")) {
                        severity = "information";
                    }
                } else {
                    String channel = msg.optString("InfoChannelRef", "");
                    if (channel.contains("Perturbation")) {
                        severity = "blocking";
                    }
                }

                // Extract timestamps
                String recordedAt = formatDateTime(msg.optString("RecordedAtTime", ""));
                String validUntil = formatDateTime(msg.optString("ValidUntilTime", ""));

                if (!text.isEmpty()) {
                    incidents.add(new TrainIncident(
                            "Perturbation Ligne N",
                            text,
                            severity,
                            "",
                            recordedAt,
                            validUntil
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return incidents;
    }

    private static String formatDateTime(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isEmpty()) return "";
        try {
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
