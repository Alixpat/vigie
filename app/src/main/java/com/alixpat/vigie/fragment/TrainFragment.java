package com.alixpat.vigie.fragment;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // Known destinations towards Villepreux direction (away from Paris)
    // Note: Rambouillet is NOT included — trains to Rambouillet take a different branch
    // via La Verrière after Saint-Cyr and do NOT pass through Villepreux
    private static final String[] DESTINATIONS_VERS_VILLEPREUX = {
            "villepreux", "plaisir", "dreux", "mantes"
    };
    // Trains from Villepreux towards Clamart/Paris
    private static final String[] DESTINATIONS_VERS_PARIS = {
            "paris", "montparnasse", "clamart", "meudon", "chaville",
            "viroflay", "versailles", "sèvres", "sevres"
    };

    // Keywords to detect planned works in message content
    private static final String[] TRAVAUX_KEYWORDS = {
            "travaux", "chantier", "maintenance", "planifi",
            "programme", "prévu"
    };

    // Line status banner
    private LinearLayout lineStatusBanner;
    private TextView lineStatusEmoji;
    private TextView lineStatusTitle;
    private TextView lineStatusUpdate;
    private TextView lineStatusSummary;

    // Perturbations section
    private LinearLayout perturbationsSection;
    private RecyclerView perturbationsRecyclerView;
    private TrainIncidentAdapter perturbationsAdapter;

    // Travaux / planned info section
    private LinearLayout travauxSection;
    private RecyclerView travauxRecyclerView;
    private TrainIncidentAdapter travauxAdapter;

    // Schedules - Aller: Clamart → Villepreux
    private RecyclerView scheduleRecyclerViewAller;
    private TextView scheduleEmptyAller;
    private TextView scheduleLastUpdateAller;
    private TextView scheduleTitleAller;
    private TrainScheduleAdapter scheduleAdapterAller;

    // Schedules - Retour: Villepreux → Clamart
    private RecyclerView scheduleRecyclerViewRetour;
    private TextView scheduleEmptyRetour;
    private TextView scheduleLastUpdateRetour;
    private TextView scheduleTitleRetour;
    private TrainScheduleAdapter scheduleAdapterRetour;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchAll();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    /**
     * Intermediate data from parsing a single stop visit, before cross-referencing.
     */
    private static class RawStopVisit {
        String journeyRef;
        String destination;
        Date aimedDeparture;
        Date expectedDeparture;
        Date aimedArrival;
        String departureStatus;
        String platform;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_train, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Line status banner
        lineStatusBanner = view.findViewById(R.id.lineStatusBanner);
        lineStatusEmoji = view.findViewById(R.id.lineStatusEmoji);
        lineStatusTitle = view.findViewById(R.id.lineStatusTitle);
        lineStatusUpdate = view.findViewById(R.id.lineStatusUpdate);
        lineStatusSummary = view.findViewById(R.id.lineStatusSummary);

        // Perturbations
        perturbationsSection = view.findViewById(R.id.perturbationsSection);
        perturbationsRecyclerView = view.findViewById(R.id.trainRecyclerView);
        perturbationsAdapter = new TrainIncidentAdapter();
        perturbationsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        perturbationsRecyclerView.setAdapter(perturbationsAdapter);

        // Travaux / planned info
        travauxSection = view.findViewById(R.id.travauxSection);
        travauxRecyclerView = view.findViewById(R.id.travauxRecyclerView);
        travauxAdapter = new TrainIncidentAdapter();
        travauxRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        travauxRecyclerView.setAdapter(travauxAdapter);

        // Schedules - Aller
        scheduleRecyclerViewAller = view.findViewById(R.id.scheduleRecyclerViewAller);
        scheduleEmptyAller = view.findViewById(R.id.scheduleEmptyAller);
        scheduleLastUpdateAller = view.findViewById(R.id.scheduleLastUpdateAller);
        scheduleTitleAller = view.findViewById(R.id.scheduleTitleAller);
        scheduleAdapterAller = new TrainScheduleAdapter();
        scheduleRecyclerViewAller.setLayoutManager(new LinearLayoutManager(requireContext()));
        scheduleRecyclerViewAller.setAdapter(scheduleAdapterAller);

        // Schedules - Retour
        scheduleRecyclerViewRetour = view.findViewById(R.id.scheduleRecyclerViewRetour);
        scheduleEmptyRetour = view.findViewById(R.id.scheduleEmptyRetour);
        scheduleLastUpdateRetour = view.findViewById(R.id.scheduleLastUpdateRetour);
        scheduleTitleRetour = view.findViewById(R.id.scheduleTitleRetour);
        scheduleAdapterRetour = new TrainScheduleAdapter();
        scheduleRecyclerViewRetour.setLayoutManager(new LinearLayoutManager(requireContext()));
        scheduleRecyclerViewRetour.setAdapter(scheduleAdapterRetour);
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

    // ==================== LINE STATUS BANNER ====================

    private void updateLineStatusBanner(List<TrainIncident> perturbations,
                                        List<TrainIncident> travaux) {
        if (!isAdded() || getActivity() == null) return;

        int bannerColor;
        String emoji;
        String summary;

        boolean hasPerturbations = perturbations != null && !perturbations.isEmpty();
        boolean hasTravaux = travaux != null && !travaux.isEmpty();

        if (hasPerturbations) {
            // Check for blocking perturbations
            boolean hasBlocking = false;
            for (TrainIncident p : perturbations) {
                if ("blocking".equalsIgnoreCase(p.getSeverity())) {
                    hasBlocking = true;
                    break;
                }
            }
            if (hasBlocking) {
                bannerColor = 0xFFF44336; // Red
                emoji = "\uD83D\uDED1"; // stop sign
                summary = perturbations.size() + " perturbation"
                        + (perturbations.size() > 1 ? "s" : "") + " en cours";
            } else {
                bannerColor = 0xFFFF9800; // Orange
                emoji = "\u26A0\uFE0F"; // warning
                summary = perturbations.size() + " perturbation"
                        + (perturbations.size() > 1 ? "s" : "") + " en cours";
            }
            if (hasTravaux) {
                summary += " \u2022 " + travaux.size() + " info"
                        + (travaux.size() > 1 ? "s" : "") + " planifi\u00e9e"
                        + (travaux.size() > 1 ? "s" : "");
            }
        } else if (hasTravaux) {
            bannerColor = 0xFF2196F3; // Blue
            emoji = "\uD83D\uDEA7"; // construction
            summary = travaux.size() + " info"
                    + (travaux.size() > 1 ? "s" : "") + " planifi\u00e9e"
                    + (travaux.size() > 1 ? "s" : "");
        } else {
            bannerColor = 0xFF4CAF50; // Green
            emoji = "\u2705"; // check mark
            summary = "Trafic normal";
        }

        // Update banner background color
        GradientDrawable bg = (GradientDrawable) lineStatusBanner.getBackground().mutate();
        bg.setColor(bannerColor);
        lineStatusBanner.setBackground(bg);

        lineStatusEmoji.setText(emoji);
        lineStatusTitle.setText("Ligne N");
        lineStatusSummary.setText(summary);

        // Show/hide perturbation cards
        if (hasPerturbations) {
            perturbationsSection.setVisibility(View.VISIBLE);
            perturbationsAdapter.updateIncidents(perturbations);
        } else {
            perturbationsSection.setVisibility(View.GONE);
        }

        // Show/hide travaux cards
        if (hasTravaux) {
            travauxSection.setVisibility(View.VISIBLE);
            travauxAdapter.updateIncidents(travaux);
        } else {
            travauxSection.setVisibility(View.GONE);
        }
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
        SimpleDateFormat titleFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String windowStr = titleFmt.format(now) + " - " + titleFmt.format(windowEnd);
        Log.i(TAG, "fetchSchedules: fenêtre horaire = " + logFmt.format(now) + " → " + logFmt.format(windowEnd));

        // Update titles with dynamic time window on UI thread
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                scheduleTitleAller.setText("Clamart \u2192 Villepreux (" + windowStr + ")");
                scheduleTitleRetour.setText("Villepreux \u2192 Clamart (" + windowStr + ")");
            });
        }

        executor.execute(() -> {
            // Fetch raw data from both stations
            Log.d(TAG, "fetchSchedules: requête Clamart, stop=" + CLAMART_STOP_REF);
            Map<String, RawStopVisit> clamartData = fetchAndParseRaw(token, CLAMART_STOP_REF, "Clamart");

            Log.d(TAG, "fetchSchedules: requête Villepreux, stop=" + VILLEPREUX_STOP_REF);
            Map<String, RawStopVisit> villepreuxData = fetchAndParseRaw(token, VILLEPREUX_STOP_REF, "Villepreux");

            // Cross-reference to build schedules
            List<TrainSchedule> allerSchedules = buildCrossReferencedSchedules(
                    clamartData, villepreuxData,
                    DESTINATIONS_VERS_VILLEPREUX, now, windowEnd, "Aller");

            List<TrainSchedule> retourSchedules = buildCrossReferencedSchedules(
                    villepreuxData, clamartData,
                    DESTINATIONS_VERS_PARIS, now, windowEnd, "Retour");

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

    /**
     * Fetch stop-monitoring data and parse into raw visits keyed by journey reference.
     */
    private Map<String, RawStopVisit> fetchAndParseRaw(String token, String stopRef, String stationLabel) {
        HttpURLConnection connection = null;
        try {
            String apiUrl = STOP_MONITORING_URL
                    + "?MonitoringRef=" + URLEncoder.encode(stopRef, "UTF-8")
                    + "&LineRef=" + URLEncoder.encode(LINE_REF, "UTF-8");

            Log.d(TAG, "fetchAndParseRaw [" + stationLabel + "]: URL=" + apiUrl);

            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", token);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "fetchAndParseRaw [" + stationLabel + "]: HTTP " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                Log.d(TAG, "fetchAndParseRaw [" + stationLabel + "]: réponse reçue, taille=" + response.length() + " chars");
                return parseRawStopVisits(response.toString(), stationLabel);
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
                Log.e(TAG, "fetchAndParseRaw [" + stationLabel + "]: ERREUR HTTP " + responseCode + " body=" + errorBody);
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchAndParseRaw [" + stationLabel + "]: exception", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    /**
     * Parse stop-monitoring JSON response into a map of journey reference → raw stop visit data.
     */
    private Map<String, RawStopVisit> parseRawStopVisits(String jsonStr, String stationLabel) {
        Map<String, RawStopVisit> visits = new HashMap<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject delivery = root
                    .getJSONObject("Siri")
                    .getJSONObject("ServiceDelivery")
                    .getJSONArray("StopMonitoringDelivery")
                    .getJSONObject(0);

            if (!delivery.has("MonitoredStopVisit")) {
                Log.w(TAG, "parseRawStopVisits [" + stationLabel + "]: pas de MonitoredStopVisit");
                return visits;
            }

            JSONArray visitArray = delivery.getJSONArray("MonitoredStopVisit");
            Log.i(TAG, "parseRawStopVisits [" + stationLabel + "]: " + visitArray.length() + " visites");

            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
            SimpleDateFormat isoFormatNoTz = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            isoFormatNoTz.setTimeZone(TimeZone.getDefault());

            int noJourneyRef = 0;
            int parseErrors = 0;

            for (int i = 0; i < visitArray.length(); i++) {
                try {
                    JSONObject visit = visitArray.getJSONObject(i);
                    JSONObject journey = visit.getJSONObject("MonitoredVehicleJourney");
                    JSONObject call = journey.getJSONObject("MonitoredCall");

                    String journeyRef = "";
                    JSONObject framedRef = journey.optJSONObject("FramedVehicleJourneyRef");
                    if (framedRef != null) {
                        journeyRef = framedRef.optString("DatedVehicleJourneyRef", "");
                    }
                    if (journeyRef.isEmpty()) {
                        noJourneyRef++;
                        continue;
                    }

                    RawStopVisit raw = new RawStopVisit();
                    raw.journeyRef = journeyRef;

                    // Extract destination
                    JSONArray destNames = journey.optJSONArray("DestinationName");
                    if (destNames != null && destNames.length() > 0) {
                        raw.destination = destNames.getJSONObject(0).optString("value", "");
                    } else {
                        JSONObject destName = journey.optJSONObject("DestinationName");
                        if (destName != null) {
                            raw.destination = destName.optString("value", "");
                        } else {
                            raw.destination = "";
                        }
                    }

                    // Parse times
                    String aimedDepStr = call.optString("AimedDepartureTime", "");
                    String expectedDepStr = call.optString("ExpectedDepartureTime", "");
                    String aimedArrStr = call.optString("AimedArrivalTime", "");

                    if (!aimedDepStr.isEmpty()) {
                        raw.aimedDeparture = parseIsoDateTime(aimedDepStr, isoFormat, isoFormatNoTz);
                    }
                    if (!expectedDepStr.isEmpty()) {
                        raw.expectedDeparture = parseIsoDateTime(expectedDepStr, isoFormat, isoFormatNoTz);
                    }
                    if (!aimedArrStr.isEmpty()) {
                        raw.aimedArrival = parseIsoDateTime(aimedArrStr, isoFormat, isoFormatNoTz);
                    }

                    raw.departureStatus = call.optString("DepartureStatus", "onTime");

                    raw.platform = "";
                    JSONObject platformObj = call.optJSONObject("ArrivalPlatformName");
                    if (platformObj != null) {
                        raw.platform = platformObj.optString("value", "");
                    }
                    if (raw.platform.isEmpty()) {
                        JSONObject depPlatformObj = call.optJSONObject("DeparturePlatformName");
                        if (depPlatformObj != null) {
                            raw.platform = depPlatformObj.optString("value", "");
                        }
                    }

                    visits.put(journeyRef, raw);
                } catch (Exception e) {
                    parseErrors++;
                    Log.e(TAG, "parseRawStopVisits [" + stationLabel + "]: erreur visite #" + i, e);
                }
            }

            Log.i(TAG, "parseRawStopVisits [" + stationLabel + "]: RÉSUMÉ"
                    + " total=" + visitArray.length()
                    + " parsés=" + visits.size()
                    + " sansJourneyRef=" + noJourneyRef
                    + " erreurs=" + parseErrors);

        } catch (Exception e) {
            Log.e(TAG, "parseRawStopVisits [" + stationLabel + "]: exception JSON", e);
        }
        return visits;
    }

    /**
     * Build schedule list by cross-referencing origin and destination station data.
     * Only trains that stop at BOTH stations are kept.
     */
    private List<TrainSchedule> buildCrossReferencedSchedules(
            Map<String, RawStopVisit> originData,
            Map<String, RawStopVisit> destinationData,
            String[] destinationFilter,
            Date windowStart, Date windowEnd,
            String directionLabel) {

        List<TrainSchedule> schedules = new ArrayList<>();

        if (originData == null) {
            Log.e(TAG, "buildCrossReferenced [" + directionLabel + "]: originData est null");
            return null;
        }

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

        int filteredByDest = 0;
        int filteredByTime = 0;
        int filteredByNoStop = 0;
        int noAimedTime = 0;

        for (Map.Entry<String, RawStopVisit> entry : originData.entrySet()) {
            String journeyRef = entry.getKey();
            RawStopVisit origin = entry.getValue();

            // Filter by destination
            if (destinationFilter != null && destinationFilter.length > 0) {
                String destLower = origin.destination.toLowerCase(Locale.FRENCH);
                boolean matchesFilter = false;
                for (String keyword : destinationFilter) {
                    if (destLower.contains(keyword)) {
                        matchesFilter = true;
                        break;
                    }
                }
                if (!matchesFilter) {
                    filteredByDest++;
                    continue;
                }
            }

            if (origin.aimedDeparture == null) {
                noAimedTime++;
                continue;
            }

            if (origin.aimedDeparture.before(windowStart) || origin.aimedDeparture.after(windowEnd)) {
                filteredByTime++;
                continue;
            }

            // Cross-reference: only keep trains that also stop at the destination station
            String arrivalTimeStr = "";
            if (destinationData != null) {
                RawStopVisit destVisit = destinationData.get(journeyRef);
                if (destVisit == null) {
                    filteredByNoStop++;
                    continue;
                }
                if (destVisit.aimedArrival != null) {
                    arrivalTimeStr = timeFmt.format(destVisit.aimedArrival);
                } else if (destVisit.aimedDeparture != null) {
                    arrivalTimeStr = timeFmt.format(destVisit.aimedDeparture);
                }
            }

            // Calculate delay
            int delayMinutes = 0;
            String expectedTimeStr = "";
            if (origin.expectedDeparture != null) {
                long diffMs = origin.expectedDeparture.getTime() - origin.aimedDeparture.getTime();
                delayMinutes = (int) (diffMs / 60000);
                if (delayMinutes < 0) delayMinutes = 0;
                if (delayMinutes > 0) {
                    expectedTimeStr = timeFmt.format(origin.expectedDeparture);
                }
            }

            String aimedTimeStr = timeFmt.format(origin.aimedDeparture);

            Log.d(TAG, "buildCrossReferenced [" + directionLabel + "]: GARDÉ "
                    + journeyRef
                    + " départ=" + aimedTimeStr
                    + " arrivée=" + arrivalTimeStr
                    + " dest=" + origin.destination
                    + " status=" + origin.departureStatus
                    + " retard=" + delayMinutes + "min"
                    + " voie=" + origin.platform);

            schedules.add(new TrainSchedule(
                    origin.destination,
                    aimedTimeStr,
                    expectedTimeStr,
                    arrivalTimeStr,
                    origin.departureStatus,
                    origin.platform,
                    delayMinutes
            ));
        }

        // Sort by departure time
        Collections.sort(schedules, (a, b) ->
                a.getAimedDepartureTime().compareTo(b.getAimedDepartureTime()));

        Log.i(TAG, "buildCrossReferenced [" + directionLabel + "]: RÉSUMÉ"
                + " total_origine=" + originData.size()
                + " gardés=" + schedules.size()
                + " filtrés(destination)=" + filteredByDest
                + " filtrés(horsFenêtre)=" + filteredByTime
                + " filtrés(pasArrêt)=" + filteredByNoStop
                + " sansAimed=" + noAimedTime);

        return schedules;
    }

    private void updateScheduleUI(List<TrainSchedule> schedules, TextView emptyView,
                                  RecyclerView recycler, TrainScheduleAdapter scheduleAdapter,
                                  String direction) {
        if (schedules == null) {
            showMessage(emptyView, recycler, "Erreur de chargement des horaires.");
        } else if (schedules.isEmpty()) {
            showMessage(emptyView, recycler, "Aucun train prévu\ndans les 2 prochaines heures.");
        } else {
            emptyView.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            scheduleAdapter.updateSchedules(schedules);
        }
    }

    /**
     * Parse an ISO 8601 datetime string, trying first with timezone then without.
     */
    private static Date parseIsoDateTime(String isoStr, SimpleDateFormat withTz, SimpleDateFormat withoutTz) {
        if (isoStr == null || isoStr.isEmpty()) return null;

        try {
            String cleaned = isoStr;
            int dotIdx = cleaned.indexOf('.');
            if (dotIdx > 0) {
                int tzStart = cleaned.indexOf('+', dotIdx);
                if (tzStart < 0) tzStart = cleaned.indexOf('Z', dotIdx);
                if (tzStart < 0) tzStart = cleaned.indexOf('-', dotIdx + 1);
                if (tzStart > 0) {
                    cleaned = cleaned.substring(0, dotIdx) + cleaned.substring(tzStart);
                } else {
                    cleaned = cleaned.substring(0, dotIdx);
                }
            }

            if (cleaned.contains("+") || cleaned.contains("Z") || cleaned.matches(".*-\\d{2}:\\d{2}$")) {
                Date d = withTz.parse(cleaned);
                if (d != null) return d;
            }

            String noTz = cleaned;
            if (noTz.contains("+")) {
                noTz = noTz.substring(0, noTz.lastIndexOf('+'));
            } else if (noTz.endsWith("Z")) {
                noTz = noTz.substring(0, noTz.length() - 1);
            }
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
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    lineStatusSummary.setText("Token IDFM non configuré");
                });
            }
            return;
        }

        String token = config.getIdfmToken();
        executor.execute(() -> {
            List<TrainIncident> allIncidents = fetchIncidentsFromApi(token);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    lineStatusUpdate.setText("MAJ " + sdf.format(new Date()));

                    if (allIncidents == null) {
                        lineStatusSummary.setText("Erreur de chargement");
                        perturbationsSection.setVisibility(View.GONE);
                        travauxSection.setVisibility(View.GONE);
                        // Set banner to orange for error
                        GradientDrawable bg = (GradientDrawable) lineStatusBanner.getBackground().mutate();
                        bg.setColor(0xFFFF9800);
                        lineStatusBanner.setBackground(bg);
                        lineStatusEmoji.setText("\u26A0\uFE0F");
                    } else {
                        // Split into perturbations vs travaux/info
                        List<TrainIncident> perturbations = new ArrayList<>();
                        List<TrainIncident> travaux = new ArrayList<>();

                        for (TrainIncident incident : allIncidents) {
                            if (incident.isPerturbation()) {
                                perturbations.add(incident);
                            } else {
                                travaux.add(incident);
                            }
                        }

                        updateLineStatusBanner(perturbations, travaux);
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

                // Determine channel type
                String channel = "";
                JSONObject channelRef = msg.optJSONObject("InfoChannelRef");
                if (channelRef != null) {
                    channel = channelRef.optString("value", "");
                } else {
                    channel = msg.optString("InfoChannelRef", "");
                }

                // Determine severity and type
                String severity;
                String type;
                boolean isPerturbationChannel = channel.toLowerCase(Locale.FRENCH).contains("perturbation");

                if (isPerturbationChannel) {
                    severity = "blocking";
                    type = TrainIncident.TYPE_PERTURBATION;
                } else {
                    severity = "information";
                    // Check if it's about planned works
                    String textLower = text.toLowerCase(Locale.FRENCH);
                    boolean isTravauxMessage = false;
                    for (String keyword : TRAVAUX_KEYWORDS) {
                        if (textLower.contains(keyword)) {
                            isTravauxMessage = true;
                            break;
                        }
                    }
                    type = isTravauxMessage ? TrainIncident.TYPE_TRAVAUX : TrainIncident.TYPE_INFORMATION;
                }

                // Extract timestamps
                String recordedAt = formatDateTime(msg.optString("RecordedAtTime", ""));
                String validUntil = formatDateTime(msg.optString("ValidUntilTime", ""));

                // Build a meaningful title
                String title;
                if (TrainIncident.TYPE_PERTURBATION.equals(type)) {
                    title = "Perturbation Ligne N";
                } else if (TrainIncident.TYPE_TRAVAUX.equals(type)) {
                    title = "Travaux Ligne N";
                } else {
                    title = "Info Ligne N";
                }

                if (!text.isEmpty()) {
                    incidents.add(new TrainIncident(
                            title,
                            text,
                            severity,
                            "",
                            recordedAt,
                            validUntil,
                            type
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
