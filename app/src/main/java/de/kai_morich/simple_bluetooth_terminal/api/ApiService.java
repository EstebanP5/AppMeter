package de.kai_morich.simple_bluetooth_terminal.api;

import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.models.*;
import retrofit2.Call;
import retrofit2.http.*;

// Modelos nuevos importados explícitamente para claridad
// (ya cubiertos por el wildcard anterior)

public interface ApiService {

    // ============ AUTENTICACIÓN ============

    /**
     * Login endpoint
        * POST /api/ergoems/login/login
     */
    @POST("api/ergoems/login/login")
    Call<LoginResponse> performLogin(@Body Credentials credentials);

    @POST
    Call<LoginResponse> performLoginAt(@Url String loginPath, @Body Credentials credentials);

    // ============ GESTIÓN DE CLIENTES ============

        @GET("api/ergoems/customer/getAllCustomers")
    Call<List<CustomerViewModel>> getAllCustomers();

        @GET("api/ergoems/customer/getCustomerInformation")
    Call<CustomerViewModel> getCustomerInformation(@Query("idCustomer") Integer idCustomer);

        @POST("api/ergoems/customer/addNewCustomer")
    Call<Void> addNewCustomer(@Body CustomerViewModel customer);

        @POST("api/ergoems/customer/saveCustomerInformation")
    Call<Void> saveCustomer(@Body CustomerViewModel customer);

        @POST("api/ergoems/customer/deleteCustomer")
    Call<Void> deleteCustomer(@Body Map<String, Integer> payload);

    // ============ GESTIÓN DE UBICACIONES/LOCALIDADES ============

    @GET("api/ergoems/locations/getCustomerLocations")
    Call<List<LocationsViewModel>> getCustomerLocations(@Query("idCustomer") Integer idCustomer);

    @GET("api/ergoems/locations/getLocationInformation")
    Call<LocationsViewModel> getLocationInformation(
            @Query("idLocation") Integer idLocation,
            @Query("idCustomer") Integer idCustomer
    );

    @POST("api/ergoems/locations/addNewLocation")
    Call<LocationsViewModel> addNewLocation(@Body LocationsViewModel location);

    @POST("api/ergoems/locations/saveLocationInformation")
    Call<Void> saveLocationInformation(@Body LocationsViewModel location);

    @POST("api/ergoems/locations/deleteLocation")
    Call<Void> deleteLocation(@Body Map<String, Integer> payload);

    // ============ GESTIÓN DE CIRCUITOS ============

        @GET("api/ergoems/circuits/getCustomerCircuits")
    Call<List<CircuitsViewModel>> getCustomerCircuits(@Query("idCustomer") Integer idCustomer);

        @POST("api/ergoems/circuits/addNewCircuit")
    Call<Void> addNewCircuit(@Body CircuitsViewModel circuit);

        @POST("api/ergoems/circuits/saveCircuitInformation")
    Call<Void> saveCircuitInformation(@Body CircuitsViewModel circuit);

        @POST("api/ergoems/circuits/deleteCircuit")
    Call<Void> deleteCircuit(@Body Map<String, Integer> payload);

    /**
     * Resumen del medidor de un circuito
     * GET /api/ergoems/circuits/getMeterSummary
     * Parámetros: idCircuit, idCustomer
     */
    @GET("api/ergoems/circuits/getMeterSummary")
    Call<MeterCurrentViewModel> getMeterSummary(
            @Query("idCircuit") Integer circuitId,
            @Query("idCustomer") Integer customerId
    );

    // ============ GESTIÓN DE DISPOSITIVOS ============

    @GET("api/ergoems/devices/getCustomerDevices")
    Call<List<DevicesViewModel>> getCustomerDevices(@Query("idCustomer") Integer idCustomer);

    @POST("api/ergoems/devices/addNewSmartSunLite")
    Call<Void> addNewDevice(@Body DevicesViewModel device);

    @POST("api/ergoems/devices/saveSmartSunLiteInformation")
    Call<Void> saveDeviceInformation(@Body DevicesViewModel device);

    @POST("api/ergoems/devices/deleteSmartSunLite")
    Call<Void> deleteDevice(@Body DevicesViewModel payload);

    @GET("api/ergoems/devices/getCustomerDevicesCurrent")
    Call<List<DevicesCurrentViewModel>> getCustomerDevicesCurrent(
            @Query("idCircuit") Integer idCircuit,
            @Query("idCustomer") Integer idCustomer
    );

