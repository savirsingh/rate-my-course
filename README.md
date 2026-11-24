# Team Project

## UC3 · Compare two courses

Rate My Course compares two UofT courses in the terminal. Course titles, primary faculty, available credits and official calendar links come from the deployed 2026–2027 catalog. The console labels student ratings as **DEMO** because they come from fictional, in-memory reviews. [Course catalog API](https://unofficial-uoft-course-api-26-27.onrender.com/docs)

### Run the terminal demo

From the repository root, use JDK 11 and Maven 3.9 to build, test and launch the console.

```sh
mvn clean verify
mvn -q compile exec:java
```

Enter `CSC207H1` and `MAT237Y1` to compare two courses with demo reviews. Type `y`, then enter `APS111H1` and `CSC207H1` to see an Engineering course with published credits and no demo reviews. Type `n` after a comparison or `q` at a course prompt to exit.

The demo needs an internet connection. The first lookup can take a minute while the catalog wakes up. Each course's calendar references appear below the table so the full URLs can be opened or copied. The tests use local fixtures and can run offline once Maven has downloaded the dependencies.

| Display | Meaning |
|---|---|
| Not provided | The catalog omitted this metadata. |
| 0 reviews, N/A averages | The review lookup succeeded and returned no reviews for this course. |
| Unavailable | The review lookup failed for this course. Its count and averages are unknown. |

### How UC3's Clean Architecture engine works

1. `ComparisonConsole` reads two codes. `CompareCoursesController` puts them in a `ComparisonInput` and calls `CompareCourses`, the input boundary.
2. `CompareCoursesInteractor` validates the pair, asks `CourseCatalog` and `ReviewRepository` for data, and calculates a `ReviewSummary` for each successful review lookup.
3. The interactor calls `CompareCoursesOutputBoundary` once with either a `ComparisonOutput` or a typed `ComparisonException`. A review read failure affects only its own course column.
4. `CompareCoursesPresenter` converts that outcome into display values. It replaces the state in `CompareCoursesViewModel`, clearing the previous comparison on failure and the previous error on success.
5. The view model notifies the subscribed console. The console reads its state and `ComparisonTable` lays out the columns and full calendar links.

`CompareCoursesMain` assembles this engine. Entities and use-case classes depend only on the JDK and other inner-layer types. The HTTP adapter and presenter implement interfaces owned by the use-case layer. [CSC207 Chapter 11](https://github.com/CSC207-UofT/207-course-notes/blob/main/11-clean-architecture.md)

### Team integration

- **Savir (UC1) and Lucas (UC4)** can reuse `ratemycourse.entity.Course`, `Review` and the read interfaces in `ratemycourse.usecase.gateway` when their own interactors need course or review data. The catalog returns an empty `Optional` for an unknown code and raises `DataAccessException` on a failed lookup.
- **Carey (UC2)** can implement `ReviewRepository.findByCourseCode` and return one `Review` per stored review for the normalized code. Return an empty list for a successful lookup with no reviews and throw `DataAccessException` on a read failure. Supply the adapter and its source label in `CompareCoursesMain`. UC3 handles counts and averaging.
- **Future Swing view** can subscribe to the same view model and call `CompareCoursesController.compare` on a background thread. Model notifications run on the calling thread. Schedule rendering of each new snapshot on Swing's event-dispatch thread and remove the listener when the view closes.

## Team project notes

Please keep this up-to-date with information about your project throughout the term.

The readme should include information such as:
- a summary of what your application is all about
- a list of the user stories, along with who is responsible for each one
- information about the API(s) that your project uses 
- screenshots or animations demonstrating current functionality

By keeping this README up-to-date,
your team will find it easier to prepare for the final presentation
at the end of the term.
