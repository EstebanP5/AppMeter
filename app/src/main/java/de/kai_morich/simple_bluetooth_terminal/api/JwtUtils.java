package de.kai_morich.simple_bluetooth_terminal.api;

import android.util.Base64;

import org.json.JSONObject;

/**
 * Utilidades para decodificar el JWT y extraer claims.
 *
 * Formato real del servidor (ergometer.ddns.net):
 *   nameidentifier = IdClient · emailaddress = correo · cfg = máscara de 8 bits como texto ("11101000").
 * Se sigue aceptando el formato JSON anterior en cfg (isInstallerAdmin / IdCustomer) por compatibilidad.
 */
public class JwtUtils {

    private static final String CLAIM_ID = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier";
    private static final String CLAIM_EMAIL = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";

    /**
     * Posición del bit de "ver otros clientes" (instalador/admin) en la máscara cfg.
     * Con los tokens reales: admin@ems.mx = "11101000", un cliente = "01011000". Es una deducción:
     * confirmar con el backend si el bit de admin es otro.
     */
    private static final int ADMIN_BIT = 0;

    private JwtUtils() { /* static utility class */ }

    /** Decodifica y parsea el payload del JWT (parte central, codificada en Base64URL). */
    public static JSONObject getPayload(String token) {
        if (token == null || token.isEmpty()) return null;
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_PADDING);
            return new JSONObject(new String(decoded, "UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    /** true si el usuario es instalador/admin (puede ver a otros clientes). */
    public static boolean isInstallerAdmin(String token) {
        JSONObject payload = getPayload(token);
        if (payload == null) return false;
        try {
            // Formato anterior: cfg como objeto JSON
            JSONObject cfg = parseCfg(payload);
            if (cfg != null && cfg.has("isInstallerAdmin")) {
                return cfg.getBoolean("isInstallerAdmin");
            }
            // Formato actual: cfg como máscara de bits en texto
            String mask = payload.optString("cfg", "");
            return mask.length() > ADMIN_BIT && mask.charAt(ADMIN_BIT) == '1';
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Id del cliente dueño del token. Orden: cfg.IdCustomer (formato anterior), claims IdCustomer / IdClient
     * y, en el formato actual, el claim nameidentifier.
     */
    public static Integer getIdCustomer(String token) {
        JSONObject payload = getPayload(token);
        if (payload == null) return null;
        try {
            JSONObject cfg = parseCfg(payload);
            if (cfg != null && cfg.has("IdCustomer") && !cfg.isNull("IdCustomer")) {
                return cfg.getInt("IdCustomer");
            }
            for (String key : new String[]{"IdCustomer", "IdClient", CLAIM_ID}) {
                if (payload.has(key) && !payload.isNull(key)) {
                    return Integer.parseInt(payload.get(key).toString().trim());
                }
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    /** Nombre del usuario; si el token no lo trae, el correo. */
    public static String getUserName(String token) {
        JSONObject payload = getPayload(token);
        if (payload == null) return null;
        for (String key : new String[]{"Name", "name", "unique_name", CLAIM_EMAIL}) {
            if (payload.has(key) && !payload.isNull(key)) return payload.optString(key, null);
        }
        return null;
    }

    // -----------------------------------------------
    // Helpers privados
    // -----------------------------------------------

    /** cfg como objeto JSON (formato anterior); null si es una máscara de bits o no existe. */
    private static JSONObject parseCfg(JSONObject payload) {
        try {
            if (!payload.has("cfg")) return null;
            Object cfgRaw = payload.get("cfg");
            if (cfgRaw instanceof JSONObject) return (JSONObject) cfgRaw;
            return new JSONObject(cfgRaw.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
