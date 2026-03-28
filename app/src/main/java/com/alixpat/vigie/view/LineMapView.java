package com.alixpat.vigie.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import android.util.Log;

import com.alixpat.vigie.model.LineNStation;
import com.alixpat.vigie.model.TrainStop;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vue personnalisée qui dessine le schéma de la ligne N du Transilien
 * avec les arrêts et les positions des trains en temps réel.
 */
public class LineMapView extends View {

    private static final String TAG = "LineMapView";

    // Couleur officielle de la Ligne N (vert Transilien)
    private static final int COLOR_LINE_N = 0xFF00A86B;
    private static final int COLOR_STOP_FILL = 0xFFFFFFFF;
    private static final int COLOR_STOP_STROKE = 0xFF424242;
    private static final int COLOR_TEXT = 0xFF212121;
    private static final int COLOR_TEXT_SECONDARY = 0xFF757575;
    private static final int COLOR_TRAIN_ON_TIME = 0xFF4CAF50;
    private static final int COLOR_TRAIN_DELAYED = 0xFFFF9800;
    private static final int COLOR_TRAIN_CANCELLED = 0xFFF44336;
    private static final int COLOR_JUNCTION = 0xFF00A86B;
    private static final int COLOR_BG_LEGEND = 0xFFF5F5F5;

    // Dimensions en dp (converties en px dans init)
    private float lineWidth;
    private float stopRadius;
    private float stopRadiusJunction;
    private float trainSize;
    private float textSize;
    private float textSizeSmall;
    private float textSizeLegend;
    private float rowHeight;
    private float branchOffsetX;
    private float startX;
    private float startY;
    private float legendHeight;

    // Paints
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stopFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stopStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textSecondaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trainStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Data
    private List<LineNStation> trunk;
    private List<LineNStation> branchRambouillet;
    private List<LineNStation> branchMantes;
    private List<LineNStation> branchDreux;

    // Positions calculées des gares : nom normalisé → (x, y)
    private final Map<String, float[]> stationPositions = new HashMap<>();

    // Trains à afficher
    private final List<TrainOnMap> trains = new ArrayList<>();

    public static class TrainOnMap {
        public final String journeyRef;
        public final String destination;
        public final String currentStopName;
        public final String nextStopName;
        public final float progressBetweenStops; // 0.0 à 1.0
        public final boolean onTime;
        public final boolean delayed;
        public final boolean cancelled;
        public final int delayMinutes;
        public final String label;
        public final String trainNumber;
        public final String missionName;

        public TrainOnMap(String journeyRef, String destination,
                          String currentStopName, String nextStopName,
                          float progressBetweenStops,
                          boolean onTime, boolean delayed, boolean cancelled,
                          int delayMinutes, String label,
                          String trainNumber, String missionName) {
            this.journeyRef = journeyRef;
            this.destination = destination;
            this.currentStopName = currentStopName;
            this.nextStopName = nextStopName;
            this.progressBetweenStops = progressBetweenStops;
            this.onTime = onTime;
            this.delayed = delayed;
            this.cancelled = cancelled;
            this.delayMinutes = delayMinutes;
            this.label = label;
            this.trainNumber = trainNumber;
            this.missionName = missionName;
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
        float density = getResources().getDisplayMetrics().density;

        lineWidth = 4 * density;
        stopRadius = 5 * density;
        stopRadiusJunction = 7 * density;
        trainSize = 10 * density;
        textSize = 12 * density;
        textSizeSmall = 10 * density;
        textSizeLegend = 11 * density;
        rowHeight = 36 * density;
        branchOffsetX = 100 * density;
        startX = 90 * density;
        startY = 60 * density;
        legendHeight = 52 * density;

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(lineWidth);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        stopFillPaint.setStyle(Paint.Style.FILL);
        stopFillPaint.setColor(COLOR_STOP_FILL);

        stopStrokePaint.setStyle(Paint.Style.STROKE);
        stopStrokePaint.setColor(COLOR_STOP_STROKE);
        stopStrokePaint.setStrokeWidth(2 * density);

        textPaint.setTextSize(textSize);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTypeface(Typeface.DEFAULT);

        textSecondaryPaint.setTextSize(textSizeSmall);
        textSecondaryPaint.setColor(COLOR_TEXT_SECONDARY);

        trainPaint.setStyle(Paint.Style.FILL);

        trainStrokePaint.setStyle(Paint.Style.STROKE);
        trainStrokePaint.setColor(Color.WHITE);
        trainStrokePaint.setStrokeWidth(2 * density);

        legendBgPaint.setStyle(Paint.Style.FILL);
        legendBgPaint.setColor(COLOR_BG_LEGEND);

        legendTextPaint.setTextSize(textSizeLegend);
        legendTextPaint.setColor(COLOR_TEXT);

        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setStrokeWidth(1 * density);
        dashPaint.setColor(0xFFBDBDBD);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{4 * density, 4 * density}, 0));

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
        for (int i = 0; i < trains.size(); i++) {
            TrainOnMap t = trains.get(i);
            Log.d(TAG, "  train[" + i + "] journey=" + t.journeyRef
                    + " current=" + t.currentStopName + " next=" + t.nextStopName
                    + " progress=" + t.progressBetweenStops + " dest=" + t.destination
                    + " mission=" + t.missionName + " num=" + t.trainNumber);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width == 0) width = (int) (400 * getResources().getDisplayMetrics().density);

