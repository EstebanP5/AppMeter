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

    private Constants() {}
}
