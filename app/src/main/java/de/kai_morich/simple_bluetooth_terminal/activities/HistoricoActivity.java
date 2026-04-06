package de.kai_morich.simple_bluetooth_terminal.activities;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.R;

public class HistoricoActivity extends AppCompatActivity {

    public static final int TAB_POWER_DAY   = 0;
    public static final int TAB_ENERGY_DAY  = 1;

    private static final String[] TAB_TITLES = {
            "Potencia Día", "Energía Día"
    };

    // Intent extras
    private int idMeasure, idCustomer, idCircuit, idLocation, nodeType;
    private String nodeName;

    // Date navigation
    private Calendar currentDate = Calendar.getInstance();
    private TextView tvDate;
    private int currentTab = TAB_POWER_DAY;

    // Listener for date changes
    public interface OnDateChangedListener {
        void onDateChanged(String formattedDate);
    }

    private OnDateChangedListener dateChangedListener;

    public void setOnDateChangedListener(OnDateChangedListener listener) {
        this.dateChangedListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historico);

        // Read intent extras
        idMeasure  = getIntent().getIntExtra("idMeasure", 0);
        idCustomer = getIntent().getIntExtra("idCustomer", 0);
        idCircuit  = getIntent().getIntExtra("idCircuit", 0);
        idLocation = getIntent().getIntExtra("idLocation", 0);
        nodeName   = getIntent().getStringExtra("nodeName");
        nodeType   = getIntent().getIntExtra("nodeType", 0);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(nodeName != null ? nodeName : "Histórico");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Date controls
        tvDate = findViewById(R.id.tvDate);
        ImageButton btnPrev = findViewById(R.id.btnPrev);
        ImageButton btnNext = findViewById(R.id.btnNext);

        btnPrev.setOnClickListener(v -> navigateDate(-1));
        btnNext.setOnClickListener(v -> navigateDate(1));

        updateDateDisplay();

        // ViewPager2 + TabLayout
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        viewPager.setAdapter(new HistoricoPagerAdapter(this));
        viewPager.setOffscreenPageLimit(1);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentTab = position;
                updateDateDisplay();
            }
        });
    }

    // ---- Date navigation ----

    private void navigateDate(int direction) {
        Calendar today = Calendar.getInstance();

        currentDate.add(Calendar.DAY_OF_MONTH, direction);

        // Don't go to future
        if (currentDate.after(today)) {
            currentDate.setTime(today.getTime());
        }

        updateDateDisplay();
        notifyDateChanged();
    }

    private void updateDateDisplay() {
        String pattern = "dd/MM/yyyy";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        tvDate.setText(sdf.format(currentDate.getTime()));
    }

    private void notifyDateChanged() {
        if (dateChangedListener != null) {
            SimpleDateFormat apiFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            dateChangedListener.onDateChanged(apiFormat.format(currentDate.getTime()));
        }
    }

    /** Returns date formatted for API calls (yyyy-MM-dd). */
    public String getApiDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(currentDate.getTime());
    }

    public int getIdMeasure()  { return idMeasure; }
    public int getIdCustomer() { return idCustomer; }
    public int getIdCircuit()  { return idCircuit; }
    public int getIdLocation() { return idLocation; }
    public int getNodeType()   { return nodeType; }
    public String getNodeName(){ return nodeName; }
    public int getCurrentTab() { return currentTab; }

    // ---- Pager Adapter ----

    private class HistoricoPagerAdapter extends FragmentStateAdapter {

        HistoricoPagerAdapter(@NonNull AppCompatActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return HistoricoTabFragment.newInstance(position);
        }

        @Override
        public int getItemCount() {
            return TAB_TITLES.length;
        }
    }
}
