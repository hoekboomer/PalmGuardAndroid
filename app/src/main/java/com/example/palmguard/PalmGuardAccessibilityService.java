package com.example.palmguard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.TouchInteractionController;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

public class PalmGuardAccessibilityService extends AccessibilityService {
    private static final long CLASSIFICATION_DELAY_MS = 80;
    private static final int DEFAULT_THRESHOLD_DP2 = MainActivity.DEFAULT_THRESHOLD_DP2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TouchInteractionController controller;
    private SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener = (prefs, key) -> {
        if (MainActivity.KEY_ENABLED.equals(key)) updateTouchInterception();
    };
    private boolean interactionPending;
    private boolean interactionHadLargeContact;
    private final List<Integer> pointerDownOrder = new ArrayList<>();
    private long interactionToken;
    private Runnable classificationTimeout;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (Build.VERSION.SDK_INT < 33) return;
        preferences = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        updateTouchInterception();
    }

    private void updateTouchInterception() {
        if (Build.VERSION.SDK_INT < 33) return;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;

        if (isFilterEnabled()) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            setServiceInfo(info);
            if (controller == null) {
                controller = getTouchInteractionController(Display.DEFAULT_DISPLAY);
                controller.registerCallback(getMainExecutor(), new TouchInteractionController.Callback() {
                    @Override public void onMotionEvent(MotionEvent event) {
                        handleMotionEvent(event);
                    }

                    @Override public void onStateChanged(int state) {
                        if (state == TouchInteractionController.STATE_CLEAR) finishInteraction();
                    }
                });
            }
        } else {
            if (controller != null) {
                if (controller.getState() == TouchInteractionController.STATE_TOUCH_INTERACTING) {
                    controller.requestDelegating();
                }
                finishInteraction();
                controller.unregisterAllCallbacks();
                controller = null;
            }
            info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            setServiceInfo(info);
        }
    }

    private void handleMotionEvent(MotionEvent event) {
        if (controller == null || controller.getState() != TouchInteractionController.STATE_TOUCH_INTERACTING) return;

        if (!isFilterEnabled() || isLocked()) {
            cancelClassificationTimeout();
            controller.requestDelegating();
            finishInteraction();
            return;
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || !interactionPending) beginInteraction(event.getPointerId(0));
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            int newPointerId = event.getPointerId(event.getActionIndex());
            if (!pointerDownOrder.contains(newPointerId)) pointerDownOrder.add(newPointerId);
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            pointerDownOrder.remove(Integer.valueOf(event.getPointerId(event.getActionIndex())));
        }
        boolean hasLargeContact = false;
        boolean hasUnknownSizeMetric = false;

        for (int i = 0; i < event.getPointerCount(); i++) {
            int pointerId = event.getPointerId(i);
            double area = touchAreaDp2(event, i);
            boolean usesNormalizedSize = false;
            if (area <= 0) area = toolAreaDp2(event, i);
            if (area <= 0 && event.getSize(i) > 0) {
                area = event.getSize(i) * 100.0;
                usesNormalizedSize = true;
            }
            boolean nativeStylus = event.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS ||
                    event.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER;
            boolean fingerLike = event.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER ||
                    event.getToolType(i) == MotionEvent.TOOL_TYPE_UNKNOWN;
            if (!nativeStylus && fingerLike && (area <= 0 || usesNormalizedSize)) {
                // A weak normalized size value cannot safely rule out a palm-first contact.
                hasUnknownSizeMetric = true;
            }
            boolean large = !nativeStylus && area > 0 && (usesNormalizedSize
                    ? area > getSizeThresholdPercent()
                    : area > getAreaThreshold());
            if (large) {
                hasLargeContact = true;
                interactionHadLargeContact = true;
            }
        }

        if (hasLargeContact || interactionHadLargeContact) {
            cancelClassificationTimeout();
            if (event.getPointerCount() > 1) {
                int latestPointerId = getLatestActivePointerId(event);
                if (latestPointerId != MotionEvent.INVALID_POINTER_ID) {
                    controller.requestDragging(latestPointerId);
                } else {
                    controller.requestTouchExploration();
                }
                finishInteraction();
                return;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                // A palm-only interaction is consumed instead of reaching the app.
                controller.requestTouchExploration();
                finishInteraction();
            }
            // Keep classifying while a large contact is down; a small pen tip may arrive next.
            return;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            cancelClassificationTimeout();
            controller.requestDelegating();
            finishInteraction();
            return;
        }

        if (event.getPointerCount() > 1) {
            cancelClassificationTimeout();
            int latestPointerId = getLatestActivePointerId(event);
            if (latestPointerId != MotionEvent.INVALID_POINTER_ID) {
                // Palm-first mode: forward the newest still-down contact as a single pointer.
                controller.requestDragging(latestPointerId);
            } else {
                controller.requestTouchExploration();
            }
            finishInteraction();
            return;
        }

        // When a finger-like contact has no usable size, keep it pending. In the
        // palm-first workflow the pen may arrive after an arbitrary pause; the timer
        // would otherwise delegate the palm before the pen touches the screen.
        if (!hasUnknownSizeMetric && event.getPointerCount() == 1 &&
                (action == MotionEvent.ACTION_DOWN ||
                        (action == MotionEvent.ACTION_MOVE && classificationTimeout == null))) {
            scheduleClassificationTimeout();
        }
    }

    private void beginInteraction(int firstPointerId) {
        cancelClassificationTimeout();
        interactionPending = true;
        interactionHadLargeContact = false;
        pointerDownOrder.clear();
        pointerDownOrder.add(firstPointerId);
        interactionToken++;
    }

    private int getLatestActivePointerId(MotionEvent event) {
        int liftedIndex = event.getActionMasked() == MotionEvent.ACTION_POINTER_UP
                ? event.getActionIndex() : -1;
        for (int i = pointerDownOrder.size() - 1; i >= 0; i--) {
            int pointerId = pointerDownOrder.get(i);
            int pointerIndex = event.findPointerIndex(pointerId);
            if (pointerIndex >= 0 && pointerIndex != liftedIndex) return pointerId;
        }
        for (int i = event.getPointerCount() - 1; i >= 0; i--) {
            if (i != liftedIndex) return event.getPointerId(i);
        }
        return MotionEvent.INVALID_POINTER_ID;
    }

    private void scheduleClassificationTimeout() {
        cancelClassificationTimeout();
        long token = interactionToken;
        classificationTimeout = () -> {
            if (token != interactionToken || !interactionPending || controller == null) return;
            if (controller.getState() == TouchInteractionController.STATE_TOUCH_INTERACTING) {
                controller.requestDelegating();
            }
            finishInteraction();
        };
        handler.postDelayed(classificationTimeout, CLASSIFICATION_DELAY_MS);
    }

    private void cancelClassificationTimeout() {
        if (classificationTimeout != null) {
            handler.removeCallbacks(classificationTimeout);
            classificationTimeout = null;
        }
    }

    private void finishInteraction() {
        cancelClassificationTimeout();
        interactionPending = false;
        interactionHadLargeContact = false;
        pointerDownOrder.clear();
        interactionToken++;
    }

    private double touchAreaDp2(MotionEvent event, int pointerIndex) {
        float density = getResources().getDisplayMetrics().density;
        float major = event.getTouchMajor(pointerIndex) / density;
        float minor = event.getTouchMinor(pointerIndex) / density;
        if (major <= 0 || minor <= 0) return 0;
        return Math.PI * major * minor / 4.0;
    }

    private double toolAreaDp2(MotionEvent event, int pointerIndex) {
        float density = getResources().getDisplayMetrics().density;
        float major = event.getToolMajor(pointerIndex) / density;
        float minor = event.getToolMinor(pointerIndex) / density;
        if (major <= 0 || minor <= 0) return 0;
        return Math.PI * major * minor / 4.0;
    }

    private int getAreaThreshold() {
        return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getInt(MainActivity.KEY_THRESHOLD, DEFAULT_THRESHOLD_DP2);
    }

    private int getSizeThresholdPercent() {
        return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getInt(MainActivity.KEY_SIZE_THRESHOLD, MainActivity.DEFAULT_SIZE_THRESHOLD_PERCENT);
    }

    private boolean isFilterEnabled() {
        return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_ENABLED, false);
    }

    private boolean isLocked() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return keyguard != null && keyguard.isKeyguardLocked();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() {
        finishInteraction();
    }

    @Override public void onDestroy() {
        finishInteraction();
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        if (controller != null) controller.unregisterAllCallbacks();
        super.onDestroy();
    }
}
