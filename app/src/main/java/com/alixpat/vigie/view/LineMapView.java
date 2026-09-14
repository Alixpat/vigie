package com.alixpat.vigie.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.LineNStation;
import com.alixpat.vigie.train.LineSegment;
import com.alixpat.vigie.train.StationMatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Schéma de la ligne N : les gares en colonnes (tronc + branches) et les trains
 * à leur position de l'instant.
 *
 * <p>Quatre choix de dessin méritent une explication :</p>
 * <ul>
 *   <li><b>Colonnes mesurées</b> — l'écart entre deux colonnes est calculé à
 *       partir du nom de gare le plus long de la colonne de gauche, et chaque
 *       nom est tronqué à cette largeur. Avec un écart fixe, les noms du tronc
 *       débordaient sur la branche voisine et se superposaient à ses gares.</li>
 *   <li><b>Couleurs du thème</b> — prises dans les ressources, donc valides en
 *       mode nuit. En dur, le plan écrivait du texte gris foncé sur une carte
 *       gris foncé : illisible.</li>
 *   <li><b>Mon tronçon surligné</b> — au milieu de 30 gares, les deux seules
 *       qui comptent sont les miennes ({@link #setHighlightedSegment}).</li>
 *   <li><b>Mes trains détachés du reste</b> — la ligne fait circuler une
 *       cinquantaine de trains, dont la plupart ne desservent pas mes gares.
 *       Ceux qui les desservent sont pleins et à taille normale, les autres
 *       réduits et translucides : le trafic reste lisible en fond sans noyer
 *       les trains que je peux prendre ({@code TrainOnMap.onMyRoute},
 *       {@link #setShowOtherTrains}).</li>
 * </ul>
 */
public class LineMapView extends View {

    private static final String TAG = "LineMapView";

    // Couleur officielle de la Ligne N (vert Transilien) : identité de la ligne,
    // elle ne suit pas le thème. Elle reste lisible sur fond clair comme sombre.
    private static final int COLOR_LINE_N = 0xFF00A86B;

    // Couleurs issues du thème (clair / nuit), résolues dans init().
    private int colorText;
    private int colorTextSecondary;
    private int colorSurface;
    private int colorLegendBg;
    private int colorHighlight;
    private int colorTrainOnTime;
    private int colorTrainDelayed;

    // Dimensions en dp (converties en px dans init)
    private float density;
    private float lineWidth;
    private float highlightWidth;
    private float stopRadius;
    private float stopRadiusJunction;
    private float stopRadiusMine;
    private float trainSize;
    private float textSize;
    private float textSizeSmall;
    private float textSizeLegend;
    private float rowHeight;
    private float startX;
    private float startY;
    private float legendHeight;
    private float columnGap;

    // Paints
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stopFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stopStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trainStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Data
    private List<LineNStation> trunk;
    private List<LineNStation> branchRambouillet;
    private List<LineNStation> branchMantes;
    private List<LineNStation> branchDreux;

    // Positions calculées des gares : nom normalisé → (x, y)
    private final Map<String, float[]> stationPositions = new HashMap<>();

    // Mon tronçon : gares normalisées à surligner, dans l'ordre du corridor.
    private final List<String> highlightedStations = new ArrayList<>();

    // Trains à afficher
    private final List<TrainOnMap> trains = new ArrayList<>();

    // Le trafic qui ne me concerne pas peut être masqué entièrement : sur un
    // téléphone, cinquante triangles sur trente gares finissent par cacher les
    // deux ou trois qui comptent.
    private boolean showOtherTrains = true;

    // Zones de tap pour chaque train (recalculées à chaque onDraw)
    private final List<HitArea> trainHitAreas = new ArrayList<>();
    private OnTrainClickListener trainClickListener;

    public interface OnTrainClickListener {
        void onTrainClicked(TrainOnMap train);
    }

    public void setOnTrainClickListener(OnTrainClickListener listener) {
        this.trainClickListener = listener;
    }

    private static class HitArea {
        final RectF rect;
        final TrainOnMap train;
        HitArea(RectF rect, TrainOnMap train) {
            this.rect = rect;
            this.train = train;
        }
    }

    public static class TrainOnMap {
        public final String journeyRef;
        public final String destination;
        public final String currentStopName;
        public final String nextStopName;
        public final float progressBetweenStops; // 0.0 à 1.0
        public final int delayMinutes;
        /** Ce train dessert-il mes deux gares ? (cf. {@code MyTrains}) */
        public final boolean onMyRoute;
        /**
         * La desserte est-elle prouvée, ou seulement déduite ? L'
         * {@code estimated-timetable} ne décrivant que les arrêts restants, un
         * train qui a déjà franchi une de mes gares ne permet plus de le dire —
         * il est compté comme mien, mais le détail doit l'annoncer comme une
         * déduction et non comme un fait.
         */
        public final boolean routeConfirmed;
        public final String trainNumber;
        public final String missionName;

        public TrainOnMap(String journeyRef, String destination,
                          String currentStopName, String nextStopName,
                          float progressBetweenStops, int delayMinutes,
                          boolean onMyRoute, boolean routeConfirmed,
                          String trainNumber, String missionName) {
            this.journeyRef = journeyRef;
            this.destination = destination;
            this.currentStopName = currentStopName;
            this.nextStopName = nextStopName;
            this.progressBetweenStops = progressBetweenStops;
            this.delayMinutes = delayMinutes;
            this.onMyRoute = onMyRoute;
            this.routeConfirmed = routeConfirmed;
            this.trainNumber = trainNumber;
            this.missionName = missionName;
        }

        public boolean isDelayed() {
            return delayMinutes > 0;
        }
    }

    public LineMapView(Context context) {
        super(context);
        init();
    }

    public LineMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineMapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        Context ctx = getContext();

        colorText = ContextCompat.getColor(ctx, R.color.text_primary);
        colorTextSecondary = ContextCompat.getColor(ctx, R.color.text_secondary);
        colorSurface = ContextCompat.getColor(ctx, R.color.background_card);
        colorLegendBg = ContextCompat.getColor(ctx, R.color.background_item);
        colorHighlight = ContextCompat.getColor(ctx, R.color.secondary);
        colorTrainOnTime = ContextCompat.getColor(ctx, R.color.status_ok);
        colorTrainDelayed = ContextCompat.getColor(ctx, R.color.status_warning);

        lineWidth = 4 * density;
        highlightWidth = 9 * density;
        stopRadius = 5 * density;
        stopRadiusJunction = 7 * density;
        stopRadiusMine = 9 * density;
        trainSize = 9 * density;
        textSize = 12 * density;
        textSizeSmall = 10 * density;
        textSizeLegend = 11 * density;
        rowHeight = 42 * density;
        startX = 30 * density;
        startY = 8 * density;
        legendHeight = 66 * density;
        columnGap = 14 * density;

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(lineWidth);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setColor(COLOR_LINE_N);

        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(highlightWidth);
        highlightPaint.setStrokeCap(Paint.Cap.ROUND);
        highlightPaint.setColor(colorHighlight);
        highlightPaint.setAlpha(120);

        stopFillPaint.setStyle(Paint.Style.FILL);
        stopFillPaint.setColor(colorSurface);

        stopStrokePaint.setStyle(Paint.Style.STROKE);
        stopStrokePaint.setStrokeWidth(2 * density);

        textPaint.setTextSize(textSize);
        textPaint.setColor(colorText);

        trainPaint.setStyle(Paint.Style.FILL);

        trainStrokePaint.setStyle(Paint.Style.STROKE);
        trainStrokePaint.setColor(colorSurface);
        trainStrokePaint.setStrokeWidth(2 * density);

        legendBgPaint.setStyle(Paint.Style.FILL);
        legendBgPaint.setColor(colorLegendBg);

        legendTextPaint.setTextSize(textSizeLegend);
        legendTextPaint.setColor(colorText);

        trunk = LineNStation.getTrunk();
        branchRambouillet = LineNStation.getBranchRambouillet();
        branchMantes = LineNStation.getBranchMantes();
        branchDreux = LineNStation.getBranchDreux();
    }

    public void setTrains(List<TrainOnMap> trainList) {
        trains.clear();
        if (trainList != null) {
            trains.addAll(trainList);
        }
        Log.i(TAG, "setTrains: " + trains.size() + " trains reçus");
        invalidate();
    }

    /**
     * Affiche ou masque les trains qui ne desservent pas mes deux gares. Ils
     * restent dessinés en retrait quand ils sont visibles : c'est du contexte,
     * pas de l'information sur mon trajet.
     */
    public void setShowOtherTrains(boolean show) {
        if (showOtherTrains == show) return;
        showOtherTrains = show;
        invalidate();
    }

    public boolean isShowingOtherTrains() {
        return showOtherTrains;
    }

    /**
     * Surligne le tronçon entre mes deux gares. Le corridor est celui de
     * {@link LineSegment} : même géographie que la sélection des trains en
     * circulation, donc le plan et les listes racontent la même chose.
     */
    public void setHighlightedSegment(String originName, String destinationName) {
        highlightedStations.clear();
        for (String station : LineSegment.between(originName, destinationName).stations()) {
            highlightedStations.add(LineNStation.normalize(station));
        }
        Log.i(TAG, "setHighlightedSegment: " + originName + " → " + destinationName
                + " = " + highlightedStations.size() + " gares");
        invalidate();
    }

    // ==================== MISE EN PAGE ====================

    /** Décalage entre le point d'une gare et le début de son nom. */
    private float textOffset() {
        return stopRadiusMine + trainSize + 12 * density;
    }

    /** Largeur du plus long nom d'une colonne, en gras (cas le plus large). */
    private float widestName(List<LineNStation>... branches) {
        Paint probe = new Paint(textPaint);
        probe.setTypeface(Typeface.DEFAULT_BOLD);
        float max = 0;
        for (List<LineNStation> branch : branches) {
            if (branch == null) continue;
            for (LineNStation station : branch) {
                max = Math.max(max, probe.measureText(station.getName()));
            }
        }
        return max;
    }

    /** Abscisse de la colonne du milieu (branche Mantes). */
    private float columnMantes() {
        return startX + textOffset() + widestName(trunk, branchRambouillet) + columnGap;
    }

    /** Abscisse de la colonne de droite (branche Dreux). */
    private float columnDreux() {
        return columnMantes() + textOffset() + widestName(branchMantes) + columnGap;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Largeur : la dernière colonne est la seule dont les noms peuvent
        // s'étendre librement, il faut donc qu'ils tiennent dans la vue —
        // sinon, dans un HorizontalScrollView, ils sont dessinés hors-canvas.
        int neededWidth = (int) (columnDreux() + textOffset()
                + widestName(branchDreux) + 16 * density);

        int requestedWidth = MeasureSpec.getSize(widthMeasureSpec);
        int width = Math.max(neededWidth, requestedWidth);
        if (width == 0) width = neededWidth;

        // Hauteur : tronc puis branches en parallèle.
        int trunkRows = trunk.size();
        int maxBranchRows = Math.max(branchRambouillet.size(),
                Math.max(branchMantes.size(), branchDreux.size() + 4));
        // +4 pour Dreux : la branche bifurque de Plaisir-Grignon (4e gare de Mantes)

        int totalRows = trunkRows + maxBranchRows + 2; // +2 pour espacement
        float totalHeight = startY + legendHeight + totalRows * rowHeight;

        setMeasuredDimension(width, (int) totalHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        stationPositions.clear();
        trainHitAreas.clear();

        float width = getWidth();
        drawLegend(canvas, width);

        float colTrunk = startX;
        float colMantes = columnMantes();
        float colDreux = columnDreux();
        float y = startY + legendHeight;

        // Chaque colonne tronque ses noms avant la colonne suivante : c'est ce
        // qui empêche le tronc d'écrire par-dessus la branche Mantes.
        float trunkTextWidth = colMantes - (colTrunk + textOffset()) - columnGap;
        float mantesTextWidth = colDreux - (colMantes + textOffset()) - columnGap;
        float dreuxTextWidth = width - (colDreux + textOffset()) - 8;

        // ====== TRACÉ DES LIGNES ======
        float trunkEndY = y + (trunk.size() - 1) * rowHeight;
        canvas.drawLine(colTrunk, y, colTrunk, trunkEndY, linePaint);

        float junctionY = trunkEndY;                       // Saint-Cyr
        float rambStartY = junctionY + rowHeight;
        canvas.drawLine(colTrunk, junctionY, colTrunk,
                rambStartY + (branchRambouillet.size() - 1) * rowHeight, linePaint);

        float mantesStartY = junctionY + rowHeight;
        drawBranchCurve(canvas, colTrunk, junctionY, colMantes, mantesStartY, linePaint);
        canvas.drawLine(colMantes, mantesStartY, colMantes,
                mantesStartY + (branchMantes.size() - 1) * rowHeight, linePaint);

        float plaisirGrignonY = mantesStartY + 3 * rowHeight;
        float dreuxStartY = plaisirGrignonY + rowHeight;
        drawBranchCurve(canvas, colMantes, plaisirGrignonY, colDreux, dreuxStartY, linePaint);
        canvas.drawLine(colDreux, dreuxStartY, colDreux,
                dreuxStartY + (branchDreux.size() - 1) * rowHeight, linePaint);

        // ====== POSITIONS DES GARES ======
        for (int i = 0; i < trunk.size(); i++) {
            remember(trunk.get(i), colTrunk, y + i * rowHeight);
        }
        for (int i = 0; i < branchRambouillet.size(); i++) {
            remember(branchRambouillet.get(i), colTrunk, rambStartY + i * rowHeight);
        }
        for (int i = 0; i < branchMantes.size(); i++) {
            remember(branchMantes.get(i), colMantes, mantesStartY + i * rowHeight);
        }
        for (int i = 0; i < branchDreux.size(); i++) {
            remember(branchDreux.get(i), colDreux, dreuxStartY + i * rowHeight);
        }

        // ====== MON TRONÇON (par-dessus le tracé, sous les gares) ======
        drawHighlight(canvas);

        // ====== GARES ======
        for (int i = 0; i < trunk.size(); i++) {
            drawStop(canvas, trunk.get(i), colTrunk, y + i * rowHeight,
                    i == trunk.size() - 1, trunkTextWidth);
        }
        for (int i = 0; i < branchRambouillet.size(); i++) {
            drawStop(canvas, branchRambouillet.get(i), colTrunk, rambStartY + i * rowHeight,
                    i == branchRambouillet.size() - 1, trunkTextWidth);
        }
        for (int i = 0; i < branchMantes.size(); i++) {
            drawStop(canvas, branchMantes.get(i), colMantes, mantesStartY + i * rowHeight,
                    i == 3 || i == branchMantes.size() - 1, mantesTextWidth);
        }
        for (int i = 0; i < branchDreux.size(); i++) {
            drawStop(canvas, branchDreux.get(i), colDreux, dreuxStartY + i * rowHeight,
                    i == branchDreux.size() - 1, dreuxTextWidth);
        }

        // ====== TRAINS ======
        // Cluster par (currentStop, nextStop) pour éviter que plusieurs trains
        // au même segment se dessinent les uns sur les autres.
        Map<String, List<TrainOnMap>> clusters = new LinkedHashMap<>();
        int shown = 0;
        for (TrainOnMap train : trains) {
            if (!train.onMyRoute && !showOtherTrains) continue;
            shown++;
            String key = (train.currentStopName == null ? "" : train.currentStopName)
                    + "→" + (train.nextStopName == null ? "" : train.nextStopName);
            List<TrainOnMap> cluster = clusters.get(key);
            if (cluster == null) {
                cluster = new ArrayList<>();
                clusters.put(key, cluster);
            }
            cluster.add(train);
        }
        // Mes trains passent en dernier : ils se dessinent par-dessus le trafic
        // de fond, et le hit-test (qui parcourt à l'envers) les choisit d'abord.
        int drawn = 0;
        for (int pass = 0; pass < 2; pass++) {
            boolean mine = pass == 1;
            for (List<TrainOnMap> cluster : clusters.values()) {
                for (int i = 0; i < cluster.size(); i++) {
                    if (cluster.get(i).onMyRoute != mine) continue;
                    if (drawTrain(canvas, cluster.get(i), i, cluster.size())) drawn++;
                }
            }
        }
        Log.i(TAG, "onDraw: " + stationPositions.size() + " gares, "
                + drawn + "/" + shown + " trains placés");
    }

    private void remember(LineNStation station, float x, float y) {
        stationPositions.put(LineNStation.normalize(station.getName()), new float[]{x, y});
    }

    /** Est-ce une de mes gares (extrémité de mon tronçon) ? */
    private boolean isMyStation(String normalizedName) {
        if (highlightedStations.isEmpty()) return false;
        return normalizedName.equals(highlightedStations.get(0))
                || normalizedName.equals(highlightedStations.get(highlightedStations.size() - 1));
    }

    private boolean isOnMySegment(String normalizedName) {
        return highlightedStations.contains(normalizedName);
    }

    /** Repasse mon tronçon en surbrillance, gare après gare. */
    private void drawHighlight(Canvas canvas) {
        for (int i = 0; i + 1 < highlightedStations.size(); i++) {
            float[] from = stationPositions.get(highlightedStations.get(i));
            float[] to = stationPositions.get(highlightedStations.get(i + 1));
            if (from == null || to == null) continue;
            if (from[0] == to[0]) {
                canvas.drawLine(from[0], from[1], to[0], to[1], highlightPaint);
            } else {
                // Changement de colonne (Saint-Cyr → Fontenay) : même courbe que
                // le raccordement dessiné dessous, sinon le surlignage coupe au
                // travers du plan.
                drawBranchCurve(canvas, from[0], from[1], to[0], to[1], highlightPaint);
            }
        }
    }

    private void drawLegend(Canvas canvas, float width) {
        canvas.drawRoundRect(new RectF(4 * density, 4 * density,
                        width - 4 * density, startY + legendHeight - 10 * density),
                6 * density, 6 * density, legendBgPaint);

        float lx = 12 * density;
        float ly = startY + 6 * density;

        legendTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        legendTextPaint.setTextSize(textSizeLegend);
        legendTextPaint.setColor(colorText);
        canvas.drawText("Ligne N — Transilien", lx, ly + textSizeLegend, legendTextPaint);

        // Statuts des trains, puis le repère qui distingue mes trains du reste.
        ly += textSizeLegend + 12 * density;
        legendTextPaint.setTypeface(Typeface.DEFAULT);
        legendTextPaint.setTextSize(textSizeSmall);
        lx = drawLegendTrain(canvas, lx, ly, colorTrainOnTime, 255, "À l'heure");
        lx = drawLegendTrain(canvas, lx, ly, colorTrainDelayed, 255, "Retardé");
        drawLegendTrain(canvas, lx, ly, colorTrainOnTime, 90, "Ne dessert pas mes gares");

        // Sens de circulation + repère de mes gares
        lx = 12 * density;
        ly += 18 * density;
        legendTextPaint.setColor(colorTextSecondary);
        String senses = "▲ vers Paris   ▼ vers la province";
        canvas.drawText(senses, lx, ly + 8 * density, legendTextPaint);

        if (!highlightedStations.isEmpty()) {
            lx += legendTextPaint.measureText(senses) + 16 * density;
            stopStrokePaint.setColor(colorHighlight);
            canvas.drawCircle(lx + 5 * density, ly + 4 * density, 5 * density, stopFillPaint);
            canvas.drawCircle(lx + 5 * density, ly + 4 * density, 5 * density, stopStrokePaint);
            canvas.drawText("mon trajet", lx + 14 * density, ly + 8 * density, legendTextPaint);
        }
    }

    /** Un pictogramme de train + son libellé ; @return l'abscisse du suivant. */
    private float drawLegendTrain(Canvas canvas, float lx, float ly,
                                  int color, int alpha, String label) {
        trainPaint.setColor(color);
        trainPaint.setAlpha(alpha);
        canvas.drawPath(trianglePath(lx + 7 * density, ly + 4 * density, 6 * density, true),
                trainPaint);
        trainPaint.setAlpha(255);
        legendTextPaint.setColor(colorText);
        canvas.drawText(label, lx + 18 * density, ly + 8 * density, legendTextPaint);
        return lx + 18 * density + legendTextPaint.measureText(label) + 16 * density;
    }

    private void drawBranchCurve(Canvas canvas, float fromX, float fromY,
                                 float toX, float toY, Paint paint) {
        Path path = new Path();
        path.moveTo(fromX, fromY);
        float midY = (fromY + toY) / 2;
        path.cubicTo(fromX, midY, toX, midY, toX, toY);
        canvas.drawPath(path, paint);
    }

    private void drawStop(Canvas canvas, LineNStation station, float x, float y,
                          boolean isJunction, float maxTextWidth) {
        String normalized = LineNStation.normalize(station.getName());
        boolean mine = isMyStation(normalized);
        boolean onSegment = isOnMySegment(normalized);

        float radius = mine ? stopRadiusMine : (isJunction ? stopRadiusJunction : stopRadius);
        stopStrokePaint.setColor(mine || onSegment ? colorHighlight : COLOR_LINE_N);
        canvas.drawCircle(x, y, radius, stopFillPaint);
        canvas.drawCircle(x, y, radius, stopStrokePaint);
        if (mine) {
            // Pastille pleine : mes deux gares se repèrent d'un coup d'œil.
            stopFillPaint.setColor(colorHighlight);
            canvas.drawCircle(x, y, radius - 3 * density, stopFillPaint);
            stopFillPaint.setColor(colorSurface);
        }

        textPaint.setTypeface(mine || isJunction ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        textPaint.setColor(mine ? colorHighlight : colorText);
        textPaint.setTextSize(textSize);

        float textX = x + textOffset();
        float textY = y + textSize / 3;
        canvas.drawText(ellipsize(station.getName(), maxTextWidth), textX, textY, textPaint);
    }

    /** Tronque un nom à la largeur disponible de sa colonne. */
    private String ellipsize(String name, float maxWidth) {
        if (maxWidth <= 0 || textPaint.measureText(name) <= maxWidth) return name;
        String shortened = name;
        while (shortened.length() > 3 && textPaint.measureText(shortened + "…") > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    /** @return true si le train a pu être placé sur le plan */
    private boolean drawTrain(Canvas canvas, TrainOnMap train, int clusterIndex, int clusterSize) {
        float[] posFrom = findStationPos(train.currentStopName);
        float[] posTo = findStationPos(train.nextStopName);

        float tx, ty;
        if (posFrom != null && posTo != null) {
            float progress = Math.max(0, Math.min(1, train.progressBetweenStops));
            tx = posFrom[0] + (posTo[0] - posFrom[0]) * progress;
            ty = posFrom[1] + (posTo[1] - posFrom[1]) * progress;
        } else if (posFrom != null) {
            tx = posFrom[0];
            ty = posFrom[1];
        } else if (posTo != null) {
            tx = posTo[0];
            ty = posTo[1];
        } else {
            Log.w(TAG, "drawTrain: gare inconnue pour mission=" + train.missionName
                    + " current='" + train.currentStopName + "' next='" + train.nextStopName + "'");
            return false;
        }

        // Décale verticalement les trains du même cluster pour qu'ils ne se
        // superposent pas (centré autour de la position d'origine).
        if (clusterSize > 1) {
            float spacing = trainSize * 2.2f;
            ty += (clusterIndex - (clusterSize - 1) / 2.0f) * spacing;
        }

        // Sens : montant (vers Paris, Y décroissant) ou descendant
        boolean goingUp;
        if (posFrom != null && posTo != null && posFrom[1] != posTo[1]) {
            goingUp = posTo[1] < posFrom[1];
        } else {
            String destLower = train.destination != null
                    ? train.destination.toLowerCase(Locale.FRENCH) : "";
            goingUp = destLower.contains("paris") || destLower.contains("montparnasse");
        }

        // Deux voies : montants à droite du trait, descendants à gauche.
        tx += goingUp ? (trainSize + 3 * density) : -(trainSize + 3 * density);

        // Un train qui ne dessert pas mes gares est du décor : même forme, mais
        // réduit et translucide, pour qu'il ne se dispute pas l'attention avec
        // ceux que je peux prendre.
        float size = train.onMyRoute ? trainSize : trainSize * 0.62f;
        trainPaint.setColor(train.isDelayed() ? colorTrainDelayed : colorTrainOnTime);
        trainPaint.setAlpha(train.onMyRoute ? 255 : 90);

        // Triangle : ▲ montant vers Paris, ▼ descendant vers la province
        Path path = trianglePath(tx, ty, size, goingUp);
        canvas.drawPath(path, trainPaint);
        if (train.onMyRoute) {
            // Liseré de la couleur de la carte : détache le triangle du trait de
            // la ligne, sur lequel il est posé.
            canvas.drawPath(path, trainStrokePaint);
        }
        trainPaint.setAlpha(255);

        // Pas de label sur le plan : trop d'encombrement avec 30+ trains.
        // L'info détaillée est accessible au tap (hit-test enregistré ci-dessous).
        float pad = 4 * density;
        trainHitAreas.add(new HitArea(
                new RectF(tx - size - pad, ty - size - pad,
                        tx + size + pad, ty + size + pad),
                train));
        return true;
    }

    /** ▲ vers Paris (Y décroissant), ▼ vers la province. */
    private static Path trianglePath(float cx, float cy, float size, boolean goingUp) {
        Path path = new Path();
        if (goingUp) {
            path.moveTo(cx, cy - size);
            path.lineTo(cx - size * 0.7f, cy + size * 0.5f);
            path.lineTo(cx + size * 0.7f, cy + size * 0.5f);
        } else {
            path.moveTo(cx, cy + size);
            path.lineTo(cx - size * 0.7f, cy - size * 0.5f);
            path.lineTo(cx + size * 0.7f, cy - size * 0.5f);
        }
        path.close();
        return path;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP) {
            return super.onTouchEvent(event);
        }

        HitArea hit = findTrainAt(event.getX(), event.getY());
        if (hit == null) {
            return false; // pas un train : on laisse vivre le scroll parent
        }

        if (action == MotionEvent.ACTION_DOWN) {
            // Le DOWN tombe sur un train : on intercepte ET on bloque le
            // HorizontalScrollView parent, ce qui garantit de recevoir l'UP.
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        } else {
            if (trainClickListener != null) {
                trainClickListener.onTrainClicked(hit.train);
            }
            performClick();
        }
        return true;
    }

    /**
     * Zone de tap contenant (x, y), ou null si le point ne touche aucun train.
     * Parcours en sens inverse : le triangle dessiné en dernier (= au-dessus)
     * gagne en cas de chevauchement.
     */
    private HitArea findTrainAt(float x, float y) {
        for (int i = trainHitAreas.size() - 1; i >= 0; i--) {
            HitArea area = trainHitAreas.get(i);
            if (area.rect.contains(x, y)) {
                return area;
            }
        }
        return null;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /**
     * Position de la gare portant ce nom, ou null quand elle n'est pas
     * identifiable sans ambiguïté (cf. {@link StationMatch}).
     */
    private float[] findStationPos(String stopName) {
        String match = StationMatch.bestMatch(stopName, stationPositions.keySet());
        if (match == null) {
            Log.w(TAG, "findStationPos: gare non identifiée '" + stopName + "'");
            return null;
        }
        return stationPositions.get(match);
    }
}
