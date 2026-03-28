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
import com.alixpat.vigie.model.LineNStation;
import com.alixpat.vigie.model.TrainIncident;
import com.alixpat.vigie.model.TrainSchedule;
import com.alixpat.vigie.model.TrainStop;
import com.alixpat.vigie.view.LineMapView;
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
    private static final String STOP_POINTS_DISCOVERY_URL =
            "https://prim.iledefrance-mobilites.fr/marketplace/stop-points-discovery"
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
    private static final String[] PERTURBATION_KEYWORDS = {
            "interrompu", "perturbé", "ralenti", "supprimé",
            "retard", "allongement", "dévié", "régulation"
    };
    private static final String[] BLOCKING_KEYWORDS = {
            "interrompu", "supprimé", "immobilis"
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
    private final Map<String, String> journeyTrainNumberCache = new HashMap<>();
    private final Map<String, String> journeyMissionNameCache = new HashMap<>();
    private final Map<String, String> stopPointNameCache = new HashMap<>();

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
        String trainNumber;
        String missionName;
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

        View lineMapButton = view.findViewById(R.id.lineMapButton);
        if (lineMapButton != null) {
            lineMapButton.setOnClickListener(v -> showLineMapDialog());
        }
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
        fetchStopPointNames();
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

        // Info train (numéro + mission)
        StringBuilder trainInfoStr = new StringBuilder();
        if (schedule.getTrainNumber() != null && !schedule.getTrainNumber().isEmpty()) {
            trainInfoStr.append("Train ").append(schedule.getTrainNumber());
        }
        if (schedule.getMissionName() != null && !schedule.getMissionName().isEmpty()) {
            if (trainInfoStr.length() > 0) trainInfoStr.append(" \u2022 ");
            trainInfoStr.append(schedule.getMissionName());
        }
        if (trainInfoStr.length() > 0) {
            TextView trainInfoHeader = new TextView(requireContext());
            trainInfoHeader.setText(trainInfoStr.toString());
            trainInfoHeader.setTextColor(0xFF00A86B);
            trainInfoHeader.setTextSize(15);
            trainInfoHeader.setTypeface(null, Typeface.BOLD);
            trainInfoHeader.setPadding(0, 0, 0, dpToPx(4));
            container.addView(trainInfoHeader);
        }

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
        StringBuilder trainId = new StringBuilder();
        if (schedule.getTrainNumber() != null && !schedule.getTrainNumber().isEmpty()) {
            trainId.append("Train ").append(schedule.getTrainNumber());
        }
        if (schedule.getMissionName() != null && !schedule.getMissionName().isEmpty()) {
            if (trainId.length() > 0) trainId.append(" \u2022 ");
            trainId.append(schedule.getMissionName());
        }
        String message = (trainId.length() > 0 ? trainId + "\n\n" : "")
                + schedule.estimatePosition()
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

    // ==================== PLAN DE LA LIGNE ====================

    private void showLineMapDialog() {
        if (!isAdded() || getActivity() == null) return;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_line_map, null);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }

        LineMapView lineMapView = dialogView.findViewById(R.id.lineMapView);
        TextView trainCountView = dialogView.findViewById(R.id.lineMapTrainCount);
        TextView closeButton = dialogView.findViewById(R.id.lineMapClose);

        closeButton.setOnClickListener(v -> dialog.dismiss());

        // Construire la liste des trains sur le plan
        List<LineMapView.TrainOnMap> trainsOnMap = buildTrainsOnMap();
        lineMapView.setTrains(trainsOnMap);

        if (trainCountView != null) {
            int count = trainsOnMap.size();
            trainCountView.setText(count + " train" + (count > 1 ? "s" : "") + " en circulation");
        }

        dialog.show();

        // Forcer le plein écran après show()
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private List<LineMapView.TrainOnMap> buildTrainsOnMap() {
        List<LineMapView.TrainOnMap> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        Log.i(TAG, "buildTrainsOnMap: journeyStopsCache contient " + journeyStopsCache.size() + " trajets");

        for (Map.Entry<String, List<TrainStop>> entry : journeyStopsCache.entrySet()) {
            String journeyRef = entry.getKey();
            List<TrainStop> stops = entry.getValue();
            if (stops == null || stops.isEmpty()) continue;

            // Vérifier si ce train est encore actif (pas encore arrivé au terminus)
            TrainStop lastStop = stops.get(stops.size() - 1);
            TrainStop firstStop = stops.get(0);
            long lastTime = lastStop.getBestArrivalMillis();
            long firstTime = firstStop.getBestTimeMillis();

            // Ignorer les trains déjà arrivés ou pas encore partis dans > 30min
            if (lastTime > 0 && now > lastTime + 5 * 60_000L) {
                Log.d(TAG, "buildTrainsOnMap: SKIP " + journeyRef + " déjà arrivé (lastTime=" + lastTime + " now=" + now + " diff=" + ((now - lastTime) / 60000) + "min)");
                continue;
            }
            if (firstTime > 0 && firstTime - now > 30 * 60_000L) {
                Log.d(TAG, "buildTrainsOnMap: SKIP " + journeyRef + " départ dans +" + ((firstTime - now) / 60000) + "min");
                continue;
            }

            // Trouver la position du train
            String currentStop = null;
            String nextStop = null;
            float progress = 0f;

            // Log des statuts de chaque arrêt pour ce trajet
            StringBuilder stopStatuses = new StringBuilder();
            for (int i = 0; i < stops.size(); i++) {
                TrainStop s = stops.get(i);
                if (i > 0) stopStatuses.append(" | ");
                stopStatuses.append(resolveStopName(s.getStopName())).append("=").append(s.getStatus());
            }
            Log.d(TAG, "buildTrainsOnMap: " + journeyRef + " stops(" + stops.size() + "): " + stopStatuses);

            // Chercher entre quels arrêts le train se trouve
            for (int i = 0; i < stops.size(); i++) {
                TrainStop stop = stops.get(i);
                TrainStop.StopStatus status = stop.getStatus();

                if (status == TrainStop.StopStatus.CURRENT) {
                    currentStop = resolveStopName(stop.getStopName());
                    if (i + 1 < stops.size()) {
                        nextStop = resolveStopName(stops.get(i + 1).getStopName());
                    }
                    progress = 0.1f; // En gare
                    Log.d(TAG, "buildTrainsOnMap: " + journeyRef + " → CURRENT trouvé: " + currentStop);
                    break;
                }
            }

            // Si pas trouvé "CURRENT", chercher entre deux arrêts
            if (currentStop == null) {
                for (int i = 0; i < stops.size() - 1; i++) {
                    if (stops.get(i).getStatus() == TrainStop.StopStatus.PASSED
                            && stops.get(i + 1).getStatus() == TrainStop.StopStatus.UPCOMING) {
                        currentStop = resolveStopName(stops.get(i).getStopName());
                        nextStop = resolveStopName(stops.get(i + 1).getStopName());

                        // Calculer la progression entre les deux arrêts
                        long depTime = stops.get(i).getBestTimeMillis();
                        long arrTime = stops.get(i + 1).getBestArrivalMillis();
                        if (depTime > 0 && arrTime > depTime) {
                            progress = (float)(now - depTime) / (float)(arrTime - depTime);
                            progress = Math.max(0.1f, Math.min(0.9f, progress));
                        } else {
                            progress = 0.5f;
                        }
                        break;
                    }
                }
            }

            // Train pas encore parti ou toutes les gares sont UPCOMING
            if (currentStop == null) {
                currentStop = resolveStopName(firstStop.getStopName());
                if (stops.size() > 1) {
                    nextStop = resolveStopName(stops.get(1).getStopName());
                }
                progress = 0f;
            }

            // Déterminer le statut du train
            String destination = resolveStopName(lastStop.getStopName());
            boolean onTime = true;
            boolean delayed = false;
            boolean cancelled = false;
            int delayMinutes = 0;

            // Chercher le TrainSchedule correspondant pour le statut
            List<TrainSchedule> allSchedules = new ArrayList<>();
            if (scheduleAdapterAller != null) allSchedules.addAll(scheduleAdapterAller.getSchedules());
            if (scheduleAdapterRetour != null) allSchedules.addAll(scheduleAdapterRetour.getSchedules());

            for (TrainSchedule schedule : allSchedules) {
                if (journeyRef.equals(schedule.getJourneyRef())) {
                    cancelled = schedule.isCancelled();
                    delayed = schedule.isDelayed();
                    onTime = schedule.isOnTime();
                    delayMinutes = schedule.getDelayMinutes();
                    destination = schedule.getDestination();
                    // Corréler l'ID du dernier arrêt avec le nom de destination
                    String lastStopRaw = lastStop.getStopName();
                    if (lastStopRaw != null && lastStopRaw.startsWith("Arrêt ")
                            && destination != null && !destination.isEmpty()) {
                        String id = lastStopRaw.substring(6).trim();
                        if (!stopPointNameCache.containsKey(id)) {
                            stopPointNameCache.put(id, destination);
                            Log.d(TAG, "buildTrainsOnMap: cache enrichi " + id + "=" + destination);
                        }
                    }
                    break;
                }
            }

            if (cancelled) continue; // Ne pas afficher les trains supprimés sur le plan

            // Label court pour le train
            String label = shortenDestination(destination);

            // Récupérer numéro de train et nom de mission
            String trainNumber = journeyTrainNumberCache.get(journeyRef);
            String missionName = journeyMissionNameCache.get(journeyRef);
            if (trainNumber == null) trainNumber = "";
            if (missionName == null) missionName = "";

            Log.d(TAG, "buildTrainsOnMap: AJOUTÉ " + journeyRef
                    + " currentStop=" + currentStop + " nextStop=" + nextStop
                    + " progress=" + progress + " dest=" + destination
                    + " train=" + trainNumber + " mission=" + missionName);

            result.add(new LineMapView.TrainOnMap(
                    journeyRef, destination,
                    currentStop, nextStop, progress,
                    onTime, delayed, cancelled, delayMinutes, label,
                    trainNumber, missionName));
        }

        return result;
    }

    private static String shortenDestination(String dest) {
        if (dest == null) return "";
        String lower = dest.toLowerCase(Locale.FRENCH);
        if (lower.contains("montparnasse") || lower.contains("paris")) return "Paris";
        if (lower.contains("rambouillet")) return "Ramb.";
        if (lower.contains("mantes")) return "Mantes";
        if (lower.contains("dreux")) return "Dreux";
        if (lower.contains("plaisir")) return "Plaisir";
        if (lower.contains("versailles")) return "Versail.";
        if (lower.contains("villepreux")) return "Villep.";
        if (dest.length() > 8) return dest.substring(0, 7) + ".";
        return dest;
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
                        JSONObject djrObj = journey.optJSONObject("DatedVehicleJourneyRef");
                        if (djrObj != null) {
                            journeyRef = djrObj.optString("value", "");
                        } else {
                            journeyRef = journey.optString("DatedVehicleJourneyRef", "");
                        }
                    }
                    if (journeyRef.isEmpty()) {
                        noJourneyRef++;
                        continue;
                    }

                    // Enrichir le cache de noms d'arrêts depuis DestinationRef
                    enrichCacheFromDestination(journey);

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

                    // Numéro de train (TrainNumbers ou VehicleRef)
                    raw.trainNumber = "";
                    JSONObject trainNumbers = journey.optJSONObject("TrainNumbers");
                    if (trainNumbers != null) {
                        JSONArray trainNumArray = trainNumbers.optJSONArray("TrainNumberRef");
                        if (trainNumArray != null && trainNumArray.length() > 0) {
                            JSONObject tnObj = trainNumArray.optJSONObject(0);
                            if (tnObj != null) {
                                raw.trainNumber = tnObj.optString("value", "");
                            } else {
                                raw.trainNumber = trainNumArray.optString(0, "");
                            }
                        }
                    }
                    if (raw.trainNumber.isEmpty()) {
                        JSONObject vehicleRef = journey.optJSONObject("VehicleRef");
                        if (vehicleRef != null) {
                            raw.trainNumber = vehicleRef.optString("value", "");
                        }
                    }

                    // Nom de mission (VehicleJourneyName, ex: "MOPI")
                    raw.missionName = "";
                    JSONArray vjNames = journey.optJSONArray("VehicleJourneyName");
                    if (vjNames != null && vjNames.length() > 0) {
                        raw.missionName = vjNames.getJSONObject(0).optString("value", "");
                    } else {
                        JSONObject vjName = journey.optJSONObject("VehicleJourneyName");
                        if (vjName != null) {
                            raw.missionName = vjName.optString("value", "");
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

            String trainNum = origin.trainNumber != null ? origin.trainNumber : "";
            String missionNm = origin.missionName != null ? origin.missionName : "";

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
                    originStation,
                    trainNum,
                    missionNm
            ));

            // Stocker numéro de train et nom de mission pour l'affichage sur le plan
            if (!trainNum.isEmpty()) {
                journeyTrainNumberCache.put(journeyRef, trainNum);
            }
            if (!missionNm.isEmpty()) {
                journeyMissionNameCache.put(journeyRef, missionNm);
            }
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

    // ==================== STOP POINTS DISCOVERY (noms des arrêts) ====================

    private void fetchStopPointNames() {
        if (!stopPointNameCache.isEmpty()) return; // Déjà chargé
        BrokerConfig config = new BrokerConfig(requireContext());
        if (!config.hasIdfmToken()) return;

        String token = config.getIdfmToken();
        executor.execute(() -> fetchAndParseStopPointNames(token));
    }

    private void fetchAndParseStopPointNames(String token) {
        // Essayer l'URL principale, puis un fallback avec le format IDFM
        String[] urls = {
                STOP_POINTS_DISCOVERY_URL,
                "https://prim.iledefrance-mobilites.fr/marketplace/stop-points-discovery"
                        + "?LineRef=IDFM:Line::C01736:"
        };
        for (String urlStr : urls) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("apikey", token);
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "fetchStopPointNames: URL=" + urlStr + " HTTP " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    parseStopPointNames(response.toString());
                    if (!stopPointNameCache.isEmpty()) {
                        Log.i(TAG, "fetchStopPointNames: succès avec " + urlStr);
                        return; // Succès, pas besoin d'essayer les fallbacks
                    }
                } else {
                    Log.e(TAG, "fetchStopPointNames: ERREUR HTTP " + responseCode + " pour " + urlStr);
                    try {
                        BufferedReader errReader = new BufferedReader(
                                new InputStreamReader(connection.getErrorStream()));
                        StringBuilder errBody = new StringBuilder();
                        String errLine;
                        while ((errLine = errReader.readLine()) != null) {
                            errBody.append(errLine);
                            if (errBody.length() > 500) break;
                        }
                        errReader.close();
                        Log.e(TAG, "fetchStopPointNames: body=" + errBody);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                Log.e(TAG, "fetchStopPointNames: exception pour " + urlStr, e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        Log.w(TAG, "fetchStopPointNames: toutes les URLs ont échoué, utilisation du mapping statique");
    }

    private void parseStopPointNames(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject siri = root.optJSONObject("Siri");
            if (siri == null) { siri = root; }

            JSONObject delivery = siri.optJSONObject("StopPointsDelivery");
            if (delivery == null) {
                Log.w(TAG, "parseStopPointNames: pas de StopPointsDelivery");
                return;
            }

            JSONArray annotated = delivery.optJSONArray("AnnotatedStopPointRef");
            if (annotated == null) {
                // Essayer en tant qu'objet unique
                JSONObject single = delivery.optJSONObject("AnnotatedStopPointRef");
                if (single != null) {
                    annotated = new JSONArray();
                    annotated.put(single);
                }
            }
            if (annotated == null) {
                Log.w(TAG, "parseStopPointNames: pas de AnnotatedStopPointRef");
                return;
            }

            int count = 0;
            for (int i = 0; i < annotated.length(); i++) {
                JSONObject entry = annotated.getJSONObject(i);

                String stopRef = "";
                JSONObject stopRefObj = entry.optJSONObject("StopPointRef");
                if (stopRefObj != null) {
                    stopRef = stopRefObj.optString("value", "");
                } else {
                    stopRef = entry.optString("StopPointRef", "");
                }

                String stopName = "";
                JSONObject stopNameObj = entry.optJSONObject("StopName");
                if (stopNameObj != null) {
                    stopName = stopNameObj.optString("value", "");
                } else {
                    stopName = entry.optString("StopName", "");
                }

                if (!stopRef.isEmpty() && !stopName.isEmpty()) {
                    // Extraire l'ID numérique du ref (ex: "STIF:StopPoint:Q:43111:" -> "43111")
                    String numericId = extractNumericId(stopRef);
                    if (!numericId.isEmpty()) {
                        stopPointNameCache.put(numericId, stopName);
                        count++;
                    }
                }
            }
            Log.i(TAG, "parseStopPointNames: " + count + " arrêts chargés dans le cache");

        } catch (Exception e) {
            Log.e(TAG, "parseStopPointNames: exception JSON", e);
        }
    }

    /**
     * Résout un nom d'arrêt "Arrêt XXXXX" en nom réel depuis le cache,
     * puis depuis le mapping statique de la ligne N en fallback.
     */
    private String resolveStopName(String name) {
        if (name == null || !name.startsWith("Arrêt ")) return name;
        String numericId = name.substring(6).trim(); // "Arrêt 43219" → "43219"
        // 1. Cache dynamique (stop-points-discovery ou DestinationRef)
        String cached = stopPointNameCache.get(numericId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        // 2. Mapping statique de la ligne N en fallback
        String staticName = LineNStation.getStopIdToNameMapping().get(numericId);
        if (staticName != null && !staticName.isEmpty()) {
            // Mémoriser pour éviter de chercher à chaque fois
            stopPointNameCache.put(numericId, staticName);
            return staticName;
        }
        return name;
    }

    /**
     * Extrait l'ID numérique d'un StopPointRef STIF.
     * Ex: "STIF:StopPoint:Q:43111:" -> "43111"
     * Ex: "STIF:StopArea:SP:43111:" -> "43111"
     */
    private static String extractNumericId(String stopRef) {
        if (stopRef == null || stopRef.isEmpty()) return "";
        String[] parts = stopRef.split(":");
        for (int p = parts.length - 1; p >= 0; p--) {
            if (!parts[p].isEmpty()) {
                return parts[p];
            }
        }
        return "";
    }

    /**
     * Extrait DestinationRef + DestinationName d'un EstimatedVehicleJourney
     * et les ajoute au cache de noms d'arrêts.
     */
    private void enrichCacheFromDestination(JSONObject journey) {
        try {
            // DestinationRef + DestinationName
            extractAndCacheRef(journey, "DestinationRef", "DestinationName");
            // OriginRef + OriginName
            extractAndCacheRef(journey, "OriginRef", "OriginName");
        } catch (Exception e) {
            // Ignorer silencieusement
        }
    }

    private void extractAndCacheRef(JSONObject journey, String refKey, String nameKey) {
        String ref = "";
        JSONObject refObj = journey.optJSONObject(refKey);
        if (refObj != null) {
            ref = refObj.optString("value", "");
        } else {
            ref = journey.optString(refKey, "");
        }
        String name = "";
        JSONArray names = journey.optJSONArray(nameKey);
        if (names != null && names.length() > 0) {
            name = names.optJSONObject(0) != null
                    ? names.getJSONObject(0).optString("value", "") : "";
        } else {
            JSONObject nameObj = journey.optJSONObject(nameKey);
            if (nameObj != null) {
                name = nameObj.optString("value", "");
            }
        }
        if (!ref.isEmpty() && !name.isEmpty()) {
            String numericId = extractNumericId(ref);
            if (!numericId.isEmpty() && !stopPointNameCache.containsKey(numericId)) {
                stopPointNameCache.put(numericId, name);
                Log.d(TAG, "enrichCache: " + numericId + "=" + name + " (from " + refKey + ")");
            }
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
        // S'assurer que le cache de noms d'arrêts est chargé avant de parser le timetable
        if (stopPointNameCache.isEmpty()) {
            Log.i(TAG, "fetchAndParseEstimatedTimetable: cache de noms vide, chargement synchrone");
            fetchAndParseStopPointNames(token);
            Log.i(TAG, "fetchAndParseEstimatedTimetable: cache chargé avec " + stopPointNameCache.size() + " entrées");
        }
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

            Log.d(TAG, "parseEstimatedTimetable: frames.length=" + frames.length());
            for (int f = 0; f < frames.length(); f++) {
                JSONObject frame = frames.getJSONObject(f);
                if (f == 0) {
                    Log.d(TAG, "parseEstimatedTimetable: frame[0] keys=" + iteratorToString(frame.keys()));
                }
                if (!frame.has("EstimatedVehicleJourney")) {
                    Log.d(TAG, "parseEstimatedTimetable: frame[" + f + "] pas de EstimatedVehicleJourney");
                    continue;
                }

                JSONArray journeys = getFlexibleJSONArray(frame, "EstimatedVehicleJourney");
                if (journeys == null) continue;
                Log.d(TAG, "parseEstimatedTimetable: frame[" + f + "] journeys.length=" + journeys.length());
                for (int j = 0; j < journeys.length(); j++) {
                    try {
                        JSONObject journey = journeys.getJSONObject(j);
                        if (j == 0 && f == 0) {
                            Log.d(TAG, "parseEstimatedTimetable: journey[0] keys=" + iteratorToString(journey.keys()));
                        }

                        String journeyRef = "";
                        JSONObject framedRef = journey.optJSONObject("FramedVehicleJourneyRef");
                        if (framedRef != null) {
                            if (f == 0 && j == 0) {
                                Log.d(TAG, "parseEstimatedTimetable: framedRef keys=" + iteratorToString(framedRef.keys()));
                            }
                            JSONObject jrObj = framedRef.optJSONObject("DatedVehicleJourneyRef");
                            if (jrObj != null) {
                                journeyRef = jrObj.optString("value", "");
                            } else {
                                journeyRef = framedRef.optString("DatedVehicleJourneyRef", "");
                            }
                        } else {
                            if (f == 0 && j == 0) {
                                Log.d(TAG, "parseEstimatedTimetable: journey[0] pas de FramedVehicleJourneyRef");
                            }
                        }
                        if (journeyRef.isEmpty()) {
                            JSONObject djrObj = journey.optJSONObject("DatedVehicleJourneyRef");
                            if (djrObj != null) {
                                journeyRef = djrObj.optString("value", "");
                            } else {
                                journeyRef = journey.optString("DatedVehicleJourneyRef", "");
                            }
                            if (f == 0 && j == 0 && !journeyRef.isEmpty()) {
                                Log.d(TAG, "parseEstimatedTimetable: journey[0] using direct DatedVehicleJourneyRef=" + journeyRef);
                            }
                        }
                        if (journeyRef.isEmpty()) {
                            if (j < 3) Log.d(TAG, "parseEstimatedTimetable: journey[" + j + "] journeyRef vide, skip");
                            continue;
                        }

                        // Extraire DestinationRef + DestinationName pour enrichir le cache
                        enrichCacheFromDestination(journey);

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
                            // Trier les arrêts par horaire pour garantir l'ordre chronologique
                            Collections.sort(stops, (a, b) -> {
                                long ta = a.getBestTimeMillis();
                                long tb = b.getBestTimeMillis();
                                if (ta == 0 && tb == 0) return 0;
                                if (ta == 0) return 1;
                                if (tb == 0) return -1;
                                return Long.compare(ta, tb);
                            });
                            journeyStopsCache.put(journeyRef, stops);
                            totalJourneys++;
                            totalStops += stops.size();

                            // Extraire numéro de train et nom de mission
                            String trainNum = "";
                            JSONObject trainNumbers = journey.optJSONObject("TrainNumbers");
                            if (trainNumbers != null) {
                                JSONArray trainNumArray = trainNumbers.optJSONArray("TrainNumberRef");
                                if (trainNumArray != null && trainNumArray.length() > 0) {
                                    JSONObject tnObj = trainNumArray.optJSONObject(0);
                                    if (tnObj != null) {
                                        trainNum = tnObj.optString("value", "");
                                    } else {
                                        trainNum = trainNumArray.optString(0, "");
                                    }
                                }
                            }
                            if (trainNum.isEmpty()) {
                                JSONObject vehicleRef = journey.optJSONObject("VehicleRef");
                                if (vehicleRef != null) {
                                    trainNum = vehicleRef.optString("value", "");
                                }
                            }
                            if (!trainNum.isEmpty()) {
                                journeyTrainNumberCache.put(journeyRef, trainNum);
                            }

                            String missionName = "";
                            JSONArray vjNames = journey.optJSONArray("VehicleJourneyName");
                            if (vjNames != null && vjNames.length() > 0) {
                                missionName = vjNames.getJSONObject(0).optString("value", "");
                            } else {
                                JSONObject vjName = journey.optJSONObject("VehicleJourneyName");
                                if (vjName != null) {
                                    missionName = vjName.optString("value", "");
                                }
                            }
                            if (!missionName.isEmpty()) {
                                journeyMissionNameCache.put(journeyRef, missionName);
                            }
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
            // Fallback : résoudre le nom depuis StopPointRef via le cache stop-points-discovery
            if (stopName.isEmpty()) {
                String stopRef = "";
                JSONObject stopRefObj = call.optJSONObject("StopPointRef");
                if (stopRefObj != null) {
                    stopRef = stopRefObj.optString("value", "");
                } else {
                    stopRef = call.optString("StopPointRef", "");
                }
                if (!stopRef.isEmpty()) {
                    String numericId = extractNumericId(stopRef);
                    if (!numericId.isEmpty()) {
                        // Chercher dans le cache stop-points-discovery
                        String cached = stopPointNameCache.get(numericId);
                        if (cached != null && !cached.isEmpty()) {
                            stopName = cached;
                        } else {
                            stopName = "Arrêt " + numericId;
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

    private String iteratorToString(java.util.Iterator<?> it) {
        StringBuilder sb = new StringBuilder("[");
        while (it.hasNext()) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(it.next());
        }
        sb.append("]");
        return sb.toString();
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
            Log.d(TAG, "fetchIncidentsFromApi: response code " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                List<TrainIncident> result = parseIncidentResponse(response.toString());
                Log.i(TAG, "fetchIncidentsFromApi: parsed " + result.size() + " incidents");
                return result;
            } else {
                Log.w(TAG, "fetchIncidentsFromApi: HTTP error " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchIncidentsFromApi: network error", e);
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
                        // Handle MessageText as object {"value": "..."} or plain string
                        JSONObject msgText = msgObj.optJSONObject("MessageText");
                        if (msgText != null) {
                            String value = msgText.optString("value", "");
                            if (!value.isEmpty()) {
                                text = value;
                                break;
                            }
                        } else {
                            String value = msgObj.optString("MessageText", "");
                            if (!value.isEmpty()) {
                                text = value;
                                break;
                            }
                        }
                    }
                }

                // Fallback: try to get text directly from Content if Message array yielded nothing
                if (text.isEmpty()) {
                    // Some API responses put text directly in Content.Message as a string
                    String directMsg = content.optString("Message", "");
                    if (!directMsg.isEmpty()) {
                        text = directMsg;
                    }
                }

                if (text.isEmpty()) {
                    Log.w(TAG, "parseIncidentResponse: empty text for message index " + i
                            + ", content keys: " + content.keys());
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
                String textLower = text.toLowerCase(Locale.FRENCH);
                boolean isPerturbationChannel = channel.toLowerCase(Locale.FRENCH).contains("perturbation");

                // Detect perturbation keywords in text regardless of channel
                boolean hasPerturbationKeywords = false;
                for (String keyword : PERTURBATION_KEYWORDS) {
                    if (textLower.contains(keyword)) {
                        hasPerturbationKeywords = true;
                        break;
                    }
                }

                if (isPerturbationChannel || hasPerturbationKeywords) {
                    type = TrainIncident.TYPE_PERTURBATION;
                    // Determine actual severity from message content
                    boolean isBlocking = false;
                    for (String keyword : BLOCKING_KEYWORDS) {
                        if (textLower.contains(keyword)) {
                            isBlocking = true;
                            break;
                        }
                    }
                    severity = isBlocking ? "blocking" : "delays";
                } else {
                    severity = "information";
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
            Log.e(TAG, "parseIncidentResponse: error parsing JSON", e);
            Log.d(TAG, "parseIncidentResponse: raw response (first 500 chars): "
                    + jsonStr.substring(0, Math.min(500, jsonStr.length())));
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
