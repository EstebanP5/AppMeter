// File: ApiClient.java
package de.kai_morich.simple_bluetooth_terminal.api;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String TAG = "ApiClient";

    // URL base para ErgoSolar API
    private static final String BASE_URL = "https://ergometer.ddns.net/";

    private static Retrofit retrofit = null;
    private static TokenManager tokenManager = null;

    /**
     * Inicializa el ApiClient con el contexto de la aplicación.
     * Debe llamarse una sola vez al inicio de la aplicación.
     */
    public static void init(Context context) {
        tokenManager = new TokenManager(context.getApplicationContext());
    }

    /**
     * Devuelve la instancia del ApiService con configuración completa
     * incluyendo timeouts, logging y manejo automático de tokens.
     */
    public static ApiService getApiService() {
        if (retrofit == null) {
            retrofit = createRetrofitInstance();
        }
        return retrofit.create(ApiService.class);
    }

    /**
     * Crea la instancia de Retrofit con toda la configuración necesaria
     */
    private static Retrofit createRetrofitInstance() {
        // Configurar logging interceptor para debug
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // Crear interceptor de autenticación
        AuthInterceptor authInterceptor = new AuthInterceptor();

        // Construir OkHttpClient con timeouts y interceptors
        OkHttpClient client = new OkHttpClient.Builder()
                // Timeouts similares a ErgoSolarApiClient
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                // Interceptors
                .addInterceptor(logging)
                .addInterceptor(authInterceptor)
                .build();

        // Crear y retornar instancia de Retrofit
        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    /**
     * Interceptor personalizado para manejar autenticación automáticamente
     */
    private static class AuthInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            String token = null;

            // Obtener token si tokenManager está disponible y tiene token válido
            if (tokenManager != null && tokenManager.hasValidToken()) {
                token = tokenManager.getToken();
            }

            // Construir request con headers necesarios
            Request.Builder builder = originalRequest.newBuilder()
                    .header("Content-Type", "application/json");

            // Agregar Authorization header si hay token
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
                Log.d(TAG, "Adding Authorization header with token");
            } else {
                Log.d(TAG, "No valid token available, proceeding without Authorization header");
            }

            Request requestWithHeaders = builder.build();

            // Ejecutar request
            Response response = chain.proceed(requestWithHeaders);

            // Log del resultado
            if (response.isSuccessful()) {
                Log.d(TAG, "Request successful: " + response.code());
            } else {
                Log.w(TAG, "Request failed: " + response.code() + " - " + response.message());
            }

            return response;
        }
    }

    /**
     * Método helper para verificar si hay un token válido
     */
    public static boolean hasValidToken() {
        return tokenManager != null && tokenManager.hasValidToken();
    }

    /**
     * Método helper para obtener el token actual
     */
    public static String getCurrentToken() {
        if (tokenManager != null) {
            return tokenManager.getToken();
        }
        return null;
    }

    /**
     * Método helper para limpiar el token (logout)
     */
    public static void clearToken() {
        if (tokenManager != null) {
            tokenManager.clearToken();
        }
    }

    /**
     * Método helper para establecer un nuevo token
     */
    public static void setToken(String token) {
        if (tokenManager != null) {
            tokenManager.saveToken(token);
        }
    }

    /**
     * Reinicia la instancia de Retrofit (útil después de cambios de configuración)
     */
    public static void resetInstance() {
        retrofit = null;
    }
}