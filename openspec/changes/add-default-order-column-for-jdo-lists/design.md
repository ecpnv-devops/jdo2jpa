## Context

`ReplacePersistentWithOneToManyAnnotation` converts JDO `@Persistent(mappedBy = ...)` / `@Element` one-to-many fields into JPA `@OneToMany`. It already special-cases an explicit `@javax.jdo.annotations.Order(column = "...")` and rewrites it to `@OrderColumn(name = "...")` (see `ReplacePersistentWithOneToManyAnnotationTest#replacePersistentWithOneToManyAnnotation`).

DataNucleus does not require `@Order` to persist list order: for any `List`-typed relationship it defaults to an implicit index column named `<fieldName>_INTEGER_IDX` when no ordering strategy is otherwise specified. JPA providers (EclipseLink/Hibernate) have no such implicit default — without `@OrderColumn`, a `List`'s persisted order is undefined. This was found in production (`ReminderLetterBatch#letters`) where the field had no `@Order` annotation, so the migrated JPA code silently dropped ordering until `@OrderColumn(name = "letters_INTEGER_IDX")` was added by hand.

## Goals / Non-Goals

**Goals:**
- When a `List`-typed field is converted via `mappedBy` and carries no `@Order` annotation, emit `@OrderColumn(name = "<fieldName>_INTEGER_IDX")` alongside `@OneToMany`, matching DataNucleus's default column-naming convention so no schema/data migration is needed.
- Leave existing explicit-`@Order` handling untouched — it already produces the correct `@OrderColumn` and takes precedence.
- Leave `Set`/`SortedSet`/other non-`List` collections untouched — they never had an implicit index column in JDO.

**Non-Goals:**
- `@Order(mappedBy = ...)` (ordering by an existing property/column, not an implicit index column) — different JDO strategy, not handled here.
- Unidirectional/join-table `List` associations without `mappedBy` — out of scope for this change; DataNucleus's index-column default also applies there but the existing recipe's `mappedBy`-driven flow doesn't cover that path yet.
- Any change to cascade, fetch, or orphan-removal behavior — unaffected by this change.

## Decisions

- **Detect "no explicit `@Order`" rather than trying to always add `@OrderColumn`:** the existing explicit-`@Order` → `@OrderColumn` conversion already exists and is correct; this change only needs to fill the gap where `@Order` is absent. Reusing `FindAnnotations.find(multiVariable, Constants.Jdo.ORDER_ANNOTATION_FULL)` (a new constant, mirroring the existing `ELEMENT_ANNOTATION_FULL` pattern) keeps the two code paths from conflicting.
- **Gate on `hasCollection(multiVariable)` and the field's declared type being `List`** (not just "some Collection"): use the same `RewriteUtils`/`JavaType` inspection already used elsewhere in the file to check the raw type is `java.util.List` (or a subtype), so `Set`/`SortedSet` fields are left alone. This mirrors DataNucleus's actual behavior — only `List` needs an index column to preserve iteration order.
- **Column name defaults to `<fieldName>_INTEGER_IDX`:** matches DataNucleus's own default naming (confirmed by the production example, `letters` → `letters_INTEGER_IDX`), so recipe output requires no separate column rename/migration.
- **Implement inside `ReplacePersistentWithOneToManyAnnotationVisitor.visitVariableDeclarations`,** appending the `@OrderColumn` addition immediately after the existing `@OneToMany` template is applied (same place `@JoinTable`/`@JoinColumn` are conditionally added today), using the same `AddAnnotationConditionally` + `JavaTemplate` machinery already used in this file, rather than introducing a new recipe class. This keeps one recipe responsible for one field's full one-to-many translation, consistent with the file's current structure.

## Risks / Trade-offs

- [Risk] A `List` field might be ordered via an in-memory `Comparator`/business logic rather than a persisted DB column, and adding `@OrderColumn` could be semantically unnecessary (though harmless) → Mitigation: this only affects `mappedBy` relationship fields that were already relying on DataNucleus's implicit index column, so the behavior we're adding matches what JDO was already doing at the DB level; no new semantics are introduced.
- [Risk] Column name collision if a project already has a same-named column for a different purpose → Mitigation: the default name mirrors the DataNucleus-generated name already present in the schema, so it can only reintroduce a column that JDO was already creating.
- [Risk] Under-detecting explicit `@Order` (e.g. `@Order(extensions = ...)` or fully-qualified annotation usage) could cause a duplicate/conflicting `@OrderColumn` → Mitigation: reuse the existing `FindAnnotations` lookup pattern already proven for `@Element`/`@Persistent` in this file; add a test for a field with `@Order` present to confirm no double-annotation.

## Migration Plan

Not applicable beyond running the updated recipe — this is a source-code rewrite recipe with no separate deployment; verification happens by running the recipe over the full Estatio codebase in CI (per user's own workflow) rather than a staged rollout.
