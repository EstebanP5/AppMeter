package de.kai_morich.simple_bluetooth_terminal.util;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import de.kai_morich.simple_bluetooth_terminal.R;

/** Animaciones pequeñas y reutilizables de la vista Cliente. */
public final class Anim {

    private Anim() {}

    public interface Format { CharSequence of(double value); }

    /**
     * El número "corre" desde el último valor mostrado hasta el nuevo (la primera vez, desde 0).
     * El último valor se guarda en el propio TextView para que las actualizaciones en vivo se animen suaves.
     */
    public static void countUp(final TextView tv, final double to, final Format fmt) {
        Object last = tv.getTag(R.id.tag_last_value);
        double from = last instanceof Double ? (Double) last : 0.0;
        tv.setTag(R.id.tag_last_value, to);
        Object running = tv.getTag(R.id.tag_value_anim);
        if (running instanceof ValueAnimator) ((ValueAnimator) running).cancel();
        if (Math.abs(to - from) < 1e-9) { tv.setText(fmt.of(to)); return; }
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        final double start = from;
        a.setDuration(700);
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        a.addUpdateListener(x -> tv.setText(fmt.of(start + (to - start) * (float) x.getAnimatedValue())));
        tv.setTag(R.id.tag_value_anim, a);
        a.start();
    }

    /** Cada hijo entra desde abajo con fundido, uno tras otro. */
    public static void staggerIn(ViewGroup parent) {
        int shift = Math.round(22 * parent.getResources().getDisplayMetrics().density);
        long delay = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View c = parent.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE) continue;
            c.setAlpha(0f);
            c.setTranslationY(shift);
            c.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator(1.8f)).start();
            delay += 70;
        }
    }

    /** Aparece desde la altura 0 hacia la derecha (barras horizontales del mes). */
    public static void growX(View v, long delay) {
        v.setPivotX(0f);
        v.setScaleX(0f);
        v.animate().scaleX(1f).setStartDelay(delay).setDuration(650).setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    /** Pulso continuo (punto "en vivo"). */
    public static void pulse(View v) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, View.ALPHA, 1f, 0.25f);
        a.setDuration(900);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.start();
    }

    /** Se hunde un poco al tocarlo (retroalimentación táctil), sin cambiar el click. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    public static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }
}
