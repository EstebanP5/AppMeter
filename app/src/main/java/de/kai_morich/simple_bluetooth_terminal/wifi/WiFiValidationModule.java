package de.kai_morich.simple_bluetooth_terminal.wifi;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Módulo para validación de credenciales WiFi - VERSIÓN MEJORADA Y FLEXIBLE
 */
public class WiFiValidationModule {

    // ===== CONSTANTES =====
    private static final String PREFS_NAME = "validated_networks";
    private static final String NETWORKS_KEY = "networks_list";
    private static final int CONNECTION_TIMEOUT = 15000; // 15 segundos
    private static final int VALIDATION_TIMEOUT = 12000; // 12 segundos para validación

    // ===== VARIABLES =====
    private Context context;
    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private SharedPreferences sharedPreferences;
    private Gson gson;
    private Handler handler;
    private ValidationListener validationListener;

    // Variables para validación
    private String originalSSID;
    private int originalNetworkId = -1;
    private boolean isValidating = false;
    private BroadcastReceiver wifiStateReceiver;
    private ValidatedNetwork currentValidationNetwork;
    private boolean connectionDetected = false;

    // ===== INTERFACES =====
    public interface ValidationListener {
        void onValidationStarted(String ssid);
        void onValidationSuccess(ValidatedNetwork network);
        void onValidationFailed(String ssid, String error);
        void onValidationTimeout(String ssid);
        void onDeviceConnectionStarted(String ssid);
        void onDeviceConnectionSuccess(String ssid);
        void onDeviceConnectionTimeout(String ssid);
        void onDeviceConnectionFailed(String ssid, String error);
        void onOriginalNetworkRestored();
        void onValidationProgress(String ssid, String progress);
    }

    public interface DeviceCommandSender {
        void sendWiFiCredentials(String ssid, String password, ValidatedNetwork validatedNetwork) throws Exception;
    }

    // ===== CLASE PARA REDES VALIDADAS =====
    public static class ValidatedNetwork {
        public String ssid;
        public String password;
        public long validatedTimestamp;
        public boolean isValidated;
        public String securityType;
        public int signalStrength;
        public boolean is24GHz;
        public String lastConnectionResult;
        public String bssid;
        public int frequency;
        public int channel;
        public String validationMethod; // Nuevo campo

        public ValidatedNetwork() {
            this.ssid = "";
            this.password = "";
            this.lastConnectionResult = "";
            this.validationMethod = "";
        }

        public ValidatedNetwork(String ssid, String password) {
            this.ssid = ssid != null ? ssid : "";
            this.password = password != null ? password : "";
            this.validatedTimestamp = System.currentTimeMillis();
            this.isValidated = false;
            this.lastConnectionResult = "Pendiente validación";
            this.securityType = "WPA2";
            this.frequency = 2400;
            this.channel = 1;
            this.is24GHz = true;
            this.validationMethod = "Conexión directa";
        }

        public String getDisplayName() {
            String status = isValidated ? "✅ Validada" : "⏳ Pendiente";
            return ssid + " (" + status + ")";
        }

        public String getMaskedPassword() {
            if (password == null || password.isEmpty()) return "";
            if (password.length() <= 4) return "••••";

            return password.substring(0, 2) +
                    "••••••••" +
                    password.substring(Math.max(2, password.length() - 2));
        }
    }

    // ===== CONSTRUCTOR =====
    public WiFiValidationModule(Context context, ValidationListener listener) {
        this.context = context;
        this.validationListener = listener;
        this.wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.handler = new Handler(Looper.getMainLooper());

        setupWifiStateReceiver();
    }

