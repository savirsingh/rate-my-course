package ratemycourse.usecase.search;

import java.util.Locale;

/** Empty department and year zero mean no filter. */
public final class SearchRequest {
    private final String query;
    private final String department;
    private final int year;

    public SearchRequest(String query, String department, int year) {
        if (year < 0 || year > 4) {
            throw new IllegalArgumentException("Year must be zero (all) or between 1 and 4");
        }
        this.query = normalize(query);
        this.department = normalize(department);
        this.year = year;
    }

    static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public String getQuery() { return query; }
    public String getDepartment() { return department; }
    public int getYear() { return year; }
}
