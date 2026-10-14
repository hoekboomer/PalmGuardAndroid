package com.example.palmguard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {
    static final String PREFS = "palm_guard_settings";
    static final String KEY_ENABLED = "global_filter_enabled";
    static final String KEY_THRESHOLD = "area_threshold_dp2";
    static final String KEY_SIZE_THRESHOLD = "normalized_size_threshold_percent";
    static final int DEFAULT_THRESHOLD_DP2 = 650;
    static final int DEFAULT_SIZE_THRESHOLD_PERCENT = 45;
    private static final int MIN_THRESHOLD_DP2 = 100;
    private static final int MAX_THRESHOLD_DP2 = 2400;

    private TextView serviceStatus;
    private TextView thresholdLabel;
    private Switch filterSwitch;
    private SeekBar thresholdSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(20);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, dp(24), pad, dp(28));
        content.setBackgroundColor(Color.rgb(244, 246, 248));

        TextView title = text("掌拒", 30, true, Color.rgb(29, 43, 58));
        content.addView(title, matchWrap());

        TextView intro = text(
                "让普通电容笔保持轻触书写，同时按触点面积过滤较大的掌缘接触。",
                16, false, Color.rgb(67, 78, 91));
        addGap(content, 8);
        content.addView(intro, matchWrap());

        serviceStatus = text("辅助功能服务：正在检查…", 14, true, Color.rgb(49, 92, 136));
        addGap(content, 18);
        content.addView(serviceStatus, matchWrap());

        Button settingsButton = new Button(this);
        settingsButton.setText("打开安卓辅助功能设置");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addGap(content, 8);
        content.addView(settingsButton, matchWrap());

        filterSwitch = new Switch(this);
        filterSwitch.setText("启用全局防误触");
        filterSwitch.setTextSize(17);
        filterSwitch.setPadding(0, dp(14), 0, dp(10));
        filterSwitch.setChecked(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false));
        filterSwitch.setOnCheckedChangeListener((button, checked) ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, checked).apply());
        addGap(content, 14);
        content.addView(filterSwitch, matchWrap());

        TextView hint = text(
                "默认关闭。开启后需要先在系统设置中启用本应用的辅助功能服务；服务会短暂接管每次触摸，再将笔尖操作交给当前应用。",
                14, false, Color.rgb(88, 98, 108));
        addGap(content, 2);
        content.addView(hint, matchWrap());

        thresholdLabel = text("掌缘过滤阈值", 16, true, Color.rgb(29, 43, 58));
        addGap(content, 20);
        content.addView(thresholdLabel, matchWrap());

        thresholdSeekBar = new SeekBar(this);
        thresholdSeekBar.setMax(MAX_THRESHOLD_DP2 - MIN_THRESHOLD_DP2);
        int initial = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD_DP2);
        thresholdSeekBar.setProgress(clamp(initial, MIN_THRESHOLD_DP2, MAX_THRESHOLD_DP2) - MIN_THRESHOLD_DP2);
        thresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = MIN_THRESHOLD_DP2 + progress;
                thresholdLabel.setText(String.format(Locale.getDefault(), "掌缘过滤阈值：%d dp²", value));
                if (fromUser) getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_THRESHOLD, value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        thresholdLabel.setText(String.format(Locale.getDefault(), "掌缘过滤阈值：%d dp²", clamp(initial, MIN_THRESHOLD_DP2, MAX_THRESHOLD_DP2)));
        addGap(content, 2);
        content.addView(thresholdSeekBar, matchWrap());

        TextView sizeThresholdLabel = text("备用 size 阈值", 16, true, Color.rgb(29, 43, 58));
        addGap(content, 12);
        content.addView(sizeThresholdLabel, matchWrap());
        TextView sizeThresholdHint = text("主触点轴不可用时，使用系统提供的 0–1 粗略大小值；该值不是线性的。", 14, false, Color.rgb(88, 98, 108));
        addGap(content, 3);
        content.addView(sizeThresholdHint, matchWrap());
        SeekBar sizeThresholdSeekBar = new SeekBar(this);
        sizeThresholdSeekBar.setMax(90);
        int initialSizeThreshold = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_SIZE_THRESHOLD, DEFAULT_SIZE_THRESHOLD_PERCENT);
        sizeThresholdSeekBar.setProgress(clamp(initialSizeThreshold, 5, 95) - 5);
        sizeThresholdLabel.setText(String.format(Locale.getDefault(), "备用 size 阈值：%.2f", clamp(initialSizeThreshold, 5, 95) / 100.0));
        sizeThresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 5 + progress;
                sizeThresholdLabel.setText(String.format(Locale.getDefault(), "备用 size 阈值：%.2f", value / 100.0));
                if (fromUser) getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_SIZE_THRESHOLD, value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        addGap(content, 2);
        content.addView(sizeThresholdSeekBar, matchWrap());

        TextView calibrationTitle = text("触点校准", 18, true, Color.rgb(29, 43, 58));
        addGap(content, 16);
        content.addView(calibrationTitle, matchWrap());
        TextView calibrationHint = text("分别用电容笔尖和手掌轻触下面区域，观察设备实际报告的面积。不同屏幕的数值会不同。", 14, false, Color.rgb(88, 98, 108));
        addGap(content, 4);
        content.addView(calibrationHint, matchWrap());
        ContactProbeView probe = new ContactProbeView();
        LinearLayout.LayoutParams probeParams = matchWrap();
        probeParams.height = dp(220);
        addGap(content, 10);
        content.addView(probe, probeParams);

        TextView limitations = text(
                "手缘先落时会暂缓转发；笔尖随后落下后，只把最后落下且仍按住的触点交给前台应用，避免把手缘和笔尖组成双指操作。若设备提供的面积信息不足，会优先等待第二个触点。设备底层仍会检测全部物理触点。全局模式需要 Android 13 或更高版本。",
                13, false, Color.rgb(88, 98, 108));
        addGap(content, 18);
        content.addView(limitations, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (serviceStatus != null) {
            boolean enabled = isAccessibilityServiceEnabled();
            serviceStatus.setText(enabled
                    ? "辅助功能服务：已启用"
                    : "辅助功能服务：未启用，请在系统设置中打开");
            serviceStatus.setTextColor(enabled ? Color.rgb(38, 120, 78) : Color.rgb(174, 94, 35));
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabledServices)) return false;
        ComponentName component = new ComponentName(this, PalmGuardAccessibilityService.class);
        String full = component.flattenToString();
        String shortName = component.flattenToShortString();
        for (String entry : enabledServices.split(":")) {
            if (full.equalsIgnoreCase(entry) || shortName.equalsIgnoreCase(entry)) return true;
        }
        return false;
    }

    private class ContactProbeView extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private String line1 = "在此触摸以读取面积";
        private String line2 = "长按也可观察触点变化";
        private String line3 = "";
        private String line4 = "";
        private String line5 = "";
        private String line6 = "";

        ContactProbeView() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(Color.rgb(225, 232, 239));
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(14), dp(14), paint);
            paint.setColor(Color.rgb(29, 43, 58));
            paint.setTextSize(dp(15));
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            float y = dp(28);
            float step = dp(29);
            canvas.drawText(line1, dp(16), y, paint);
            canvas.drawText(line2, dp(16), y + step, paint);
            canvas.drawText(line3, dp(16), y + step * 2, paint);
            canvas.drawText(line4, dp(16), y + step * 3, paint);
            canvas.drawText(line5, dp(16), y + step * 4, paint);
            canvas.drawText(line6, dp(16), y + step * 5, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getPointerCount() > 0) {
                float density = getResources().getDisplayMetrics().density;
                float major = event.getTouchMajor(0) / density;
                float minor = event.getTouchMinor(0) / density;
                float toolMajor = event.getToolMajor(0) / density;
                float toolMinor = event.getToolMinor(0) / density;
                float touchArea = ellipseArea(major, minor);
                float toolArea = ellipseArea(toolMajor, toolMinor);
                float size = event.getSize(0);
                int type = event.getToolType(0);
                String tool = type == MotionEvent.TOOL_TYPE_STYLUS ? "触控笔" :
                        type == MotionEvent.TOOL_TYPE_FINGER ? "手指/普通电容笔" : "其他/未知";
                int threshold = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD_DP2);
                int sizeThreshold = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_SIZE_THRESHOLD, DEFAULT_SIZE_THRESHOLD_PERCENT);
                boolean large;
                if (touchArea > 0) {
                    line4 = String.format(Locale.getDefault(), "识别来源：触点面积 %.0f dp²", touchArea);
                    large = touchArea > threshold;
                } else if (toolArea > 0) {
                    line4 = String.format(Locale.getDefault(), "识别来源：工具面积代理 %.0f dp²", toolArea);
                    large = toolArea > threshold;
                } else if (size > 0) {
                    line4 = String.format(Locale.getDefault(), "识别来源：备用 size %.2f", size);
                    large = size * 100 > sizeThreshold;
                } else {
                    line4 = "识别来源：系统未提供尺寸数据";
                    large = false;
                }
                line1 = String.format(Locale.getDefault(), "触点轴 %.1f × %.1f dp", major, minor);
                line2 = String.format(Locale.getDefault(), "工具轴 %.1f × %.1f dp", toolMajor, toolMinor);
                line3 = String.format(Locale.getDefault(), "size %.2f · pressure %.2f · %s", size, event.getPressure(0), tool);
                line5 = String.format(Locale.getDefault(), "阈值：%d dp² / size %.2f", threshold, sizeThreshold / 100.0);
                line6 = large ? "当前判定：大面积触点" : (touchArea > 0 || toolArea > 0 || size > 0 ? "当前判定：通过" : "当前判定：尺寸数据缺失，无法按面积过滤");
                invalidate();
            }
            return true;
        }
    }

    private TextView text(String value, float sizeSp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void addGap(LinearLayout parent, int heightDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float ellipseArea(float major, float minor) {
        if (major <= 0 || minor <= 0) return 0;
        return (float) (Math.PI * major * minor / 4.0);
    }
}
