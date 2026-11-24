package ratemycourse.interfaceadapter.compare;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;

/** Shares one immutable snapshot per comparison with any subscribed view. */
public final class CompareCoursesViewModel {
    public static final String MISSING_METADATA = "Not provided";
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private volatile State state = new State(List.of(), "");
    public final String reviewSource;

    public CompareCoursesViewModel(String reviewSource) {
        this.reviewSource = reviewSource;
    }

    public State getState() {
        return state;
    }

    public void addListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    public void removeListener(PropertyChangeListener listener) {
        changes.removePropertyChangeListener(listener);
    }

    void showComparison(Column first, Column second) {
        update(new State(List.of(first, second), ""));
    }

    void showError(String message) {
        update(new State(List.of(), message));
    }

    private void update(State next) {
        State previous = state;
        state = next;
        changes.firePropertyChange("state", previous, next);
    }

    public static final class State {
        /** A successful comparison has exactly two columns, in the user's order. */
        public final List<Column> courses;
        public final String errorMessage;

        private State(List<Column> courses, String errorMessage) {
            this.courses = courses;
            this.errorMessage = errorMessage;
        }
    }

    public static final class Column {
        public final String code;
        public final String title;
        public final String faculty;
        public final String credits;
        public final List<String> calendarLinks;
        public final ReviewDisplay reviews;

        Column(String code, String title, String faculty, String credits, List<String> calendarLinks,
                ReviewDisplay reviews) {
            this.code = code;
            this.title = title;
            this.faculty = faculty;
            this.credits = credits;
            this.calendarLinks = List.copyOf(calendarLinks);
            this.reviews = reviews;
        }
    }

    public static final class ReviewDisplay {
        public final String count;
        public final String enjoyment;
        public final String difficulty;
        public final String workload;
        public final String note;

        ReviewDisplay(String count, String enjoyment, String difficulty, String workload, String note) {
            this.count = count;
            this.enjoyment = enjoyment;
            this.difficulty = difficulty;
            this.workload = workload;
            this.note = note;
        }
    }
}
