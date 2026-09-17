package com.codex.lle;

import java.util.ArrayList;
import java.util.List;

public final class AppPickerModelTest {
    private AppPickerModelTest() {
    }

    public static void main(String[] args) {
        List<AppPickerModel.Entry> entries = new ArrayList<AppPickerModel.Entry>();
        entries.add(new AppPickerModel.Entry(
                "Zulu", "com.example.zulu", false, false, false));
        entries.add(new AppPickerModel.Entry(
                "Alpha System", "com.android.alpha", true, false, false));
        entries.add(new AppPickerModel.Entry(
                "Beta", "com.example.beta", false, true, false));

        List<AppPickerModel.Entry> all = AppPickerModel.filterAndSort(
                entries, AppPickerModel.CATEGORY_ALL, "");
        require(all.size() == 3, "all category should retain every entry");
        require("com.example.beta".equals(all.get(0).packageName),
                "selected entries should sort first");

        List<AppPickerModel.Entry> users = AppPickerModel.filterAndSort(
                entries, AppPickerModel.CATEGORY_USER, "");
        require(users.size() == 2, "user category should exclude system apps");

        List<AppPickerModel.Entry> systems = AppPickerModel.filterAndSort(
                entries, AppPickerModel.CATEGORY_SYSTEM, "");
        require(systems.size() == 1 && systems.get(0).system,
                "system category should retain only system apps");

        List<AppPickerModel.Entry> packageSearch = AppPickerModel.filterAndSort(
                entries, AppPickerModel.CATEGORY_ALL, "android.alpha");
        require(packageSearch.size() == 1, "package search should match");

        List<AppPickerModel.Entry> labelSearch = AppPickerModel.filterAndSort(
                entries, AppPickerModel.CATEGORY_ALL, "zUlU");
        require(labelSearch.size() == 1, "label search should ignore case");
        System.out.println("AppPickerModelTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
