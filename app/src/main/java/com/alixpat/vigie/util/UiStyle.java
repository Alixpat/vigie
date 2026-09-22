package com.alixpat.vigie.util;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

/**
 * Petites briques de rendu communes à tous les onglets : la pastille de statut
 * et la pastille ronde d'état. Les couleurs arrivent déjà résolues (ressources
 * {@code values} / {@code values-night}), jamais en constante.
 */
public final class UiStyle {

    /** Opacité du fond d'une pastille : une teinte estompée de sa propre couleur. */
    private static final int PILL_BACKGROUND_ALPHA = 0x26;

    private UiStyle() {}

    /**
     * Habille un texte en pastille : texte de la couleur donnée, sur un fond de
     * la même couleur estompée. Un fond neuf à chaque appel — muter le drawable
     * du layout le partagerait entre toutes les lignes de la liste.
     */
    public static void pill(TextView view, int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(view.getResources().getDisplayMetrics().density * 100);
        background.setColor(ColorUtils.setAlphaComponent(color, PILL_BACKGROUND_ALPHA));
        view.setBackground(background);
        view.setTextColor(color);
    }

    /**
     * Colore la pastille ronde d'état ({@code circle_indicator}). {@code mutate()}
     * est indispensable : sans lui, toutes les pastilles issues du même drawable
     * partagent leur couleur, et la dernière liée repeint les autres.
     */
    public static void dot(View view, int color) {
        ((GradientDrawable) view.getBackground().mutate()).setColor(color);
    }
}
