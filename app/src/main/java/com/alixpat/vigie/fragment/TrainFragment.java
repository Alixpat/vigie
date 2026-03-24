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
import com.alixpat.vigie.adapter.TrainScheduleAdapter;
import com.alixpat.vigie.model.TrainIncident;
import com.alixpat.vigie.model.TrainSchedule;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrainFragment extends Fragment {

    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    // Ligne N Transilien
    private static final String LINE_REF = "STIF:Line::C01736:";
    private static final String GENERAL_MESSAGE_URL =
            "https://prim.iledefrance-mobilites.fr/marketplace/general-message"
                    + "?LineRef=" + LINE_REF;

    // Stop monitoring API for schedules
    private static final String STOP_MONITORING_URL =
            "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring";
    // Clamart station MonitoringRef (Ligne N)
    private static final String CLAMART_STOP_REF = "STIF:StopPoint:Q:41109:";
    // Villepreux-Fontenay station MonitoringRef (Ligne N)
    private static final String VILLEPREUX_STOP_REF = "STIF:StopPoint:Q:41326:";

    // Time filters (hours and minutes)
    private static final int ALLER_START_HOUR = 6;
    private static final int ALLER_START_MIN = 30;
    private static final int ALLER_END_HOUR = 8;
    private static final int ALLER_END_MIN = 0;

    private static final int RETOUR_START_HOUR = 7;
    private static final int RETOUR_START_MIN = 0;
    private static final int RETOUR_END_HOUR = 8;
    private static final int RETOUR_END_MIN = 30;

    // Schedules - Aller: Clamart → Villepreux
    private RecyclerView scheduleRecyclerViewAller;
    private TextView scheduleEmptyAller;
    private TextView scheduleLastUpdateAller;
    private TrainScheduleAdapter scheduleAdapterAller;

    // Schedules - Retour: Villepreux → Clamart
    private RecyclerView scheduleRecyclerViewRetour;
    private TextView scheduleEmptyRetour;
    private TextView scheduleLastUpdateRetour;
    private TrainScheduleAdapter scheduleAdapterRetour;

    // Incidents
    private RecyclerView recyclerView;
    private TextView emptyText;
    private TextView lastUpdateText;
    private TrainIncidentAdapter adapter;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchAll();
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

        // Schedules - Aller
        scheduleRecyclerViewAller = view.findViewById(R.id.scheduleRecyclerViewAller);
        scheduleEmptyAller = view.findViewById(R.id.scheduleEmptyAller);
        scheduleLastUpdateAller = view.findViewById(R.id.scheduleLastUpdateAller);
        scheduleAdapterAller = new TrainScheduleAdapter();
        scheduleRecyclerViewAller.setLayoutManager(new LinearLayoutManager(requireContext()));
        scheduleRecyclerViewAller.setAdapter(scheduleAdapterAller);

        // Schedules - Retour
        scheduleRecyclerViewRetour = view.findViewById(R.id.scheduleRecyclerViewRetour);
        scheduleEmptyRetour = view.findViewById(R.id.scheduleEmptyRetour);
        scheduleLastUpdateRetour = view.findViewById(R.id.scheduleLastUpdateRetour);
        scheduleAdapterRetour = new TrainScheduleAdapter();
        scheduleRecyclerViewRetour.setLayoutManager(new LinearLayoutManager(requireContext()));
        scheduleRecyclerViewRetour.setAdapter(scheduleAdapterRetour);

        // Incidents
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
        fetchAll();
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void fetchAll() {
        fetchSchedules();
        fetchIncidents();
    }

    // ==================== SCHEDULES ====================

    private void fetchSchedules() {
        BrokerConfig config = new BrokerConfig(requireContext());
        if (!config.hasIdfmToken()) {
            showMessage(scheduleEmptyAller, scheduleRecyclerViewAller,
                    "Token IDFM non configuré.");
            showMessage(scheduleEmptyRetour, scheduleRecyclerViewRetour,
                    "Token IDFM non configuré.");
            return;
        }

        String token = config.getIdfmToken();
        executor.execute(() -> {
            List<TrainSchedule> allerSchedules = fetchStopMonitoring(
                    token, CLAMART_STOP_REF,
                    ALLER_START_HOUR, ALLER_START_MIN, ALLER_END_HOUR, ALLER_END_MIN);

            List<TrainSchedule> retourSchedules = fetchStopMonitoring(
                    token, VILLEPREUX_STOP_REF,
                    RETOUR_START_HOUR, RETOUR_START_MIN, RETOUR_END_HOUR, RETOUR_END_MIN);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    String updateTime = "MAJ " + sdf.format(new Date());
                    scheduleLastUpdateAller.setText(updateTime);
                    scheduleLastUpdateRetour.setText(updateTime);

                    updateScheduleUI(allerSchedules, scheduleEmptyAller,
                            scheduleRecyclerViewAller, scheduleAdapterAller, "Clamart \u2192 Villepreux");
                    updateScheduleUI(retourSchedules, scheduleEmptyRetour,
                            scheduleRecyclerViewRetour, scheduleAdapterRetour, "Villepreux \u2192 Clamart");
                });
            }
        });
    }

    private void updateScheduleUI(List<TrainSchedule> schedules, TextView emptyView,
                                  RecyclerView recycler, TrainScheduleAdapter scheduleAdapter,
                                  String direction) {
        if (schedules == null) {
            showMessage(emptyView, recycler, "Erreur de chargement des horaires.");
        } else if (schedules.isEmpty()) {
            showMessage(emptyView, recycler, "Aucun train prévu\ndans ce créneau horaire.");
        } else {
            emptyView.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            scheduleAdapter.updateSchedules(schedules);
        }
    }

    private List<TrainSchedule> fetchStopMonitoring(String token, String stopRef,
                                                     int startHour, int startMin,
                                                     int endHour, int endMin) {
        HttpURLConnection connection = null;
        try {
            String apiUrl = STOP_MONITORING_URL
                    + "?MonitoringRef=" + stopRef
                    + "&LineRef=" + LINE_REF;

            URL url = new URL(apiUrl);
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

                return parseStopMonitoring(response.toString(),
                        startHour, startMin, endHour, endMin);
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

    private List<TrainSchedule> parseStopMonitoring(String jsonStr,
                                                     int startHour, int startMin,
                                                     int endHour, int endMin) {
        List<TrainSchedule> schedules = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject delivery = root
                    .getJSONObject("Siri")
                    .getJSONObject("ServiceDelivery")
                    .getJSONArray("StopMonitoringDelivery")
                    .getJSONObject(0);

            if (!delivery.has("MonitoredStopVisit")) {
                return schedules;
            }

            JSONArray visits = delivery.getJSONArray("MonitoredStopVisit");
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            isoFormat.setTimeZone(TimeZone.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

            for (int i = 0; i < visits.length(); i++) {
                JSONObject visit = visits.getJSONObject(i);
                JSONObject journey = visit.getJSONObject("MonitoredVehicleJourney");
                JSONObject call = journey.getJSONObject("MonitoredCall");

                // Extract aimed departure time
                String aimedStr = call.optString("AimedDepartureTime", "");
                String expectedStr = call.optString("ExpectedDepartureTime", "");

                if (aimedStr.isEmpty()) continue;

                // Parse aimed time
                String aimedClean = cleanIsoTime(aimedStr);
                Date aimedDate = isoFormat.parse(aimedClean);
                if (aimedDate == null) continue;

                // Filter by time range
                Calendar cal = Calendar.getInstance();
                cal.setTime(aimedDate);
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                int minute = cal.get(Calendar.MINUTE);
                int timeInMinutes = hour * 60 + minute;
                int startInMinutes = startHour * 60 + startMin;
                int endInMinutes = endHour * 60 + endMin;

                if (timeInMinutes < startInMinutes || timeInMinutes > endInMinutes) {
                    continue;
                }

                // Calculate delay
                int delayMinutes = 0;
                String expectedTimeStr = "";
                if (!expectedStr.isEmpty()) {
                    String expectedClean = cleanIsoTime(expectedStr);
                    Date expectedDate = isoFormat.parse(expectedClean);
                    if (expectedDate != null) {
                        long diffMs = expectedDate.getTime() - aimedDate.getTime();
                        delayMinutes = (int) (diffMs / 60000);
                        if (delayMinutes < 0) delayMinutes = 0;
                        expectedTimeStr = timeFormat.format(expectedDate);
                    }
                }

                // Extract destination
                String destination = "";
                JSONArray destNames = journey.optJSONArray("DestinationName");
                if (destNames != null && destNames.length() > 0) {
                    destination = destNames.getJSONObject(0).optString("value", "");
                } else {
                    JSONObject destName = journey.optJSONObject("DestinationName");
                    if (destName != null) {
                        destination = destName.optString("value", "");
                    }
                }

                // Extract departure status
                String departureStatus = call.optString("DepartureStatus", "onTime");

                // Extract platform
                String platform = "";
                JSONObject platformObj = call.optJSONObject("ArrivalPlatformName");
                if (platformObj != null) {
                    platform = platformObj.optString("value", "");
                } else {
                    JSONObject depPlatformObj = call.optJSONObject("DeparturePlatformName");
                    if (depPlatformObj != null) {
                        platform = depPlatformObj.optString("value", "");
                    }
                }

                String aimedTimeStr = timeFormat.format(aimedDate);

                schedules.add(new TrainSchedule(
                        destination,
                        aimedTimeStr,
                        expectedTimeStr,
                        departureStatus,
                        platform,
                        delayMinutes
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schedules;
    }

    private static String cleanIsoTime(String isoDateTime) {
        if (isoDateTime == null) return "";
        // Remove timezone offset for parsing (e.g., +01:00 or Z)
        String clean = isoDateTime;
        if (clean.contains("+")) {
            clean = clean.substring(0, clean.indexOf('+'));
        } else if (clean.endsWith("Z")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        // Truncate to seconds
        if (clean.contains(".")) {
            clean = clean.substring(0, clean.indexOf('.'));
        }
        return clean;
    }

    // ==================== INCIDENTS ====================

    private void fetchIncidents() {
        BrokerConfig config = new BrokerConfig(requireContext());
        if (!config.hasIdfmToken()) {
            showMessage(emptyText, recyclerView, "Token IDFM non configuré.\nAllez dans Paramètres pour l'ajouter.");
            return;
        }

        String token = config.getIdfmToken();
        executor.execute(() -> {
            List<TrainIncident> incidents = fetchIncidentsFromApi(token);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    String updateTime = "MAJ " + sdf.format(new Date());
                    lastUpdateText.setText(updateTime);

                    if (incidents == null) {
                        showMessage(emptyText, recyclerView, "Erreur de chargement.\nVérifiez votre token IDFM.");
                    } else if (incidents.isEmpty()) {
                        showMessage(emptyText, recyclerView, "Aucune perturbation en cours\nsur la Ligne N");
                    } else {
                        emptyText.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.updateIncidents(incidents);
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

    private List<TrainIncident> fetchIncidentsFromApi(String token) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(GENERAL_MESSAGE_URL);
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

                return parseIncidentResponse(response.toString());
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

    private List<TrainIncident> parseIncidentResponse(String jsonStr) {
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

                // Extract severity from InfoChannelRef
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