        // Calcul de la hauteur nécessaire
        // Tronc : 10 gares, puis les 3 branches partent en parallèle
        int trunkRows = trunk.size();
        int maxBranchRows = Math.max(branchRambouillet.size(),
                Math.max(branchMantes.size() + 4, branchDreux.size() + 4));
        // +4 pour Mantes/Dreux car la branche Dreux bifurque de Plaisir-Grignon (4e gare de branche Mantes)

        int totalRows = trunkRows + maxBranchRows + 2; // +2 pour espacement
        float totalHeight = startY + legendHeight + totalRows * rowHeight + rowHeight;

        setMeasuredDimension(width, (int) totalHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        stationPositions.clear();

        float width = getWidth();

        // ====== LÉGENDE ======
        drawLegend(canvas, width);

        // ====== TRONC COMMUN (colonne gauche) ======
        float x = startX;
        float y = startY + legendHeight;
        float colTrunk = x;

        // Dessiner la ligne du tronc
        float trunkStartY = y;
        float trunkEndY = y + (trunk.size() - 1) * rowHeight;
        linePaint.setColor(COLOR_LINE_N);
        canvas.drawLine(colTrunk, trunkStartY, colTrunk, trunkEndY, linePaint);

        for (int i = 0; i < trunk.size(); i++) {
            LineNStation station = trunk.get(i);
            float sy = y + i * rowHeight;
            boolean isJunction = (i == trunk.size() - 1); // Saint-Cyr
            drawStop(canvas, colTrunk, sy, station.getName(), isJunction, COLOR_LINE_N, width);
            stationPositions.put(LineNStation.normalize(station.getName()), new float[]{colTrunk, sy});
        }

        // Point de bifurcation : Saint-Cyr
        float junctionY = y + (trunk.size() - 1) * rowHeight;

        // ====== BRANCHE RAMBOUILLET (colonne gauche, continue tout droit) ======
        float colRamb = colTrunk;
        float rambStartY = junctionY + rowHeight;

        linePaint.setColor(COLOR_LINE_N);
        canvas.drawLine(colRamb, junctionY, colRamb, rambStartY + (branchRambouillet.size() - 1) * rowHeight, linePaint);

        for (int i = 0; i < branchRambouillet.size(); i++) {
            LineNStation station = branchRambouillet.get(i);
            float sy = rambStartY + i * rowHeight;
            boolean isTerminus = (i == branchRambouillet.size() - 1);
            drawStop(canvas, colRamb, sy, station.getName(), isTerminus, COLOR_LINE_N, width);
            stationPositions.put(LineNStation.normalize(station.getName()), new float[]{colRamb, sy});
        }

        // ====== BRANCHE MANTES (colonne centre) ======
        float colMantes = colTrunk + branchOffsetX;

        // Courbe de raccordement Saint-Cyr → Fontenay
        float mantesStartY = junctionY + rowHeight;
        drawBranchCurve(canvas, colTrunk, junctionY, colMantes, mantesStartY, COLOR_LINE_N);

        // Ligne verticale branche Mantes
        float mantesEndY = mantesStartY + (branchMantes.size() - 1) * rowHeight;
        linePaint.setColor(COLOR_LINE_N);
        canvas.drawLine(colMantes, mantesStartY, colMantes, mantesEndY, linePaint);

        // Point de bifurcation Plaisir-Grignon (index 3 dans branche Mantes)
        float plaisirGrignonY = mantesStartY + 3 * rowHeight;

        for (int i = 0; i < branchMantes.size(); i++) {
            LineNStation station = branchMantes.get(i);
            float sy = mantesStartY + i * rowHeight;
            boolean isJunction = (i == 3); // Plaisir-Grignon
            boolean isTerminus = (i == branchMantes.size() - 1);
            drawStop(canvas, colMantes, sy, station.getName(), isJunction || isTerminus, COLOR_LINE_N, width);
            stationPositions.put(LineNStation.normalize(station.getName()), new float[]{colMantes, sy});
        }

        // ====== BRANCHE DREUX (colonne droite, bifurque de Plaisir-Grignon) ======
        float colDreux = colMantes + branchOffsetX;
        float dreuxStartY = plaisirGrignonY + rowHeight;

        // Courbe de raccordement Plaisir-Grignon → Montfort
        drawBranchCurve(canvas, colMantes, plaisirGrignonY, colDreux, dreuxStartY, COLOR_LINE_N);

        // Ligne verticale branche Dreux
        float dreuxEndY = dreuxStartY + (branchDreux.size() - 1) * rowHeight;
        linePaint.setColor(COLOR_LINE_N);
        canvas.drawLine(colDreux, dreuxStartY, colDreux, dreuxEndY, linePaint);

        for (int i = 0; i < branchDreux.size(); i++) {
            LineNStation station = branchDreux.get(i);
            float sy = dreuxStartY + i * rowHeight;
            boolean isTerminus = (i == branchDreux.size() - 1);
            drawStop(canvas, colDreux, sy, station.getName(), isTerminus, COLOR_LINE_N, width);
            stationPositions.put(LineNStation.normalize(station.getName()), new float[]{colDreux, sy});
        }

        // ====== TRAINS ======
        Log.i(TAG, "onDraw: " + stationPositions.size() + " stations positionnées, " + trains.size() + " trains à dessiner");
        for (TrainOnMap train : trains) {
            drawTrain(canvas, train);
        }
    }

