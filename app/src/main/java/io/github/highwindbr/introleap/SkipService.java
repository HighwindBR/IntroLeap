package io.github.highwindbr.introleap;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SkipService extends AccessibilityService {
    public static final String PREFS = "introleap_settings";
    public static final String PREF_DISNEY = "disney_enabled";
    public static final String PREF_DISNEY_INTRO = "disney_intro";
    public static final String PREF_SHOW_TOAST = "show_skip_toast";

    public static final String DISNEY_PACKAGE = "com.disney.disneyplus";
    private static final int MAX_NODES = 900;
    private static final long CONTENT_COOLDOWN_MS = 5000;

    private final Map<String, Long> lastClicks = new HashMap<>();
    private SharedPreferences prefs;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence eventPackage = event.getPackageName();
        if (eventPackage == null) return;
        String packageName = eventPackage.toString();
        if (!isAppEnabled(packageName)) return;

        AccessibilityNodeInfo source = event.getSource();
        if (findAndClick(source, packageName, 80)) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        findAndClick(root, packageName, MAX_NODES);
    }

    private boolean isAppEnabled(String packageName) {
        SharedPreferences p = preferences();
        return DISNEY_PACKAGE.equals(packageName) && p.getBoolean(PREF_DISNEY, false);
    }

    private boolean findAndClick(AccessibilityNodeInfo start, String packageName, int limit) {
        if (start == null) return false;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(start);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < limit) {
            AccessibilityNodeInfo node = queue.removeFirst();
            Match match = classify(node, packageName);
            if (match != null && tryClick(match, packageName)) return true;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        return false;
    }

    private Match classify(AccessibilityNodeInfo node, String packageName) {
        if (!node.isVisibleToUser()) return null;
        String label = normalize(value(node.getText()) + " " + value(node.getContentDescription()));

        if (DISNEY_PACKAGE.equals(packageName)) {
            if (enabled(PREF_DISNEY_INTRO) && isIntro(label)) return new Match(node, "intro");
            return null;
        }
        return null;
    }

    private boolean tryClick(Match match, String packageName) {
        String key = packageName + ':' + match.type;
        long now = SystemClock.elapsedRealtime();
        Long previous = lastClicks.get(key);
        if (previous != null && now - previous < CONTENT_COOLDOWN_MS) return false;

        AccessibilityNodeInfo clickable = findClickableDescendant(match.node, 24);
        if (clickable == null) clickable = match.node;
        int parents = 0;
        while (clickable != null && !clickable.isClickable() && parents++ < 6)
            clickable = clickable.getParent();

        boolean done = clickEnabled(clickable);
        if (!done && clickable != match.node) done = clickEnabled(match.node);
        if (done) {
            lastClicks.put(key, now);
            if (preferences().getBoolean(PREF_SHOW_TOAST, false))
                Toast.makeText(this, R.string.toast_intro_skipped, Toast.LENGTH_SHORT).show();
        }
        return done;
    }

    private static AccessibilityNodeInfo findClickableDescendant(AccessibilityNodeInfo start, int limit) {
        if (start == null) return null;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(start);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < limit) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isVisibleToUser() && node.isEnabled() && node.isClickable()) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        return null;
    }

    private static boolean clickEnabled(AccessibilityNodeInfo node) {
        return node != null && node.isVisibleToUser() && node.isEnabled()
                && node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private SharedPreferences preferences() {
        if (prefs == null) prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        return prefs;
    }

    private boolean enabled(String key) {
        return preferences().getBoolean(key, true);
    }

    private static boolean isIntro(String s) {
        return containsAny(s, "pular abertura", "pular introducao", "skip intro", "skip opening",
                "saltar intro", "saltar introduccion", "omitir intro", "omitir introduccion");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String value(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return normalized.replaceAll("\\s+", " ").trim();
    }

    @Override public void onInterrupt() {}

    private static final class Match {
        final AccessibilityNodeInfo node;
        final String type;
        Match(AccessibilityNodeInfo node, String type) {
            this.node = node;
            this.type = type;
        }
    }
}