        @GET("api/ergoems/devices/getEnergyFlowGraph")
        Call<EnergyFlowGraphViewModel> getEnergyFlowGraph(
            @Query("idCircuit") Integer idCircuit,
            @Query("idCustomer") Integer idCustomer
        );

    @POST("api/ergoems/devices/registerDeviceSerial")
    Call<Void> registerDeviceSerial(@Body DevicesViewModel device);

    @POST("api/ergoems/devices/linkNodeWithSerial")
    Call<Void> linkNodeWithSerial(@Body DevicesViewModel device);

    /** Alias con Map para enviar solo los campos necesarios. */
    @POST("api/ergoems/devices/linkNodeWithSerial")
    Call<Void> linkNodeWithSerialMap(@Body Map<String, Object> payload);

    @POST("api/ergoems/devices/updateSerialLoad")
    Call<Void> updateSerialLoad(@Body Map<String, Object> payload);

    @GET("api/ergoems/devices/getRegisteredSerials")
    Call<List<RegisteredSerial>> getRegisteredSerials(
            @Query("idCustomer") Integer idCustomer,
            @Query("deviceType") Integer deviceType,
            @Query("debug") Boolean debug
    );

    @GET("api/ergoems/devices/validateSerialForLink")
    Call<Void> validateSerialForLink(
            @Query("serie") String serie,
            @Query("deviceType") Integer deviceType,
            @Query("idCustomer") Integer idCustomer,
            @Query("load") Integer load
    );

    @POST("api/ergoems/devices/unlinkNodeSerial")
    Call<Void> unlinkNodeSerial(
            @Query("idNode") Integer idNode,
            @Query("deviceType") Integer deviceType,
            @Query("idCustomer") Integer idCustomer
    );

    @POST("api/ergoems/devices/unlinkSerial")
    Call<Void> unlinkSerial(
            @Query("serie") String serie,
            @Query("deviceType") Integer deviceType,
            @Query("idCustomer") Integer idCustomer,
            @Query("load") Integer load
    );

    @POST("api/ergoems/devices/addNewHub")
    Call<Void> addNewHub(@Body DevicesViewModel hub);

    @GET("api/ergoems/devices/getHubAssignments")
    Call<List<Map<String, Object>>> getHubAssignments(
            @Query("idHub") Integer idHub,
            @Query("serieHub") String serieHub,
            @Query("idCustomer") Integer idCustomer
    );

    @POST("api/ergoems/devices/assignNodeToHub")
    Call<Void> assignNodeToHub(@Body Map<String, Object> payload);

    @POST("api/ergoems/devices/unassignNodeFromHub")
    Call<Void> unassignNodeFromHub(@Body Map<String, Object> payload);

    @GET("api/ergoems/devices/getCustomerHubNodeMap")
    Call<List<Map<String, Object>>> getCustomerHubNodeMap(@Query("idCustomer") Integer idCustomer);

    // ============ MEDICIONES Y TELEMETRÍA ============

    @GET("api/ergoems/measurements/getSmartSunLiteCurrent")
    Call<PhotocellCurrentViewModel> getSmartSunLiteCurrent(
            @Query("idDevice") Integer idDevice,
            @Query("idCustomer") Integer idCustomer
    );

    @GET("api/ergoems/measurements/getSmartSunLitePowerDay")
    Call<List<GenConViewModel>> getSmartSunLitePowerDay(
            @Query("idDevice") Integer idDevice,
            @Query("date") String date,
            @Query("idCustomer") Integer idCustomer
    );

    @GET("api/ergoems/measurements/get5MPowerDay")
    Call<List<GenConViewModel>> get5MPowerDay(
            @Query("idDevice") Integer idDevice,
            @Query("date") String date,
            @Query("idCustomer") Integer idCustomer
    );

    // ============ CONTROL DE DISPOSITIVOS ============

        @POST("api/ergoems/devices/setSmartSunLiteMasterStatusOnOff")
    Call<Void> setSmartSunLiteMasterStatusOnOff(@Body Map<String, Object> payload);

        @POST("api/ergoems/devices/setSmartSunLiteMasterDimmer")
    Call<Void> setSmartSunLiteMasterDimmer(@Body Map<String, Object> payload);

    // ============ NUEVOS ENDPOINTS ============

    /** Nodos vinculados a un serial físico (para editar Load en MisDispositivosActivity). */
    @GET("api/ergoems/devices/getSerialLinks")
    Call<List<SerialLink>> getSerialLinks(
            @Query("serie") String serie,
            @Query("frontendType") Integer frontendType,
            @Query("idCustomer") Integer idCustomer
    );

