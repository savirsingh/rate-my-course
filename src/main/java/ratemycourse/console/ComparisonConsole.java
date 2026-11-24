package ratemycourse.console;

import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Scanner;
import ratemycourse.interfaceadapter.compare.CompareCoursesController;
import ratemycourse.interfaceadapter.compare.CompareCoursesViewModel;

public final class ComparisonConsole {
    private final CompareCoursesController controller;
    private final CompareCoursesViewModel viewModel;
    private final Scanner input;
    private final PrintWriter out;

    public ComparisonConsole(CompareCoursesController controller, CompareCoursesViewModel viewModel,
            Scanner input, PrintWriter out) {
        this.controller = controller;
        this.viewModel = viewModel;
        this.input = input;
        this.out = out;
    }

    public void run() {
        ComparisonTable table = new ComparisonTable(out);
        PropertyChangeListener render = event -> table.show(viewModel.getState(), viewModel.reviewSource);
        viewModel.addListener(render);
        try {
            promptForComparisons();
        } finally {
            viewModel.removeListener(render);
        }
    }

    private void promptForComparisons() {
        out.println("Rate My Course | Compare two UofT courses");
        out.println("Enter full codes, for example CSC207H1 and MAT237Y1. Type q to exit.");
        do {
            String first = read("First course: ");
            if (isExit(first)) {
                break;
            }
            String second = read("Second course: ");
            if (isExit(second)) {
                break;
            }
            out.println("Checking courses. The catalog may take a minute to wake up.");
            out.flush();
            controller.compare(first, second);
        } while (askAgain());
        out.println("Goodbye.");
        out.flush();
    }

    private boolean askAgain() {
        while (true) {
            String answer = read("Compare another pair? [y/n]: ").toLowerCase(Locale.ROOT);
            if ("y".equals(answer) || "yes".equals(answer)) {
                return true;
            }
            if (isExit(answer) || "n".equals(answer) || "no".equals(answer)) {
                return false;
            }
            out.println("Enter y or n.");
        }
    }

    private String read(String prompt) {
        out.print(prompt);
        out.flush();
        return input.hasNextLine() ? input.nextLine().strip() : "q";
    }

    private static boolean isExit(String value) {
        return "q".equalsIgnoreCase(value) || "exit".equalsIgnoreCase(value);
    }
}
