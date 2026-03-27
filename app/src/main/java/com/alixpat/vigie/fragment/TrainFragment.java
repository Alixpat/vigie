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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.BrokerConfig;
import com.alixpat.vigie.R;
import com.alixpat.vigie.adapter.TrainIncidentAdapter;
import com.alixpat.vigie.adapter.TrainScheduleAdapter;
import com.alixpat.vigie.model.TrainIncident;
import com.alixpat.vigie.model.TrainSchedule;
import com.alixpat.vigie.model.TrainStop;
import com.google.android.material.card.MaterialCardView;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.widget.LinearLayout;
import android.widget.ScrollView;

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
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000;
    private static final long SCHEDULE_WINDOW_MS = 2 * 60 * 60 * 1000;
    private static final String LINE_REF = "STIF:Line::C01736:";
    private static final String GENERAL_MESSAGE_URL =
            "https://prim.iledefrance-mobilites.fr/marketplace/general-message"
                    + "?LineRef=" + LINE_REF;
    private static final String STOP_MONITORING_URL =
            "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring";
    private static final String ESTIMATED_TIMETABLE_URL =
            "https://prim.iledefrance-mobilites.fr/marketplace/estimated-timetable"
                    + "?LineRef=" + LINE_REF;
    private static final String CLAMART_STOP_REF = "STIF:StopArea:SP:43111:";
    private static final String VILLEPREUX_STOP_REF = "STIF:StopArea:SP:43221:";

    private static final String[] DESTINATIONS_VERS_VILLEPREUX = {
            "villepreux", "plaisir", "dreux", "mantes"
    };
    private static final String[] DESTINATIONS_VERS_PARIS = {
            "paris", "montparnasse", "clamart", "meudon", "chaville",
            "viroflay", "versailles", "sèvres", "sevres"
    };
    private static final String[] TRAVAUX_KEYWORDS = {
            "travaux", "chantier", "maintenance", "planifi",
            "programme", "prévu"
    };

    private MaterialCardView lineStatusCard;
    private View lineStatusStripe;
    private TextView lineStatusEmoji;
    private TextView lineStatusTitle;
    private TextView lineStatusUpdate;
    private TextView lineStatusSummary;

    private View perturbationsSection;
    private RecyclerView perturbationsRecyclerView;
    private TrainIncidentAdapter perturbationsAdapter;

    private View travauxSection;
    private RecyclerView travauxRecyclerView;
    private TrainIncidentAdapter travauxAdapter;

    private RecyclerView scheduleRecyclerViewAller;
    private TextView scheduleEmptyAller;
    private TextView scheduleLastUpdateAller;
    private TextView scheduleTitleAller;
    private TrainScheduleAdapter scheduleAdapterAller;

    private RecyclerView scheduleRecyclerViewRetour;
    private TextView scheduleEmptyRetour;
    private TextView scheduleLastUpdateRetour;
    private TextView scheduleTitleRetour;
    private TrainScheduleAdapter scheduleAdapterRetour;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, List<TrainStop>> journeyStopsCache = new HashMap<>();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchAll();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

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

        lineStatusCard = view.findViewById(R.id.lineStatusCard);
        lineStatusStripe = view.findViewById(R.id.lineStatusStripe);
        lineStatusEmoji = view.findViewById(R.id.lineStatusEmoji);
        lineStatusTitle = view.findViewById(R.id.lineStatusTitle);
        lineStatusUpdate = view.findViewById(R.id.lineStatusUpdate);
        lineStatusSummary = view.findViewById(R.id.lineStatusSummary);

        perturbationsSection = view.findViewById(R.id.perturbationsSection);
        perturbationsRecyclerView = view.findViewById(R.id.trainRecyclerView);
        perturbationsAdapter = new TrainIncidentAdapter();
        perturbationsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        perturbationsRecyclerView.setAdapter(perturbationsAdapter);

        travauxSection = view.findViewById(R.id.travauxSection);
        travauxRecyclerView = view.findViewById(R.id.travauxRecyclerView);
        travauxAdapter = new TrainIncidentAdapter();
        travauxRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        travauxRecyclerView.setAdapter(travauxAdapter);

        scheduleRecyclerViewAller = view.findViewById(R.id.scheduleRecyclerViewAller);
        scheduleEmptyAller = view.findViewById(R.id.scheduleEmptyAller);
        scheduleLastUpdateAller = view.findViewById(R.id.scheduleLastUpdateAller);
        scheduleTitleAller = view.findViewById(R.id.scheduleTitleAller);
        scheduleAdapterAller = new TrainScheduleAdapter();
        scheduleRecyclerViewAller.setLayoutManager(new LinearLayoutManager(requireContext()));
        scheduleRecyclerViewAller.setAdapter(scheduleAdapterAller);

        scheduleRecyclerViewRetour = view.findViewById(R.id.scheduleRecyclerViewRetour);
        scheduleEmptyRetour = view.findViewById(R.id.scheduleEmptyRetour);
        scheduleLastUpdateRetour = view.findViewById(R.id.scheduleLastUpdateRetour);
        scheduleTitleRetour = view.findViewById(R.id.scheduleTitleRetour);
        scheduleAdapterRetour = new TrainScheduleAdapter();
        scheduleRecyclerViewRetour.setLayoutManager(new LinearLayoutManager(requireContext()));
        scheduleRecyclerViewRetour.setAdapter(scheduleAdapterRetour);

        scheduleAdapterAller.setOnTrainClickListener(this::showTrainDetailDialog);
        scheduleAdapterRetour.setOnTrainClickListener(this::showTrainDetailDialog);
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
        fetchEstimatedTimetable();
    }

    // ==================== TRAIN DETAIL DIALOG ====================

    private void showTrainDetailDialog(TrainSchedule schedule) {
        if (!isAdded() || getActivity() == null) return;

        String journeyRef = schedule.getJourneyRef();
        List<TrainStop> stops = journeyStopsCache.get(journeyRef);

        if (stops != null && !stops.isEmpty()) {
            showStopByStopDialog(schedule, stops);
        } else {
            // Pas de données d'arrêts, on tente un fetch à la demande
            Log.i(TAG, "showTrainDetailDialog: pas de stops en cache pour " + journeyRef
                    + ", cache contient " + journeyStopsCache.size() + " trajets, fetch en cours...");
            BrokerConfig config = new BrokerConfig(requireContext());
            if (config.hasIdfmToken()) {
                executor.execute(() -> {
                    // Vérifier le cache d'abord (un fetch précédent a pu le remplir pendant l'attente dans la queue)
                    List<TrainStop> alreadyCached = journeyStopsCache.get(journeyRef);
                    if (alreadyCached == null || alreadyCached.isEmpty()) {
                        fetchAndParseEstimatedTimetable(config.getIdfmToken());
                    }
                    List<TrainStop> freshStops = journeyStopsCache.get(journeyRef);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            if (freshStops != null && !freshStops.isEmpty()) {
                                showStopByStopDialog(schedule, freshStops);
                            } else {
                                Log.w(TAG, "showTrainDetailDialog: stops introuvables pour " + journeyRef
                                        + " après fetch, clés en cache: " + journeyStopsCache.keySet());
                                showFallbackDialog(schedule);
                            }
                        });
                    }
                });
            } else {
                showFallbackDialog(schedule);
            }
        }
    }

    private void showStopByStopDialog(TrainSchedule schedule, List<TrainStop> stops) {
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(16);
        container.setPadding(pad, pad, pad, pad);
        scrollView.addView(container);

        // Statut global du train
        TextView statusHeader = new TextView(requireContext());
        statusHeader.setText(schedule.getStatusEmoji() + " " + schedule.getStatusLabel());
        statusHeader.setTextColor(schedule.getStatusColor());
        statusHeader.setTextSize(14);
        statusHeader.setTypeface(null, Typeface.BOLD);
        statusHeader.setPadding(0, 0, 0, dpToPx(12));
        container.addView(statusHeader);

        SimpleDateFormat timeFmtHm = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat timeFmtSec = new SimpleDateFormat(":ss", Locale.getDefault());
        long now = System.currentTimeMillis();

        // Trouver l'arrêt actuel pour le marqueur
        int currentStopIndex = -1;
        int betweenAfterIndex = -1;
        for (int i = 0; i < stops.size(); i++) {
            TrainStop.StopStatus status = stops.get(i).getStatus();
            if (status == TrainStop.StopStatus.CURRENT) {
                currentStopIndex = i;
                break;
            }
        }
        // Si pas d'arrêt "CURRENT", trouver entre quels arrêts
        if (currentStopIndex == -1) {
            for (int i = 0; i < stops.size() - 1; i++) {
                if (stops.get(i).getStatus() == TrainStop.StopStatus.PASSED
                        && stops.get(i + 1).getStatus() == TrainStop.StopStatus.UPCOMING) {
                    betweenAfterIndex = i;
                    break;
                }
            }
        }

        for (int i = 0; i < stops.size(); i++) {
            TrainStop stop = stops.get(i);
            TrainStop.StopStatus status = stop.getStatus();

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dpToPx(2), 0, dpToPx(2));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // Indicateur visuel (cercle/trait)
            TextView indicator = new TextView(requireContext());
            indicator.setTextSize(16);
            if (i == currentStopIndex) {
                indicator.setText("\uD83D\uDD35"); // Cercle bleu = arrêt actuel
            } else if (i == betweenAfterIndex) {
                indicator.setText("\u25CF"); // Cercle plein = dernier arrêt passé avant "entre deux"
                indicator.setTextColor(0xFF9E9E9E);
            } else if (status == TrainStop.StopStatus.PASSED) {
                indicator.setText("\u2713"); // Check = passé
                indicator.setTextColor(0xFF4CAF50);
            } else {
                indicator.setText("\u25CB"); // Cercle vide = à venir
                indicator.setTextColor(0xFFBDBDBD);
            }
            indicator.setPadding(0, 0, dpToPx(10), 0);
            row.addView(indicator);

            // Horaire
            TextView timeView = new TextView(requireContext());
            long bestTime = stop.getBestArrivalMillis();
            if (bestTime > 0) {
                Date bestDate = new Date(bestTime);
                String main = timeFmtHm.format(bestDate);
                String sec = timeFmtSec.format(bestDate);
                SpannableString span = new SpannableString(main + sec);
                span.setSpan(new RelativeSizeSpan(0.7f), main.length(), main.length() + sec.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                timeView.setText(span);
            } else {
                timeView.setText("--:--");
            }
            timeView.setTextSize(13);
            timeView.setMinWidth(dpToPx(45));
            if (i == currentStopIndex) {
                timeView.setTypeface(null, Typeface.BOLD);
                timeView.setTextColor(0xFF1976D2);
            } else if (status == TrainStop.StopStatus.PASSED) {
                timeView.setTextColor(0xFF9E9E9E);
            } else {
                timeView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            }
            timeView.setPadding(0, 0, dpToPx(10), 0);
            row.addView(timeView);

            // Nom de l'arrêt
            TextView nameView = new TextView(requireContext());
            nameView.setText(stop.getStopName());
            nameView.setTextSize(13);
            if (i == currentStopIndex) {
                nameView.setTypeface(null, Typeface.BOLD);
                nameView.setTextColor(0xFF1976D2);
            } else if (status == TrainStop.StopStatus.PASSED) {
                nameView.setTextColor(0xFF9E9E9E);
            } else {
                nameView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            }
            row.addView(nameView);

            // Voie (si disponible et arrêt actuel)
            if (stop.getPlatformName() != null && !stop.getPlatformName().isEmpty()
                    && (i == currentStopIndex || status == TrainStop.StopStatus.UPCOMING)) {
                TextView platformView = new TextView(requireContext());
                platformView.setText("  v." + stop.getPlatformName());
                platformView.setTextSize(11);
                platformView.setTextColor(0xFF9E9E9E);
                row.addView(platformView);
            }

            container.addView(row);

            // Indicateur "entre deux arrêts"
            if (i == betweenAfterIndex) {
                LinearLayout betweenRow = new LinearLayout(requireContext());
                betweenRow.setOrientation(LinearLayout.HORIZONTAL);
                betweenRow.setPadding(0, dpToPx(1), 0, dpToPx(1));
                betweenRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

                TextView betweenIndicator = new TextView(requireContext());
                betweenIndicator.setText("\uD83D\uDE86"); // Train emoji
                betweenIndicator.setTextSize(14);
                betweenIndicator.setPadding(0, 0, dpToPx(10), 0);
                betweenRow.addView(betweenIndicator);

                TextView betweenText = new TextView(requireContext());
                betweenText.setText("En route...");
                betweenText.setTextSize(12);
                betweenText.setTypeface(null, Typeface.BOLD_ITALIC);
                betweenText.setTextColor(0xFF1976D2);
                betweenRow.addView(betweenText);

                container.addView(betweenRow);
            }
        }

        String title = schedule.getDestination();
        if (!schedule.getOriginStation().isEmpty()) {
            title = schedule.getOriginStation() + " \u2192 " + schedule.getDestination();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton("Fermer", null)
                .show();
    }

    private void showFallbackDialog(TrainSchedule schedule) {
        String message = schedule.estimatePosition()
                + "\n\nStatut : " + schedule.getStatusEmoji() + " " + schedule.getStatusLabel()
                + "\nDépart : " + schedule.getAimedDepartureTime();
        if (schedule.isDelayed() && !schedule.getExpectedDepartureTime().isEmpty()) {
            message += " (retardé \u2192 " + schedule.getExpectedDepartureTime() + ")";
        }
        if (schedule.getArrivalTime() != null && !schedule.getArrivalTime().isEmpty()) {
            message += "\nArrivée : " + schedule.getArrivalTime();
        }
        if (schedule.getPlatformName() != null && !schedule.getPlatformName().isEmpty()) {
            message += "\nVoie : " + schedule.getPlatformName();
        }
        message += "\n\nDétail des arrêts indisponible.";

        new AlertDialog.Builder(requireContext())
                .setTitle("Détails du train")
                .setMessage(message)
                .setPositiveButton("Fermer", null)
                .show();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Formate un timestamp avec les secondes en plus petit.
     * Ex: "MAJ 14:32:05" avec ":05" en taille réduite (0.7x).
     */
    private static SpannableString formatTimeWithSmallSeconds(String prefix, Date date) {
        SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat ss = new SimpleDateFormat(":ss", Locale.getDefault());
        String main = prefix + hhmm.format(date);
        String seconds = ss.format(date);
        SpannableString span = new SpannableString(main + seconds);
        span.setSpan(new RelativeSizeSpan(0.7f), main.length(), main.length() + seconds.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    // ==================== LINE STATUS BANNER ====================

    private void updateLineStatusBanner(List<TrainIncident> perturbations,
                                        List<TrainIncident> travaux) {
        if (!isAdded() || getActivity() == null) return;

        int stripeColorRes;
        String statusLabel;
        String summary;

        boolean hasPerturbations = perturbations != null && !perturbations.isEmpty();
        boolean hasTravaux = travaux != null && !travaux.isEmpty();

        if (hasPerturbations) {
            boolean hasBlocking = false;
            for (TrainIncident p : perturbations) {
                if ("blocking".equalsIgnoreCase(p.getSeverity())) {
                    hasBlocking = true;
                    break;
                }
            }
            if (hasBlocking) {
                stripeColorRes = R.color.status_error;
                statusLabel = "INTERROMPU";
                summary = perturbations.size() + " perturbation"
                        + (perturbations.size() > 1 ? "s" : "") + " en cours";
            } else {
                stripeColorRes = R.color.status_warning;
                statusLabel = "PERTURBÉ";
                summary = perturbations.size() + " perturbation"
                        + (perturbations.size() > 1 ? "s" : "") + " en cours";
            }
            if (hasTravaux) {
                summary += " \u2022 " + travaux.size() + " info"
                        + (travaux.size() > 1 ? "s" : "") + " planifi\u00e9e"
                        + (travaux.size() > 1 ? "s" : "");
            }
        } else if (hasTravaux) {
            stripeColorRes = R.color.status_info;
            statusLabel = "TRAVAUX";
            summary = travaux.size() + " info"
                    + (travaux.size() > 1 ? "s" : "") + " planifi\u00e9e"
                    + (travaux.size() > 1 ? "s" : "");
        } else {
            stripeColorRes = R.color.status_ok;
            statusLabel = "NORMAL";
            summary = "Trafic normal";
        }

        int stripeColor = ContextCompat.getColor(requireContext(), stripeColorRes);
        lineStatusStripe.setBackgroundColor(stripeColor);

        lineStatusEmoji.setText(statusLabel);
        lineStatusEmoji.setTextColor(stripeColor);
        lineStatusTitle.setText("Ligne N");
        lineStatusSummary.setText(summary);

        if (hasPerturbations) {
            perturbationsSection.setVisibility(View.VISIBLE);
            perturbationsAdapter.updateIncidents(perturbations);
        } else {
            perturbationsSection.setVisibility(View.GONE);
        }

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
        Log.i(TAG, "fetchSchedules: fenêtre horaire = " + logFmt.format(now) + " → " + logFmt.format(windowEnd));

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                scheduleTitleAller.setText("Clamart \u2192 Villepreux des 2 prochaines heures");
                scheduleTitleRetour.setText("Villepreux \u2192 Clamart des 2 prochaines heures");
            });
        }

        executor.execute(() -> {
            Log.d(TAG, "fetchSchedules: requête Clamart, stop=" + CLAMART_STOP_REF);
            Map<String, RawStopVisit> clamartData = fetchAndParseRaw(token, CLAMART_STOP_REF, "Clamart");

            Log.d(TAG, "fetchSchedules: requête Villepreux, stop=" + VILLEPREUX_STOP_REF);
            Map<String, RawStopVisit> villepreuxData = fetchAndParseRaw(token, VILLEPREUX_STOP_REF, "Villepreux");

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

                    SpannableString updateTime = formatTimeWithSmallSeconds("MAJ ", new Date());
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

    private Map<String, RawStopVisit> fetchAndParseRaw(String token, String stopRef, String stationLabel) {
        int maxRetries = 2;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpURLConnection connection = null;
            try {
                if (attempt > 0) {
                    Log.i(TAG, "fetchAndParseRaw [" + stationLabel + "]: tentative " + (attempt + 1));
                    Thread.sleep(1000L * attempt);
                }

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
                    if (responseCode >= 500 && attempt < maxRetries) continue;
                }
            } catch (Exception e) {
                Log.e(TAG, "fetchAndParseRaw [" + stationLabel + "]: exception (tentative " + (attempt + 1) + ")", e);
                if (attempt < maxRetries) continue;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            break;
        }
        return null;
    }

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
                        JSONObject jrObj = framedRef.optJSONObject("DatedVehicleJourneyRef");
                        if (jrObj != null) {
                            journeyRef = jrObj.optString("value", "");
                        } else {
                            journeyRef = framedRef.optString("DatedVehicleJourneyRef", "");
                        }
                    }
                    if (journeyRef.isEmpty()) {
                        noJourneyRef++;
                        continue;
                    }

                    RawStopVisit raw = new RawStopVisit();
                    raw.journeyRef = journeyRef;

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

    private List<TrainSchedule> buildCrossReferencedSchedules(
            Map<String, RawStopVisit> originData,
            Map<String, RawStopVisit> destinationData,
            String[] destinationFilter,
            Date windowStart, Date windowEnd,
            String directionLabel) {

        List<TrainSchedule> schedules = new ArrayList<>();

        if (originData == null || originData.isEmpty()) {
            Log.e(TAG, "buildCrossReferenced [" + directionLabel + "]: originData est null ou vide");
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

            String arrivalTimeStr = "";
            if (destinationData != null) {
                RawStopVisit destVisit = destinationData.get(journeyRef);
                if (destVisit != null) {
                    if (destVisit.aimedArrival != null) {
                        arrivalTimeStr = timeFmt.format(destVisit.aimedArrival);
                    } else if (destVisit.aimedDeparture != null) {
                        arrivalTimeStr = timeFmt.format(destVisit.aimedDeparture);
                    }
                } else {
                    filteredByNoStop++;
                    // Train gardé sans heure d'arrivée
                }
            }

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

            long arrivalMillis = 0;
            if (destinationData != null) {
                RawStopVisit destVisit = destinationData.get(journeyRef);
                if (destVisit != null) {
                    if (destVisit.aimedArrival != null) arrivalMillis = destVisit.aimedArrival.getTime();
                    else if (destVisit.aimedDeparture != null) arrivalMillis = destVisit.aimedDeparture.getTime();
                }
            }

            String originStation = "Aller".equals(directionLabel) ? "Clamart" : "Villepreux";

            schedules.add(new TrainSchedule(
                    origin.destination,
                    aimedTimeStr,
                    expectedTimeStr,
                    arrivalTimeStr,
                    origin.departureStatus,
                    origin.platform,
                    delayMinutes,
                    journeyRef,
                    origin.aimedDeparture.getTime(),
                    arrivalMillis,
                    originStation
            ));
        }

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

    // ==================== ESTIMATED TIMETABLE (tous les arrêts) ====================

    private void fetchEstimatedTimetable() {
        BrokerConfig config = new BrokerConfig(requireContext());
        if (!config.hasIdfmToken()) return;

        String token = config.getIdfmToken();
        executor.execute(() -> fetchAndParseEstimatedTimetable(token));
    }

    private void fetchAndParseEstimatedTimetable(String token) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(ESTIMATED_TIMETABLE_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", token);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "fetchEstimatedTimetable: HTTP " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                Log.d(TAG, "fetchEstimatedTimetable: réponse reçue, taille=" + response.length());
                parseEstimatedTimetable(response.toString());
            } else {
                Log.e(TAG, "fetchEstimatedTimetable: ERREUR HTTP " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchEstimatedTimetable: exception", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void parseEstimatedTimetable(String jsonStr) {
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
        SimpleDateFormat isoFormatNoTz = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        isoFormatNoTz.setTimeZone(TimeZone.getDefault());

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject serviceDelivery = root
                    .getJSONObject("Siri")
                    .getJSONObject("ServiceDelivery");

            // EstimatedTimetableDelivery peut être un objet ou un tableau
            JSONObject delivery;
            JSONArray etdArray = serviceDelivery.optJSONArray("EstimatedTimetableDelivery");
            if (etdArray != null) {
                delivery = etdArray.getJSONObject(0);
            } else {
                delivery = serviceDelivery.optJSONObject("EstimatedTimetableDelivery");
            }
            if (delivery == null) {
                Log.w(TAG, "parseEstimatedTimetable: pas de EstimatedTimetableDelivery");
                return;
            }

            if (!delivery.has("EstimatedJourneyVersionFrame")) {
                Log.w(TAG, "parseEstimatedTimetable: pas de EstimatedJourneyVersionFrame");
                return;
            }

            JSONArray frames = getFlexibleJSONArray(delivery, "EstimatedJourneyVersionFrame");
            if (frames == null) {
                Log.w(TAG, "parseEstimatedTimetable: EstimatedJourneyVersionFrame illisible");
                return;
            }
            int totalJourneys = 0;
            int totalStops = 0;

            for (int f = 0; f < frames.length(); f++) {
                JSONObject frame = frames.getJSONObject(f);
                if (!frame.has("EstimatedVehicleJourney")) continue;

                JSONArray journeys = getFlexibleJSONArray(frame, "EstimatedVehicleJourney");
                if (journeys == null) continue;
                for (int j = 0; j < journeys.length(); j++) {
                    try {
                        JSONObject journey = journeys.getJSONObject(j);

                        String journeyRef = "";
                        JSONObject framedRef = journey.optJSONObject("FramedVehicleJourneyRef");
                        if (framedRef != null) {
                            JSONObject jrObj = framedRef.optJSONObject("DatedVehicleJourneyRef");
                            if (jrObj != null) {
                                journeyRef = jrObj.optString("value", "");
                            } else {
                                journeyRef = framedRef.optString("DatedVehicleJourneyRef", "");
                            }
                        }
                        if (journeyRef.isEmpty()) continue;

                        List<TrainStop> stops = new ArrayList<>();

                        // EstimatedCalls
                        JSONObject estimatedCalls = journey.optJSONObject("EstimatedCalls");
                        if (estimatedCalls != null) {
                            JSONArray callArray = getFlexibleJSONArray(estimatedCalls, "EstimatedCall");
                            if (callArray != null) {
                                int callCount = callArray.length();
                                for (int c = 0; c < callCount; c++) {
                                    TrainStop stop = parseEstimatedCall(
                                            callArray.getJSONObject(c),
                                            isoFormat, isoFormatNoTz,
                                            c == 0, c == callCount - 1);
                                    if (stop != null) stops.add(stop);
                                }
                            }
                        }

                        // RecordedCalls (arrêts déjà passés)
                        JSONObject recordedCalls = journey.optJSONObject("RecordedCalls");
                        if (recordedCalls != null) {
                            JSONArray recArray = getFlexibleJSONArray(recordedCalls, "RecordedCall");
                            if (recArray != null) {
                                List<TrainStop> recorded = new ArrayList<>();
                                for (int c = 0; c < recArray.length(); c++) {
                                    boolean isFirst = (c == 0 && stops.isEmpty());
                                    TrainStop stop = parseEstimatedCall(
                                            recArray.getJSONObject(c),
                                            isoFormat, isoFormatNoTz,
                                            isFirst, false);
                                    if (stop != null) recorded.add(stop);
                                }
                                recorded.addAll(stops);
                                stops = recorded;
                            }
                        }

                        if (!stops.isEmpty()) {
                            // Marquer premier et dernier
                            journeyStopsCache.put(journeyRef, stops);
                            totalJourneys++;
                            totalStops += stops.size();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "parseEstimatedTimetable: erreur journey #" + j, e);
                    }
                }
            }

            Log.i(TAG, "parseEstimatedTimetable: " + totalJourneys + " trajets, "
                    + totalStops + " arrêts au total");

        } catch (Exception e) {
            Log.e(TAG, "parseEstimatedTimetable: exception JSON", e);
        }
    }

    private TrainStop parseEstimatedCall(JSONObject call, SimpleDateFormat isoFormat,
                                          SimpleDateFormat isoFormatNoTz,
                                          boolean isFirst, boolean isLast) {
        try {
            // Nom de l'arrêt
            String stopName = "";
            JSONArray stopNames = call.optJSONArray("StopPointName");
            if (stopNames != null && stopNames.length() > 0) {
                stopName = stopNames.getJSONObject(0).optString("value", "");
            } else {
                JSONObject stopNameObj = call.optJSONObject("StopPointName");
                if (stopNameObj != null) {
                    stopName = stopNameObj.optString("value", "");
                }
            }
            // Fallback : extraire le nom depuis StopPointRef si StopPointName absent
            if (stopName.isEmpty()) {
                String stopRef = "";
                JSONObject stopRefObj = call.optJSONObject("StopPointRef");
                if (stopRefObj != null) {
                    stopRef = stopRefObj.optString("value", "");
                } else {
                    stopRef = call.optString("StopPointRef", "");
                }
                if (!stopRef.isEmpty()) {
                    // Extraire un nom lisible du ref (ex: "STIF:StopPoint:Q:43111:" -> "Arrêt 43111")
                    String[] parts = stopRef.split(":");
                    for (int p = parts.length - 1; p >= 0; p--) {
                        if (!parts[p].isEmpty()) {
                            stopName = "Arrêt " + parts[p];
                            break;
                        }
                    }
                }
            }
            if (stopName.isEmpty()) return null;

            // Horaires
            long aimedArr = parseToMillis(call.optString("AimedArrivalTime", ""), isoFormat, isoFormatNoTz);
            long expectedArr = parseToMillis(call.optString("ExpectedArrivalTime", ""), isoFormat, isoFormatNoTz);
            long aimedDep = parseToMillis(call.optString("AimedDepartureTime", ""), isoFormat, isoFormatNoTz);
            long expectedDep = parseToMillis(call.optString("ExpectedDepartureTime", ""), isoFormat, isoFormatNoTz);

            // Voie
            String platform = "";
            JSONObject platformObj = call.optJSONObject("ArrivalPlatformName");
            if (platformObj != null) platform = platformObj.optString("value", "");
            if (platform.isEmpty()) {
                JSONObject depPlatformObj = call.optJSONObject("DeparturePlatformName");
                if (depPlatformObj != null) platform = depPlatformObj.optString("value", "");
            }

            return new TrainStop(stopName, aimedArr, expectedArr, aimedDep, expectedDep,
                    platform, isFirst, isLast);
        } catch (Exception e) {
            Log.e(TAG, "parseEstimatedCall: erreur", e);
            return null;
        }
    }

    private long parseToMillis(String isoStr, SimpleDateFormat withTz, SimpleDateFormat withoutTz) {
        if (isoStr == null || isoStr.isEmpty()) return 0;
        Date d = parseIsoDateTime(isoStr, withTz, withoutTz);
        return d != null ? d.getTime() : 0;
    }

    /**
     * L'API IDFM SIRI peut retourner un élément unique comme JSONObject
     * au lieu d'un JSONArray. Cette méthode normalise en JSONArray.
     */
    private JSONArray getFlexibleJSONArray(JSONObject parent, String key) {
        JSONArray arr = parent.optJSONArray(key);
        if (arr != null) return arr;
        JSONObject obj = parent.optJSONObject(key);
        if (obj != null) {
            JSONArray wrapped = new JSONArray();
            wrapped.put(obj);
            return wrapped;
        }
        return null;
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

                    lineStatusUpdate.setText(formatTimeWithSmallSeconds("MAJ ", new Date()));

                    if (allIncidents == null) {
                        lineStatusSummary.setText("Erreur de chargement");
                        perturbationsSection.setVisibility(View.GONE);
                        travauxSection.setVisibility(View.GONE);
                        int warningColor = ContextCompat.getColor(requireContext(), R.color.status_warning);
                        lineStatusStripe.setBackgroundColor(warningColor);
                        lineStatusEmoji.setText("ERREUR");
                        lineStatusEmoji.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_warning));
                    } else {
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

                String channel = "";
                JSONObject channelRef = msg.optJSONObject("InfoChannelRef");
                if (channelRef != null) {
                    channel = channelRef.optString("value", "");
                } else {
                    channel = msg.optString("InfoChannelRef", "");
                }

                String severity;
                String type;
                boolean isPerturbationChannel = channel.toLowerCase(Locale.FRENCH).contains("perturbation");

                if (isPerturbationChannel) {
                    severity = "blocking";
                    type = TrainIncident.TYPE_PERTURBATION;
                } else {
                    severity = "information";
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

                String recordedAt = formatDateTime(msg.optString("RecordedAtTime", ""));
                String validUntil = formatDateTime(msg.optString("ValidUntilTime", ""));

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
