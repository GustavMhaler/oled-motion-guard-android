package com.shijia.oledmotionguard;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;

public class MotionOverlayService extends Service {
    public static final String ACTION_START = "com.shijia.oledmotionguard.START";
    public static final String ACTION_TEST = "com.shijia.oledmotionguard.TEST";
    public static final String ACTION_TRIGGER = "com.shijia.oledmotionguard.TRIGGER";
    public static final String ACTION_STOP = "com.shijia.oledmotionguard.STOP";

    private static final String CHANNEL_ID = "oled_motion_guard";
    private static final int NOTIFICATION_ID = 4107;
    private static final int ALARM_REQUEST_CODE = 4108;
    private static final long FRAME_MS = 40L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private AlarmManager alarmManager;
    private OverlayView overlayView;
    private boolean overlayAdded;
    private long animationStartedAt;
    private long animationDurationMs;
    private Runnable frameRunnable;
    private Runnable finishRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        createNotificationChannel();
        startAsForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!canDrawOverlays()) {
            cancelAlarm();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_TEST.equals(action) || ACTION_TRIGGER.equals(action)) {
            showAnimation();
            scheduleNext();
        } else {
            scheduleNext();
        }
        return START_STICKY;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    /**
     * Use a one-shot idle-aware alarm instead of relying only on a Handler in
     * this process. If an OEM reclaims the service process, the receiver can
     * bring the foreground service back for the next animation.
     */
    private void scheduleNext() {
        cancelAlarm();
        long intervalMs = getPreferences().getInt("interval_minutes", 10) * 60_000L;
        long delayMs = Math.max(60_000L, intervalMs);
        PendingIntent pendingIntent = alarmPendingIntent();
        long triggerAt = SystemClock.elapsedRealtime() + delayMs;
        alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent
        );
    }

    private PendingIntent alarmPendingIntent() {
        Intent intent = new Intent(this, MotionAlarmReceiver.class);
        intent.setAction(ACTION_TRIGGER);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, intent, flags);
    }

    private void cancelAlarm() {
        if (alarmManager != null) {
            alarmManager.cancel(alarmPendingIntent());
        }
    }

    private void showAnimation() {
        if (!canDrawOverlays()) return;
        int opacity = getPreferences().getInt("opacity_percent", 22);
        int durationSeconds = getPreferences().getInt("duration_seconds", 20);
        String mode = getPreferences().getString("mode", "ribbons");
        animationDurationMs = Math.max(3_000L, Math.min(600_000L, durationSeconds * 1_000L));
        animationStartedAt = System.currentTimeMillis();

        if (!overlayAdded) {
            overlayView = new OverlayView(this);
            overlayView.setMode(mode);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.alpha = Math.max(0.05f, Math.min(0.70f, opacity / 100f));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            try {
                windowManager.addView(overlayView, params);
                overlayAdded = true;
            } catch (WindowManager.BadTokenException | SecurityException ignored) {
                overlayView = null;
                overlayAdded = false;
                return;
            }
        } else if (overlayView != null) {
            overlayView.setMode(mode);
        }

        if (frameRunnable != null) handler.removeCallbacks(frameRunnable);
        if (finishRunnable != null) handler.removeCallbacks(finishRunnable);
        frameRunnable = new Runnable() {
            @Override
            public void run() {
                if (!overlayAdded || overlayView == null) return;
                long elapsed = System.currentTimeMillis() - animationStartedAt;
                float progress = Math.min(1f, elapsed / (float) animationDurationMs);
                overlayView.setProgress(progress);
                handler.postDelayed(this, FRAME_MS);
            }
        };
        finishRunnable = this::removeOverlay;
        handler.post(frameRunnable);
        handler.postDelayed(finishRunnable, animationDurationMs);
    }

    private void removeOverlay() {
        if (frameRunnable != null) handler.removeCallbacks(frameRunnable);
        if (overlayAdded && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (IllegalArgumentException ignored) {
                // The system may have removed the window during shutdown.
            }
        }
        overlayView = null;
        overlayAdded = false;
    }

    private android.content.SharedPreferences getPreferences() {
        return getSharedPreferences("oled_motion_guard", MODE_PRIVATE);
    }

    private void startAsForeground() {
        Intent launch = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("OLED 动态保护运行中")
                .setContentText("定时动态覆盖 spacedesk 副屏")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "OLED 动态保护", NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持定时悬浮层服务运行");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        cancelAlarm();
        if (finishRunnable != null) handler.removeCallbacks(finishRunnable);
        removeOverlay();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
