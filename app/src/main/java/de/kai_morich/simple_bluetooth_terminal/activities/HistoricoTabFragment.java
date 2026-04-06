package de.kai_morich.simple_bluetooth_terminal.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.EnergyDayResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoricoTabFragment extends Fragment
        implements HistoricoActivity.OnDateChangedListener {

    private static final String TAG = "HistoricoTab";
    private static final String ARG_TAB = "tab_index";

    // Colors
    private static final int COLOR_PV      = 0xFFFFC107;
    private static final int COLOR_BESS    = 0xFF4CAF50;
    private static final int COLOR_CFE     = 0xFF2196F3;
    private static final int COLOR_LOAD    = 0xFF9E9E9E;
    private static final int COLOR_GEN     = 0xFFFF5722;
    private static final int COLOR_CONSUMO = 0xFF2196F3;
    private static final int COLOR_GENERACION = 0xFFFFC107;

    private int tabIndex;
    private ApiService apiService;

    // Views
    private FrameLayout chartContainer;
    private CardView cardSummary, cardTable;
    private TextView tvSummary, tvEmpty;
    private LinearLayout tableContainer;
    private ProgressBar progressBar;

    public static HistoricoTabFragment newInstance(int tabIndex) {
        HistoricoTabFragment f = new HistoricoTabFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TAB, tabIndex);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tabIndex = getArguments() != null ? getArguments().getInt(ARG_TAB, 0) : 0;
        apiService = ApiClient.getApiService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_historico_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        chartContainer = view.findViewById(R.id.chartContainer);
        cardSummary    = view.findViewById(R.id.cardSummary);
        tvSummary      = view.findViewById(R.id.tvSummary);
        cardTable      = view.findViewById(R.id.cardTable);
        tableContainer = view.findViewById(R.id.tableContainer);
        tvEmpty        = view.findViewById(R.id.tvEmpty);
        progressBar    = view.findViewById(R.id.progressBar);

        loadData();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof HistoricoActivity) {
            ((HistoricoActivity) getActivity()).setOnDateChangedListener(this);
            loadData();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() instanceof HistoricoActivity) {
            ((HistoricoActivity) getActivity()).setOnDateChangedListener(null);
        }
    }

    @Override
    public void onDateChanged(String formattedDate) {
        loadData();
    }

    private HistoricoActivity getHostActivity() {
        return (HistoricoActivity) requireActivity();
    }

    private void loadData() {
        if (getActivity() == null || !isAdded()) return;

        HistoricoActivity host = getHostActivity();
        if (host.getCurrentTab() != tabIndex) return;

        showLoading();

        switch (tabIndex) {
            case HistoricoActivity.TAB_POWER_DAY:
                loadPowerDay(host);
                break;
            case HistoricoActivity.TAB_ENERGY_DAY:
                loadEnergyDay(host);
                break;
        };
    }

    // ---- TAB 1: Power Day ----
    // API response: {"H00":val, "H01":val, ..., "H23":val}

    private void loadPowerDay(HistoricoActivity host) {
        apiService.getPowerDay(host.getIdMeasure(), host.getApiDate(), host.getIdCustomer())
                .enqueue(new Callback<Map<String, Double>>() {
                    @Override
                    public void onResponse(Call<Map<String, Double>> call,
                                           Response<Map<String, Double>> response) {
                        if (!isAdded()) return;
                        hideLoading();
                        if (response.isSuccessful() && response.body() != null
                                && !response.body().isEmpty()) {
                            renderPowerDayChart(response.body(), host.getNodeType());
                        } else {
                            Log.d(TAG, "PowerDay empty/error code=" + response.code());
                            showEmpty();
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Double>> call, Throwable t) {
                        if (!isAdded()) return;
                        hideLoading();
                        showEmpty();
                    }
                });
    }

    private void renderPowerDayChart(Map<String, Double> data, int nodeType) {
        chartContainer.removeAllViews();

        // Sort by key (H00, H01, ..., H23)
        TreeMap<String, Double> sorted = new TreeMap<>(data);

        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Double> entry : sorted.entrySet()) {
            double val = entry.getValue() != null ? entry.getValue() / 10.0 : 0; // décimas → W
            entries.add(new Entry(i, (float) val));
            // H00 → 00:00, H01 → 01:00...
            String key = entry.getKey();
            String label = key.length() >= 3 ? key.substring(1) + ":00" : key;
            labels.add(label);
            i++;
        }

        LineChart chart = new LineChart(requireContext());
        chart.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LineDataSet dataSet = new LineDataSet(entries, "Potencia (W)");
        dataSet.setColor(getColorForType(nodeType));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        chart.setData(new LineData(dataSet));
        styleChart(chart, labels);
        chart.animateY(800);
        chartContainer.addView(chart);

        // Build table
        buildPowerDayTable(sorted);
    }

    private void buildPowerDayTable(TreeMap<String, Double> data) {
        cardTable.setVisibility(View.VISIBLE);
        tableContainer.removeAllViews();

        LinearLayout header = createTableRow(true);
        header.addView(createCell("Hora", true, 1));
        header.addView(createCell("Potencia (W)", true, 1));
        tableContainer.addView(header);

        for (Map.Entry<String, Double> entry : data.entrySet()) {
            double val = entry.getValue() != null ? entry.getValue() / 10.0 : 0;
            String key = entry.getKey();
            String label = key.length() >= 3 ? key.substring(1) + ":00" : key;

            LinearLayout row = createTableRow(false);
            row.addView(createCell(label, false, 1));
            row.addView(createCell(String.format(Locale.US, "%.1f", val), false, 1));
            tableContainer.addView(row);
        }
    }

    // ---- TAB 2: Energy Day ----
    // API response: {"Generation":{"0":val,...},"Consumption":{"0":val,...}}

    private void loadEnergyDay(HistoricoActivity host) {
        Callback<EnergyDayResponse> callback = new Callback<EnergyDayResponse>() {
            @Override
            public void onResponse(Call<EnergyDayResponse> call,
                                   Response<EnergyDayResponse> response) {
                if (!isAdded()) return;
                hideLoading();
                if (response.isSuccessful() && response.body() != null) {
                    renderEnergyDayChart(response.body(), host.getNodeType());
                } else {
                    Log.d(TAG, "EnergyDay empty/error code=" + response.code());
                    showEmpty();
                }
            }
            @Override
            public void onFailure(Call<EnergyDayResponse> call, Throwable t) {
                if (!isAdded()) return;
                hideLoading();
                showEmpty();
            }
        };

        if (host.getNodeType() == 3) {
            apiService.getBessEnergyDay(host.getIdMeasure(), host.getApiDate(), host.getIdCustomer())
                    .enqueue(callback);
        } else {
            apiService.getEnergyDay(host.getIdMeasure(), host.getApiDate(), host.getIdCustomer())
                    .enqueue(callback);
        }
    }

    private void renderEnergyDayChart(EnergyDayResponse data, int nodeType) {
        chartContainer.removeAllViews();

        Map<String, Double> consumption = data.getConsumption();
        Map<String, Double> generation = data.getGeneration();
        boolean hasConsumption = consumption != null && !consumption.isEmpty();
        boolean hasGeneration = generation != null && !generation.isEmpty();

        if (!hasConsumption && !hasGeneration) {
            showEmpty();
            return;
        }

        // Build sorted entries (keys: "0","1",...,"23")
        List<BarEntry> consumoEntries = new ArrayList<>();
        List<BarEntry> genEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        double totalConsumo = 0;
        double totalGen = 0;

        for (int h = 0; h < 24; h++) {
            String key = String.valueOf(h);
            double cVal = hasConsumption && consumption.containsKey(key) && consumption.get(key) != null
                    ? consumption.get(key) : 0;
            double gVal = hasGeneration && generation.containsKey(key) && generation.get(key) != null
                    ? generation.get(key) : 0;
            consumoEntries.add(new BarEntry(h, (float) Math.abs(cVal)));
            genEntries.add(new BarEntry(h, (float) Math.abs(gVal)));
            labels.add(String.format(Locale.US, "%02d:00", h));
            totalConsumo += Math.abs(cVal);
            totalGen += Math.abs(gVal);
        }

        BarChart chart = new BarChart(requireContext());
        chart.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        BarDataSet consumoSet = new BarDataSet(consumoEntries, "Consumo (Wh)");
        consumoSet.setColor(COLOR_CONSUMO);
        consumoSet.setDrawValues(false);

        BarData barData = new BarData(consumoSet);

        if (totalGen > 0) {
            BarDataSet genSet = new BarDataSet(genEntries, "Generación (Wh)");
            genSet.setColor(COLOR_GENERACION);
            genSet.setDrawValues(false);
            barData.addDataSet(genSet);
            float groupSpace = 0.06f;
            float barSpace = 0.02f;
            float barWidth = 0.45f;
            barData.setBarWidth(barWidth);
            chart.setData(barData);
            chart.groupBars(-0.5f, groupSpace, barSpace);
        } else {
            chart.setData(barData);
        }

        styleBarChart(chart, labels);
        chart.animateY(800);
        chartContainer.addView(chart);

        cardSummary.setVisibility(View.VISIBLE);
        if (totalGen > 0) {
            tvSummary.setText(String.format(Locale.US, "Consumo: %s | Gen: %s",
                    formatEnergySummary(totalConsumo), formatEnergySummary(totalGen)));
        } else {
            tvSummary.setText(formatEnergySummary(totalConsumo) + " total");
        }
    }

    // ---- TAB 3: Energy Month ----
    // API response: {"Days":N,"Generation":{"D01":val,...},"Consumption":{"D01":val,...}}

    private void loadEnergyMonth(HistoricoActivity host) {
        Callback<EnergyDayResponse> callback = new Callback<EnergyDayResponse>() {
            @Override
            public void onResponse(Call<EnergyDayResponse> call,
                                   Response<EnergyDayResponse> response) {
                if (!isAdded()) return;
                hideLoading();
                if (response.isSuccessful() && response.body() != null) {
                    renderEnergyMonthChart(response.body(), host.getNodeType());
                } else {
                    Log.d(TAG, "EnergyMonth empty/error code=" + response.code());
                    showEmpty();
                }
            }
            @Override
            public void onFailure(Call<EnergyDayResponse> call, Throwable t) {
                if (!isAdded()) return;
                hideLoading();
                showEmpty();
            }
        };

        if (host.getNodeType() == 3) {
            apiService.getBessEnergyMonth(host.getIdMeasure(), host.getApiDate(), host.getIdCustomer())
                    .enqueue(callback);
        } else {
            apiService.getEnergyMonth(host.getIdMeasure(), host.getApiDate(), host.getIdCustomer())
                    .enqueue(callback);
        }
    }

    private void renderEnergyMonthChart(EnergyDayResponse data, int nodeType) {
        chartContainer.removeAllViews();

        Map<String, Double> consumption = data.getConsumption();
        Map<String, Double> generation = data.getGeneration();
        boolean hasConsumption = consumption != null && !consumption.isEmpty();
        boolean hasGeneration = generation != null && !generation.isEmpty();

        if (!hasConsumption && !hasGeneration) {
            showEmpty();
            return;
        }

        // Keys are D01, D02, ..., D31
        List<BarEntry> consumoEntries = new ArrayList<>();
        List<BarEntry> genEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        double totalConsumo = 0;
        double totalGen = 0;

        for (int d = 1; d <= 31; d++) {
            String key = String.format(Locale.US, "D%02d", d);
            double cVal = hasConsumption && consumption.containsKey(key) && consumption.get(key) != null
                    ? consumption.get(key) : 0;
            double gVal = hasGeneration && generation.containsKey(key) && generation.get(key) != null
                    ? generation.get(key) : 0;
            if (cVal == 0 && gVal == 0 && d > 28) continue; // skip trailing empty days
            consumoEntries.add(new BarEntry(labels.size(), (float) Math.abs(cVal)));
            genEntries.add(new BarEntry(labels.size(), (float) Math.abs(gVal)));
            labels.add(String.valueOf(d));
            totalConsumo += Math.abs(cVal);
            totalGen += Math.abs(gVal);
        }

        BarChart chart = new BarChart(requireContext());
        chart.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        BarDataSet consumoSet = new BarDataSet(consumoEntries, "Consumo (Wh)");
        consumoSet.setColor(COLOR_CONSUMO);
        consumoSet.setDrawValues(false);

        BarData barData = new BarData(consumoSet);

        if (totalGen > 0) {
            BarDataSet genSet = new BarDataSet(genEntries, "Generación (Wh)");
            genSet.setColor(COLOR_GENERACION);
            genSet.setDrawValues(false);
            barData.addDataSet(genSet);
            float groupSpace = 0.06f;
            float barSpace = 0.02f;
            float barWidth = 0.45f;
            barData.setBarWidth(barWidth);
            chart.setData(barData);
            chart.groupBars(-0.5f, groupSpace, barSpace);
        } else {
            chart.setData(barData);
        }

        styleBarChart(chart, labels);
        chart.animateY(800);
        chartContainer.addView(chart);

        cardSummary.setVisibility(View.VISIBLE);
        if (totalGen > 0) {
            tvSummary.setText(String.format(Locale.US, "Consumo: %s | Gen: %s",
                    formatEnergySummary(totalConsumo), formatEnergySummary(totalGen)));
        } else {
            double total = totalConsumo > 0 ? totalConsumo : totalGen;
            double avg = labels.size() > 0 ? total / labels.size() : 0;
            tvSummary.setText(String.format(Locale.US,
                    "Total mes: %s  |  Promedio día: %s",
                    formatEnergySummary(total), formatEnergySummary(avg)));
        }
    }

    // ---- TAB 4: Energy Year ----
    // API response: {"Generation":{"M01":val,...,"M12":val},"Consumption":{"M01":val,...}}

    private void loadEnergyYear(HistoricoActivity host) {
        apiService.getEnergyYear(host.getIdMeasure(), host.getApiDate(), host.getIdCustomer())
                .enqueue(new Callback<EnergyDayResponse>() {
                    @Override
                    public void onResponse(Call<EnergyDayResponse> call,
                                           Response<EnergyDayResponse> response) {
                        if (!isAdded()) return;
                        hideLoading();
                        if (response.isSuccessful() && response.body() != null) {
                            renderEnergyYearChart(response.body(), host.getNodeType());
                        } else {
                            Log.d(TAG, "EnergyYear empty/error code=" + response.code());
                            showEmpty();
                        }
                    }
                    @Override
                    public void onFailure(Call<EnergyDayResponse> call, Throwable t) {
                        if (!isAdded()) return;
                        hideLoading();
                        showEmpty();
                    }
                });
    }

    private void renderEnergyYearChart(EnergyDayResponse data, int nodeType) {
        chartContainer.removeAllViews();

        String[] monthNames = {"Ene","Feb","Mar","Abr","May","Jun",
                               "Jul","Ago","Sep","Oct","Nov","Dic"};

        Map<String, Double> consumption = data.getConsumption();
        Map<String, Double> generation = data.getGeneration();
        boolean hasConsumption = consumption != null && !consumption.isEmpty();
        boolean hasGeneration = generation != null && !generation.isEmpty();

        if (!hasConsumption && !hasGeneration) {
            showEmpty();
            return;
        }

        // Keys are M01, M02, ..., M12
        List<BarEntry> consumoEntries = new ArrayList<>();
        List<BarEntry> genEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        double totalConsumoWh = 0;
        double totalGenWh = 0;

        for (int m = 1; m <= 12; m++) {
            String key = String.format(Locale.US, "M%02d", m);
            double cVal = hasConsumption && consumption.containsKey(key) && consumption.get(key) != null
                    ? consumption.get(key) : 0;
            double gVal = hasGeneration && generation.containsKey(key) && generation.get(key) != null
                    ? generation.get(key) : 0;
            float cKwh = (float) (Math.abs(cVal) / 1000.0);
            float gKwh = (float) (Math.abs(gVal) / 1000.0);
            consumoEntries.add(new BarEntry(m - 1, cKwh));
            genEntries.add(new BarEntry(m - 1, gKwh));
            labels.add(monthNames[m - 1]);
            totalConsumoWh += Math.abs(cVal);
            totalGenWh += Math.abs(gVal);
        }

        BarChart chart = new BarChart(requireContext());
        chart.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        BarDataSet consumoSet = new BarDataSet(consumoEntries, "Consumo (kWh)");
        consumoSet.setColor(COLOR_CONSUMO);
        consumoSet.setDrawValues(false);

        BarData barData = new BarData(consumoSet);

        if (totalGenWh > 0) {
            BarDataSet genSet = new BarDataSet(genEntries, "Generación (kWh)");
            genSet.setColor(COLOR_GENERACION);
            genSet.setDrawValues(false);
            barData.addDataSet(genSet);
            float groupSpace = 0.06f;
            float barSpace = 0.02f;
            float barWidth = 0.45f;
            barData.setBarWidth(barWidth);
            chart.setData(barData);
            chart.groupBars(-0.5f, groupSpace, barSpace);
        } else {
            chart.setData(barData);
        }
        styleBarChart(chart, labels);
        chart.animateY(800);
        chartContainer.addView(chart);

        cardSummary.setVisibility(View.VISIBLE);
        if (totalGenWh > 0) {
            tvSummary.setText(String.format(Locale.US, "Consumo: %.1f kWh | Gen: %.1f kWh",
                    totalConsumoWh / 1000.0, totalGenWh / 1000.0));
        } else {
            double totalWh = totalConsumoWh > 0 ? totalConsumoWh : totalGenWh;
            tvSummary.setText(String.format(Locale.US, "Total año: %.1f kWh", totalWh / 1000.0));
        }
    }

    // ---- Chart styling ----

    private void styleChart(LineChart chart, List<String> labels) {
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.setDrawGridBackground(false);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(0xFF9AA5B4);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelRotationAngle(-45);
        xAxis.setDrawGridLines(false);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(0xFF9AA5B4);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(0x332A3645);

        chart.getAxisRight().setEnabled(false);

        Legend legend = chart.getLegend();
        legend.setTextColor(Color.WHITE);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
    }

    private void styleBarChart(BarChart chart, List<String> labels) {
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.setDrawGridBackground(false);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(0xFF9AA5B4);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelRotationAngle(-45);
        xAxis.setDrawGridLines(false);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(0xFF9AA5B4);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(0x332A3645);
        leftAxis.setAxisMinimum(0f);

        chart.getAxisRight().setEnabled(false);

        Legend legend = chart.getLegend();
        legend.setTextColor(Color.WHITE);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
    }

    // ---- Helpers ----

    private int getColorForType(int nodeType) {
        switch (nodeType) {
            case 2: return COLOR_PV;
            case 3: return COLOR_BESS;
            case 0:
            case 1: return COLOR_CFE;
            case 4: return COLOR_GEN;
            case 7: return COLOR_LOAD;
            default: return COLOR_CFE;
        }
    }

    private String formatEnergySummary(double wh) {
        if (wh >= 1000) return String.format(Locale.US, "%.1f kWh", wh / 1000.0);
        return String.format(Locale.US, "%.1f Wh", wh);
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        cardSummary.setVisibility(View.GONE);
        cardTable.setVisibility(View.GONE);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
    }

    private void showEmpty() {
        tvEmpty.setVisibility(View.VISIBLE);
        cardSummary.setVisibility(View.GONE);
        cardTable.setVisibility(View.GONE);
        chartContainer.removeAllViews();
    }

    // ---- Table helpers ----

    private LinearLayout createTableRow(boolean isHeader) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (isHeader) {
            row.setBackgroundColor(0x201C2333);
        }
        row.setPadding(0, dp(4), 0, dp(4));
        return row;
    }

    private TextView createCell(String text, boolean isHeader, float weight) {
        TextView tv = new TextView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextColor(isHeader ? 0xFFFFFFFF : 0xFF9AA5B4);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, isHeader ? 12 : 11);
        tv.setGravity(Gravity.CENTER);
        if (isHeader) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
