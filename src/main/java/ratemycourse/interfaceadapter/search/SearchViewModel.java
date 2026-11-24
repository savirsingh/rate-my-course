package ratemycourse.interfaceadapter.search;

import java.util.List;

/** One immutable display snapshot, following UC3's view-model convention. */
public final class SearchViewModel {
    private volatile State state = new State(List.of(), "Loading courses...", "", 1, false);

    public State getState() { return state; }

    void show(State next) { state = next; }

    public static final class State {
        public final List<Row> courses;
        public final String status;
        public final String error;
        public final int page;
        public final boolean hasNext;

        State(List<Row> courses, String status, String error, int page, boolean hasNext) {
            this.courses = List.copyOf(courses);
            this.status = status;
            this.error = error;
            this.page = page;
            this.hasNext = hasNext;
        }
    }

    public static final class Row {
        public final String code;
        public final String label;
        public final String details;

        Row(String code, String label, String details) {
            this.code = code;
            this.label = label;
            this.details = details;
        }
    }
}
