package ratemycourse.console;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import ratemycourse.interfaceadapter.compare.CompareCoursesViewModel;
import ratemycourse.interfaceadapter.compare.CompareCoursesViewModel.Column;

final class ComparisonTable {
    private static final int COLUMN_WIDTH = 28;
    private final PrintWriter out;

    ComparisonTable(PrintWriter out) {
        this.out = out;
    }

    void show(CompareCoursesViewModel.State state, String reviewSource) {
        if (!state.errorMessage.isEmpty()) {
            out.println(state.errorMessage);
            return;
        }
        Column left = state.courses.get(0);
        Column right = state.courses.get(1);
        out.println();
        row("Course code", left.code, right.code);
        out.println("-".repeat(14) + "-+-" + "-".repeat(COLUMN_WIDTH) + "-+-" + "-".repeat(COLUMN_WIDTH));
        row("Title", left.title, right.title);
        row("Faculty", left.faculty, right.faculty);
        row("Credits", left.credits, right.credits);
        row("Calendar", reference(left, "[1]"), reference(right, "[2]"));
        out.println();
        out.println("Review source: " + reviewSource + " | Averages out of 5");
        row("Review count", left.reviews.count, right.reviews.count);
        row("Avg enjoyment", left.reviews.enjoyment, right.reviews.enjoyment);
        row("Avg difficulty", left.reviews.difficulty, right.reviews.difficulty);
        row("Avg workload", left.reviews.workload, right.reviews.workload);
        for (Column column : state.courses) {
            if (!column.reviews.note.isEmpty()) {
                out.println(column.reviews.note);
            }
        }
        out.println();
        links(left, "[1]");
        links(right, "[2]");
    }

    private static String reference(Column column, String marker) {
        return column.calendarLinks.isEmpty() ? CompareCoursesViewModel.MISSING_METADATA : marker;
    }

    private void links(Column column, String marker) {
        for (String url : column.calendarLinks) {
            out.println(marker + " " + column.code + "  " + url);
        }
    }

    private void row(String label, String left, String right) {
        List<String> first = wrap(left);
        List<String> second = wrap(right);
        for (int i = 0; i < Math.max(first.size(), second.size()); i++) {
            out.printf("%-14s | %-" + COLUMN_WIDTH + "s | %s%n", i == 0 ? label : "",
                    i < first.size() ? first.get(i) : "", i < second.size() ? second.get(i) : "");
        }
    }

    private static List<String> wrap(String value) {
        String remaining = value.strip().replaceAll("\\s+", " ");
        List<String> lines = new ArrayList<>();
        while (remaining.length() > COLUMN_WIDTH) {
            int space = remaining.lastIndexOf(' ', COLUMN_WIDTH);
            int end = space > 0 ? space : COLUMN_WIDTH;
            lines.add(remaining.substring(0, end));
            remaining = remaining.substring(end).stripLeading();
        }
        lines.add(remaining);
        return lines;
    }
}
