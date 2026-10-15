// app/src/main/java/de/kai_morich/simple_bluetooth_terminal/Constants.java
package de.kai_morich.simple_bluetooth_terminal;

class Constants {
    // values have to be globally unique
    private static final String APP_ID = "de.kai_morich.simple_bluetooth_terminal";

    static final String INTENT_ACTION_DISCONNECT      = APP_ID + ".Disconnect";
    static final String NOTIFICATION_CHANNEL          = APP_ID + ".Channel";
    static final String INTENT_CLASS_MAIN_ACTIVITY    = APP_ID + ".MainActivity";

    // values have to be unique within each app
    static final int NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001;

    // Prefijos del AP que levanta el medidor: los equipos viejos son ESP*, los nuevos METER*
    private static final String[] METER_AP_PREFIXES = {"ESP", "METER"};

    /** true si el SSID corresponde al access point del medidor */
    static boolean isMeterAp(String ssid) {
        if (ssid == null) return false;
        String s = ssid.trim().toUpperCase();
        for (String prefix : METER_AP_PREFIXES) {
            if (s.startsWith(prefix)) return true;
        }
        return false;
    }

    private Constants() {}
}