    private void drawLegend(Canvas canvas, float width) {
        float density = getResources().getDisplayMetrics().density;
        float ly = 8 * density;
        float lx = startX;

        // Fond légende
        canvas.drawRoundRect(new RectF(4 * density, 2 * density, width - 4 * density, startY + legendHeight - 8 * density),
                6 * density, 6 * density, legendBgPaint);

        // Titre
        legendTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        legendTextPaint.setTextSize(textSizeLegend);
        canvas.drawText("Ligne N — Transilien", lx, ly + textSizeLegend, legendTextPaint);

        legendTextPaint.setTypeface(Typeface.DEFAULT);
        legendTextPaint.setTextSize(textSizeSmall);
        ly += textSizeLegend + 10 * density;

        // Légende trains (statuts)
        lx = startX;
        int[] trainColors = {COLOR_TRAIN_ON_TIME, COLOR_TRAIN_DELAYED, COLOR_TRAIN_CANCELLED};
        String[] trainLabels = {"À l'heure", "Retardé", "Supprimé"};
        for (int i = 0; i < trainColors.length; i++) {
            trainPaint.setColor(trainColors[i]);
            float triX = lx;
            float triY = ly;
            Path tri = new Path();
            tri.moveTo(triX, triY + 10 * density);
            tri.lineTo(triX + 8 * density, triY);
            tri.lineTo(triX + 16 * density, triY + 10 * density);
            tri.close();
            canvas.drawPath(tri, trainPaint);
            legendTextPaint.setColor(COLOR_TEXT);
            canvas.drawText(trainLabels[i], triX + 18 * density, triY + 9 * density, legendTextPaint);
            lx += textPaint.measureText(trainLabels[i]) + 36 * density;
        }

        // Légende sens (deux voies)
        ly += 16 * density;
        lx = startX;
        trainPaint.setColor(COLOR_LINE_N);
        // ▲ Vers Paris
        Path upTri = new Path();
        upTri.moveTo(lx + 8 * density, ly);
        upTri.lineTo(lx, ly + 10 * density);
        upTri.lineTo(lx + 16 * density, ly + 10 * density);
        upTri.close();
        canvas.drawPath(upTri, trainPaint);
        legendTextPaint.setColor(COLOR_TEXT);
        canvas.drawText("Vers Paris (droite)", lx + 18 * density, ly + 9 * density, legendTextPaint);
        lx += legendTextPaint.measureText("Vers Paris (droite)") + 36 * density;
        // ▼ Vers banlieue
        Path downTri = new Path();
        downTri.moveTo(lx + 8 * density, ly + 10 * density);
        downTri.lineTo(lx, ly);
        downTri.lineTo(lx + 16 * density, ly);
        downTri.close();
        canvas.drawPath(downTri, trainPaint);
        legendTextPaint.setColor(COLOR_TEXT);
        canvas.drawText("Vers banlieue (gauche)", lx + 18 * density, ly + 9 * density, legendTextPaint);
    }

