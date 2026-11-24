package ratemycourse.usecase.search;

import java.util.Locale;

/** Subject is a code prefix; year zero means all levels. */
public final class SearchRequest {
    private final String query;
    private final String department;
    private final int year;
    private final int page;
    private final int pageSize;

    public SearchRequest(String query, String department, int year) {
        this(query, department, year, 1, 20);
    }

    public SearchRequest(String query, String department, int year, int page, int pageSize) {
        if (year < 0 || year > 4 || page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid year or pagination");
        }
        String normalized = normalize(query);
        this.query = normalized.matches("[a-z]{2,5} +[0-9]{1,3}[a-z]?[0-9]?")
                ? normalized.replace(" ", "") : normalized;
        this.department = normalize(department);
        this.year = year;
        this.page = page;
        this.pageSize = pageSize;
    }

    static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public String getQuery() { return query; }
    public String getDepartment() { return department; }
    public int getYear() { return year; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }

    public SearchRequest withPage(int nextPage) {
        return new SearchRequest(query, department, year, nextPage, pageSize);
    }
}
