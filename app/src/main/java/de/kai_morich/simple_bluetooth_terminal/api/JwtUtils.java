package de.kai_morich.simple_bluetooth_terminal.api;

import android.util.Base64;

import org.json.JSONObject;

/**
 * Utilidades para decodificar el JWT y extraer claims.
 *
 * El claim "cfg" determina permisos (objeto JSON con isInstallerAdmin, IdCustomer...).
 */
public class JwtUtils {

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

    /**
     * Retorna true si el claim cfg.isInstallerAdmin == true.
     */
    public static boolean isInstallerAdmin(String token) {
        JSONObject payload = getPayload(token);
        if (payload == null) return false;
        try {
            if (payload.has("cfg")) {
                JSONObject cfg = parseCfg(payload);
                if (cfg != null && cfg.has("isInstallerAdmin")) {
                    return cfg.getBoolean("isInstallerAdmin");
                }
            }
        } catch (Exception e) { /* ignore */ }
        return false;
    }

    /**
     * Obtiene el IdCustomer del JWT.
     * Primero busca en el claim "cfg", luego directamente en el payload.
     */
    public static Integer getIdCustomer(String token) {
        JSONObject payload = getPayload(token);
        if (payload == null) return null;
        try {
            // Buscar en cfg primero
            JSONObject cfg = parseCfg(payload);
            if (cfg != null && cfg.has("IdCustomer") && !cfg.isNull("IdCustomer")) {
                return cfg.getInt("IdCustomer");
            }
            // Fallback: payload raíz
            if (payload.has("IdCustomer") && !payload.isNull("IdCustomer")) {
                return payload.getInt("IdCustomer");
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    /** Obtiene el nombre del usuario del token. */
    public static String getUserName(String token) {
        JSONObject payload = getPayload(token);
        if (payload == null) return null;
        try {
            if (payload.has("Name") && !payload.isNull("Name"))
                return payload.getString("Name");
            if (payload.has("name") && !payload.isNull("name"))
                return payload.getString("name");
            if (payload.has("unique_name") && !payload.isNull("unique_name"))
                return payload.getString("unique_name");
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    // -----------------------------------------------
    // Helpers privados
    // -----------------------------------------------

    private static JSONObject parseCfg(JSONObject payload) {
        try {
            if (!payload.has("cfg")) return null;
            Object cfgRaw = payload.get("cfg");
            if (cfgRaw instanceof JSONObject) return (JSONObject) cfgRaw;
            // Si llega como String
            return new JSONObject(cfgRaw.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