    private void drawBranchCurve(Canvas canvas, float fromX, float fromY,
                                  float toX, float toY, int color) {
        linePaint.setColor(color);
        Path path = new Path();
        path.moveTo(fromX, fromY);
        float midY = (fromY + toY) / 2;
        path.cubicTo(fromX, midY, toX, midY, toX, toY);
        canvas.drawPath(path, linePaint);
    }

    private void drawStop(Canvas canvas, float x, float y, String name,
                           boolean isJunction, int branchColor, float viewWidth) {
        float radius = isJunction ? stopRadiusJunction : stopRadius;

        // Cercle de la gare
        stopStrokePaint.setColor(branchColor);
        canvas.drawCircle(x, y, radius, stopFillPaint);
        canvas.drawCircle(x, y, radius, stopStrokePaint);

        // Nom de la gare (décalé pour laisser la voie montante à droite)
        float textX = x + radius + trainSize + 16 * getResources().getDisplayMetrics().density;
        float textY = y + textSize / 3;

        if (isJunction) {
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            textPaint.setTypeface(Typeface.DEFAULT);
        }

        // Tronquer le texte si nécessaire
        float maxTextWidth = viewWidth - textX - 8 * getResources().getDisplayMetrics().density;
        String displayName = name;
        if (textPaint.measureText(displayName) > maxTextWidth && maxTextWidth > 0) {
            while (displayName.length() > 3 && textPaint.measureText(displayName + "…") > maxTextWidth) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            displayName += "…";
        }
        canvas.drawText(displayName, textX, textY, textPaint);
    }

    private void drawTrain(Canvas canvas, TrainOnMap train) {
        float[] posFrom = findStationPos(train.currentStopName);
        float[] posTo = findStationPos(train.nextStopName);

        Log.d(TAG, "drawTrain: mission=" + train.missionName + " num=" + train.trainNumber
                + " current='" + train.currentStopName + "' → posFrom=" + (posFrom != null ? "[" + posFrom[0] + "," + posFrom[1] + "]" : "NULL")
                + " | next='" + train.nextStopName + "' → posTo=" + (posTo != null ? "[" + posTo[0] + "," + posTo[1] + "]" : "NULL"));

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
            Log.w(TAG, "drawTrain: AUCUNE position trouvée pour train mission=" + train.missionName
                    + " current='" + train.currentStopName + "' next='" + train.nextStopName + "' → TRAIN NON DESSINÉ");
            return;
        }

        float density = getResources().getDisplayMetrics().density;

        // Déterminer le sens : montant (vers Paris, Y décroissant) ou descendant
        boolean goingUp = false;
        if (posFrom != null && posTo != null) {
            goingUp = posTo[1] < posFrom[1];
        } else {
            String destLower = train.destination != null ? train.destination.toLowerCase(Locale.FRENCH) : "";
            goingUp = destLower.contains("paris") || destLower.contains("montparnasse");
        }

        // Deux voies : montants à droite, descendants à gauche
        if (goingUp) {
            tx += trainSize + 4 * density;
        } else {
            tx -= trainSize + 4 * density;
        }

