package ratemycourse.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import ratemycourse.interfaceadapter.search.SearchController;
import ratemycourse.interfaceadapter.search.SearchViewModel;

/** Swing adapter: invokes the controller in a worker and renders presenter-owned display state. */
public final class CourseBrowser extends JFrame {
    private final SearchController controller;
    private final SearchViewModel viewModel;
    private final JTextField query = new JTextField(16);
    private final JTextField subject = new JTextField(5);
    private final JComboBox<String> year = new JComboBox<>(new String[]{"All levels", "1", "2", "3", "4"});
    private final JButton search = new JButton("Search");
    private final JButton retry = new JButton("Retry");
    private final JButton previous = new JButton("Previous");
    private final JButton next = new JButton("Next");
    private final JLabel status = new JLabel("Loading courses...");
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> results = new JList<>(listModel);
    private final JTextArea details = new JTextArea();
    private List<SearchViewModel.Row> displayedCourses = List.of();
    private Runnable retryAction;

    public CourseBrowser(SearchController controller, SearchViewModel viewModel, String catalogueLabel) {
        super("Rate My Course — UC1 search preview");
        this.controller = controller;
        this.viewModel = viewModel;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 600);
        setLocationRelativeTo(null);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addLabelled(filters, "Course code or name", query);
        addLabelled(filters, "Subject (e.g. CSC)", subject);
        addLabelled(filters, "Code level", year);
        filters.add(search);
        filters.add(retry);
        retry.setVisible(false);
        status.getAccessibleContext().setAccessibleName("Search status");
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        details.getAccessibleContext().setAccessibleName("Selected course metadata");
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.getAccessibleContext().setAccessibleName("Matching courses");
        results.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                int index = results.getSelectedIndex();
                if (index >= 0 && index < displayedCourses.size()) {
                    details.setText(displayedCourses.get(index).details);
                    details.setCaretPosition(0);
                }
            }
        });
        JSplitPane content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(results), new JScrollPane(details));
        content.setResizeWeight(0.5);
        JPanel heading = new JPanel(new BorderLayout());
        heading.add(new JLabel(catalogueLabel), BorderLayout.NORTH);
        heading.add(filters, BorderLayout.CENTER);
        heading.add(status, BorderLayout.SOUTH);
        JPanel pages = new JPanel();
        pages.add(previous);
        pages.add(next);
        add(heading, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        add(pages, BorderLayout.SOUTH);
        search.addActionListener(event -> submit());
        query.addActionListener(event -> submit());
        subject.addActionListener(event -> submit());
        retry.addActionListener(event -> load(retryAction));
        previous.addActionListener(event -> page(viewModel.getState().page - 1));
        next.addActionListener(event -> page(viewModel.getState().page + 1));
        getRootPane().setDefaultButton(search);
        submit();
    }

    private static void addLabelled(JPanel panel, String text, javax.swing.JComponent component) {
        JLabel label = new JLabel(text);
        label.setLabelFor(component);
        panel.add(label);
        panel.add(component);
    }

    private void submit() {
        String text = query.getText();
        String prefix = subject.getText();
        int level = year.getSelectedIndex();
        load(() -> controller.search(text, prefix, level));
    }

    private void page(int page) {
        load(() -> controller.page(page));
    }

    private void load(Runnable action) {
        retryAction = action;
        setBusy(true);
        status.setText("Loading courses... The API may need a minute to wake up.");
        retry.setVisible(false);
        displayedCourses = List.of();
        listModel.clear();
        details.setText("");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                if (!isDisplayable()) {
                    return;
                }
                setBusy(false);
                try {
                    get();
                    render(viewModel.getState());
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFailure("Search was interrupted. Please retry.");
                }
                catch (ExecutionException exception) {
                    showFailure("Search could not finish. Please retry.");
                }
            }
        }.execute();
    }

    private void render(SearchViewModel.State state) {
        status.setText(state.status);
        displayedCourses = state.courses;
        state.courses.forEach(course -> listModel.addElement(course.label));
        previous.setEnabled(state.error.isEmpty() && state.page > 1);
        next.setEnabled(state.hasNext);
        retry.setVisible(!state.error.isEmpty());
        if (state.error.isEmpty()) {
            details.setText("Select a result to inspect its course metadata.");
        }
    }

    private void showFailure(String message) {
        status.setText(message);
        retry.setVisible(true);
        previous.setEnabled(false);
        next.setEnabled(false);
    }

    private void setBusy(boolean busy) {
        search.setEnabled(!busy);
        query.setEnabled(!busy);
        subject.setEnabled(!busy);
        year.setEnabled(!busy);
        retry.setEnabled(!busy);
        previous.setEnabled(false);
        next.setEnabled(false);
    }
}
