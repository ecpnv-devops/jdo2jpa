## 1. Characterize Default-Composite Ordering

- [ ] 1.1 Add a composite-level regression fixture showing that a delegated stream helper with comparator-defined ordering receives no generated natural sort.
- [ ] 1.2 Add no-rewrite fixtures for direct streams from an ordered `List`, an insertion-ordered set, an ordinary set, a naturally ordered `SortedSet`, and a custom-comparator `SortedSet`.
- [ ] 1.3 Add no-rewrite fixtures for pipelines containing `flatMap`, visible comparator sorting, and `.unordered()`.
- [ ] 1.4 Add a fixture documenting that the current `SortedSet` to `TreeSet` field migration preserves ordering and requires no stream compensation.

## 2. Remove Unsafe Default Registration

- [ ] 2.1 Remove `AddSortedMethodToStreamMethods` from `com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent` in `datanucleus-jdo-to-jpa-eclipselink.yml`.
- [ ] 2.2 Remove or update the adjacent stale comment so the declarative recipe accurately describes current collection conversion.
- [ ] 2.3 Verify that no other bundled default composite activates `AddSortedMethodToStreamMethods`.

## 3. Deprecate the Standalone Recipe

- [ ] 3.1 Mark `AddSortedMethodToStreamMethods` deprecated without changing its explicit invocation contract.
- [ ] 3.2 Update its display name, description, and implementation documentation to warn that it introduces natural ordering without proving source-order loss.
- [ ] 3.3 Retain direct unit tests for explicit standalone invocation, structural detection of existing `sorted` calls, false-positive avoidance, and idempotence.

## 4. Verify Behaviour

- [ ] 4.1 Run the standalone and composite ordering tests and confirm all exclusion scenarios pass.
- [ ] 4.2 Run the complete jdo2jpa test suite and resolve any recipe regressions.
- [ ] 4.3 Regenerate a representative Estatio JPA diff and confirm the unsafe tenant invoice-address `.sorted()` calls are removed.
- [ ] 4.4 Inventory other removed generated `.sorted()` calls and identify any business ordering that must instead be made explicit in source.
