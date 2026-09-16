## Why

`ReplacePersistentWithOneToManyAnnotation` already converts an explicit JDO `@Order(column = "...")` into JPA `@OrderColumn(name = "...")`. But when a `List`-typed `@Persistent(mappedBy = ...)` field has **no** `@Order` annotation at all, DataNucleus still implicitly persists list order via a default index column named `<fieldName>_INTEGER_IDX` on the child table. JPA has no equivalent implicit default: an `@OneToMany` on a `List` without `@OrderColumn` has unspecified persisted order. Migrated code silently loses persisted ordering in this case, which was discovered in production code (`ReminderLetterBatch#letters`) and had to be fixed by hand. The recipe should close this gap so it isn't repeated across the rest of the codebase.

## What Changes

- `ReplacePersistentWithOneToManyAnnotation` adds a default `@OrderColumn(name = "<fieldName>_INTEGER_IDX")` when all of the following hold:
  - the field type is `java.util.List` (not `Set`, `SortedSet`, or another `Collection`)
  - the field is being converted via `mappedBy` (a JDO `@Persistent`/`@Element` one-to-many relationship)
  - no `@javax.jdo.annotations.Order` annotation is present on the field (the existing explicit-`@Order` handling is unchanged and takes precedence)
- No change to behavior for `Set`/`SortedSet`/other `Collection` types, or for fields that already carry an explicit `@Order`.

## Capabilities

### New Capabilities
- `implicit-list-order-preservation`: default `@OrderColumn` generation for JDO `List` one-to-many relationships that rely on DataNucleus's implicit index column, i.e. have no explicit `@Order` annotation.

### Modified Capabilities
(none — the existing explicit-`@Order` → `@OrderColumn` behavior is unchanged)

## Impact

- Code: `src/main/java/com/ecpnv/openrewrite/jdo2jpa/ReplacePersistentWithOneToManyAnnotation.java`, `src/main/java/com/ecpnv/openrewrite/jdo2jpa/Constants.java`
- Tests: `src/test/java/com/ecpnv/openrewrite/jdo2jpa/ReplacePersistentWithOneToManyAnnotationTest.java` (a few focused cases; minimal unit testing is sufficient — the change will be validated end-to-end by running the recipe against the full Estatio codebase in CI)
- Non-goals: `@Order(mappedBy = ...)` (ordering by an existing property, not an implicit index column) is out of scope; unidirectional/join-table `List` associations without `mappedBy` are out of scope for this change.
