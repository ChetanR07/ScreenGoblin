package com.example.screengoblin;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // Data model for each app's usage
    public static class AppUsageInfo {
        public String appName;
        public String packageName;
        public long timeInForeground; // in milliseconds

        public AppUsageInfo(String appName, String packageName, long timeInForeground) {
            this.appName = appName;
            this.packageName = packageName;
            this.timeInForeground = timeInForeground;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    // Step 5: Trigger data loading whenever the user returns to the app
    @Override
    protected void onResume() {
        super.onResume();
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission();
        } else {
            loadRealData();
        }
    }

    // Step 2: Permission check and request methods
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void requestUsageStatsPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
    }

    // Step 3: Fetch real usage statistics from the device
    private List<AppUsageInfo> getTodayUsageStats() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager packageManager = getPackageManager();

        // Calculate start of today (00:00:00)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        Map<String, UsageStats> stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
        List<AppUsageInfo> usageList = new ArrayList<>();

        if (stats != null) {
            for (UsageStats usageStats : stats.values()) {
                long totalTimeMs = usageStats.getTotalTimeInForeground();
                // Filter out apps with 0 usage or launcher itself
                if (totalTimeMs > 0 && !usageStats.getPackageName().equals(getPackageName())) {
                    try {
                        ApplicationInfo appInfo = packageManager.getApplicationInfo(usageStats.getPackageName(), 0);
                        String appName = packageManager.getApplicationLabel(appInfo).toString();
                        usageList.add(new AppUsageInfo(appName, usageStats.getPackageName(), totalTimeMs));
                    } catch (PackageManager.NameNotFoundException ignored) {
                        // Skip uninstalled or system packages with no display name
                    }
                }
            }
        }

        // Sort descending by usage time (most used first)
        usageList.sort((a, b) -> Long.compare(b.timeInForeground, a.timeInForeground));
        return usageList;
    }

    // Step 4: Millisecond formatter
    private String formatDuration(long millis) {
        long hours = millis / (1000 * 60 * 60);
        long minutes = (millis / (1000 * 60)) % 60;

        if (hours > 0) {
            return hours + "hr " + minutes + "min";
        } else {
            return minutes + "min";
        }
    }

    // Step 5: Update the UI views with real data
    private void loadRealData() {
        List<AppUsageInfo> usageList = getTodayUsageStats();

        // 1. Calculate total screen time & update main text view
        long totalMillis = 0;
        for (AppUsageInfo app : usageList) {
            totalMillis += app.timeInForeground;
        }
        TextView screenTimeView = findViewById(R.id.screen_time_value);
        screenTimeView.setText(formatDuration(totalMillis));

        // 2. Populate app rows dynamically into app_usage_list
        LinearLayout appUsageContainer = findViewById(R.id.app_usage_list);
        appUsageContainer.removeAllViews(); // Clear dummy static rows

        // Show top apps
        int limit = Math.min(usageList.size(), 10);
        for (int i = 0; i < limit; i++) {
            AppUsageInfo app = usageList.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, 24, 0, 0);
            row.setLayoutParams(rowParams);

            // App Name
            TextView nameView = new TextView(this);
            nameView.setText(app.appName);
            nameView.setTextSize(16);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
            );
            nameView.setLayoutParams(nameParams);

            // App Time
            TextView timeView = new TextView(this);
            timeView.setText(formatDuration(app.timeInForeground));
            timeView.setTextSize(16);
            timeView.setGravity(Gravity.END);

            row.addView(nameView);
            row.addView(timeView);
            appUsageContainer.addView(row);
        }
    }
}