package de.kai_morich.simple_bluetooth_terminal.util;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.R;

/** Formato de números y textos de la vista Cliente. */
public final class Fmt {

    private Fmt() {}

    public static String n0(double v) { return String.format(Locale.US, "%,.0f", v); }

    public static String n1(double v) { return String.format(Locale.US, "%,.1f", v); }

    public static String hhmm(long ms) { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms)); }

    /** "6,290" + " kWh" con la unidad más chica (relSize) y gris. */
    public static CharSequence withUnit(Context c, String number, String unit, float relSize) {
        SpannableString s = new SpannableString(number + unit);
        s.setSpan(new RelativeSizeSpan(relSize), number.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(ContextCompat.getColor(c, R.color.am_t777)), number.length(), s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }
}
