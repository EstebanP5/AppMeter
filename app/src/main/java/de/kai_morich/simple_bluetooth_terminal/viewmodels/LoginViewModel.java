package de.kai_morich.simple_bluetooth_terminal.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.Credentials;
import de.kai_morich.simple_bluetooth_terminal.models.LoginResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginViewModel extends ViewModel {

    private final MutableLiveData<LoginResponse> loginResult = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>();
    private Call<LoginResponse> currentCall;

    public void loginUser(String email, String password) {
        loading.setValue(true);
        cancelCurrentRequest();

        ApiService apiService = ApiClient.getApiService();
        currentCall = apiService.performLogin(new Credentials(email, password));

        currentCall.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                loading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    loginResult.setValue(response.body());
                } else {
                    handleErrorResponse(response.code());
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                if (!call.isCanceled()) {
                    loading.setValue(false);
                    error.setValue("Error de conexión: " + t.getMessage());
                }
            }
        });
    }

    private void handleErrorResponse(int statusCode) {
        String errorMessage = "Error de autenticación";
        switch (statusCode) {
            case 400: errorMessage = "Credenciales inválidas"; break;
            case 401: errorMessage = "No autorizado"; break;
            case 500: errorMessage = "Error del servidor"; break;
        }
        error.setValue(errorMessage);
    }

    private void cancelCurrentRequest() {
        if (currentCall != null) {
            currentCall.cancel();
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelCurrentRequest();
    }

    public LiveData<LoginResponse> getLoginResult() { return loginResult; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getLoading() { return loading; }

    public void clearState() {
        loginResult.setValue(null);
        error.setValue(null);
        loading.setValue(false);
    }
}