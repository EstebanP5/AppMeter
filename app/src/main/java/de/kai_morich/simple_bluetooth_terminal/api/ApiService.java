package de.kai_morich.simple_bluetooth_terminal.api;

import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.models.*;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    // ============ AUTENTICACIÓN ============

    /**
     * Login endpoint
     * POST /api/octosunlux/login/login
     */
    @POST("api/octosunlux/login/login")
    Call<LoginResponse> performLogin(@Body Credentials credentials);

    // ============ GESTIÓN DE CLIENTES ============

    @GET("api/octosunlux/customer/getAllCustomers")
    Call<List<CustomerViewModel>> getAllCustomers();

    @GET("api/octosunlux/customer/getCustomerInformation")
    Call<CustomerViewModel> getCustomerInformation(@Query("idCustomer") Integer idCustomer);

    @POST("api/octosunlux/customer/addNewCustomer")
    Call<Void> addNewCustomer(@Body CustomerViewModel customer);

    @POST("api/octosunlux/customer/saveCustomerInformation")
    Call<Void> saveCustomer(@Body CustomerViewModel customer);

    @POST("api/octosunlux/customer/deleteCustomer")
    Call<Void> deleteCustomer(@Body Map<String, Integer> payload);

    // ============ GESTIÓN DE UBICACIONES/LOCALIDADES ============

    @GET("api/octosunlux/locations/getCustomerLocations")
    Call<List<LocationsViewModel>> getCustomerLocations(@Query("idCustomer") Integer idCustomer);

    @GET("api/octosunlux/locations/getLocationInformation")
    Call<LocationsViewModel> getLocationInformation(
            @Query("idLocation") Integer idLocation,
            @Query("idCustomer") Integer idCustomer
    );

    @POST("api/octosunlux/locations/addNewLocation")
    Call<Void> addNewLocation(@Body LocationsViewModel location);

    @POST("api/octosunlux/locations/saveLocationInformation")
    Call<Void> saveLocationInformation(@Body LocationsViewModel location);

    @POST("api/octosunlux/locations/deleteLocation")
    Call<Void> deleteLocation(@Body Map<String, Integer> payload);

    // ============ GESTIÓN DE CIRCUITOS ============

    @GET("api/octosunlux/circuits/getCustomerCircuits")
    Call<List<CircuitsViewModel>> getCustomerCircuits(@Query("idCustomer") Integer idCustomer);

    @POST("api/octosunlux/circuits/addNewCircuit")
    Call<Void> addNewCircuit(@Body CircuitsViewModel circuit);

    @POST("api/octosunlux/circuits/saveCircuitInformation")
    Call<Void> saveCircuitInformation(@Body CircuitsViewModel circuit);

    @POST("api/octosunlux/circuits/deleteCircuit")
    Call<Void> deleteCircuit(@Body Map<String, Integer> payload);

    /**
     * Resumen del medidor de un circuito
     * GET /api/octosunlux/circuits/getMeterSummary
     * Parámetros: idCircuit, idCustomer
     */
    @GET("api/octosunlux/circuits/getMeterSummary")
    Call<MeterCurrentViewModel> getMeterSummary(
            @Query("idCircuit") Integer circuitId,
            @Query("idCustomer") Integer customerId
    );

    // ============ GESTIÓN DE DISPOSITIVOS ============

    @GET("api/octosunlux/devices/getCustomerDevices")
    Call<List<DevicesViewModel>> getCustomerDevices(@Query("idCustomer") Integer idCustomer);

    @POST("api/octosunlux/devices/addNewDevice")
    Call<Void> addNewDevice(@Body DevicesViewModel device);

    @POST("api/octosunlux/devices/saveDeviceInformation")
    Call<Void> saveDeviceInformation(@Body DevicesViewModel device);

    @POST("api/octosunlux/devices/deleteDevice")
    Call<Void> deleteDevice(@Body Map<String, Integer> payload);

    @GET("api/octosunlux/devices/getCustomerDevicesCurrent")
    Call<List<DevicesCurrentViewModel>> getCustomerDevicesCurrent(
            @Query("idCircuit") Integer idCircuit,
            @Query("idCustomer") Integer idCustomer
    );

    // ============ MEDICIONES Y TELEMETRÍA ============

    @GET("api/octosunlux/measurements/getSmartSunLiteCurrent")
    Call<PhotocellCurrentViewModel> getSmartSunLiteCurrent(
            @Query("idDevice") Integer idDevice,
            @Query("idCustomer") Integer idCustomer
    );

    @GET("api/octosunlux/measurements/getSmartSunLitePowerDay")
    Call<List<GenConViewModel>> getSmartSunLitePowerDay(
            @Query("idDevice") Integer idDevice,
            @Query("date") String date,
            @Query("idCustomer") Integer idCustomer
    );

    @GET("api/octosunlux/measurements/get5MPowerDay")
    Call<List<GenConViewModel>> get5MPowerDay(
            @Query("idDevice") Integer idDevice,
            @Query("date") String date,
            @Query("idCustomer") Integer idCustomer
    );

    // ============ CONTROL DE DISPOSITIVOS ============

    @POST("api/octosunlux/devices/setSmartSunLiteMasterStatusOnOff")
    Call<Void> setSmartSunLiteMasterStatusOnOff(@Body Map<String, Object> payload);

    @POST("api/octosunlux/devices/setSmartSunLiteMasterDimmer")
    Call<Void> setSmartSunLiteMasterDimmer(@Body Map<String, Object> payload);
}