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

import com.alixpat.vigie.Settings;
import com.alixpat.vigie.R;
import com.alixpat.vigie.adapter.TrainIncidentAdapter;
import com.alixpat.vigie.adapter.TrainOngoingAdapter;
import com.alixpat.vigie.adapter.TrainPinListener;
import com.alixpat.vigie.adapter.TrainScheduleAdapter;
import com.alixpat.vigie.model.LineNStation;
import com.alixpat.vigie.model.OngoingTrain;
import com.alixpat.vigie.model.TrainIncident;
import com.alixpat.vigie.model.TrainSchedule;
import com.alixpat.vigie.model.TrainStop;
import com.alixpat.vigie.train.IdfmClient;
import com.alixpat.vigie.train.IncidentClassifier;
import com.alixpat.vigie.train.JourneyRoutes;
import com.alixpat.vigie.train.LineNDirection;
import com.alixpat.vigie.train.MyTrains;
import com.alixpat.vigie.train.OngoingTrains;
import com.alixpat.vigie.train.PassageHistory;
import com.alixpat.vigie.train.PinnedTrains;
import com.alixpat.vigie.train.StopVisit;
import com.alixpat.vigie.train.TrainPosition;
import com.alixpat.vigie.util.DateFormats;
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

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrainFragment extends Fragment {

    private static final String TAG = "TrainFragment";
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000;
    private static final long SCHEDULE_WINDOW_MS = 2 * 60 * 60 * 1000;
    /**
     * On remonte un peu dans le passé pour ne pas perdre les trains déjà partis
     * (ceux dans lesquels je suis) ni ceux dont le départ a été retardé.
     */
    private static final long SCHEDULE_LOOKBACK_MS = 30 * 60 * 1000;
    /** Rafraîchissement local de la position des trains en cours (sans appel réseau). */
    private static final long POSITION_TICK_MS = 20 * 1000;
    /** Sur le plan, un train reste affiché ce délai après son arrivée au terminus. */
    private static final long MAP_ARRIVED_GRACE_MS = 5 * 60 * 1000;
    /** Sur le plan, un train dont le départ est plus loin que ça n'est pas dessiné. */
    private static final long MAP_DEPARTURE_HORIZON_MS = 30 * 60 * 1000;

    private final IdfmClient idfmClient = new IdfmClient(
            "STIF:Line::C01736:",   // Ligne N (SIRI LineRef)
            "line:IDFM:C01736"       // Ligne N (Navitia line ID)
    );

    private MaterialCardView lineStatusCard;
    private View lineStatusStripe;
    private TextView lineStatusEmoji;
    private TextView lineStatusTitle;
    private TextView lineStatusUpdate;
    private TextView lineStatusSummary;
    private TextView lineStatusChevron;

    private View perturbationsSection;
    private RecyclerView perturbationsRecyclerView;
    private TrainIncidentAdapter perturbationsAdapter;

    private View travauxSection;
    private RecyclerView travauxRecyclerView;
    private TrainIncidentAdapter travauxAdapter;

    // Liste actuelle des incidents (pour redessiner sur toggle sans refetch)
    private final List<TrainIncident> lastPerturbations = new ArrayList<>();
    private final List<TrainIncident> lastTravaux = new ArrayList<>();
    private boolean incidentsListVisible = false;

    /**
     * Les trains que je suis. Épingler un train le remonte dans une carte à part,
     * en haut de l'onglet : plus besoin de le rechercher dans sa carte de sens à
     * chaque rafraîchissement. Persisté par {@link Settings}, purgé tout seul
     * (un journeyRef IDFM est daté, il ne survit pas à la journée).
     */
    private PinnedTrains pinnedTrains = new PinnedTrains();

    private MaterialCardView pinnedCard;
    private TextView pinnedTitle;
    private TextView pinnedLastUpdate;
    private TextView pinnedEmpty;
    private RecyclerView pinnedRecyclerView;
    private TrainOngoingAdapter pinnedAdapter;

    /** Branche les épingles des listes sur {@link #pinnedTrains}. */
    private final TrainPinListener pinListener = new TrainPinListener() {
        @Override
        public boolean isPinned(TrainSchedule schedule) {
            return schedule != null && pinnedTrains.isPinned(schedule.getJourneyRef());
        }

        @Override
        public void onPinToggled(TrainSchedule schedule) {
            togglePin(schedule);
        }
    };

    // Une carte par sens, et dans chaque carte deux divisions : les prochains
    // départs puis les trains en circulation dans ce sens. Les deux sens ne
    // partagent plus aucune liste à l'écran.
    private RecyclerView ongoingRecyclerViewAller;
    private TextView ongoingTitleAller;
    private TextView ongoingLastUpdateAller;
    private TextView ongoingEmptyAller;
    private TrainOngoingAdapter ongoingAdapterAller;

    private RecyclerView ongoingRecyclerViewRetour;
    private TextView ongoingTitleRetour;
    private TextView ongoingLastUpdateRetour;
    private TextView ongoingEmptyRetour;
    private TrainOngoingAdapter ongoingAdapterRetour;

    // Derniers trajets en cours connus, par sens : la position est recalculée
    // localement toutes les POSITION_TICK_MS sans refaire d'appel réseau.
    private final List<TrainSchedule> ongoingAller = new ArrayList<>();
    private final List<TrainSchedule> ongoingRetour = new ArrayList<>();

    // Souvenir des trains vus à chacune de mes deux gares. Indispensable : un train
    // qui vient de partir disparaît du stop-monitoring de sa gare de départ, et son
    // heure de départ serait alors introuvable — donc le train ne serait jamais
    // reconnu comme « en circulation ». Purgé par OngoingTrains.rememberOriginVisits.
    // Concurrentes : écrites par l'executor (rememberOriginVisits), lues par le
    // thread UI quand le plan de la ligne trie mes trains du reste du trafic.
    private final Map<String, StopVisit> seenAtClamart = new ConcurrentHashMap<>();
    private final Map<String, StopVisit> seenAtVillepreux = new ConcurrentHashMap<>();

    // Derniers passages ramenés par l'API, conservés pour rejouer la construction des
    // trains en circulation quand le parcours des trains (estimated-timetable) arrive
    // après les horaires — c'est le cas au tout premier chargement.
    private Map<String, StopVisit> lastClamartData;
    private Map<String, StopVisit> lastVillepreuxData;

    // Derniers horaires ramenés par l'API, avant partage "à venir" / "en cours" :
    // le tick local rejoue ce partage pour qu'un train qui vient de partir bascule
    // dans la section "en circulation" sans attendre le prochain appel réseau.
    private final List<TrainSchedule> lastAllerSchedules = new ArrayList<>();
    private final List<TrainSchedule> lastRetourSchedules = new ArrayList<>();

    private RecyclerView scheduleRecyclerViewAller;
    private TextView scheduleEmptyAller;
    private TextView scheduleLastUpdateAller;
    private TextView scheduleTitleAller;
    private TextView departuresTitleAller;
    private TrainScheduleAdapter scheduleAdapterAller;

    private RecyclerView scheduleRecyclerViewRetour;
    private TextView scheduleEmptyRetour;
    private TextView scheduleLastUpdateRetour;
    private TextView scheduleTitleRetour;
    private TextView departuresTitleRetour;
    private TrainScheduleAdapter scheduleAdapterRetour;

    // Plan de la ligne actuellement ouvert (null si le dialogue est fermé) :
    // gardé pour rejouer les positions à chaque tick au lieu de figer la vue.
    private LineMapView openLineMapView;
    private TextView openLineMapCount;
    private TextView openLineMapFilter;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Écrits par l'executor, lus par le tick de position sur le thread UI : la
    // purge des parcours retire des entrées pendant que l'UI en lit.
    private final Map<String, List<TrainStop>> journeyStopsCache = new ConcurrentHashMap<>();
    private final Map<String, String> journeyTrainNumberCache = new ConcurrentHashMap<>();
    private final Map<String, String> journeyMissionNameCache = new ConcurrentHashMap<>();
    private final Map<String, String> stopPointNameCache = new ConcurrentHashMap<>();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchAll();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private final Runnable positionTickRunnable = new Runnable() {
        @Override
        public void run() {
            updateOngoingSection();
            refreshHandler.postDelayed(this, POSITION_TICK_MS);
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

        lineStatusCard = view.findViewById(R.id.lineStatusCard);
        lineStatusStripe = view.findViewById(R.id.lineStatusStripe);
        lineStatusEmoji = view.findViewById(R.id.lineStatusEmoji);
        lineStatusTitle = view.findViewById(R.id.lineStatusTitle);
        lineStatusUpdate = view.findViewById(R.id.lineStatusUpdate);
        lineStatusSummary = view.findViewById(R.id.lineStatusSummary);
        lineStatusChevron = view.findViewById(R.id.lineStatusChevron);

        // Tap sur le bandeau de statut → toggle l'affichage des listes d'incidents
        lineStatusCard.setOnClickListener(v -> {
            if (lastPerturbations.isEmpty() && lastTravaux.isEmpty()) return;
            incidentsListVisible = !incidentsListVisible;
            updateIncidentsVisibility();
        });

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

        pinnedTrains = PinnedTrains.decode(new Settings(requireContext()).getPinnedTrains(),
                System.currentTimeMillis());
        pinnedCard = view.findViewById(R.id.pinnedCard);
        pinnedTitle = view.findViewById(R.id.pinnedTitle);
        pinnedLastUpdate = view.findViewById(R.id.pinnedLastUpdate);
        pinnedEmpty = view.findViewById(R.id.pinnedEmpty);
        pinnedRecyclerView = view.findViewById(R.id.pinnedRecyclerView);
        // Les suivis mélangent les deux sens : contrairement aux cartes de sens,
        // l'item doit dire dans quel sens roule le train.
        pinnedAdapter = createOngoingAdapter(pinnedRecyclerView);
        pinnedAdapter.setShowDirection(true);

        ongoingRecyclerViewAller = view.findViewById(R.id.ongoingRecyclerViewAller);
        ongoingTitleAller = view.findViewById(R.id.ongoingTitleAller);
        ongoingLastUpdateAller = view.findViewById(R.id.ongoingLastUpdateAller);
        ongoingEmptyAller = view.findViewById(R.id.ongoingEmptyAller);
        ongoingAdapterAller = createOngoingAdapter(ongoingRecyclerViewAller);

        ongoingRecyclerViewRetour = view.findViewById(R.id.ongoingRecyclerViewRetour);
        ongoingTitleRetour = view.findViewById(R.id.ongoingTitleRetour);
        ongoingLastUpdateRetour = view.findViewById(R.id.ongoingLastUpdateRetour);
        ongoingEmptyRetour = view.findViewById(R.id.ongoingEmptyRetour);
        ongoingAdapterRetour = createOngoingAdapter(ongoingRecyclerViewRetour);

        scheduleRecyclerViewAller = view.findViewById(R.id.scheduleRecyclerViewAller);
        scheduleEmptyAller = view.findViewById(R.id.scheduleEmptyAller);
        scheduleLastUpdateAller = view.findViewById(R.id.scheduleLastUpdateAller);
        scheduleTitleAller = view.findViewById(R.id.scheduleTitleAller);
        departuresTitleAller = view.findViewById(R.id.departuresTitleAller);
        scheduleAdapterAller = new TrainScheduleAdapter();
        scheduleRecyclerViewAller.setLayoutManager(new LinearLayoutManager(requireContext()));
        scheduleRecyclerViewAller.setAdapter(scheduleAdapterAller);

        scheduleRecyclerViewRetour = view.findViewById(R.id.scheduleRecyclerViewRetour);
        scheduleEmptyRetour = view.findViewById(R.id.scheduleEmptyRetour);
        scheduleLastUpdateRetour = view.findViewById(R.id.scheduleLastUpdateRetour);
        scheduleTitleRetour = view.findViewById(R.id.scheduleTitleRetour);
        departuresTitleRetour = view.findViewById(R.id.departuresTitleRetour);
        scheduleAdapterRetour = new TrainScheduleAdapter();
        scheduleRecyclerViewRetour.setLayoutManager(new LinearLayoutManager(requireContext()));
        scheduleRecyclerViewRetour.setAdapter(scheduleAdapterRetour);

        scheduleAdapterAller.setOnTrainClickListener(this::showTrainDetailDialog);
        scheduleAdapterRetour.setOnTrainClickListener(this::showTrainDetailDialog);
        scheduleAdapterAller.setPinListener(pinListener);
        scheduleAdapterRetour.setPinListener(pinListener);

        View lineMapButton = view.findViewById(R.id.lineMapButton);
        if (lineMapButton != null) {
            lineMapButton.setOnClickListener(v -> showLineMapDialog());
        }
    }

    /**
     * Monte une liste "en circulation" sur son RecyclerView. Une par sens : le
     * sens est déjà écrit en tête de la carte, donc l'item affiche le terminus
     * du train plutôt que de répéter "Clamart → Villepreux".
     */
    private TrainOngoingAdapter createOngoingAdapter(RecyclerView recycler) {
        TrainOngoingAdapter adapter = new TrainOngoingAdapter();
        adapter.setShowDirection(false);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        adapter.setOnOngoingTrainClickListener(train -> showTrainDetailDialog(train.getSchedule()));
        adapter.setPinListener(pinListener);
        return adapter;
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchAll();
        updateOngoingSection();
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
        refreshHandler.postDelayed(positionTickRunnable, POSITION_TICK_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.removeCallbacks(positionTickRunnable);
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
            Settings config = new Settings(requireContext());
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
        // Trier les arrêts chronologiquement
        stops = new ArrayList<>(stops);
        Collections.sort(stops, (a, b) -> Long.compare(a.getBestTimeMillis(), b.getBestTimeMillis()));
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
        statusHeader.setPadding(0, 0, 0, dpToPx(4));
        container.addView(statusHeader);

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

        // Résumé de la position actuelle du train
        String positionText = null;
        if (currentStopIndex >= 0) {
            positionText = "\uD83D\uDCCD En gare de " + stops.get(currentStopIndex).getStopName();
        } else if (betweenAfterIndex >= 0 && betweenAfterIndex + 1 < stops.size()) {
            positionText = "\uD83D\uDE86 Entre " + stops.get(betweenAfterIndex).getStopName()
                    + " et " + stops.get(betweenAfterIndex + 1).getStopName();
        } else {
            // Tous UPCOMING = pas encore parti
            boolean allUpcoming = true;
            for (TrainStop s : stops) {
                if (s.getStatus() != TrainStop.StopStatus.UPCOMING) { allUpcoming = false; break; }
            }
            if (allUpcoming && !stops.isEmpty()) {
                positionText = "\u23F3 Pas encore parti de " + stops.get(0).getStopName();
            }
            // Tous PASSED = arrivé
            boolean allPassed = true;
            for (TrainStop s : stops) {
                if (s.getStatus() != TrainStop.StopStatus.PASSED) { allPassed = false; break; }
            }
            if (allPassed && !stops.isEmpty()) {
                positionText = "\u2705 Arrivé à " + stops.get(stops.size() - 1).getStopName();
            }
        }
        if (positionText != null) {
            TextView positionView = new TextView(requireContext());
            positionView.setText(positionText);
            positionView.setTextColor(0xFF1976D2);
            positionView.setTextSize(14);
            positionView.setTypeface(null, Typeface.BOLD);
            positionView.setPadding(0, dpToPx(4), 0, dpToPx(12));
            container.addView(positionView);
        }

        addPassageHistory(container, schedule, stops, now);

        TextView routeHeader = new TextView(requireContext());
        routeHeader.setText("\uD83D\uDE89 Parcours complet");
        routeHeader.setTextSize(14);
        routeHeader.setTypeface(null, Typeface.BOLD);
        routeHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        routeHeader.setPadding(0, dpToPx(4), 0, dpToPx(6));
        container.addView(routeHeader);

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
                String main = DateFormats.formatHhmm(bestDate);
                String sec = DateFormats.formatColonSeconds(bestDate);
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
                platformView.setText("  Voie " + stop.getPlatformName());
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

        showTrainDialog(title, scrollView, schedule);
    }

    /** Le dialogue d'un train, avec la bascule de suivi en bouton neutre. */
    private void showTrainDialog(String title, View content, TrainSchedule schedule) {
        boolean pinned = pinnedTrains.isPinned(schedule.getJourneyRef());
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(content)
                .setNeutralButton(pinned ? "\uD83D\uDCCC Ne plus suivre" : "\uD83D\uDCCC Suivre",
                        (dialog, which) -> togglePin(schedule))
                .setPositiveButton("Fermer", null)
                .show();
    }

    /**
     * Historique des passages du train sur mon tronçon : une ligne par gare,
     * l'heure théorique et l'heure réalisée côte à côte.
     *
     * <p>« Réalisée » n'a de sens qu'une fois la gare franchie ; avant, c'est une
     * prévision. Les deux viennent du même champ IDFM, donc l'affichage doit les
     * distinguer lui-même — ici par la couleur et un libellé de colonne qui
     * change de ton (gris estompé tant que le passage est à venir).</p>
     */
    private void addPassageHistory(LinearLayout container, TrainSchedule schedule,
                                   List<TrainStop> stops, long now) {
        LineNDirection direction = directionOf(schedule);
        List<PassageHistory.Passage> passages =
                PassageHistory.build(resolveStopNames(stops), direction, now);
        if (passages.isEmpty()) return;

        TextView header = new TextView(requireContext());
        header.setText("\uD83D\uDCCB Passages sur mon trajet \u00B7 "
                + direction.getOriginName() + " \u2192 " + direction.getDestinationName());
        header.setTextSize(14);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        header.setPadding(0, dpToPx(4), 0, dpToPx(4));
        container.addView(header);

        container.addView(passageRow("Gare", "Théorique", "Réel", 0xFF9E9E9E, true));

        for (PassageHistory.Passage passage : passages) {
            String aimed = passage.getAimedMillis() > 0
                    ? DateFormats.formatHhmm(new Date(passage.getAimedMillis())) : "--:--";

            String actual;
            if (passage.hasActual()) {
                actual = DateFormats.formatHhmm(new Date(passage.getActualMillis()));
                int delay = passage.getDelayMinutes();
                if (delay != 0) actual += (delay > 0 ? " +" : " ") + delay;
            } else {
                // Rien d'annoncé : le théorique fait foi, on ne l'invente pas en réel.
                actual = passage.isPassed() ? "\u2713" : "\u2014";
            }

            int color;
            if (!passage.isPassed()) {
                color = 0xFF9E9E9E;                       // encore à venir : prévision
            } else if (passage.getDelayMinutes() > 0) {
                color = 0xFFFF9800;                       // passé en retard
            } else {
                color = 0xFF4CAF50;                       // passé à l'heure
            }
            container.addView(passageRow(passage.getStationName(), aimed, actual, color, false));
        }

        View divider = new View(requireContext());
        divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        dividerParams.topMargin = dpToPx(10);
        dividerParams.bottomMargin = dpToPx(10);
        divider.setLayoutParams(dividerParams);
        container.addView(divider);
    }

    /** Une ligne du tableau des passages : gare, heure théorique, heure réelle. */
    private LinearLayout passageRow(String station, String aimed, String actual,
                                    int actualColor, boolean isHeader) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dpToPx(2), 0, dpToPx(2));

        int textPrimary = ContextCompat.getColor(requireContext(), R.color.text_primary);
        int textHint = ContextCompat.getColor(requireContext(), R.color.text_hint);

        TextView nameView = new TextView(requireContext());
        nameView.setText(station);
        nameView.setTextSize(isHeader ? 11 : 13);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setTextColor(isHeader ? textHint : textPrimary);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(nameView);

        TextView aimedView = new TextView(requireContext());
        aimedView.setText(aimed);
        aimedView.setTextSize(isHeader ? 11 : 13);
        aimedView.setGravity(android.view.Gravity.END);
        aimedView.setMinWidth(dpToPx(52));
        aimedView.setTextColor(isHeader ? textHint : textPrimary);
        row.addView(aimedView);

        TextView actualView = new TextView(requireContext());
        actualView.setText(actual);
        actualView.setTextSize(isHeader ? 11 : 13);
        actualView.setGravity(android.view.Gravity.END);
        actualView.setMinWidth(dpToPx(64));
        actualView.setTextColor(isHeader ? textHint : actualColor);
        if (!isHeader) actualView.setTypeface(null, Typeface.BOLD);
        row.addView(actualView);

        return row;
    }

    private void showFallbackDialog(TrainSchedule schedule) {
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

        // Statut
        TextView statusView = new TextView(requireContext());
        statusView.setText(schedule.getStatusEmoji() + " " + schedule.getStatusLabel());
        statusView.setTextColor(schedule.getStatusColor());
        statusView.setTextSize(14);
        statusView.setTypeface(null, Typeface.BOLD);
        statusView.setPadding(0, 0, 0, dpToPx(12));
        container.addView(statusView);

        // Position actuelle (mise en avant)
        TextView positionView = new TextView(requireContext());
        positionView.setText(schedule.estimatePosition());
        positionView.setTextColor(0xFF1976D2);
        positionView.setTextSize(15);
        positionView.setTypeface(null, Typeface.BOLD);
        positionView.setPadding(0, 0, 0, dpToPx(4));
        container.addView(positionView);

        // Barre de progression visuelle
        long now = System.currentTimeMillis();
        long effectiveDeparture = schedule.getAimedDepartureMillis();
        if (schedule.getDelayMinutes() > 0) {
            effectiveDeparture += schedule.getDelayMinutes() * 60_000L;
        }
        if (effectiveDeparture > 0 && schedule.getArrivalMillis() > 0 && now >= effectiveDeparture && now < schedule.getArrivalMillis()) {
            long totalTravel = schedule.getArrivalMillis() - effectiveDeparture;
            long elapsed = now - effectiveDeparture;
            int progress = (int) ((elapsed * 100) / totalTravel);
            progress = Math.max(0, Math.min(100, progress));

            android.widget.ProgressBar progressBar = new android.widget.ProgressBar(
                    requireContext(), null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(progress);
            progressBar.setPadding(0, dpToPx(4), 0, dpToPx(12));
            container.addView(progressBar);
        } else {
            // Espacement
            TextView spacer = new TextView(requireContext());
            spacer.setPadding(0, 0, 0, dpToPx(8));
            container.addView(spacer);
        }

        // Trajet : origine → destination
        if (!schedule.getOriginStation().isEmpty()) {
            TextView trajetView = new TextView(requireContext());
            trajetView.setText(schedule.getOriginStation() + "  \u2192  " + schedule.getDestination());
            trajetView.setTextSize(13);
            trajetView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            trajetView.setPadding(0, 0, 0, dpToPx(8));
            container.addView(trajetView);
        }

        // Départ
        StringBuilder depStr = new StringBuilder("Départ : " + schedule.getAimedDepartureTime());
        if (schedule.isDelayed() && !schedule.getExpectedDepartureTime().isEmpty()) {
            depStr.append("  \u2192  ").append(schedule.getExpectedDepartureTime());
        }
        TextView depView = new TextView(requireContext());
        depView.setText(depStr.toString());
        depView.setTextSize(13);
        depView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        container.addView(depView);

        // Arrivée
        if (schedule.getArrivalTime() != null && !schedule.getArrivalTime().isEmpty()) {
            TextView arrView = new TextView(requireContext());
            arrView.setText("Arrivée : " + schedule.getArrivalTime());
            arrView.setTextSize(13);
            arrView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            container.addView(arrView);
        }

        // Temps de trajet
        String travelTime = schedule.getTravelTime();
        if (travelTime != null) {
            TextView travelView = new TextView(requireContext());
            travelView.setText("Trajet : " + travelTime);
            travelView.setTextSize(13);
            travelView.setTextColor(0xFF757575);
            container.addView(travelView);
        }

        // Voie
        if (schedule.getPlatformName() != null && !schedule.getPlatformName().isEmpty()) {
            TextView platformView = new TextView(requireContext());
            platformView.setText("Voie " + schedule.getPlatformName());
            platformView.setTextSize(13);
            platformView.setTextColor(0xFF757575);
            platformView.setPadding(0, dpToPx(4), 0, 0);
            container.addView(platformView);
        }

        String title = schedule.getDestination();
        if (!schedule.getOriginStation().isEmpty()) {
            title = schedule.getOriginStation() + " \u2192 " + schedule.getDestination();
        }

        showTrainDialog(title, scrollView, schedule);
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
        TextView filterButton = dialogView.findViewById(R.id.lineMapFilter);
        TextView closeButton = dialogView.findViewById(R.id.lineMapClose);

        closeButton.setOnClickListener(v -> dialog.dismiss());
        filterButton.setOnClickListener(v -> {
            lineMapView.setShowOtherTrains(!lineMapView.isShowingOtherTrains());
            refreshOpenLineMap();
        });

        // Mes deux gares sont mises en évidence sur le plan : c'est le seul
        // tronçon qui m'intéresse au milieu de toute la ligne N.
        lineMapView.setHighlightedSegment(
                LineNDirection.ALLER.getOriginName(),
                LineNDirection.ALLER.getDestinationName());
        lineMapView.setOnTrainClickListener(this::showTrainOnMapDialog);

        openLineMapView = lineMapView;
        openLineMapCount = trainCountView;
        openLineMapFilter = filterButton;
        refreshOpenLineMap();

        dialog.setOnDismissListener(d -> {
            openLineMapView = null;
            openLineMapCount = null;
            openLineMapFilter = null;
        });

        dialog.show();

        // Forcer le plein écran après show()
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    /**
     * Rejoue les positions sur le plan ouvert. Appelée au tick local (20 s) :
     * sans ça, le plan reste figé à l'instant où on l'a ouvert alors que les
     * cartes de trains, elles, avancent.
     */
    private void refreshOpenLineMap() {
        if (openLineMapView == null) return;
        List<LineMapView.TrainOnMap> trainsOnMap = buildTrainsOnMap();
        openLineMapView.setTrains(trainsOnMap);

        // Le compte sépare les deux populations : « 42 trains » ne dit pas si
        // l'un d'eux passe chez moi, qui est la seule question que je me pose.
        int mine = countOnMyRoute(trainsOnMap);
        int others = trainsOnMap.size() - mine;
        if (openLineMapCount != null) {
            openLineMapCount.setText(openLineMapView.isShowingOtherTrains()
                    ? mine + " chez moi · " + others + " autres"
                    : mine + " chez moi");
        }
        if (openLineMapFilter != null) {
            openLineMapFilter.setText(openLineMapView.isShowingOtherTrains()
                    ? "Mes gares" : "Tous");
        }
    }

    private void showTrainOnMapDialog(LineMapView.TrainOnMap train) {
        if (!isAdded() || getActivity() == null) return;

        StringBuilder titleSb = new StringBuilder();
        if (train.missionName != null && !train.missionName.isEmpty()) {
            titleSb.append(train.missionName);
        }
        if (train.trainNumber != null && !train.trainNumber.isEmpty()) {
            if (titleSb.length() > 0) titleSb.append(" • ");
            titleSb.append(train.trainNumber);
        }
        if (titleSb.length() == 0) titleSb.append("Train");

        StringBuilder body = new StringBuilder();
        if (train.destination != null && !train.destination.isEmpty()) {
            body.append("Direction : ").append(train.destination).append("\n\n");
        }
        if (train.isDelayed()) {
            body.append("⏰ Retard +").append(train.delayMinutes).append(" min\n");
        } else {
            body.append("✅ À l'heure\n");
        }
        if (!train.onMyRoute) {
            body.append("➖ Ne dessert pas mes deux gares\n");
        } else if (train.routeConfirmed) {
            body.append("🎯 Dessert Clamart et Villepreux\n");
        } else {
            // Une de mes gares est déjà derrière lui : l'API ne la décrit plus,
            // donc « dessert » serait affirmer ce qu'on ne sait pas.
            body.append("🎯 Sur mon trajet (desserte non confirmée)\n");
        }
        body.append("\nPosition : ");
        if (train.currentStopName != null && !train.currentStopName.isEmpty()) {
            body.append(train.currentStopName);
        } else {
            body.append("?");
        }
        if (train.nextStopName != null && !train.nextStopName.isEmpty()
                && !train.nextStopName.equals(train.currentStopName)) {
            body.append(" → ").append(train.nextStopName);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(titleSb.toString())
                .setMessage(body.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Les trains à poser sur le plan, à l'instant {@code now}.
     *
     * <p>Deux populations, distinguées par {@link MyTrains} : ceux qui desservent
     * mes deux gares — les seuls que je puisse prendre — et tout le reste du
     * trafic de la ligne, dessiné en retrait. Un train dont une de mes gares est
     * déjà derrière lui reste dans la première : le parcours ne la décrit plus,
     * ce qui ne prouve rien contre lui. La position vient de
     * {@link TrainPosition}, la même que celle des cartes « en circulation » :
     * deux calculs concurrents finissaient par se contredire à l'écran.</p>
     */
    private List<LineMapView.TrainOnMap> buildTrainsOnMap() {
        List<LineMapView.TrainOnMap> result = new ArrayList<>();
        long now = System.currentTimeMillis();

        // Les statuts connus, indexés une fois pour toutes : les chercher dans la
        // boucle reconstruisait la liste complète pour chaque train.
        Map<String, TrainSchedule> knownSchedules = new HashMap<>();
        if (scheduleAdapterAller != null) {
            indexSchedules(knownSchedules, scheduleAdapterAller.getSchedules());
        }
        if (scheduleAdapterRetour != null) {
            indexSchedules(knownSchedules, scheduleAdapterRetour.getSchedules());
        }
        // En dernier : pour un train déjà parti, c'est la carte « en circulation »
        // qui porte le retard réel, pas la liste des départs qu'il a quittée.
        indexSchedules(knownSchedules, ongoingAller);
        indexSchedules(knownSchedules, ongoingRetour);

        for (Map.Entry<String, List<TrainStop>> entry : journeyStopsCache.entrySet()) {
            String journeyRef = entry.getKey();
            List<TrainStop> stops = entry.getValue();
            if (stops == null || stops.isEmpty()) continue;

            // Hors fenêtre d'intérêt : déjà arrivé, ou départ trop lointain.
            long lastTime = stops.get(stops.size() - 1).getBestArrivalMillis();
            long firstTime = stops.get(0).getBestTimeMillis();
            if (lastTime > 0 && now > lastTime + MAP_ARRIVED_GRACE_MS) continue;
            if (firstTime > 0 && firstTime - now > MAP_DEPARTURE_HORIZON_MS) continue;

            TrainSchedule schedule = knownSchedules.get(journeyRef);
            // Un train supprimé ne circule pas : il n'a rien à faire sur le plan.
            if (schedule != null && schedule.isCancelled()) continue;

            List<TrainStop> named = resolveStopNames(stops);
            TrainPosition position = TrainPosition.compute(named, now);
            if (!position.isKnown()) continue;

            String destination = schedule != null && !schedule.getDestination().isEmpty()
                    ? schedule.getDestination()
                    : named.get(named.size() - 1).getStopName();
            int delayMinutes = schedule != null ? schedule.getDelayMinutes() : 0;

            MyTrains.Verdict verdict = MyTrains.verdict(journeyRef, named,
                    LineNDirection.ALLER, seenAtClamart, seenAtVillepreux);

            result.add(new LineMapView.TrainOnMap(
                    journeyRef, destination,
                    position.getCurrentStopName(), position.getNextStopName(),
                    position.getSegmentProgress(), delayMinutes,
                    verdict.isMine(), verdict == MyTrains.Verdict.SERVES,
                    valueOrEmpty(journeyTrainNumberCache.get(journeyRef)),
                    valueOrEmpty(journeyMissionNameCache.get(journeyRef))));
        }

        Log.i(TAG, "buildTrainsOnMap: " + result.size() + " trains sur "
                + journeyStopsCache.size() + " parcours en cache, dont "
                + countOnMyRoute(result) + " desservant mes gares");
        return result;
    }

    private static void indexSchedules(Map<String, TrainSchedule> target,
                                       List<TrainSchedule> schedules) {
        if (schedules == null) return;
        for (TrainSchedule schedule : schedules) {
            String ref = schedule.getJourneyRef();
            if (ref != null && !ref.isEmpty()) target.put(ref, schedule);
        }
    }

    private static int countOnMyRoute(List<LineMapView.TrainOnMap> trains) {
        int count = 0;
        for (LineMapView.TrainOnMap train : trains) {
            if (train.onMyRoute) count++;
        }
        return count;
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Formate un timestamp avec les secondes en plus petit.
     * Ex: "MAJ 14:32:05" avec ":05" en taille réduite (0.7x).
     */
    private static SpannableString formatTimeWithSmallSeconds(String prefix, Date date) {
        String main = prefix + DateFormats.formatHhmm(date);
        String seconds = DateFormats.formatColonSeconds(date);
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

        // Stocke les listes courantes ; l'affichage réel est conditionné par
        // incidentsListVisible (toggle via tap sur le bandeau).
        lastPerturbations.clear();
        if (hasPerturbations) lastPerturbations.addAll(perturbations);
        lastTravaux.clear();
        if (hasTravaux) lastTravaux.addAll(travaux);

        // Si on n'a plus aucun incident, on collapse automatiquement.
        if (lastPerturbations.isEmpty() && lastTravaux.isEmpty()) {
            incidentsListVisible = false;
        }

        updateIncidentsVisibility();
    }

    /** Applique la visibilité des sections perturbations/travaux + chevron en
     *  fonction du flag {@code incidentsListVisible} et de la disponibilité de
     *  données. */
    private void updateIncidentsVisibility() {
        boolean hasAny = !lastPerturbations.isEmpty() || !lastTravaux.isEmpty();

        // Chevron : visible uniquement si on a quelque chose à montrer
        if (lineStatusChevron != null) {
            lineStatusChevron.setVisibility(hasAny ? View.VISIBLE : View.GONE);
            lineStatusChevron.setText(incidentsListVisible ? "▲" : "▼");
        }

        boolean show = hasAny && incidentsListVisible;

        if (show && !lastPerturbations.isEmpty()) {
            perturbationsAdapter.updateIncidents(lastPerturbations);
            perturbationsSection.setVisibility(View.VISIBLE);
        } else {
            perturbationsSection.setVisibility(View.GONE);
        }

        if (show && !lastTravaux.isEmpty()) {
            travauxAdapter.updateIncidents(lastTravaux);
            travauxSection.setVisibility(View.VISIBLE);
        } else {
            travauxSection.setVisibility(View.GONE);
        }
    }

    // ==================== SCHEDULES ====================

    private void fetchSchedules() {
        Settings config = new Settings(requireContext());
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
        // La fenêtre démarre avant "maintenant" : les trains déjà partis (ceux dans
        // lesquels je peux être) et les départs retardés doivent rester visibles.
        Date windowStart = new Date(now.getTime() - SCHEDULE_LOOKBACK_MS);
        Date windowEnd = new Date(now.getTime() + SCHEDULE_WINDOW_MS);
        Log.i(TAG, "fetchSchedules: fenêtre horaire = "
                + DateFormats.formatHhmmss(windowStart) + " → "
                + DateFormats.formatHhmmss(windowEnd));

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                scheduleTitleAller.setText("Clamart \u2192 Villepreux");
                scheduleTitleRetour.setText("Villepreux \u2192 Clamart");
            });
        }

        executor.execute(() -> {
            Log.d(TAG, "fetchSchedules: requête Clamart, stop=" + LineNDirection.ALLER.getOriginStopRef());
            Map<String, StopVisit> clamartData = fetchAndParseRaw(
                    token, LineNDirection.ALLER.getOriginStopRef(), LineNDirection.ALLER.getOriginName());

            Log.d(TAG, "fetchSchedules: requête Villepreux, stop=" + LineNDirection.ALLER.getDestinationStopRef());
            Map<String, StopVisit> villepreuxData = fetchAndParseRaw(
                    token, LineNDirection.ALLER.getDestinationStopRef(), LineNDirection.ALLER.getDestinationName());

            List<TrainSchedule> allerSchedules = buildCrossReferencedSchedules(
                    clamartData, villepreuxData,
                    LineNDirection.ALLER, windowStart, windowEnd);

            List<TrainSchedule> retourSchedules = buildCrossReferencedSchedules(
                    villepreuxData, clamartData,
                    LineNDirection.RETOUR, windowStart, windowEnd);

            // Trains actuellement en circulation sur mon trajet : reconstruits depuis
            // la gare d'arrivée, car un train déjà parti a disparu du stop-monitoring
            // de la gare de départ.
            long nowMillis = System.currentTimeMillis();
            lastClamartData = clamartData;
            lastVillepreuxData = villepreuxData;
            OngoingTrains.rememberOriginVisits(seenAtClamart, clamartData, nowMillis);
            OngoingTrains.rememberOriginVisits(seenAtVillepreux, villepreuxData, nowMillis);

            List<TrainSchedule> ongoingAllerNow = OngoingTrains.buildOngoing(
                    seenAtClamart, villepreuxData, journeyStopsCache,
                    journeyTrainNumberCache, journeyMissionNameCache,
                    LineNDirection.ALLER, nowMillis);
            List<TrainSchedule> ongoingRetourNow = OngoingTrains.buildOngoing(
                    seenAtVillepreux, clamartData, journeyStopsCache,
                    journeyTrainNumberCache, journeyMissionNameCache,
                    LineNDirection.RETOUR, nowMillis);

            Log.i(TAG, "enCirculation: visites Clamart=" + sizeOf(clamartData)
                    + " Villepreux=" + sizeOf(villepreuxData)
                    + ", mémoire Clamart=" + seenAtClamart.size()
                    + " Villepreux=" + seenAtVillepreux.size()
                    + ", parcours en cache=" + journeyStopsCache.size()
                    + " → aller=" + ongoingAllerNow.size()
                    + " retour=" + ongoingRetourNow.size());

            Log.i(TAG, "fetchSchedules: résultats Aller=" + (allerSchedules != null ? allerSchedules.size() : "null")
                    + ", Retour=" + (retourSchedules != null ? retourSchedules.size() : "null"));

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    SpannableString updateTime = formatTimeWithSmallSeconds("MAJ ", new Date());
                    scheduleLastUpdateAller.setText(updateTime);
                    scheduleLastUpdateRetour.setText(updateTime);

                    long uiNow = System.currentTimeMillis();
                    updateScheduleUI(allerSchedules == null ? null
                                    : OngoingTrains.selectUpcoming(allerSchedules, uiNow),
                            departuresTitleAller, scheduleEmptyAller,
                            scheduleRecyclerViewAller, scheduleAdapterAller);
                    updateScheduleUI(retourSchedules == null ? null
                                    : OngoingTrains.selectUpcoming(retourSchedules, uiNow),
                            departuresTitleRetour, scheduleEmptyRetour,
                            scheduleRecyclerViewRetour, scheduleAdapterRetour);

                    if (allerSchedules != null) {
                        lastAllerSchedules.clear();
                        lastAllerSchedules.addAll(allerSchedules);
                    }
                    if (retourSchedules != null) {
                        lastRetourSchedules.clear();
                        lastRetourSchedules.addAll(retourSchedules);
                    }

                    applyOngoing(ongoingAllerNow, ongoingRetourNow);
                });
            }
        });
    }

    /**
     * Rejoue la construction des trains en circulation depuis les derniers passages
     * connus, sans nouvel appel de stop-monitoring. Appelée après chaque
     * estimated-timetable : au premier chargement, les horaires arrivent avant les
     * parcours, donc l'heure de départ des trains déjà partis est encore inconnue au
     * moment du fetch. À exécuter sur le thread de l'executor.
     */
    private void rebuildOngoingFromLastFetch() {
        if (lastClamartData == null && lastVillepreuxData == null) return;
        long now = System.currentTimeMillis();
        List<TrainSchedule> aller = OngoingTrains.buildOngoing(
                seenAtClamart, lastVillepreuxData, journeyStopsCache,
                journeyTrainNumberCache, journeyMissionNameCache,
                LineNDirection.ALLER, now);
        List<TrainSchedule> retour = OngoingTrains.buildOngoing(
                seenAtVillepreux, lastClamartData, journeyStopsCache,
                journeyTrainNumberCache, journeyMissionNameCache,
                LineNDirection.RETOUR, now);
        Log.i(TAG, "enCirculation (après parcours): parcours en cache="
                + journeyStopsCache.size() + " → aller=" + aller.size()
                + " retour=" + retour.size());
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            applyOngoing(aller, retour);
        });
    }

    /** Remplace les trains en circulation connus et redessine la section (thread UI). */
    private void applyOngoing(List<TrainSchedule> aller, List<TrainSchedule> retour) {
        long now = System.currentTimeMillis();
        ongoingAller.clear();
        ongoingAller.addAll(aller);
        // Filet de sécurité : un train encore annoncé au départ mais dont l'heure
        // est passée est aussi un train "en cours".
        mergeOngoing(ongoingAller, lastAllerSchedules, now);

        ongoingRetour.clear();
        ongoingRetour.addAll(retour);
        mergeOngoing(ongoingRetour, lastRetourSchedules, now);

        updateOngoingSection();
    }

    private static int sizeOf(Map<String, StopVisit> visits) {
        return visits != null ? visits.size() : -1;
    }

    private Map<String, StopVisit> fetchAndParseRaw(String token, String stopRef, String stationLabel) {
        int maxRetries = 2;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    Log.i(TAG, "fetchAndParseRaw [" + stationLabel + "]: tentative " + (attempt + 1));
                    Thread.sleep(1000L * attempt);
                }

                String body = idfmClient.fetchStopMonitoring(token, stopRef);
                Log.d(TAG, "fetchAndParseRaw [" + stationLabel + "]: réponse reçue, taille="
                        + body.length() + " chars");
                return parseRawStopVisits(body, stationLabel);

            } catch (IdfmClient.HttpException e) {
                Log.e(TAG, "fetchAndParseRaw [" + stationLabel + "]: ERREUR HTTP " + e.code
                        + " body=" + e.body);
                if (e.isServerError() && attempt < maxRetries) continue;
                break;
            } catch (Exception e) {
                Log.e(TAG, "fetchAndParseRaw [" + stationLabel + "]: exception (tentative "
                        + (attempt + 1) + ")", e);
                if (attempt < maxRetries) continue;
                break;
            }
        }
        return null;
    }

    private Map<String, StopVisit> parseRawStopVisits(String jsonStr, String stationLabel) {
        Map<String, StopVisit> visits = new HashMap<>();
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

                    StopVisit raw = new StopVisit();
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
                    String expectedArrStr = call.optString("ExpectedArrivalTime", "");

                    if (!aimedDepStr.isEmpty()) {
                        raw.aimedDeparture = DateFormats.parseIsoDateTime(aimedDepStr);
                    }
                    if (!expectedDepStr.isEmpty()) {
                        raw.expectedDeparture = DateFormats.parseIsoDateTime(expectedDepStr);
                    }
                    if (!aimedArrStr.isEmpty()) {
                        raw.aimedArrival = DateFormats.parseIsoDateTime(aimedArrStr);
                    }
                    if (!expectedArrStr.isEmpty()) {
                        raw.expectedArrival = DateFormats.parseIsoDateTime(expectedArrStr);
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
            Map<String, StopVisit> originData,
            Map<String, StopVisit> destinationData,
            LineNDirection direction,
            Date windowStart, Date windowEnd) {

        List<TrainSchedule> schedules = new ArrayList<>();

        if (originData == null || originData.isEmpty()) {
            Log.e(TAG, "buildCrossReferenced [" + direction.getLabel() + "]: originData est null ou vide");
            return null;
        }

        int filteredByDest = 0;
        int filteredByTime = 0;
        int noArrivalVisit = 0;
        int noAimedTime = 0;

        for (Map.Entry<String, StopVisit> entry : originData.entrySet()) {
            String journeyRef = entry.getKey();
            StopVisit origin = entry.getValue();

            if (!direction.matchesDestination(origin.destination)) {
                filteredByDest++;
                continue;
            }

            if (origin.aimedDeparture == null) {
                noAimedTime++;
                continue;
            }

            if (origin.aimedDeparture.before(windowStart) || origin.aimedDeparture.after(windowEnd)) {
                filteredByTime++;
                continue;
            }

            // Le passage à ma gare d'arrivée n'est pas toujours publié : le train
            // est alors gardé, mais sans heure d'arrivée (mieux vaut un horaire
            // incomplet qu'un train manquant).
            StopVisit destination = destinationData != null
                    ? destinationData.get(journeyRef) : null;
            if (destinationData != null && destination == null) noArrivalVisit++;

            long arrivalMillis = 0;
            if (destination != null) {
                if (destination.aimedArrival != null) {
                    arrivalMillis = destination.aimedArrival.getTime();
                } else if (destination.aimedDeparture != null) {
                    arrivalMillis = destination.aimedDeparture.getTime();
                }
            }
            String arrivalTimeStr = arrivalMillis > 0
                    ? DateFormats.formatHhmm(new Date(arrivalMillis)) : "";

            int delayMinutes = 0;
            String expectedTimeStr = "";
            if (origin.expectedDeparture != null) {
                long diffMs = origin.expectedDeparture.getTime() - origin.aimedDeparture.getTime();
                delayMinutes = (int) (diffMs / 60000);
                if (delayMinutes < 0) delayMinutes = 0;
                if (delayMinutes > 0) {
                    expectedTimeStr = DateFormats.formatHhmm(origin.expectedDeparture);
                }
            }

            String aimedTimeStr = DateFormats.formatHhmm(origin.aimedDeparture);

            Log.d(TAG, "buildCrossReferenced [" + direction.getLabel() + "]: GARDÉ "
                    + journeyRef
                    + " départ=" + aimedTimeStr
                    + " arrivée=" + arrivalTimeStr
                    + " dest=" + origin.destination
                    + " status=" + origin.departureStatus
                    + " retard=" + delayMinutes + "min"
                    + " voie=" + origin.platform);

            String originStation = direction.getOriginName();

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
                    origin.expectedDeparture != null
                            ? origin.expectedDeparture.getTime() : 0,
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
                Long.compare(a.getAimedDepartureMillis(), b.getAimedDepartureMillis()));

        Log.i(TAG, "buildCrossReferenced [" + direction.getLabel() + "]: RÉSUMÉ"
                + " total_origine=" + originData.size()
                + " gardés=" + schedules.size()
                + " filtrés(destination)=" + filteredByDest
                + " filtrés(horsFenêtre)=" + filteredByTime
                + " sansPassageArrivée=" + noArrivalVisit
                + " sansAimed=" + noAimedTime);

        return schedules;
    }

    /**
     * Ajoute à {@code ongoing} les trajets de {@code schedules} qui sont partis mais
     * pas encore arrivés et qui n'y figurent pas déjà (même journeyRef).
     */
    private void mergeOngoing(List<TrainSchedule> ongoing, List<TrainSchedule> schedules, long now) {
        if (schedules == null) return;
        for (TrainSchedule candidate : OngoingTrains.selectOngoing(schedules, now)) {
            boolean known = false;
            for (TrainSchedule existing : ongoing) {
                if (existing.getJourneyRef().equals(candidate.getJourneyRef())) {
                    known = true;
                    break;
                }
            }
            if (!known) ongoing.add(candidate);
        }
    }

    /**
     * Redessine les deux cartes de sens à partir des trajets déjà connus :
     * recalcule la position de chaque train à l'instant T et retire ceux qui sont
     * arrivés. Aucun appel réseau — appelée aussi par le tick local.
     */
    private void updateOngoingSection() {
        if (!isAdded() || ongoingRecyclerViewAller == null) return;

        long now = System.currentTimeMillis();

        // Un train dont l'heure de départ vient de passer quitte la liste des
        // prochains départs et rejoint les trains en circulation, dans sa carte.
        repartitionDepartures(lastAllerSchedules, ongoingAller, scheduleAdapterAller,
                departuresTitleAller, scheduleEmptyAller, scheduleRecyclerViewAller, now);
        repartitionDepartures(lastRetourSchedules, ongoingRetour, scheduleAdapterRetour,
                departuresTitleRetour, scheduleEmptyRetour, scheduleRecyclerViewRetour, now);

        renderOngoing(ongoingAller, LineNDirection.ALLER, ongoingAdapterAller,
                ongoingTitleAller, ongoingLastUpdateAller, ongoingRecyclerViewAller,
                ongoingEmptyAller, now);
        renderOngoing(ongoingRetour, LineNDirection.RETOUR, ongoingAdapterRetour,
                ongoingTitleRetour, ongoingLastUpdateRetour, ongoingRecyclerViewRetour,
                ongoingEmptyRetour, now);

        // Les trains suivis se lisent dans les mêmes listes, une fois celles-ci
        // rafraîchies : la carte des suivis vient donc après, pas avant.
        updatePinnedSection(now);

        // Le plan de la ligne ouvert suit le même tick : sinon il fige les trains
        // à l'instant où on l'a ouvert.
        refreshOpenLineMap();
    }

    /**
     * Redessine la carte des trains suivis.
     *
     * <p>Un train épinglé peut être encore à quai ou déjà en route : les deux se
     * décrivent avec la même carte que la section « En circulation » (position à
     * l'instant T, prochain arrêt, arrivée estimée), donc on n'en fait pas deux
     * présentations. {@link OngoingTrains#describe} sait dire « pas encore parti »
     * aussi bien que « entre Meudon et Chaville ».</p>
     *
     * <p>Un train suivi est cherché d'abord parmi les trains en circulation, puis
     * parmi les prochains départs : la première liste porte la position réelle, la
     * seconde n'a que les horaires. Trouvé dans l'une, il n'est pas repris dans
     * l'autre.</p>
     */
    private void updatePinnedSection(long now) {
        if (pinnedCard == null) return;

        pinnedTrains.purge(now);
        if (pinnedTrains.isEmpty()) {
            pinnedCard.setVisibility(View.GONE);
            pinnedAdapter.updateTrains(Collections.<OngoingTrain>emptyList());
            return;
        }

        List<OngoingTrain> display = new ArrayList<>();
        Set<String> already = new HashSet<>();
        collectPinned(display, already, ongoingAller, LineNDirection.ALLER, now);
        collectPinned(display, already, ongoingRetour, LineNDirection.RETOUR, now);
        collectPinned(display, already, lastAllerSchedules, LineNDirection.ALLER, now);
        collectPinned(display, already, lastRetourSchedules, LineNDirection.RETOUR, now);

        // Même ordre qu'ailleurs : le prochain à arriver chez moi en tête.
        Collections.sort(display, (a, b) -> Long.compare(
                OngoingTrains.effectiveArrivalMillis(a.getSchedule()),
                OngoingTrains.effectiveArrivalMillis(b.getSchedule())));

        pinnedCard.setVisibility(View.VISIBLE);
        pinnedTitle.setText(display.isEmpty()
                ? "📌 Trains suivis"
                : "📌 Trains suivis · " + display.size());
        pinnedLastUpdate.setText(formatTimeWithSmallSeconds("à ", new Date(now)));
        pinnedRecyclerView.setVisibility(display.isEmpty() ? View.GONE : View.VISIBLE);
        // La carte reste visible même sans train à montrer : un suivi qui
        // s'évapore en silence ferait croire à un oubli de l'app.
        pinnedEmpty.setVisibility(display.isEmpty() ? View.VISIBLE : View.GONE);
        pinnedAdapter.updateTrains(display);
    }

    /** Ajoute les trains suivis de {@code source} qui ne sont pas déjà affichés. */
    private void collectPinned(List<OngoingTrain> target, Set<String> already,
                               List<TrainSchedule> source, LineNDirection direction, long now) {
        if (source == null) return;
        for (TrainSchedule schedule : source) {
            String journeyRef = schedule.getJourneyRef();
            if (!pinnedTrains.isPinned(journeyRef) || !already.add(journeyRef)) continue;
            List<TrainStop> stops = resolveStopNames(journeyStopsCache.get(journeyRef));
            target.add(OngoingTrains.describe(schedule, direction, stops, now));
        }
    }

    /**
     * Épingle / désépingle un train, persiste le choix et redessine.
     *
     * <p>Les listes de départs sont redessinées à la main : elles ne se
     * rafraîchissent d'elles-mêmes que lorsque leur contenu change, or ici seule
     * l'épingle a changé.</p>
     */
    private void togglePin(TrainSchedule schedule) {
        if (schedule == null || !isAdded()) return;
        pinnedTrains.toggle(schedule.getJourneyRef(), System.currentTimeMillis());
        new Settings(requireContext()).savePinnedTrains(pinnedTrains.encode());
        scheduleAdapterAller.notifyDataSetChanged();
        scheduleAdapterRetour.notifyDataSetChanged();
        updateOngoingSection();
    }

    /**
     * Sens de circulation d'un trajet, lu sur sa gare de départ — celle-ci vient
     * toujours de {@link LineNDirection#getOriginName()}, quelle que soit la
     * source du trajet.
     */
    private static LineNDirection directionOf(TrainSchedule schedule) {
        String origin = schedule != null ? schedule.getOriginStation() : null;
        String normalized = LineNStation.normalize(origin != null ? origin : "");
        String retourOrigin = LineNStation.normalize(LineNDirection.RETOUR.getOriginName());
        return normalized.equals(retourOrigin) ? LineNDirection.RETOUR : LineNDirection.ALLER;
    }

    /**
     * Met en forme les trains en circulation d'un seul sens et les pose dans sa
     * carte. La liste vide reste affichée (message explicite) : sans elle,
     * impossible de distinguer « aucun train dans ce sens » d'un affichage en panne.
     */
    private void renderOngoing(List<TrainSchedule> source, LineNDirection direction,
                               TrainOngoingAdapter adapter, TextView titleView,
                               TextView lastUpdateView, RecyclerView recycler,
                               TextView emptyView, long now) {
        List<OngoingTrain> display = new ArrayList<>();
        collectOngoing(display, source, direction, now);

        // Le prochain à arriver chez moi en tête : c'est l'ordre utile pour choisir
        // un train, et il reste défini quand l'heure de départ est inconnue.
        Collections.sort(display, (a, b) -> Long.compare(
                OngoingTrains.effectiveArrivalMillis(a.getSchedule()),
                OngoingTrains.effectiveArrivalMillis(b.getSchedule())));

        titleView.setText(display.isEmpty()
                ? "🚆 En circulation"
                : "🚆 En circulation · " + display.size());
        lastUpdateView.setText(formatTimeWithSmallSeconds("à ", new Date(now)));
        recycler.setVisibility(display.isEmpty() ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(display.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.updateTrains(display);
    }

    /**
     * Rejoue le partage "à venir" / "en cours" sur les derniers horaires connus,
     * sans appel réseau. La liste des départs n'est redessinée que si elle a
     * réellement changé, pour ne pas la rafraîchir toutes les 20 s — comparée sur
     * les trains eux-mêmes et non sur leur nombre : un train qui part pendant
     * qu'un autre apparaît laisse le compte identique.
     */
    private void repartitionDepartures(List<TrainSchedule> source, List<TrainSchedule> ongoing,
                                       TrainScheduleAdapter adapter, TextView titleView,
                                       TextView emptyView, RecyclerView recycler, long now) {
        if (source.isEmpty()) return;
        mergeOngoing(ongoing, source, now);
        List<TrainSchedule> upcoming = OngoingTrains.selectUpcoming(source, now);
        if (!sameJourneys(upcoming, adapter.getSchedules())) {
            updateScheduleUI(upcoming, titleView, emptyView, recycler, adapter);
        }
    }

    /** Deux listes de trajets décrivent-elles les mêmes trains, dans le même ordre ? */
    private static boolean sameJourneys(List<TrainSchedule> a, List<TrainSchedule> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getJourneyRef().equals(b.get(i).getJourneyRef())) return false;
        }
        return true;
    }

    /** Retire de {@code source} les trains qui ont quitté mon segment, met en forme les autres. */
    private void collectOngoing(List<OngoingTrain> target, List<TrainSchedule> source,
                                LineNDirection direction, long now) {
        java.util.Iterator<TrainSchedule> it = source.iterator();
        while (it.hasNext()) {
            TrainSchedule schedule = it.next();
            List<TrainStop> stops = journeyStopsCache.get(schedule.getJourneyRef());
            // Même règle qu'à la construction : la position réelle prime, les
            // horaires ne tranchent que faute de parcours.
            OngoingTrains.Placement placement = OngoingTrains.locate(stops, direction, now);
            boolean stillHere = placement == OngoingTrains.Placement.ON_SEGMENT
                    || (placement == OngoingTrains.Placement.UNKNOWN
                        && OngoingTrains.isOngoing(schedule, now));
            if (!stillHere) {
                it.remove();
                continue;
            }
            target.add(OngoingTrains.describe(schedule, direction, resolveStopNames(stops), now));
        }
    }

    /** Remplace les "Arrêt 43219" par le vrai nom de gare quand le cache le permet. */
    private List<TrainStop> resolveStopNames(List<TrainStop> stops) {
        if (stops == null) return null;
        List<TrainStop> resolved = new ArrayList<>(stops.size());
        for (TrainStop stop : stops) {
            String name = resolveStopName(stop.getStopName());
            if (name != null && name.equals(stop.getStopName())) {
                resolved.add(stop);
            } else {
                resolved.add(new TrainStop(name, stop.getStopRef(),
                        stop.getAimedArrivalMillis(), stop.getExpectedArrivalMillis(),
                        stop.getAimedDepartureMillis(), stop.getExpectedDepartureMillis(),
                        stop.getPlatformName(), stop.isDeparture(), stop.isArrival()));
            }
        }
        return resolved;
    }

    private void updateScheduleUI(List<TrainSchedule> schedules, TextView titleView,
                                  TextView emptyView, RecyclerView recycler,
                                  TrainScheduleAdapter scheduleAdapter) {
        int count = schedules != null ? schedules.size() : 0;
        if (titleView != null) {
            titleView.setText(count == 0
                    ? "🕑 Prochains départs"
                    : "🕑 Prochains départs · " + count);
        }
        if (schedules == null) {
            scheduleAdapter.updateSchedules(Collections.<TrainSchedule>emptyList());
            showMessage(emptyView, recycler, "Erreur de chargement des horaires.");
        } else if (schedules.isEmpty()) {
            scheduleAdapter.updateSchedules(schedules);
            showMessage(emptyView, recycler, "Aucun train prévu\ndans les 2 prochaines heures.");
        } else {
            emptyView.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            scheduleAdapter.updateSchedules(schedules);
        }
    }


    // ==================== STOP POINTS DISCOVERY (noms des arrêts) ====================

    private void fetchStopPointNames() {
        if (!stopPointNameCache.isEmpty()) return; // Déjà chargé
        Settings config = new Settings(requireContext());
        if (!config.hasIdfmToken()) return;

        String token = config.getIdfmToken();
        executor.execute(() -> fetchAndParseStopPointNames(token));
    }

    private void fetchAndParseStopPointNames(String token) {
        try {
            String body = idfmClient.fetchStopPointsDiscovery(token);
            parseStopPointNames(body);
        } catch (IdfmClient.HttpException e) {
            Log.e(TAG, "fetchStopPointNames: HTTP " + e.code, e);
        } catch (Exception e) {
            Log.e(TAG, "fetchStopPointNames: exception", e);
        }
    }

    private void parseStopPointNames(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray stopPoints = root.optJSONArray("stop_points");
            if (stopPoints == null) {
                Log.w(TAG, "parseStopPointNames: pas de stop_points dans la réponse Navitia");
                return;
            }

            int count = 0;
            for (int i = 0; i < stopPoints.length(); i++) {
                JSONObject entry = stopPoints.getJSONObject(i);
                String stopId = entry.optString("id", "");
                String stopName = entry.optString("name", "");

                if (!stopId.isEmpty() && !stopName.isEmpty()) {
                    String numericId = extractNumericId(stopId);
                    if (!numericId.isEmpty()) {
                        stopPointNameCache.put(numericId, stopName);
                        count++;
                    }
                }
            }
            Log.i(TAG, "parseStopPointNames: " + count + " arrêts chargés dans le cache (Navitia)");

        } catch (Exception e) {
            Log.e(TAG, "parseStopPointNames: exception JSON", e);
        }
    }

    /**
     * Résout un nom d'arrêt "Arrêt XXXXX" en nom réel depuis le cache.
     * Si le nom n'est pas un ID ou si le cache ne contient pas l'ID, retourne le nom original.
     */
    private String resolveStopName(String name) {
        if (name == null || !name.startsWith("Arrêt ")) return name;
        String numericId = name.substring(6).trim(); // "Arrêt 43219" → "43219"
        String cached = stopPointNameCache.get(numericId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
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

    // ==================== ESTIMATED TIMETABLE (tous les arrêts) ====================

    private void fetchEstimatedTimetable() {
        Settings config = new Settings(requireContext());
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
        try {
            String body = idfmClient.fetchEstimatedTimetable(token);
            Log.d(TAG, "fetchEstimatedTimetable: réponse reçue, taille=" + body.length());
            parseEstimatedTimetable(body);
            // Les parcours viennent d'arriver : les trains déjà partis peuvent enfin
            // être reconnus (leur heure de départ y est).
            rebuildOngoingFromLastFetch();
        } catch (IdfmClient.HttpException e) {
            Log.e(TAG, "fetchEstimatedTimetable: ERREUR HTTP " + e.code, e);
        } catch (Exception e) {
            Log.e(TAG, "fetchEstimatedTimetable: exception", e);
        }
    }

    private void parseEstimatedTimetable(String jsonStr) {
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
                                            isFirst, false);
                                    if (stop != null) recorded.add(stop);
                                }
                                recorded.addAll(stops);
                                stops = recorded;
                            }
                        }

                        if (!stops.isEmpty()) {
                            // Fusion, jamais écrasement : IDFM ne renvoie que ce qu'il
                            // reste à parcourir, donc écraser effacerait ma gare de
                            // départ dès que le train l'a dépassée (cf. JourneyRoutes).
                            List<TrainStop> route = JourneyRoutes.merge(
                                    journeyStopsCache.get(journeyRef), stops);
                            journeyStopsCache.put(journeyRef, route);
                            totalJourneys++;
                            totalStops += route.size();

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

            JourneyRoutes.purge(journeyStopsCache, System.currentTimeMillis(),
                    JourneyRoutes.MAX_AGE_MS);
            // Les numéros et missions suivent le sort des parcours : indexés par
            // journeyRef comme eux, ils grossissaient sans fin tant que le fragment
            // vivait (un journeyRef par train et par jour).
            journeyTrainNumberCache.keySet().retainAll(journeyStopsCache.keySet());
            journeyMissionNameCache.keySet().retainAll(journeyStopsCache.keySet());

            Log.i(TAG, "parseEstimatedTimetable: " + totalJourneys + " trajets, "
                    + totalStops + " arrêts au total, "
                    + journeyStopsCache.size() + " parcours mémorisés");

        } catch (Exception e) {
            Log.e(TAG, "parseEstimatedTimetable: exception JSON", e);
        }
    }

    private TrainStop parseEstimatedCall(JSONObject call, boolean isFirst, boolean isLast) {
        try {
            // Référence de l'arrêt : c'est elle qui identifie l'arrêt d'un
            // rafraîchissement à l'autre, le nom pouvant rester non résolu.
            String stopRef;
            JSONObject stopRefObj = call.optJSONObject("StopPointRef");
            if (stopRefObj != null) {
                stopRef = stopRefObj.optString("value", "");
            } else {
                stopRef = call.optString("StopPointRef", "");
            }

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
            if (stopName.isEmpty() && !stopRef.isEmpty()) {
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
            if (stopName.isEmpty()) return null;

            // Horaires
            long aimedArr = DateFormats.parseIsoToMillis(call.optString("AimedArrivalTime", ""));
            long expectedArr = DateFormats.parseIsoToMillis(call.optString("ExpectedArrivalTime", ""));
            long aimedDep = DateFormats.parseIsoToMillis(call.optString("AimedDepartureTime", ""));
            long expectedDep = DateFormats.parseIsoToMillis(call.optString("ExpectedDepartureTime", ""));

            // Voie
            String platform = "";
            JSONObject platformObj = call.optJSONObject("ArrivalPlatformName");
            if (platformObj != null) platform = platformObj.optString("value", "");
            if (platform.isEmpty()) {
                JSONObject depPlatformObj = call.optJSONObject("DeparturePlatformName");
                if (depPlatformObj != null) platform = depPlatformObj.optString("value", "");
            }

            return new TrainStop(stopName, stopRef, aimedArr, expectedArr, aimedDep, expectedDep,
                    platform, isFirst, isLast);
        } catch (Exception e) {
            Log.e(TAG, "parseEstimatedCall: erreur", e);
            return null;
        }
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
        Settings config = new Settings(requireContext());
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
            List<TrainIncident> generalMessageIncidents = fetchIncidentsFromApi(token);
            List<TrainIncident> lineReportIncidents = fetchLineReportsFromApi(token);

            // Fusionner les deux sources
            List<TrainIncident> allIncidents = new ArrayList<>();
            if (lineReportIncidents != null) {
                allIncidents.addAll(lineReportIncidents);
            }
            if (generalMessageIncidents != null) {
                // Ajouter les messages généraux qui ne font pas doublon
                for (TrainIncident gm : generalMessageIncidents) {
                    boolean duplicate = false;
                    String gmTextLower = gm.getMessage().toLowerCase(Locale.FRENCH);
                    for (TrainIncident lr : allIncidents) {
                        String lrTextLower = lr.getMessage().toLowerCase(Locale.FRENCH);
                        // Doublon si le texte de l'un contient celui de l'autre
                        if (gmTextLower.contains(lrTextLower) || lrTextLower.contains(gmTextLower)) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        allIncidents.add(gm);
                    }
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    lineStatusUpdate.setText(formatTimeWithSmallSeconds("MAJ ", new Date()));

                    if (allIncidents.isEmpty() && generalMessageIncidents == null && lineReportIncidents == null) {
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

                        Log.i(TAG, "incidents counts: total=" + allIncidents.size()
                                + " perturbations=" + perturbations.size()
                                + " travaux=" + travaux.size());

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

    private List<TrainIncident> fetchLineReportsFromApi(String token) {
        try {
            String body = idfmClient.fetchLineReports(token);
            List<TrainIncident> result = parseLineReportsResponse(body);
            Log.i(TAG, "fetchLineReportsFromApi: parsed " + result.size() + " disruptions");
            return result;
        } catch (IdfmClient.HttpException e) {
            Log.w(TAG, "fetchLineReportsFromApi: HTTP error " + e.code);
        } catch (Exception e) {
            Log.e(TAG, "fetchLineReportsFromApi: network error", e);
        }
        return null;
    }

    private List<TrainIncident> parseLineReportsResponse(String jsonStr) {
        List<TrainIncident> incidents = new ArrayList<>();
        Map<String, Integer> severityDistribution = new java.util.TreeMap<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray disruptions = root.optJSONArray("disruptions");
            if (disruptions == null) return incidents;

            for (int i = 0; i < disruptions.length(); i++) {
                JSONObject disruption = disruptions.getJSONObject(i);

                // Statut de la disruption (past = terminée, on ignore)
                String disruptionStatus = disruption.optString("status", "");
                if ("past".equalsIgnoreCase(disruptionStatus)) continue;

                // Sévérité
                JSONObject severityObj = disruption.optJSONObject("severity");
                String severityEffect = "";
                String severityName = "";
                int severityPriority = 99;
                if (severityObj != null) {
                    severityEffect = severityObj.optString("effect", "");
                    severityName = severityObj.optString("name", "");
                    severityPriority = severityObj.optInt("priority", 99);
                }

                // Cause
                String cause = disruption.optString("cause", "");

                // Périodes d'application
                String validFrom = "";
                String validUntil = "";
                JSONArray periods = disruption.optJSONArray("application_periods");
                if (periods != null && periods.length() > 0) {
                    JSONObject period = periods.getJSONObject(0);
                    validFrom = formatNavitiaDateTime(period.optString("begin", ""));
                    validUntil = formatNavitiaDateTime(period.optString("end", ""));
                }

                // Messages
                String text = "";
                JSONArray messages = disruption.optJSONArray("messages");
                if (messages != null) {
                    for (int j = 0; j < messages.length(); j++) {
                        JSONObject msgObj = messages.getJSONObject(j);
                        String msgText = msgObj.optString("text", "");
                        String channel = "";
                        JSONObject channelObj = msgObj.optJSONObject("channel");
                        if (channelObj != null) {
                            channel = channelObj.optString("name", "");
                        }
                        // Préférer le message du canal "titre" ou le plus long
                        if (text.isEmpty() || msgText.length() > text.length()) {
                            text = msgText;
                        }
                    }
                }

                if (text.isEmpty()) {
                    text = cause.isEmpty() ? severityName : cause;
                }
                if (text.isEmpty()) continue;

                // Tags : Navitia met "Ascenseur" pour signaler une panne d'ascenseur
                // (vs perturbation trafic). C'est le seul vrai discriminant côté API
                // car severity.effect/priority est uniforme ("SIGNIFICANT_DELAYS"/30)
                // pour toutes les disruptions IDFM Ligne N.
                List<String> tags = new ArrayList<>();
                JSONArray tagsArr = disruption.optJSONArray("tags");
                if (tagsArr != null) {
                    for (int t = 0; t < tagsArr.length(); t++) {
                        tags.add(tagsArr.optString(t, ""));
                    }
                }
                boolean hasAscenseurTag = tags.contains("Ascenseur");

                // Distribution agrégée pour debug : (cause, tags)
                String distKey = "cause=" + (cause.isEmpty() ? "-" : cause)
                        + " tags=" + (tags.isEmpty() ? "-" : tags.toString());
                severityDistribution.merge(distKey, 1, Integer::sum);

                // Classification basée sur cause + tags (le plus discriminant) :
                //   - tag "Ascenseur"   → information ascenseur
                //   - cause "travaux"   → travaux planifiés
                //   - cause "perturbation" sans Ascenseur → vraie perturbation trafic
                //   - sinon              → information générique
                String severity;
                String type;
                if (hasAscenseurTag) {
                    severity = "ascenseur";
                    type = TrainIncident.TYPE_INFORMATION;
                } else if ("travaux".equalsIgnoreCase(cause)
                        || IncidentClassifier.hasTravauxKeyword(text)) {
                    severity = "information";
                    type = TrainIncident.TYPE_TRAVAUX;
                } else if ("perturbation".equalsIgnoreCase(cause)) {
                    severity = "NO_SERVICE".equalsIgnoreCase(severityEffect) ? "blocking" : "delays";
                    type = TrainIncident.TYPE_PERTURBATION;
                } else if (IncidentClassifier.hasPerturbationKeyword(text)) {
                    severity = "delays";
                    type = TrainIncident.TYPE_PERTURBATION;
                } else {
                    severity = "information";
                    type = TrainIncident.TYPE_INFORMATION;
                }

                String title;
                if (TrainIncident.TYPE_PERTURBATION.equals(type)) {
                    title = "Perturbation Ligne N";
                } else if (TrainIncident.TYPE_TRAVAUX.equals(type)) {
                    title = "Travaux Ligne N";
                } else if ("ascenseur".equals(severity)) {
                    title = "Ascenseur Ligne N";
                } else {
                    title = "Info Ligne N";
                }

                incidents.add(new TrainIncident(
                        title, text, severity, cause, validFrom, validUntil, type
                ));
            }

            if (!severityDistribution.isEmpty()) {
                Log.i(TAG, "parseLineReportsResponse severity distribution:");
                for (Map.Entry<String, Integer> e : severityDistribution.entrySet()) {
                    Log.i(TAG, "  [" + e.getValue() + "] " + e.getKey());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseLineReportsResponse: error parsing JSON", e);
            Log.d(TAG, "parseLineReportsResponse: raw (first 500): "
                    + jsonStr.substring(0, Math.min(500, jsonStr.length())));
        }
        return incidents;
    }

    private static String formatNavitiaDateTime(String navitiaDate) {
        if (navitiaDate == null || navitiaDate.isEmpty()) return "";
        try {
            // Format Navitia: "20260328T143000" -> "2026-03-28 14:30"
            if (navitiaDate.length() >= 13) {
                return navitiaDate.substring(0, 4) + "-" + navitiaDate.substring(4, 6) + "-"
                        + navitiaDate.substring(6, 8) + " " + navitiaDate.substring(9, 11)
                        + ":" + navitiaDate.substring(11, 13);
            }
            return navitiaDate;
        } catch (Exception e) {
            return navitiaDate;
        }
    }

    private List<TrainIncident> fetchIncidentsFromApi(String token) {
        try {
            String body = idfmClient.fetchGeneralMessage(token);
            List<TrainIncident> result = parseIncidentResponse(body);
            Log.i(TAG, "fetchIncidentsFromApi: parsed " + result.size() + " incidents");
            return result;
        } catch (IdfmClient.HttpException e) {
            Log.w(TAG, "fetchIncidentsFromApi: HTTP error " + e.code);
        } catch (Exception e) {
            Log.e(TAG, "fetchIncidentsFromApi: network error", e);
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

                IncidentClassifier.Classification classification =
                        IncidentClassifier.classifyMessage(text, channel);
                String type = classification.type;
                String severity = classification.severity;

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
