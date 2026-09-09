# Release Notes: Preserve LOB and Lazy-Basic Metadata

## Breaking generated-source change

Explicit JDO `jdbcType = "BLOB"` and `jdbcType = "CLOB"` metadata now generates JPA `@Lob` on persistent fields and properties.
Explicit scalar JDO `defaultFetchGroup = "false"` now generates `@Basic(fetch = FetchType.LAZY)`.
Existing `@Basic` attributes are preserved, while an existing eager fetch value is replaced because the explicit JDO exclusion is authoritative.
Relationship and collection fetch metadata remains controlled by the dedicated association recipes.

Consumers should review detached payload access because attributes that were previously loaded through JPA's eager basic default can now remain unavailable after the persistence context closes.
JPA lazy basic fetching remains a provider hint and generated annotations alone do not prove deferred loading.

## Recipe composition

`v2x.Persistent` now uses explicit relationship, scalar-fetch, and cleanup wrappers in that order.
This avoids relying on apparent mixed parameterless and configured recipe order in a flat declarative list.
LOB recognition executes before JDO column attributes are removed.
LOB classification and lazy-basic selection remain independent: storage metadata determines `@Lob`, while only explicit scalar fetch-group exclusion determines `FetchType.LAZY`.

## Repository validation

Focused scalar, LOB, relationship, descriptor, and composition tests cover field and property access, existing annotations, negative association and storage cases, converters, annotation ordering, imports, and reruns.
The complete repository release verification passes under JDK 21 with 217 tests, no failures, and 2 skipped tests.
Candidate artifact `1.2.2-lob-candidate-SNAPSHOT` contains the staged persistent wrappers and combined BLOB/CLOB column rule.

## Reproducible Estatio evidence

The validation input is clean Estatio `prod` commit `4dd98637d572240ff99cb8c998590074c27c839d`.
The pre-change recipe is commit `9b149c74296664dcddbf47a062f99f9f4745f0ed`, installed as `1.2.2-lob-pre-SNAPSHOT`.
The candidate implementation is commit `9865a92b29cb25518b917c6841d79be2a8cc2080`, whose validated working tree was installed as `1.2.2-lob-candidate-SNAPSHOT`.
Estatio JPA commit `907c723889d3306352c2e50cfbbae3df6406d949` is an informational reconciliation reference only.

Both artifacts were applied independently with:

[source,shell]
----
./run-rewrite.sh -Djdo2jpa.version=1.2.2-lob-pre-SNAPSHOT
./run-rewrite.sh -Djdo2jpa.version=1.2.2-lob-candidate-SNAPSHOT
----

The same-input A/B comparison changes exactly three Java files and no non-Java files.
It adds four `@Lob` annotations for the four explicit BLOB declarations and two `@Basic(fetch = FetchType.LAZY)` annotations for `DocumentAbstract.blobBytes` and `DocumentAbstract.clobChars`.
All other changed lines are required imports or directly consequent annotation formatting.
Java payload types and mapped column names are unchanged.

The unchanged Flyway schema maps `DocumentAbstract.blob_bytes`, `FastnetApplyOrchestration.blobBytes`, `FastnetImportOrchestration.blobBytesImportSheet`, and `FastnetImportOrchestration.blobBytesAnalysisSheet` to SQL Server `image` columns.
It maps `DocumentAbstract.clob_chars` to SQL Server `text`.
Generated DDL remains diagnostic and Flyway remains authoritative.

Direct compilation attempts were not metadata acceptance evidence because the detached generated tree has unrelated missing command-log classes in the document module and missing QueryDSL `Q` classes in the Fastnet module.
Those consumer build blockers were recorded rather than bypassed.

## Estatio adoption handoff

After upgrading the recipe version and regenerating, Estatio must validate runtime behavior with EclipseLink static weaving enabled.
The consumer test should capture SQL and inspect JPA or EclipseLink attribute state before and after first access to representative binary and character payloads.
It should prove that the initial metadata load omits payload columns and that first access fetches the payload.
The test may allow first access to load a provider fetch group rather than requiring the other lazy payload attribute to remain independently unloaded.

Estatio must verify null, empty, and large binary and character payload round trips against the unchanged Flyway-managed SQL Server schema.
It must cover graph-loading and detached-access behavior and make any required explicit-fetch or transaction-boundary changes in consumer code.
No `OrmUtil.isJdo()` branch or other ORM-specific entity branch should be introduced.
