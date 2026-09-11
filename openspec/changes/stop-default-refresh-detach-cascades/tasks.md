## 1. Standard Cascade Configuration

- [x] 1.1 Change both relationship converters in the standard `v2x.Persistent.relationships` composition to default to `CascadeType.PERSIST, CascadeType.MERGE`.
- [x] 1.2 Update recipe option examples and user-facing documentation to describe `REFRESH` and `DETACH` as explicit opt-in cascade members.

## 2. Generated-Output Coverage

- [x] 2.1 Update outputs produced through the standard composition for many-to-one relationships to omit `CascadeType.REFRESH` and `CascadeType.DETACH` while retaining `PERSIST` and `MERGE`; do not change lower-level fixtures that explicitly supply the old sequence.
- [x] 2.2 Update standard-composition one-to-many and inferred or mapped one-to-one outputs to omit `CascadeType.REFRESH` and `CascadeType.DETACH` while retaining `PERSIST` and `MERGE`.
- [x] 2.3 Update reference-fetch, dependent-lifecycle, persistent-ordering, inheritance, join-column, type-change, and other composition fixtures that exercise the standard relationship defaults.
- [x] 2.4 Preserve lower-level fixtures whose explicitly supplied `defaultCascade` contains `REFRESH` or `DETACH` so those outputs remain unchanged.
- [x] 2.5 Add focused lower-level fixtures proving explicit `REFRESH`, explicit `DETACH`, and caller-defined cascade order are emitted verbatim.
- [x] 2.6 Add or update dependent single-reference and collection fixtures proving `REMOVE` and `orphanRemoval = true` compose with only `PERSIST` and `MERGE` under the standard recipe.
- [x] 2.7 Add a two-cycle `v2x.Persistent.relationships` or `v2x.Persistent` fixture proving the reduced standard default changes source only during the first cycle.

## 3. Verify the Recipe Repository

- [x] 3.1 Run focused many-to-one, one-to-one, one-to-many, dependent-lifecycle, reference-fetch, persistent-ordering, explicit-configuration, and composition tests under the repository's required JDK.
- [x] 3.2 Run the complete jdo2jpa test suite and resolve any regressions.
- [x] 3.3 Build and install the candidate snapshot locally and inspect the published recipe descriptor and generated recipe metadata to confirm both standard converters supply exactly `PERSIST` and `MERGE`.
- [x] 3.4 Run strict OpenSpec validation and record the focused and complete test summaries, skipped tests, JDK, and candidate artifact version.

## 4. Validate Detached Estatio Regenerations

- [x] 4.1 Pin clean Estatio `prod` commit `4dd98637d572240ff99cb8c998590074c27c839d` as the common source and record JPA commit `907c723889d3306352c2e50cfbbae3df6406d949` for informational reconciliation only.
- [x] 4.2 Build independently selectable pre-change and candidate jdo2jpa artifacts using distinct versions or isolated local Maven repositories.
- [x] 4.3 Regenerate two detached Estatio outputs from the pinned commit with identical rewrite commands, using the pre-change artifact for output A and the candidate artifact for output B.
- [x] 4.4 Retain the exact artifact versions, jdo2jpa commits, Estatio commit, rewrite commands, and path-level inventory required to reproduce the comparison.
- [x] 4.5 Require the A/B semantic delta to contain only removal of universally supplied `CascadeType.REFRESH` and `CascadeType.DETACH` members plus directly consequent annotation formatting.
- [x] 4.6 Confirm `PERSIST`, `MERGE`, dependency-derived `REMOVE`, `orphanRemoval`, fetch strategy, cardinality, ownership, column metadata, and unrelated annotations remain unchanged.
- [x] 4.7 Inventory affected generated mappings by `@ManyToOne`, `@OneToOne`, and `@OneToMany`, and inspect the representative Mallcomm lazy collection that motivated the change.
- [x] 4.8 Separately compare candidate output B with the recorded Estatio JPA tree and classify consumer drift plus previously approved stream-order, dependency, reference-fetch, and LOB changes independently.
- [x] 4.9 Compile directly affected generated modules where supported, recording rather than bypassing unrelated consumer build blockers.

## 5. Prepare Release and Consumer Handoff

- [x] 5.1 Update project documentation with the standard `PERSIST`/`MERGE` defaults and the explicit opt-in path for `REFRESH` and `DETACH`.
- [x] 5.2 Prepare release notes identifying the generated-source change as breaking and recording the production motivation, same-input A/B inventory, candidate version, and repository verification summary.
- [x] 5.3 Document that recipe tests and generated annotations prove metadata structure only and that consumers must test workflows which intentionally refresh or detach roots.
- [x] 5.4 Document the Estatio handoff to rerun the representative Mallcomm scenario and any deliberate detach workflows after adopting the released recipe.
- [x] 5.5 Run `mvn clean verify` through the repository's approved release workflow and record the final test count, skipped tests, JDK, candidate artifact version, and consumer build limitations.
