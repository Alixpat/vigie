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
import java.net.URLEncoder;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrainFragment extends Fragment {

    private static final String TAG = "TrainFragment";
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private static final long SCHEDULE_WINDOW_MS = 2 * 60 * 60 * 1000; // 2 hours window
    // Ligne N Transilien
    private static final String LINE_REF = "STIF:Line::C01736:";
    private static final String GENERAL_MESSAGE_URL =
            "https://prim.iledefrance-mobilites.fr/marketplace/general-message"
                    + "?LineRef=" + LINE_REF;

    // Stop monitoring API for schedules
    private static final String STOP_MONITORING_URL =
            "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring";
    // Clamart station MonitoringRef (Ligne N) — StopArea format required since March 2025
    private static final String CLAMART_STOP_REF = "STIF:StopArea:SP:43111:";
    // Villepreux-Les Clayes station MonitoringRef (Ligne N) — StopArea format required since March 2025
    private static final String VILLEPREUX_STOP_REF = "STIF:StopArea:SP:43221:";

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

    // Incidents - Aller: Clamart → Villepreux
    private RecyclerView recyclerView;
    private TextView emptyText;
    private TextView lastUpdateText;
    private TrainIncidentAdapter adapter;

    // Incidents - Retour: Villepreux → Clamart
    private RecyclerView recyclerViewRetour;
    private TextView emptyTextRetour;
    private TextView lastUpdateTextRetour;
    private TrainIncidentAdapter adapterRetour;

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

        // Incidents - Aller
        recyclerView = view.findViewById(R.id.trainRecyclerView);
        emptyText = view.findViewById(R.id.trainEmptyText);
        lastUpdateText = view.findViewById(R.id.trainLastUpdate);
        adapter = new TrainIncidentAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // Incidents - Retour
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
            Log.w(TAG, "fetchSchedules: Token IDFM non configuré");
            showMessage(scheduleEmptyAller, scheduleRecyclerViewAller,
                    "Token IDFM non configuré.");
            showMessage(scheduleEmptyRetour, scheduleRecyclerViewRetour,
                    "Token IDFM non configuré.");
            return;
        }

        String token = config.getIdfmToken();
        Date now = new Date();
        Date windowEnd = new Date(now.getTime() + SCHEDULE_WINDOW_MS);
        SimpleDateFormat logFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        Log.i(TAG, "fetchSchedules: fenêtre horaire = " + logFmt.format(now) + " → " + logFmt.format(windowEnd));

        executor.execute(() -> {
            Log.d(TAG, "fetchSchedules: requête Aller (Clamart → Villepreux), stop=" + CLAMART_STOP_REF);
            List<TrainSchedule> allerSchedules = fetchStopMonitoring(
                    token, CLAMART_STOP_REF, now, windowEnd, "Aller");

            Log.d(TAG, "fetchSchedules: requête Retour (Villepreux → Clamart), stop=" + VILLEPREUX_STOP_REF);
            List<TrainSchedule> retourSchedules = fetchStopMonitoring(
                    token, VILLEPREUX_STOP_REF, now, windowEnd, "Retour");

            Log.i(TAG, "fetchSchedules: résultats Aller=" + (allerSchedules != null ? allerSchedules.size() : "null")
                    + ", Retour=" + (retourSchedules != null ? retourSchedules.size() : "null"));

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    String updateTime = "MAJ " + sdf.format(new Date());
                    scheduleLastUpdateAller.setText(updateTime);
                    scheduleLastUpdateRetour.setText(updateTime);

                    updateScheduleUI(allerSchedules, scheduleEmptyAller,
                            scheduleRecyclerViewAller, scheduleAdapterAller, "Clamart → Villepreux");
                    updateScheduleUI(retourSchedules, scheduleEmptyRetour,
                            scheduleRecyclerViewRetour, scheduleAdapterRetour, "Villepreux → Clamart");
                });
            }
        });
    }

    private void updateScheduleUI(List<TrainSchedule> schedules, TextView emptyView,
                                  RecyclerView recycler, TrainScheduleAdapter scheduleAdapter,
                                  String direction) {
        if (schedules == null) {
            Log.w(TAG, "updateScheduleUI [" + direction + "]: schedules est null → erreur de chargement");
            showMessage(emptyView, recycler, "Erreur de chargement des horaires.");
        } else if (schedules.isEmpty()) {
            Log.w(TAG, "updateScheduleUI [" + direction + "]: liste vide → aucun train dans la fenêtre");
            showMessage(emptyView, recycler, "Aucun train prévu\ndans les 2 prochaines heures.");
        } else {
            Log.i(TAG, "updateScheduleUI [" + direction + "]: affichage de " + schedules.size() + " trains");
            for (int i = 0; i < schedules.size(); i++) {
                TrainSchedule s = schedules.get(i);
                Log.d(TAG, "  train #" + i + ": " + s.getAimedDepartureTime()
                        + " → " + s.getDestination()
                        + " | status=" + s.getDepartureStatus()
                        + " | retard=" + s.getDelayMinutes() + "min"
                        + " | voie=" + s.getPlatformName());
            }
            emptyView.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            scheduleAdapter.updateSchedules(schedules);
        }
    }

    private List<TrainSchedule> fetchStopMonitoring(String token, String stopRef,
                                                     Date windowStart, Date windowEnd,
                                                     String directionLabel) {
        HttpURLConnection connection = null;
        try {
            String apiUrl = STOP_MONITORING_URL
                    + "?MonitoringRef=" + URLEncoder.encode(stopRef, "UTF-8")
                    + "&LineRef=" + URLEncoder.encode(LINE_REF, "UTF-8");

            Log.d(TAG, "fetchStopMonitoring [" + directionLabel + "]: URL=" + apiUrl);

            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", token);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "fetchStopMonitoring [" + directionLabel + "]: HTTP " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                Log.d(TAG, "fetchStopMonitoring [" + directionLabel + "]: réponse reçue, taille=" + response.length() + " chars");

                return parseStopMonitoring(response.toString(), windowStart, windowEnd, directionLabel);
            } else {
                String errorBody = "";
                try {
                    BufferedReader errReader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errResponse = new StringBuilder();
                    String errLine;
                    while ((errLine = errReader.readLine()) != null) {
                        errResponse.append(errLine);
                    }
                    errReader.close();
                    errorBody = errResponse.toString();
                } catch (Exception ignored) {}
                Log.e(TAG, "fetchStopMonitoring [" + directionLabel + "]: ERREUR HTTP " + responseCode + " pour stop " + stopRef + " body=" + errorBody);
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchStopMonitoring [" + directionLabel + "]: exception pour stop " + stopRef, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private List<TrainSchedule> parseStopMonitoring(String jsonStr,
                                                     Date windowStart, Date windowEnd,
                                                     String directionLabel) {
        List<TrainSchedule> schedules = new ArrayList<>();
        SimpleDateFormat logFmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject delivery = root
                    .getJSONObject("Siri")
                    .getJSONObject("ServiceDelivery")
                    .getJSONArray("StopMonitoringDelivery")
                    .getJSONObject(0);

            if (!delivery.has("MonitoredStopVisit")) {
                Log.w(TAG, "parseStopMonitoring [" + directionLabel + "]: pas de MonitoredStopVisit dans la réponse");
                return schedules;
            }

            JSONArray visits = delivery.getJSONArray("MonitoredStopVisit");
            Log.i(TAG, "parseStopMonitoring [" + directionLabel + "]: " + visits.length() + " visites dans la réponse API");

            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
            SimpleDateFormat isoFormatNoTz = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            isoFormatNoTz.setTimeZone(TimeZone.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

            int filteredOut = 0;
            int noAimedTime = 0;
            int parseErrors = 0;

            for (int i = 0; i < visits.length(); i++) {
                JSONObject visit = visits.getJSONObject(i);
                JSONObject journey = visit.getJSONObject("MonitoredVehicleJourney");
                JSONObject call = journey.getJSONObject("MonitoredCall");

                // Extract aimed departure time
                String aimedStr = call.optString("AimedDepartureTime", "");
                String expectedStr = call.optString("ExpectedDepartureTime", "");

                if (aimedStr.isEmpty()) {
                    noAimedTime++;
                    Log.d(TAG, "parseStopMonitoring [" + directionLabel + "]: visite #" + i + " ignorée (pas de AimedDepartureTime)");
                    continue;
                }

                // Parse aimed time (with timezone support)
                Date aimedDate = parseIsoDateTime(aimedStr, isoFormat, isoFormatNoTz);
                if (aimedDate == null) {
                    parseErrors++;
                    Log.e(TAG, "parseStopMonitoring [" + directionLabel + "]: visite #" + i + " impossible de parser aimedStr=" + aimedStr);
                    continue;
                }

                // Filter: keep only trains between now and now+2h
                if (aimedDate.before(windowStart) || aimedDate.after(windowEnd)) {
                    filteredOut++;
                    Log.d(TAG, "parseStopMonitoring [" + directionLabel + "]: visite #" + i
                            + " hors fenêtre: aimed=" + logFmt.format(aimedDate)
                            + " fenêtre=[" + logFmt.format(windowStart) + " - " + logFmt.format(windowEnd) + "]");
                    continue;
                }

                // Calculate delay
                int delayMinutes = 0;
                String expectedTimeStr = "";
                if (!expectedStr.isEmpty()) {
                    Date expectedDate = parseIsoDateTime(expectedStr, isoFormat, isoFormatNoTz);
                    if (expectedDate != null) {
                        long diffMs = expectedDate.getTime() - aimedDate.getTime();
                        delayMinutes = (int) (diffMs / 60000);
                        if (delayMinutes < 0) delayMinutes = 0;
                        expectedTimeStr = timeFormat.format(expectedDate);
                    } else {
                        Log.w(TAG, "parseStopMonitoring [" + directionLabel + "]: visite #" + i
                                + " impossible de parser expectedStr=" + expectedStr);
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

                Log.d(TAG, "parseStopMonitoring [" + directionLabel + "]: GARDÉ visite #" + i
                        + " aimed=" + aimedTimeStr
                        + " dest=" + destination
                        + " status=" + departureStatus
                        + " delay=" + delayMinutes + "min"
                        + " voie=" + platform);

                schedules.add(new TrainSchedule(
                        destination,
                        aimedTimeStr,
                        expectedTimeStr,
                        departureStatus,
                        platform,
                        delayMinutes
                ));
            }

            Log.i(TAG, "parseStopMonitoring [" + directionLabel + "]: RÉSUMÉ"
                    + " total=" + visits.length()
                    + " gardés=" + schedules.size()
                    + " filtrés(hors fenêtre)=" + filteredOut
                    + " sansAimed=" + noAimedTime
                    + " erreursParse=" + parseErrors);

        } catch (Exception e) {
            Log.e(TAG, "parseStopMonitoring [" + directionLabel + "]: exception parsing JSON", e);
        }
        return schedules;
    }

    /**
     * Parse an ISO 8601 datetime string, trying first with timezone then without.
     */
    private static Date parseIsoDateTime(String isoStr, SimpleDateFormat withTz, SimpleDateFormat withoutTz) {
        if (isoStr == null || isoStr.isEmpty()) return null;

        // Try parsing with timezone info first
        try {
            // Handle fractional seconds by removing them before parsing
            String cleaned = isoStr;
            int dotIdx = cleaned.indexOf('.');
            if (dotIdx > 0) {
                // Find end of fractional part (before + or Z)
                int tzStart = cleaned.indexOf('+', dotIdx);
                if (tzStart < 0) tzStart = cleaned.indexOf('Z', dotIdx);
                if (tzStart < 0) tzStart = cleaned.indexOf('-', dotIdx + 1);
                if (tzStart > 0) {
                    cleaned = cleaned.substring(0, dotIdx) + cleaned.substring(tzStart);
                } else {
                    cleaned = cleaned.substring(0, dotIdx);
                }
            }

            // Try with timezone
            if (cleaned.contains("+") || cleaned.contains("Z") || cleaned.matches(".*-\\d{2}:\\d{2}$")) {
                Date d = withTz.parse(cleaned);
                if (d != null) return d;
            }

            // Fallback: strip timezone and parse as local
            String noTz = cleaned;
            if (noTz.contains("+")) {
                noTz = noTz.substring(0, noTz.lastIndexOf('+'));
            } else if (noTz.endsWith("Z")) {
                noTz = noTz.substring(0, noTz.length() - 1);
            }
            // Remove trailing timezone like -01:00
            if (noTz.matches(".*-\\d{2}:\\d{2}$")) {
                noTz = noTz.substring(0, noTz.length() - 6);
            }
            return withoutTz.parse(noTz);
        } catch (Exception e) {
            Log.e("TrainFragment", "parseIsoDateTime: impossible de parser '" + isoStr + "'", e);
            return null;
        }
    }

    // ==================== INCIDENTS ====================

    private void fetchIncidents() {
        BrokerConfig config = new BrokerConfig(requireContext());
        if (!config.hasIdfmToken()) {
            showMessage(emptyText, recyclerView, "Token IDFM non configuré.\nAllez dans Paramètres pour l'ajouter.");
            showMessage(emptyTextRetour, recyclerViewRetour, "Token IDFM non configuré.\nAllez dans Paramètres pour l'ajouter.");
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
