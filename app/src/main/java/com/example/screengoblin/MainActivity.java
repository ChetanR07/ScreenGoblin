package com.example.screengoblin;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.pm.ResolveInfo;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // Data model for each app's usage
    public static class AppUsageInfo {
        public String appName;
        public String packageName;
        public long timeInForeground; // in milliseconds
        public Drawable appIcon;

        public AppUsageInfo(String appName, String packageName, long timeInForeground, Drawable appIcon) {
            this.appName = appName;
            this.packageName = packageName;
            this.timeInForeground = timeInForeground;
            this.appIcon = appIcon;
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

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission();
        } else {
            loadRealData();
        }
    }

    // Permission check and request methods
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
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

    // Calculate exact foreground usage strictly between startTime (00:00:00 today) and endTime
    private Map<String, Long> getExactTodayForegroundTimes(UsageStatsManager usageStatsManager, long startTime, long endTime) {
        Map<String, Long> usageMap = new HashMap<>();
        UsageEvents events = usageStatsManager.queryEvents(startTime, endTime);
        if (events == null) {
            return usageMap;
        }

        UsageEvents.Event currentEvent = new UsageEvents.Event();
        Map<String, Long> resumeTimes = new HashMap<>();
        long maxPossibleDuration = Math.max(0, endTime - startTime);

        while (events.hasNextEvent()) {
            events.getNextEvent(currentEvent);
            String pkg = currentEvent.getPackageName();
            int type = currentEvent.getEventType();
            long timestamp = currentEvent.getTimeStamp();

            if (timestamp < startTime || timestamp > endTime) {
                continue;
            }

            // Screen locked or turned off -> close all open app sessions immediately
            if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE || type == UsageEvents.Event.KEYGUARD_SHOWN) {
                for (Map.Entry<String, Long> entry : resumeTimes.entrySet()) {
                    long resumeTime = entry.getValue();
                    if (timestamp > resumeTime) {
                        long duration = timestamp - resumeTime;
                        if (duration > 0 && duration <= maxPossibleDuration) {
                            usageMap.put(entry.getKey(), usageMap.getOrDefault(entry.getKey(), 0L) + duration);
                        }
                    }
                }
                resumeTimes.clear();
                continue;
            }

            if (pkg == null || pkg.equals("android") || pkg.equals("com.android.systemui")) {
                continue;
            }

            // App came to top of screen
            if (type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (!resumeTimes.containsKey(pkg)) {
                    resumeTimes.put(pkg, timestamp);
                }
            }
            // App left top of screen / paused / stopped / moved to background
            else if (type == UsageEvents.Event.ACTIVITY_PAUSED
                    || type == UsageEvents.Event.ACTIVITY_STOPPED
                    || type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                if (resumeTimes.containsKey(pkg)) {
                    long resumeTime = resumeTimes.get(pkg);
                    if (timestamp > resumeTime) {
                        long duration = timestamp - resumeTime;
                        if (duration > 0 && duration <= maxPossibleDuration) {
                            usageMap.put(pkg, usageMap.getOrDefault(pkg, 0L) + duration);
                        }
                    }
                    resumeTimes.remove(pkg);
                }
            }
        }

        // If an app is currently open on top of the screen right now
        for (Map.Entry<String, Long> entry : resumeTimes.entrySet()) {
            long resumeTime = entry.getValue();
            if (endTime > resumeTime) {
                long duration = endTime - resumeTime;
                if (duration > 0 && duration <= maxPossibleDuration) {
                    usageMap.put(entry.getKey(), usageMap.getOrDefault(entry.getKey(), 0L) + duration);
                }
            }
        }

        // Ensure no single app exceeds the total time elapsed today
        for (Map.Entry<String, Long> entry : usageMap.entrySet()) {
            if (entry.getValue() > maxPossibleDuration) {
                usageMap.put(entry.getKey(), maxPossibleDuration);
            }
        }

        return usageMap;
    }

    // Fetch screen time for all apps available on the phone
    private List<AppUsageInfo> getAllInstalledAppsUsage() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager packageManager = getPackageManager();

        // Calculate start of today (00:00:00 today)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        // Query exact today usage events
        Map<String, Long> todayUsage = getExactTodayForegroundTimes(usageStatsManager, startTime, endTime);

        // Query all installed applications on the device
        List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        List<AppUsageInfo> usageList = new ArrayList<>();
        Map<String, Boolean> seenPackages = new HashMap<>();

        for (ApplicationInfo appInfo : installedApps) {
            String packageName = appInfo.packageName;

            if (seenPackages.containsKey(packageName) || packageName.equals(getPackageName())) {
                continue;
            }

            long usageTimeMs = todayUsage.getOrDefault(packageName, 0L);

            // Include if it's a launchable app, a user-installed app, an updated system app, or has recorded usage today
            boolean isLaunchable = packageManager.getLaunchIntentForPackage(packageName) != null;
            boolean isUserApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            boolean isUpdatedSystemApp = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

            if (isLaunchable || isUserApp || isUpdatedSystemApp || usageTimeMs > 0) {
                seenPackages.put(packageName, true);
                String appName = packageManager.getApplicationLabel(appInfo).toString();
                Drawable appIcon = packageManager.getApplicationIcon(appInfo);

                usageList.add(new AppUsageInfo(appName, packageName, usageTimeMs, appIcon));
            }
        }

        // Also check if any package with usage today wasn't in installedApps
        for (Map.Entry<String, Long> entry : todayUsage.entrySet()) {
            String pkg = entry.getKey();
            long usageTimeMs = entry.getValue();
            if (!seenPackages.containsKey(pkg) && !pkg.equals(getPackageName()) && usageTimeMs > 0) {
                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(pkg, 0);
                    String appName = packageManager.getApplicationLabel(appInfo).toString();
                    Drawable appIcon = packageManager.getApplicationIcon(appInfo);
                    seenPackages.put(pkg, true);
                    usageList.add(new AppUsageInfo(appName, pkg, usageTimeMs, appIcon));
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
        }

        // Sort: apps with most screen time first, then alphabetically for 0-minute apps
        usageList.sort((a, b) -> {
            if (b.timeInForeground != a.timeInForeground) {
                return Long.compare(b.timeInForeground, a.timeInForeground);
            }
            return a.appName.compareToIgnoreCase(b.appName);
        });

        return usageList;
    }

    // Millisecond to readable time formatter
    private String formatDuration(long millis) {
        long hours = millis / (1000 * 60 * 60);
        long minutes = (millis / (1000 * 60)) % 60;

        if (hours > 0) {
            return hours + "hr " + minutes + "min";
        } else if (minutes > 0) {
            return minutes + "min";
        } else {
            return "0min";
        }
    }

    // Update the UI with all apps and total screentime
    private void loadRealData() {
        List<AppUsageInfo> usageList = getAllInstalledAppsUsage();

        // 1. Calculate total screen time & update main text view
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long maxElapsedToday = Math.max(0, System.currentTimeMillis() - calendar.getTimeInMillis());

        long totalMillis = 0;
        for (AppUsageInfo app : usageList) {
            totalMillis += app.timeInForeground;
        }
        totalMillis = Math.min(totalMillis, maxElapsedToday);

        TextView screenTimeView = findViewById(R.id.screen_time_value);
        screenTimeView.setText(formatDuration(totalMillis));

        // 2. Populate all available apps dynamically
        LinearLayout appUsageContainer = findViewById(R.id.app_usage_list);
        appUsageContainer.removeAllViews();

        int density = (int) getResources().getDisplayMetrics().density;

        for (AppUsageInfo app : usageList) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, 8 * density, 0, 8 * density);
            row.setLayoutParams(rowParams);

            // App Icon
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(app.appIcon);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(36 * density, 36 * density);
            iconParams.setMarginEnd(12 * density);
            iconView.setLayoutParams(iconParams);

            // App Name
            TextView nameView = new TextView(this);
            nameView.setText(app.appName);
            nameView.setTextSize(16);
            nameView.setTextColor(getColor(android.R.color.tab_indicator_text));
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

            row.addView(iconView);
            row.addView(nameView);
            row.addView(timeView);
            appUsageContainer.addView(row);
        }
    }
}