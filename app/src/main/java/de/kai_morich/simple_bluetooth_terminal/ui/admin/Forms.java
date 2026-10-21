package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.content.Context;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import retrofit2.Response;

/** Formularios de texto sencillos y mensajes de error de la configuración del admin. */
public final class Forms {

    private Forms() {}

    public interface OnSave { void save(String[] values); }

    /** Diálogo con un campo por etiqueta. Si algún campo obligatorio (índice &lt; required) queda vacío no cierra. */
    public static void show(Context ctx, String title, String[] labels, String[] initial, int required, String positive, OnSave cb) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * ctx.getResources().getDisplayMetrics().density);
        box.setPadding(pad, Math.round(8 * ctx.getResources().getDisplayMetrics().density), pad, 0);
        final EditText[] fields = new EditText[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView l = new TextView(ctx);
            l.setText(labels[i] + (i < required ? "" : " · opcional"));
            l.setTextSize(12);
            l.setTextColor(0xFF666666);
            l.setPadding(0, Math.round(10 * ctx.getResources().getDisplayMetrics().density), 0, 0);
            box.addView(l);
            EditText e = new EditText(ctx);
            e.setInputType(labels[i].toLowerCase().contains("correo") ? InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT
                    : labels[i].toLowerCase().contains("teléfono") ? InputType.TYPE_CLASS_PHONE : InputType.TYPE_CLASS_TEXT);
            e.setText(initial != null && i < initial.length && initial[i] != null ? initial[i] : "");
            e.setSingleLine(true);
            box.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            fields[i] = e;
        }
        final androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(positive, null)
                .create();
        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String[] out = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                out[i] = fields[i].getText().toString().trim();
                if (i < required && out[i].isEmpty()) {
                    fields[i].setError("Obligatorio");
                    fields[i].requestFocus();
                    return;
                }
            }
            d.dismiss();
            cb.save(out);
        }));
        d.show();
    }

    public static void confirm(Context ctx, String title, String message, String positive, Runnable onYes) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(positive, (d, w) -> onYes.run())
                .show();
    }

    public static void toast(Context ctx, String msg) {
        if (ctx != null) Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }

    /** "Código · cuerpo del error" para mostrar por qué falló una llamada. */
    public static String error(String what, Response<?> r) {
        String body = "";
        try {
            if (r != null && r.errorBody() != null) body = r.errorBody().string();
        } catch (IOException ignored) { }
        if (body.length() > 160) body = body.substring(0, 160);
        return what + (r != null ? " (" + r.code() + ")" : "") + (body.isEmpty() ? "" : ": " + body);
    }
}
