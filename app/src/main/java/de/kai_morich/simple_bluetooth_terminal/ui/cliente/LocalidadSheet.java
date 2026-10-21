package de.kai_morich.simple_bluetooth_terminal.ui.cliente;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import de.kai_morich.simple_bluetooth_terminal.util.Anim;

/** Hoja para elegir localidad: una tarjeta por localidad, la que se está viendo va resaltada y con palomita. */
final class LocalidadSheet {

    private LocalidadSheet() {}

    static void show(Context ctx, List<LocationsViewModel> list, Integer currentId, Consumer<LocationsViewModel> onPick) {
        if (list == null || list.size() < 2) return;
        View root = LayoutInflater.from(ctx).inflate(R.layout.sheet_localidad, null);
        final BottomSheetDialog dialog = new BottomSheetDialog(ctx);
        ((TextView) root.findViewById(R.id.tvSheetSub)).setText(list.size() + " localidades · elige cuál quieres ver");
        LinearLayout box = root.findViewById(R.id.list);
        for (LocationsViewModel l : list) {
            View row = LayoutInflater.from(ctx).inflate(R.layout.item_localidad_row, box, false);
            boolean sel = Objects.equals(l.getIdLocation(), currentId);
            ((TextView) row.findViewById(R.id.tvName)).setText(l.getName());
            String desc = l.getDescription();
            ((TextView) row.findViewById(R.id.tvDesc)).setText(sel ? "Mostrando ahora" : desc != null && !desc.trim().isEmpty() ? desc : "Toca para ver esta localidad");
            row.findViewById(R.id.tvCheck).setVisibility(sel ? View.VISIBLE : View.INVISIBLE);
            if (sel) row.setBackgroundResource(R.drawable.am_row_sel);
            Anim.pressable(row);
            row.setOnClickListener(x -> {
                dialog.dismiss();
                if (!sel) onPick.accept(l);
            });
            box.addView(row);
        }
        // Con muchas localidades la lista scrollea en vez de crecer sin límite
        if (list.size() > 5) {
            ScrollView sv = (ScrollView) box.getParent();
            ViewGroup.LayoutParams lp = sv.getLayoutParams();
            lp.height = Math.round(400 * ctx.getResources().getDisplayMetrics().density);
            sv.setLayoutParams(lp);
        }
        dialog.setContentView(root);
        dialog.setOnShowListener(x -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }
}