        // Couleur selon le statut
        int color;
        if (train.cancelled) {
            color = COLOR_TRAIN_CANCELLED;
        } else if (train.delayed) {
            color = COLOR_TRAIN_DELAYED;
        } else {
            color = COLOR_TRAIN_ON_TIME;
        }
        trainPaint.setColor(color);

        // Triangle : ▲ montant vers Paris, ▼ descendant vers banlieue
        Path path = new Path();
        if (goingUp) {
            path.moveTo(tx, ty - trainSize);
            path.lineTo(tx - trainSize * 0.7f, ty + trainSize * 0.5f);
            path.lineTo(tx + trainSize * 0.7f, ty + trainSize * 0.5f);
        } else {
            path.moveTo(tx, ty + trainSize);
            path.lineTo(tx - trainSize * 0.7f, ty - trainSize * 0.5f);
            path.lineTo(tx + trainSize * 0.7f, ty - trainSize * 0.5f);
        }
        path.close();
        canvas.drawPath(path, trainPaint);
        canvas.drawPath(path, trainStrokePaint);

        // Label : numéro + mission + retard éventuel
        StringBuilder trainLabel = new StringBuilder();
        if (train.trainNumber != null && !train.trainNumber.isEmpty()) {
            trainLabel.append(train.trainNumber);
        }
        if (train.missionName != null && !train.missionName.isEmpty()) {
            if (trainLabel.length() > 0) trainLabel.append(" ");
            trainLabel.append(train.missionName);
        }
        if (trainLabel.length() == 0 && train.label != null && !train.label.isEmpty()) {
            trainLabel.append(train.label);
        }
        if (train.delayed && train.delayMinutes > 0) {
            trainLabel.append(" +").append(train.delayMinutes).append("min");
        }

        if (trainLabel.length() > 0) {
            textSecondaryPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textSecondaryPaint.setColor(color);
            float textWidth = textSecondaryPaint.measureText(trainLabel.toString());
            float labelX, labelY;

            if (goingUp) {
                // Voie droite : label au-dessus du triangle
                labelX = tx - textWidth / 2;
                labelY = ty - trainSize - 4 * density;
            } else {
                // Voie gauche : label à gauche du triangle
                labelX = tx - trainSize * 0.7f - textWidth - 4 * density;
                labelY = ty;
            }
            labelX = Math.max(2 * density, labelX);

            canvas.drawText(trainLabel.toString(), labelX, labelY, textSecondaryPaint);
            textSecondaryPaint.setTypeface(Typeface.DEFAULT);
            textSecondaryPaint.setColor(COLOR_TEXT_SECONDARY);
        }
    }

    private float[] findStationPos(String stopName) {
        if (stopName == null || stopName.isEmpty()) {
            Log.d(TAG, "findStationPos: stopName est null/vide");
            return null;
        }
        String normalized = LineNStation.normalize(stopName);

        // Recherche exacte
        float[] pos = stationPositions.get(normalized);
        if (pos != null) {
            Log.d(TAG, "findStationPos: '" + stopName + "' → normalized='" + normalized + "' → EXACT MATCH");
            return pos;
        }

        // Recherche partielle
        for (Map.Entry<String, float[]> entry : stationPositions.entrySet()) {
            if (entry.getKey().contains(normalized) || normalized.contains(entry.getKey())) {
                Log.d(TAG, "findStationPos: '" + stopName + "' → normalized='" + normalized + "' → PARTIAL MATCH avec '" + entry.getKey() + "'");
                return entry.getValue();
            }
        }

        // Recherche par mot-clé
        for (Map.Entry<String, float[]> entry : stationPositions.entrySet()) {
            String[] words = normalized.split("\\s+");
            for (String word : words) {
                if (word.length() > 3 && entry.getKey().contains(word)) {
                    Log.d(TAG, "findStationPos: '" + stopName + "' → normalized='" + normalized + "' → KEYWORD MATCH mot='" + word + "' avec '" + entry.getKey() + "'");
                    return entry.getValue();
                }
            }
        }

        // Log des stations connues pour diagnostic
        Log.w(TAG, "findStationPos: '" + stopName + "' → normalized='" + normalized + "' → AUCUNE CORRESPONDANCE. Stations connues: " + stationPositions.keySet());
        return null;
    }
}