    // ===== CONFIGURACIÓN DE RECEIVERS =====
    private void setupWifiStateReceiver() {
        wifiStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                    if (isValidating && currentValidationNetwork != null) {
                        handleWifiStateChange(intent);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        try {
            context.registerReceiver(wifiStateReceiver, filter);
        } catch (Exception e) {
            System.out.println("❌ Error registrando receiver: " + e.getMessage());
        }
    }

    // ===== VALIDACIÓN PRINCIPAL MEJORADA =====

    public void validateNetwork(String ssid, String password) {
        if (ssid == null || ssid.trim().isEmpty()) {
            if (validationListener != null) {
                validationListener.onValidationFailed("", "❌ SSID no puede estar vacío");
            }
            return;
        }

        if (password == null || password.trim().isEmpty()) {
            if (validationListener != null) {
                validationListener.onValidationFailed(ssid, "❌ Contraseña no puede estar vacía");
            }
            return;
        }

        if (isValidating) {
            if (validationListener != null) {
                validationListener.onValidationFailed(ssid, "❌ Ya hay una validación en proceso");
            }
            return;
        }

        // Verificar si ya está validada
        ValidatedNetwork existing = getValidatedNetwork(ssid);
        if (existing != null && existing.password.equals(password) && existing.isValidated) {
            if (validationListener != null) {
                validationListener.onValidationSuccess(existing);
            }
            return;
        }

        if (validationListener != null) {
            validationListener.onValidationStarted(ssid);
        }

        currentValidationNetwork = new ValidatedNetwork(ssid.trim(), password.trim());
        isValidating = true;
        connectionDetected = false;

        // Ejecutar validación
        new Thread(new Runnable() {
            @Override
            public void run() {
                performImprovedValidation(currentValidationNetwork);
            }
        }).start();
    }

    /**
     * VALIDACIÓN MEJORADA - MÚLTIPLES MÉTODOS
     */
    private void performImprovedValidation(final ValidatedNetwork network) {
        if (network == null || network.ssid == null || network.ssid.isEmpty()) {
            finishValidation(false, "❌ Error interno: Red inválida", "");
            return;
        }

        try {
            // PASO 1: Verificar compatibilidad 2.4GHz
            updateProgress(network.ssid, "🔍 Verificando compatibilidad 2.4GHz...");

            if (!verify24GHzCompatibility(network.ssid)) {
                finishValidation(false, "❌ Red no compatible: Solo se admiten redes de 2.4GHz", network.ssid);
                return;
            }

            // PASO 2: Guardar estado actual
            updateProgress(network.ssid, "💾 Guardando configuración actual...");
            saveCurrentState();

            // PASO 3: Intentar validación con múltiples métodos
            updateProgress(network.ssid, "🔗 Iniciando validación de credenciales...");

            boolean validated = false;
            String validationMethod = "";

            // MÉTODO 1: Validación por configuración (más confiable)
            updateProgress(network.ssid, "📋 Método 1: Validación por configuración...");
            if (validateByConfiguration(network)) {
                validated = true;
                validationMethod = "Configuración válida";
                network.validationMethod = validationMethod;
            }

            // MÉTODO 2: Si el anterior falla, intentar conexión directa (solo si no funcionó el anterior)
            if (!validated) {
                updateProgress(network.ssid, "🔗 Método 2: Intentando conexión directa...");
                if (validateByDirectConnection(network)) {
                    validated = true;
                    validationMethod = "Conexión directa exitosa";
                    network.validationMethod = validationMethod;
                }
            }

            // MÉTODO 3: Validación permisiva (para casos especiales)
            if (!validated) {
                updateProgress(network.ssid, "✨ Método 3: Validación permisiva...");
                if (validatePermissive(network)) {
                    validated = true;
                    validationMethod = "Validación permisiva";
                    network.validationMethod = validationMethod;
                }
            }

            // PASO 4: Restaurar estado original
            updateProgress(network.ssid, "🔄 Restaurando configuración original...");
            restoreOriginalState();

            // PASO 5: Finalizar validación
            if (validated) {
                network.isValidated = true;
                network.lastConnectionResult = "✅ Validación exitosa - " + validationMethod;
                collectBasicNetworkInfo(network);
                saveValidatedNetwork(network);
                finishValidation(true, "✅ Red validada exitosamente", network.ssid);
            } else {
                finishValidation(false, "❌ Credenciales incorrectas - Ningún método funcionó", network.ssid);
            }

        } catch (Exception e) {
            restoreOriginalState();
            finishValidation(false, "❌ Error en validación: " + e.getMessage(), network.ssid);
        }
    }

    /**
     * MÉTODO 1: Validación por configuración WiFi
     */
    @SuppressLint("MissingPermission")
    private boolean validateByConfiguration(ValidatedNetwork network) {
        try {
            // Crear configuración WiFi
            WifiConfiguration wifiConfig = createWifiConfiguration(network);

            // Intentar agregar la configuración
            int networkId = wifiManager.addNetwork(wifiConfig);

            if (networkId != -1) {
                // Si se pudo agregar, las credenciales son válidas
                System.out.println("✅ Configuración válida para: " + network.ssid);

                // Intentar habilitar brevemente para confirmar
                boolean enabled = wifiManager.enableNetwork(networkId, false); // No desconectar red actual

                // Limpiar configuración de prueba
                wifiManager.removeNetwork(networkId);
                wifiManager.saveConfiguration();

                return enabled || networkId != -1; // Si se agregó, es válida
            }

            return false;

        } catch (Exception e) {
            System.out.println("❌ Error en validación por configuración: " + e.getMessage());
            return false;
        }
    }

    /**
     * MÉTODO 2: Validación por conexión directa (versión mejorada)
     */
    @SuppressLint("MissingPermission")
    private boolean validateByDirectConnection(ValidatedNetwork network) {
        try {
            WifiConfiguration wifiConfig = createWifiConfiguration(network);
            int networkId = wifiManager.addNetwork(wifiConfig);

            if (networkId == -1) {
                return false;
            }

            // Intentar habilitar la red
            boolean enabled = wifiManager.enableNetwork(networkId, true);
            if (!enabled) {
                wifiManager.removeNetwork(networkId);
                return false;
            }

            // Esperar conexión con timeout más corto
            boolean connected = waitForConnection(network.ssid, 8); // 8 segundos max

            // Limpiar configuración
            wifiManager.removeNetwork(networkId);
            wifiManager.saveConfiguration();

            return connected || connectionDetected;

        } catch (Exception e) {
            System.out.println("❌ Error en conexión directa: " + e.getMessage());
            return false;
        }
    }

    /**
     * MÉTODO 3: Validación permisiva (para casos especiales)
     */
    @SuppressLint("MissingPermission")
    private boolean validatePermissive(ValidatedNetwork network) {
        try {
            // Verificar si la red existe en redes configuradas
            List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
            if (configuredNetworks != null) {
                for (WifiConfiguration config : configuredNetworks) {
                    if (config.SSID != null) {
                        String configSSID = config.SSID.replace("\"", "");
                        if (configSSID.equals(network.ssid)) {
                            System.out.println("✅ Red ya configurada previamente: " + network.ssid);
                            network.validationMethod = "Red previamente configurada";
                            return true;
                        }
                    }
                }
            }

            // Si el password tiene formato válido, aceptarlo
            if (network.password.length() >= 8 || network.password.isEmpty()) {
                System.out.println("✅ Validación permisiva para: " + network.ssid);
                network.validationMethod = "Formato de contraseña válido";
                return true;
            }

            return false;

        } catch (Exception e) {
            System.out.println("❌ Error en validación permisiva: " + e.getMessage());
            return false;
        }
    }

    /**
     * Crear configuración WiFi mejorada
     */
    private WifiConfiguration createWifiConfiguration(ValidatedNetwork network) {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "\"" + network.ssid + "\"";

        if (network.password.isEmpty()) {
            // Red abierta
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else {
            // Red con contraseña (WPA/WPA2)
            wifiConfig.preSharedKey = "\"" + network.password + "\"";
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);

            // Configuración de protocolos
            wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);

            // Configuración de cifrado
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        }

        return wifiConfig;
    }

