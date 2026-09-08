## 1. Characterize Default-Composite Ordering

- [x] 1.1 Add a composite-level regression fixture showing that a delegated stream helper with comparator-defined ordering receives no generated natural sort.
- [x] 1.2 Add no-rewrite fixtures for direct streams from an ordered `List`, an insertion-ordered set, an ordinary set, a naturally ordered `SortedSet`, and a custom-comparator `SortedSet`.
- [x] 1.3 Add no-rewrite fixtures for pipelines containing `flatMap`, visible comparator sorting, and `.unordered()`.
- [x] 1.4 Add a fixture documenting that the current `SortedSet` to `TreeSet` field migration preserves ordering and requires no stream compensation.

## 2. Remove Unsafe Default Registration

- [x] 2.1 Remove `AddSortedMethodToStreamMethods` from `com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent` in `datanucleus-jdo-to-jpa-eclipselink.yml`.
- [x] 2.2 Remove or update the adjacent stale comment so the declarative recipe accurately describes current collection conversion.
- [x] 2.3 Verify that no other bundled default composite activates `AddSortedMethodToStreamMethods`.

## 3. Deprecate the Standalone Recipe

- [x] 3.1 Mark `AddSortedMethodToStreamMethods` deprecated without changing its explicit invocation contract.
- [x] 3.2 Update its display name, description, and implementation documentation to warn that it introduces natural ordering without proving source-order loss.
- [x] 3.3 Retain direct unit tests for explicit standalone invocation, structural detection of existing `sorted` calls, false-positive avoidance, and idempotence.

## 4. Verify Behaviour

- [x] 4.1 Run the standalone and composite ordering tests and confirm all exclusion scenarios pass.
- [x] 4.2 Run the complete jdo2jpa test suite and resolve any recipe regressions.
- [x] 4.3 Regenerate a representative Estatio JPA diff and confirm the unsafe tenant invoice-address `.sorted()` calls are removed.
- [x] 4.4 Inventory other removed generated `.sorted()` calls and identify any business ordering that must instead be made explicit in source.

## Verification Notes

Focused standalone and composite tests passed with 5 tests and no failures.
The complete jdo2jpa suite passed with 191 tests, no failures, and 2 skipped tests.
A detached Estatio worktree at JDO commit `d21c47dd83` was regenerated with the locally installed `1.2.2-SNAPSHOT` recipe bundle.
The regenerated `Lease` retained its two explicit comparator sorts and did not add natural sorting to either tenant invoice-address helper call.
Across 6,057 corresponding Java files, regeneration retained all 200 source `.sorted()` calls and introduced none.
Comparison with the prior generated JPA worktree found 85 previously injected `.sorted()` calls across 63 files.
Those 85 calls had no source counterpart, so no additional source-defined ordering was removed; any consumer that intentionally relied on them must now express that business ordering explicitly.
