package com.shutternote;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Selection follows permanent roll IDs, never list positions or display names. */
final class FilmRollSelection {
    private boolean active;
    private final Set<String> ids = new HashSet<>();

    void begin(String id) { active = true; ids.add(id); }
    void range(java.util.List<String> order, String anchor, String end, Collection<String> baseline) {
        int first = order.indexOf(anchor), last = order.indexOf(end);
        if (first < 0 || last < 0) return;
        restore(true, baseline);
        for (int i = Math.min(first, last); i <= Math.max(first, last); i++) ids.add(order.get(i));
    }
    void toggle(String id) {
        if (!active) return;
        if (!ids.remove(id)) ids.add(id);
    }
    boolean isActive() { return active; }
    boolean contains(String id) { return ids.contains(id); }
    int count() { return ids.size(); }
    Set<String> snapshot() { return new HashSet<>(ids); }
    void clear() { active = false; ids.clear(); }
    void restore(boolean enabled, Collection<String> selected) {
        clear(); active = enabled;
        if (enabled && selected != null) ids.addAll(selected);
    }
    void retainAvailable(Collection<String> available) {
        ids.retainAll(available);
        if (available.isEmpty()) clear();
    }
}
