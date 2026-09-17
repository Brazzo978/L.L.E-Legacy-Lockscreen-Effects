package com.codex.lle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Host-testable filtering and ordering for the installed-app picker. */
final class AppPickerModel {
    static final int CATEGORY_ALL = 0;
    static final int CATEGORY_USER = 1;
    static final int CATEGORY_SYSTEM = 2;

    static final class Entry {
        final String label;
        final String packageName;
        final boolean system;
        final boolean selected;
        final boolean protectedByLle;

        Entry(String label, String packageName, boolean system,
                boolean selected, boolean protectedByLle) {
            this.label = label == null || label.trim().isEmpty()
                    ? packageName : label;
            this.packageName = packageName;
            this.system = system;
            this.selected = selected;
            this.protectedByLle = protectedByLle;
        }
    }

    private AppPickerModel() {
    }

    static List<Entry> filterAndSort(List<Entry> source, int category, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        List<Entry> result = new ArrayList<Entry>();
        if (source != null) {
            for (int i = 0; i < source.size(); i++) {
                Entry entry = source.get(i);
                if (entry == null || entry.packageName == null) {
                    continue;
                }
                if (category == CATEGORY_USER && entry.system) {
                    continue;
                }
                if (category == CATEGORY_SYSTEM && !entry.system) {
                    continue;
                }
                if (!needle.isEmpty()
                        && !entry.label.toLowerCase(Locale.US).contains(needle)
                        && !entry.packageName.toLowerCase(Locale.US).contains(needle)) {
                    continue;
                }
                result.add(entry);
            }
        }
        Collections.sort(result, new Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                if (left.selected != right.selected) {
                    return left.selected ? -1 : 1;
                }
                int labelOrder = left.label.compareToIgnoreCase(right.label);
                return labelOrder != 0
                        ? labelOrder
                        : left.packageName.compareToIgnoreCase(right.packageName);
            }
        });
        return result;
    }
}
