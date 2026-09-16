package com.shijia.oledmotionguard;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "oled_motion_guard";
    private static final String KEY_INTERVAL = "interval_minutes";
    private static final String KEY_DURATION = "duration_seconds";
    private static final String KEY_OPACITY = "opacity_percent";
    private static final String KEY_MODE = "mode";

    private static final String[] MODE_KEYS = {"ribbons", "rainbow", "confetti", "pixel_shift"};
    private static final String[] MODE_LABELS = {
            "彩色纸带（推荐）", "彩虹洗屏", "彩色纸屑", "柔和彩色方块"
    };

    private SharedPreferences prefs;
    private EditText intervalInput;
    private EditText durationInput;
    private EditText opacityInput;
    private Spinner modeSpinner;
    private TextView permissionText;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("OLED 副屏动态保护");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(45, 35, 65));
        page.addView(title, marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42), 0, 0, 0, 0));

        TextView intro = new TextView(this);
        intro.setText("在 spacedesk 安卓客户端上方定时显示低透明度动态色彩，动画结束后自动隐藏。\n\n它需要悬浮窗权限，并会保留一个前台服务通知。\n\n注意：它不能移动底层应用的静态 UI，只能让覆盖期间的显示像素发生变化。");
        intro.setTextSize(15);
        intro.setTextColor(Color.DKGRAY);
        page.addView(intro, marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130), 0, 0, 0, 10));

        permissionText = new TextView(this);
        permissionText.setTextSize(14);
        page.addView(permissionText, marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44), 0, 0, 0, 4));

        Button permissionButton = new Button(this);
        permissionButton.setText("允许悬浮窗权限");
        permissionButton.setOnClickListener(v -> openOverlayPermission());
        page.addView(permissionButton, marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), 0, 0, 0, 12));

        intervalInput = numberInput(String.valueOf(prefs.getInt(KEY_INTERVAL, 10)));
        addLabeledField(page, "间隔（分钟，1～1440）", intervalInput);

        durationInput = numberInput(String.valueOf(prefs.getInt(KEY_DURATION, 20)));
        addLabeledField(page, "持续（秒，3～600）", durationInput);

        opacityInput = numberInput(String.valueOf(prefs.getInt(KEY_OPACITY, 22)));
        addLabeledField(page, "透明度（%，5～70）", opacityInput);

        TextView modeLabel = label("动画模式");
        page.addView(modeLabel, marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28), 0, 8, 0, 2));
        modeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, MODE_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setSelection(indexForMode(prefs.getString(KEY_MODE, "ribbons")));
        page.addView(modeSpinner, marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), 0, 0, 0, 14));

        Button startButton = new Button(this);
        startButton.setText("启动定时保护");
        startButton.setOnClickListener(v -> startProtection());
        page.addView(startButton, marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50), 0, 0, 0, 6));

        Button testButton = new Button(this);
        testButton.setText("立即测试一次");
        testButton.setOnClickListener(v -> testOnce());
        page.addView(testButton, marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50), 0, 0, 0, 6));

        Button stopButton = new Button(this);
        stopButton.setText("停止定时保护");
        stopButton.setOnClickListener(v -> stopProtection());
        page.addView(stopButton, marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50), 0, 0, 0, 12));

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setTextColor(Color.DKGRAY);
        page.addView(statusText, marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56), 0, 0, 0, 0));

        setContentView(scroll);
        refreshPermissionStatus();
    }

    private void addLabeledField(LinearLayout page, String text, EditText input) {
        page.addView(label(text), marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28), 0, 8, 0, 2));
        page.addView(input, marginParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52), 0, 0, 0, 0));
    }

    private EditText numberInput(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextSize(17);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        return input;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(Color.DKGRAY);
        return view;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height,
                                                    int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int indexForMode(String mode) {
        for (int i = 0; i < MODE_KEYS.length; i++) {
            if (MODE_KEYS[i].equals(mode)) return i;
        }
        return 0;
    }

    private boolean saveForm() {
        int interval = boundedValue(intervalInput, 10, 1, 1440);
        int duration = boundedValue(durationInput, 20, 3, 600);
        int opacity = boundedValue(opacityInput, 22, 5, 70);
        intervalInput.setText(String.valueOf(interval));
        durationInput.setText(String.valueOf(duration));
        opacityInput.setText(String.valueOf(opacity));
        prefs.edit()
                .putInt(KEY_INTERVAL, interval)
                .putInt(KEY_DURATION, duration)
                .putInt(KEY_OPACITY, opacity)
                .putString(KEY_MODE, MODE_KEYS[modeSpinner.getSelectedItemPosition()])
                .apply();
        return true;
    }

    private int boundedValue(EditText input, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void startProtection() {
        if (!hasOverlayPermission()) {
            openOverlayPermission();
            return;
        }
        saveForm();
        requestNotificationPermissionIfNeeded();
        startMotionService(MotionOverlayService.ACTION_START);
        setStatus("定时保护已启动。动画会按间隔自动出现。\n前台服务通知请不要手动划掉。\n");
    }

    private void testOnce() {
        if (!hasOverlayPermission()) {
            openOverlayPermission();
            return;
        }
        saveForm();
        requestNotificationPermissionIfNeeded();
        startMotionService(MotionOverlayService.ACTION_TEST);
        setStatus("正在播放测试动画；如果覆盖了 spacedesk 画面，说明权限和层级正常。\n");
    }

    private void stopProtection() {
        stopService(new Intent(this, MotionOverlayService.class));
        setStatus("定时保护已停止。\n");
    }

    private void startMotionService(String action) {
        Intent intent = new Intent(this, MotionOverlayService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
    }

    private void openOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
        }
    }

    private void refreshPermissionStatus() {
        if (hasOverlayPermission()) {
            permissionText.setText("悬浮窗权限：已允许 ✓");
            permissionText.setTextColor(Color.rgb(35, 120, 70));
        } else {
            permissionText.setText("悬浮窗权限：未允许，请先点击下面的按钮");
            permissionText.setTextColor(Color.rgb(170, 70, 40));
        }
    }

    private void setStatus(String text) {
        if (statusText != null) statusText.setText(text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionText != null) refreshPermissionStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 1001 && results.length > 0
                && results[0] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "通知权限被拒绝，定时器仍可能受系统后台策略影响。", Toast.LENGTH_LONG).show();
        }
    }
}
