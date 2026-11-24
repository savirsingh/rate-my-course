## Add API-backed UC1 course search and metadata preview

The project currently has no executable course browser. This change loads the
Unofficial UofT Course API's Arts & Science catalogue and lets students search by
code/partial title, filter by subject/year, see matching codes/names and a count,
and select a result to inspect metadata. For example, ` cSc 207 ` finds
`CSC207H1 — Software Design` and displays its official source URL.

Catalogue loading runs off the Swing event thread. The HTTP adapter follows all
pages and publishes an immutable snapshot only after complete validation. Successful
loads are cached in memory; failures show an error and Retry without exposing or
caching a partial catalogue. Blank queries browse within filters, and no matches
has its own clear message. Search does not write catalogue data.

The search interactor depends on a read-only `CourseCatalogue` port. HTTP and TSV
adapters implement it, wired in `Main`, with no Swing/HTTP/JSON dependencies in the
use-case layer. API mode is the default with a configurable base URL; offline demo
and local-file modes are explicit options. There is no automatic demo fallback.

The API does not supply department or year fields. This increment uses subject
prefixes and course-code levels for Arts & Science, and labels/documents that
mapping. Public descriptions are empty, which the metadata preview identifies.
README instructions start the API separately with its public dataset.

### Validation

- `mvn --batch-mode verify`: 46 tests passed; zero Checkstyle violations.
- HTTP tests use a temporary loopback server: pagination, malformed data, incomplete
  pages, status errors, timeout, interruption, caching, and failure/retry recovery.
- Actual API integration: loaded 5,368 Arts & Science records across 54 pages.
- Local Swing smoke: normalized search, metadata selection, no matches, year and
  subject filtering, plus boundary failure/retry rendering passed.
- CI workflow runs Maven verification on push/PR; check the branch's actual run.

### Scope and follow-up

This is a first UC1 increment. Review loading/error handling and UC2–UC4 action
integration remain open. In-memory caching is not persistent storage. The project
still needs a saving/loading demonstration through another appropriate use case.

### Reviewer focus

Check search/filter semantics, subject/year derivation, inward dependencies, and
the distinction between API failure and zero matches. Run the README demo.
Per the team contract, obtain at least one teammate review before merging.