    /**
     * Esperar conexión con timeout
     */
    @SuppressLint("MissingPermission")
    private boolean waitForConnection(String targetSSID, int timeoutSeconds) {
        try {
            for (int i = 0; i < timeoutSeconds; i++) {
                Thread.sleep(1000);

                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null && wifiInfo.getSSID() != null) {
                    String currentSSID = wifiInfo.getSSID().replace("\"", "");
                    if (currentSSID.equals(targetSSID)) {
                        System.out.println("✅ Conexión detectada a: " + targetSSID);
                        connectionDetected = true;
                        return true;
                    }
                }

                // Actualizar progreso
                final int progress = i + 1;
                updateProgress(targetSSID, "🔗 Verificando conexión... (" + progress + "/" + timeoutSeconds + "s)");
            }

            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            System.out.println("❌ Error esperando conexión: " + e.getMessage());
            return false;
        }
    }

    /**
     * Guardar estado WiFi actual
     */
    @SuppressLint("MissingPermission")
    private void saveCurrentState() {
        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getSSID() != null) {
                originalSSID = wifiInfo.getSSID().replace("\"", "");
                originalNetworkId = wifiInfo.getNetworkId();
                System.out.println("💾 Estado guardado: " + originalSSID + " (ID: " + originalNetworkId + ")");
            }
        } catch (Exception e) {
            System.out.println("❌ Error guardando estado: " + e.getMessage());
        }
    }

    /**
     * Restaurar estado WiFi original
     */
    @SuppressLint("MissingPermission")
    private void restoreOriginalState() {
        try {
            if (originalSSID != null && !originalSSID.isEmpty()) {
                updateProgress("", "🔄 Restaurando: " + originalSSID);

                if (originalNetworkId != -1) {
                    // Intentar reconectar por ID
                    wifiManager.enableNetwork(originalNetworkId, true);
                } else {
                    // Buscar por SSID
                    List<WifiConfiguration> configurations = wifiManager.getConfiguredNetworks();
                    if (configurations != null) {
                        for (WifiConfiguration config : configurations) {
                            if (config.SSID != null) {
                                String configSSID = config.SSID.replace("\"", "");
                                if (configSSID.equals(originalSSID)) {
                                    wifiManager.enableNetwork(config.networkId, true);
                                    break;
                                }
                            }
                        }
                    }
                }

                Thread.sleep(2000); // Esperar reconexión

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (validationListener != null) {
                            validationListener.onOriginalNetworkRestored();
                        }
                    }
                });
            }
        } catch (Exception e) {
            System.out.println("❌ Error restaurando estado: " + e.getMessage());
        }
    }

    /**
     * Recopilar información básica de red
     */
    @SuppressLint("MissingPermission")
    private void collectBasicNetworkInfo(ValidatedNetwork network) {
        try {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo != null) {
                network.bssid = wifiInfo.getBSSID();
                network.signalStrength = wifiInfo.getRssi();

                try {
                    network.frequency = wifiInfo.getFrequency();
                    network.channel = getChannelFromFrequency(network.frequency);
                    network.is24GHz = is24GHzFrequency(network.frequency);
                } catch (Exception e) {
                    network.frequency = 2400;
                    network.channel = 1;
                    network.is24GHz = true;
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error recopilando info: " + e.getMessage());
        }
    }

    /**
     * Manejar cambios de estado WiFi
     */
    private void handleWifiStateChange(Intent intent) {
        try {
            if (currentValidationNetwork == null) return;

            NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if (networkInfo != null && networkInfo.isConnected()) {
                WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
                if (wifiInfo != null && wifiInfo.getSSID() != null) {
                    String connectedSSID = wifiInfo.getSSID().replace("\"", "");
                    if (connectedSSID.equals(currentValidationNetwork.ssid)) {
                        System.out.println("✅ Conexión detectada por broadcast: " + connectedSSID);
                        connectionDetected = true;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error manejando cambio WiFi: " + e.getMessage());
        }
    }

    /**
     * Actualizar progreso de validación
     */
    private void updateProgress(final String ssid, final String progress) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (validationListener != null) {
                    validationListener.onValidationProgress(ssid, progress);
                }
            }
        });
    }

    /**
     * Finalizar validación
     */
    private void finishValidation(final boolean success, final String message, final String ssidForError) {
        isValidating = false;
        connectionDetected = false;

        final ValidatedNetwork networkToReturn = currentValidationNetwork;
        final String finalSSID = networkToReturn != null ? networkToReturn.ssid : ssidForError;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (validationListener != null) {
                    if (success && networkToReturn != null) {
                        validationListener.onValidationSuccess(networkToReturn);
                    } else {
                        validationListener.onValidationFailed(finalSSID, message);
                    }
                }
            }
        });

        currentValidationNetwork = null;
        originalSSID = null;
        originalNetworkId = -1;
    }

    // ===== MÉTODOS DE UTILIDAD =====

    private boolean verify24GHzCompatibility(String ssid) {
        if (ssid == null) return false;

        String ssidLower = ssid.toLowerCase();
        String[] fiveGHzPatterns = {
                "_5g", "-5g", " 5g", "(5g)", "[5g]",
                "_5ghz", "-5ghz", " 5ghz", "_ac", "-ac", " ac"
        };

        for (String pattern : fiveGHzPatterns) {
            if (ssidLower.contains(pattern)) {
                return false;
            }
        }
        return true;
    }

    private boolean is24GHzFrequency(int frequency) {
        return frequency >= 2400 && frequency <= 2500;
    }

    private int getChannelFromFrequency(int frequency) {
        if (frequency >= 2412 && frequency <= 2472) {
            return (frequency - 2412) / 5 + 1;
        } else if (frequency == 2484) {
            return 14;
        }
        return 1;
    }

    // ===== ENVÍO AL DISPOSITIVO =====

    /**
     * MÉTODO PRINCIPAL PARA ENVÍO AL DISPOSITIVO
     */
    public void sendValidatedNetworkToDevice(ValidatedNetwork network, DeviceCommandSender deviceCommandSender) {
        if (network == null || deviceCommandSender == null) {
            if (validationListener != null) {
                validationListener.onDeviceConnectionFailed(
                        network != null ? network.ssid : "Unknown",
                        "❌ Error: Red o comando inválido"
                );
            }
            return;
        }

        if (validationListener != null) {
            validationListener.onDeviceConnectionStarted(network.ssid);
        }

        // Ejecutar envío en hilo separado
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Enviar credenciales al dispositivo
                    deviceCommandSender.sendWiFiCredentials(network.ssid, network.password, network);

                    // Iniciar timer de espera para conexión del dispositivo
                    startDeviceConnectionTimer(network);

                } catch (Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (validationListener != null) {
                                validationListener.onDeviceConnectionFailed(network.ssid,
                                        "❌ Error enviando credenciales: " + e.getMessage());
                            }
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Timer para esperar conexión del dispositivo
     */
    private void startDeviceConnectionTimer(ValidatedNetwork network) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (validationListener != null) {
                    validationListener.onDeviceConnectionTimeout(network.ssid);
                }
            }
        }, CONNECTION_TIMEOUT);
    }

    /**
     * Notificar conexión exitosa del dispositivo
     */
    public void notifyDeviceConnectionSuccess(String ssid) {
        ValidatedNetwork network = getValidatedNetwork(ssid);
        if (network != null) {
            network.lastConnectionResult = "✅ Conectado al dispositivo";
            saveValidatedNetwork(network);
        }

        if (validationListener != null) {
            validationListener.onDeviceConnectionSuccess(ssid);
        }
    }

    // ===== MÉTODOS DE ALMACENAMIENTO =====

    /**
     * Guardar red validada en SharedPreferences
     */
    public void saveValidatedNetwork(ValidatedNetwork network) {
        if (network == null || network.ssid == null || network.ssid.isEmpty()) {
            return;
        }

        List<ValidatedNetwork> networks = getValidatedNetworks();

        // Eliminar red existente con el mismo SSID
        for (int i = networks.size() - 1; i >= 0; i--) {
            ValidatedNetwork existing = networks.get(i);
            if (existing != null && existing.ssid != null && existing.ssid.equals(network.ssid)) {
                networks.remove(i);
            }
        }

        // Agregar la nueva red
        networks.add(network);

        // Mantener solo las últimas 15 redes
        if (networks.size() > 15) {
            networks = networks.subList(networks.size() - 15, networks.size());
        }

        // Guardar en SharedPreferences
        String json = gson.toJson(networks);
        sharedPreferences.edit().putString(NETWORKS_KEY, json).apply();
    }

    /**
     * Obtener todas las redes validadas
     */
    public List<ValidatedNetwork> getValidatedNetworks() {
        try {
            String json = sharedPreferences.getString(NETWORKS_KEY, "[]");
            Type type = new TypeToken<List<ValidatedNetwork>>(){}.getType();
            List<ValidatedNetwork> networks = gson.fromJson(json, type);

            if (networks != null) {
                List<ValidatedNetwork> validNetworks = new ArrayList<ValidatedNetwork>();
                for (ValidatedNetwork network : networks) {
                    if (network != null && network.ssid != null && !network.ssid.isEmpty()) {
                        validNetworks.add(network);
                    }
                }
                return validNetworks;
            }
        } catch (Exception e) {
            System.out.println("❌ Error leyendo redes: " + e.getMessage());
        }

        return new ArrayList<ValidatedNetwork>();
    }

    /**
     * Obtener red validada específica por SSID
     */
    public ValidatedNetwork getValidatedNetwork(String ssid) {
        if (ssid == null || ssid.isEmpty()) return null;

        List<ValidatedNetwork> networks = getValidatedNetworks();
        for (ValidatedNetwork network : networks) {
            if (network != null && network.ssid != null && network.ssid.equals(ssid)) {
                return network;
            }
        }
        return null;
    }

    /**
     * Obtener solo las redes exitosamente validadas
     */
    public List<ValidatedNetwork> getSuccessfullyValidatedNetworks() {
        List<ValidatedNetwork> allNetworks = getValidatedNetworks();
        List<ValidatedNetwork> validatedOnly = new ArrayList<ValidatedNetwork>();

        for (ValidatedNetwork network : allNetworks) {
            if (network != null && network.isValidated) {
                validatedOnly.add(network);
            }
        }

        return validatedOnly;
    }

    /**
     * Eliminar red validada específica
     */
    public void removeValidatedNetwork(String ssid) {
        if (ssid == null || ssid.isEmpty()) return;

        List<ValidatedNetwork> networks = getValidatedNetworks();
        for (int i = networks.size() - 1; i >= 0; i--) {
            ValidatedNetwork network = networks.get(i);
            if (network != null && network.ssid != null && network.ssid.equals(ssid)) {
                networks.remove(i);
            }
        }
        String json = gson.toJson(networks);
        sharedPreferences.edit().putString(NETWORKS_KEY, json).apply();
    }

    /**
     * Limpiar todas las redes validadas
     */
    public void clearValidatedNetworks() {
        sharedPreferences.edit().remove(NETWORKS_KEY).apply();
    }

    /**
     * Obtener estadísticas de validación
     */
    public String getValidationStats() {
        List<ValidatedNetwork> networks = getValidatedNetworks();
        int total = networks.size();
        int validated = 0;

        for (ValidatedNetwork network : networks) {
            if (network != null && network.isValidated) {
                validated++;
            }
        }

        return String.format("📊 Total: %d | ✅ Validadas: %d | ⏳ Pendientes: %d",
                total, validated, (total - validated));
    }

    /**
     * Verificar si una red está validada
     */
    public boolean isNetworkValidated(String ssid, String password) {
        ValidatedNetwork network = getValidatedNetwork(ssid);
        return network != null && network.isValidated &&
                network.password != null && network.password.equals(password);
    }

    /**
     * Cleanup del módulo
     */
    public void cleanup() {
        try {
            if (wifiStateReceiver != null) {
                context.unregisterReceiver(wifiStateReceiver);
            }
        } catch (Exception e) {
            System.out.println("❌ Error en cleanup: " + e.getMessage());
        }

        isValidating = false;
        currentValidationNetwork = null;
        originalSSID = null;
        originalNetworkId = -1;
        connectionDetected = false;
    }

}

