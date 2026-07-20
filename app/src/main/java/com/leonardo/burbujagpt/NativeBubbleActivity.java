package com.leonardo.burbujagpt;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Actividad anfitriona que Android coloca en la burbuja. Prueba varias rutas
 * para insertar WhatsApp oficial en la misma tarea sin WebView ni Shizuku.
 */
public class NativeBubbleActivity extends Activity {
    private static final String STATE_ATTEMPTED = "attempted";
    private static final int VERIFY_DELAY_MS = 900;
    private static final int WINDOWING_MODE_MULTI_WINDOW = 6;

    private LinearLayout root;
    private TextView status;
    private ProgressBar progress;
    private boolean attempted;
    private int nextPlanIndex;
    private List<LaunchPlan> plans;
    private final StringBuilder diagnostic = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF071A10);
        getWindow().setNavigationBarColor(0xFF071A10);
        setContentView(buildUi());

        attempted = savedInstanceState != null && savedInstanceState.getBoolean(STATE_ATTEMPTED);
        if (!attempted) root.postDelayed(this::launchOfficialInsideBubble, 120);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        attempted = false;
        nextPlanIndex = 0;
        plans = null;
        diagnostic.setLength(0);
        showLoading("Abriendo WhatsApp…");
        root.postDelayed(this::launchOfficialInsideBubble, 80);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean(STATE_ATTEMPTED, attempted);
        super.onSaveInstanceState(state);
    }

    private void launchOfficialInsideBubble() {
        if (attempted || isFinishing()) return;
        attempted = true;
        nextPlanIndex = 0;
        diagnostic.setLength(0);
        plans = buildLaunchPlans();

        diagnostic.append("taskId=").append(getTaskId())
                .append(" · display=").append(currentDisplayId())
                .append(" · planes=").append(plans.size()).append('\n');

        launchNextPlan();
    }

    private List<LaunchPlan> buildLaunchPlans() {
        List<LaunchPlan> result = new ArrayList<>();
        Set<String> components = new HashSet<>();

        Intent launcher = getPackageManager().getLaunchIntentForPackage(
                NativeBubblePublisher.WHATSAPP_PACKAGE
        );
        if (launcher != null && launcher.getComponent() != null) {
            addComponentPlan(result, components, "launcher resuelto", launcher.getComponent());
        }

        addComponentPlan(
                result,
                components,
                "HomeActivity explícita",
                new ComponentName(NativeBubblePublisher.WHATSAPP_PACKAGE, "com.whatsapp.HomeActivity")
        );
        addComponentPlan(
                result,
                components,
                "Main explícita",
                new ComponentName(NativeBubblePublisher.WHATSAPP_PACKAGE, "com.whatsapp.Main")
        );

        Intent deepLink = new Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send"));
        deepLink.setPackage(NativeBubblePublisher.WHATSAPP_PACKAGE);
        deepLink.setFlags(baseFlags());
        result.add(new LaunchPlan("enlace whatsapp://send", deepLink));

        return result;
    }

    private void addComponentPlan(
            List<LaunchPlan> result,
            Set<String> components,
            String label,
            ComponentName component
    ) {
        String flattened = component.flattenToShortString();
        if (!components.add(flattened)) return;

        Intent target = new Intent(Intent.ACTION_MAIN);
        target.addCategory(Intent.CATEGORY_LAUNCHER);
        target.setComponent(component);
        target.setFlags(baseFlags());
        result.add(new LaunchPlan(label, target));
    }

    private int baseFlags() {
        // REORDER_TO_FRONT se elimina: en WhatsApp podía recuperar una tarea externa
        // existente en lugar de crear la actividad dentro de la tarea de la burbuja.
        return Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION;
    }

    private void launchNextPlan() {
        if (isFinishing()) return;

        if (plans == null || nextPlanIndex >= plans.size()) {
            String message = "WhatsApp no tomó el control de la tarea de la burbuja después de "
                    + (plans == null ? 0 : plans.size()) + " intentos.";
            diagnostic.append(message);
            AppPreferences.recordMessage(this, diagnostic.toString());
            showError(message + " Pulsa Reintentar después de cerrar WhatsApp en aplicaciones recientes.");
            return;
        }

        LaunchPlan plan = plans.get(nextPlanIndex++);
        showLoading("Intento " + nextPlanIndex + "/" + plans.size() + ": " + plan.label + "…");

        ActivityOptions options = buildLaunchOptions();
        diagnostic.append("Intento ").append(nextPlanIndex)
                .append(": ").append(plan.label)
                .append(" · ").append(plan.intent.getComponent() != null
                        ? plan.intent.getComponent().flattenToShortString()
                        : String.valueOf(plan.intent.getData()))
                .append('\n');

        try {
            startActivity(plan.intent, options.toBundle());
            overridePendingTransition(0, 0);

            // startActivity sin excepción solo confirma que Android aceptó el intent.
            // Si esta ventana continúa enfocada, WhatsApp no se insertó y probamos otra ruta.
            root.postDelayed(() -> {
                if (isFinishing()) return;
                if (hasWindowFocus()) {
                    diagnostic.append("  resultado: anfitrión siguió visible\n");
                    launchNextPlan();
                }
            }, VERIFY_DELAY_MS);
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException error) {
            diagnostic.append("  error: ").append(error.getClass().getSimpleName())
                    .append(" · ").append(error.getMessage()).append('\n');
            root.postDelayed(this::launchNextPlan, 80);
        } catch (RuntimeException error) {
            diagnostic.append("  error runtime: ").append(error.getClass().getSimpleName())
                    .append(" · ").append(error.getMessage()).append('\n');
            root.postDelayed(this::launchNextPlan, 80);
        }
    }

    private ActivityOptions buildLaunchOptions() {
        ActivityOptions options = ActivityOptions.makeBasic();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getDisplay() != null) {
            options.setLaunchDisplayId(getDisplay().getDisplayId());
        }

        Rect bounds = new Rect();
        if (getWindow().getDecorView().getGlobalVisibleRect(bounds) && !bounds.isEmpty()) {
            options.setLaunchBounds(bounds);
        }

        boolean taskForced = invokeIntOption(options, "setLaunchTaskId", getTaskId());
        boolean windowingForced = invokeIntOption(
                options,
                "setLaunchWindowingMode",
                WINDOWING_MODE_MULTI_WINDOW
        );
        diagnostic.append("  opciones: task=").append(taskForced)
                .append(" · multiwindow=").append(windowingForced)
                .append('\n');
        return options;
    }

    private boolean invokeIntOption(ActivityOptions options, String methodName, int value) {
        try {
            Method method = ActivityOptions.class.getDeclaredMethod(methodName, int.class);
            method.setAccessible(true);
            method.invoke(options, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private int currentDisplayId() {
        return getDisplay() == null ? -1 : getDisplay().getDisplayId();
    }

    private View buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        root.setBackgroundColor(0xFF0B1510);

        TextView icon = text("☎", 32, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(0xFF25D366);
        iconBackground.setShape(GradientDrawable.OVAL);
        icon.setBackground(iconBackground);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(68), dp(68));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(icon, iconParams);

        TextView title = text("WhatsApp", 22, 0xFFF5F5F5, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(16), 0, dp(8));
        root.addView(title, titleParams);

        status = text("Abriendo la aplicación oficial…", 14, 0xFFB4B4B8, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, matchWrap());

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(18), 0, 0);
        root.addView(progress, progressParams);

        return root;
    }

    private void showLoading(String message) {
        status.setText(message);
        status.setTextColor(0xFFB4B4B8);
        progress.setVisibility(View.VISIBLE);
        removeActionButtons();
    }

    private void showError(String message) {
        status.setText(message);
        status.setTextColor(0xFFFCA5A5);
        progress.setVisibility(View.GONE);
        removeActionButtons();

        Button retry = button("Reintentar", view -> {
            attempted = false;
            nextPlanIndex = 0;
            plans = null;
            diagnostic.setLength(0);
            showLoading("Reintentando…");
            root.postDelayed(this::launchOfficialInsideBubble, 100);
        });
        retry.setTag("bubble_action");
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        retryParams.setMargins(0, dp(20), 0, 0);
        root.addView(retry, retryParams);

        Button settings = button("Ver diagnóstico", view -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        settings.setTag("bubble_action");
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        settingsParams.setMargins(0, dp(10), 0, 0);
        root.addView(settings, settingsParams);
    }

    private void removeActionButtons() {
        for (int index = root.getChildCount() - 1; index >= 0; index--) {
            if ("bubble_action".equals(root.getChildAt(index).getTag())) {
                root.removeViewAt(index);
            }
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String value, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setOnClickListener(listener);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF1F3A2A);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0xFF315B43);
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class LaunchPlan {
        final String label;
        final Intent intent;

        LaunchPlan(String label, Intent intent) {
            this.label = label;
            this.intent = intent;
        }
    }
}
