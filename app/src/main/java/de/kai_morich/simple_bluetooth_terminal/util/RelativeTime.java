package de.kai_morich.simple_bluetooth_terminal.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** "hace 5 días" en lugar de "2026-10-10T00:00:00 11:34:10". */
public final class RelativeTime {

    private RelativeTime() {}

    /** @param lastDate "2026-10-10T00:00:00" (solo se usa la fecha) @param lastHour "11:34:10" */
    public static String ago(String lastDate, String lastHour) {
        long ts = parse(lastDate, lastHour);
        if (ts < 0) return "sin datos";
        long s = Math.max(0, (System.currentTimeMillis() - ts) / 1000);
        if (s < 10) return "ahora";
        if (s < 60) return "hace " + s + " s";
        if (s < 3600) return "hace " + (s / 60) + " min";
        if (s < 86400) return "hace " + (s / 3600) + " h";
        long d = s / 86400;
        return d == 1 ? "hace 1 día" : "hace " + d + " días";
    }

    /** Epoch ms, o -1 si no se puede interpretar. */
    public static long parse(String lastDate, String lastHour) {
        if (lastDate == null || lastDate.length() < 10) return -1;
        String hour = lastHour != null && lastHour.length() >= 5 ? lastHour : "00:00:00";
        if (hour.length() == 5) hour += ":00";
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .parse(lastDate.substring(0, 10) + " " + hour.substring(0, 8)).getTime();
        } catch (ParseException | StringIndexOutOfBoundsException e) {
            return -1;
        }
    }
}