    /** Eliminar un serial físico registrado (solo si Status = RegisteredUnlinked). */
    @POST("api/ergoems/devices/deleteRegisteredSerial")
    Call<Void> deleteRegisteredSerial(@Body Map<String, Object> payload);

    /** Nodos asignados a un hub (para AjustesGatewaysFragment). */
    @GET("api/ergoems/devices/getAllHubNodes")
    Call<List<RegisteredSerial>> getAllHubNodes(@Query("idDevice") Integer idDevice);

    /**
     * Semáforo de nodos del cliente (AdminMedicionesActivity).
     * Respuesta: { Nodes: [ NodeTrafficLight ] }
     */
    @GET("api/ergoems/devices/getCustomerNodesTrafficLight")
    Call<NodeTrafficLightResponse> getCustomerNodesTrafficLight(
            @Query("idCustomer") Integer idCustomer
    );

    /**
     * Totales de energía por localidad del cliente (AdminMedicionesActivity).
     * Respuesta: { Totals: {...}, Locations: [...] }
     */
    @GET("api/ergoems/devices/getCustomerLocationsEnergyPowerTotals")
    Call<LocationsEnergyResponse> getCustomerLocationsEnergyPowerTotals(
            @Query("idCustomer") Integer idCustomer
    );

    /**
     * Totales de energía por localidad (con LocationEnergyPower detallado).
     */
    @GET("api/ergoems/devices/getCustomerLocationsEnergyPowerTotals")
    Call<LocationsEnergyPowerResponse> getLocationsEnergyPowerTotals(
            @Query("idCustomer") Integer idCustomer
    );

    /**
     * Medición detallada de dispositivos (con idHW como filtro).
     * Usada en AdminMedicionesActivity BottomSheet (idHW=0 para MEDIDORs).
     */
    @GET("api/ergoems/devices/getCustomerDevicesCurrent")
    Call<List<DeviceCurrent>> getCustomerDevicesCurrentDetailed(
            @Query("idCircuit") Integer idCircuit,
            @Query("idCustomer") Integer idCustomer,
            @Query("idHW") Integer idHW
    );

    // ============ HISTÓRICOS (MedicionesActivity) ============

    /** Potencia por hora del día. Respuesta: {"H00":val,...,"H23":val} */
    @GET("api/ergoems/measurements/getSmartSunLitePowerDay")
    Call<java.util.Map<String, Double>> getPowerDay(
            @Query("idMeasure") Integer idMeasure,
            @Query("date") String date,
            @Query("idCustomer") Integer idCustomer
    );

    /** Energía por hora del día. Respuesta: {"Generation":{...},"Consumption":{...}} */
    @GET("api/ergoems/measurements/getSmartSunLiteEnergyDay")
    Call<de.kai_morich.simple_bluetooth_terminal.models.EnergyDayResponse> getEnergyDay(
            @Query("idMeasure") Integer idMeasure,
            @Query("date") String date,
            @Query("idCustomer") Integer idCustomer
    );

    /** Energía por hora del día para BESS (tipo 3). */
    @GET("api/ergoems/measurements/getBessEnergyDay")
    Call<de.kai_morich.simple_bluetooth_terminal.models.EnergyDayResponse> getBessEnergyDay(
            @Query("idMeasure") Integer idMeasure,
            @Query("date") String date,
            @Query("idCustomer") Integer idCustomer
    );

    /** Energía por día del mes. Respuesta: {"Days":N,"Generation":{"D01":val,...},"Consumption":{...}} */
    @GET("api/ergoems/measurements/getSmartSunLiteEnergyMonth")
    Call<de.kai_morich.simple_bluetooth_terminal.models.EnergyDayResponse> getEnergyMonth(
            @Query("idMeasure") Integer idMeasure,
            @Query("date") String date,
            @Query("idCustomer") Integer idCustomer
    );

    /** Energía por día del mes para BESS (tipo 3). */
    @GET("api/ergoems/measurements/getBessEnergyMonth")
    Call<de.kai_morich.simple_bluetooth_terminal.models.EnergyDayResponse> getBessEnergyMonth(
            @Query("idMeasure") Integer idMeasure,
            @Query("date") String date,
            @Query("idCustomer") Integer idCustomer
    );

    /** Energía por mes del año. Respuesta: {"Generation":{"M01":val,...},"Consumption":{...}} */
    @GET("api/ergoems/measurements/getSmartSunLiteEnergyYear")
    Call<de.kai_morich.simple_bluetooth_terminal.models.EnergyDayResponse> getEnergyYear(
            @Query("idMeasure") Integer idMeasure,
            @Query("date") String date,
            @Query("idCustomer") Integer idCustomer
    );
}