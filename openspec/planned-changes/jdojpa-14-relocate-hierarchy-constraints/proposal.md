> **DRAFT.** Parked from finding 14 in `JDO-JPA-EQUIVALENCE-REVIEW-PI-CONSOLIDATED.md`.
> Promote this draft to the sole active OpenSpec change before implementation.

## Why

JPA ignores table constraints declared on subclasses in a `SINGLE_TABLE` hierarchy because only the root entity owns the physical table mapping.
The rewrite can therefore generate annotations that appear to preserve JDO indexes or uniqueness constraints but have no effect in JPA metadata or generated DDL.
Flyway limits immediate runtime impact, but annotation metadata remains incomplete and misleading.

## What Changes

- Detect constraints and indexes declared on JDO classes that become subclasses in a JPA `SINGLE_TABLE` hierarchy.
- Relocate those generated declarations to the effective root `@Table` mapping.
- Merge root and subclass declarations deterministically and deduplicate equivalent names or column sets.
- Report conflicts where declarations with the same name have different definitions.
- Add recipe tests for single-level, multi-level, multiple-subclass, duplicate, and conflicting hierarchies.
- Add prod metadata/schema tests for the identified hierarchies and a JPA equivalent of `InvoiceSummary_PrimaryKey_Test`.

## Scope

Hierarchy-aware annotation placement belongs in `jdo2jpa`.
Prod retains Flyway as the authoritative schema definition and supplies metadata and primary-key round-trip tests.
No runtime ORM conditional is required.

## Validation

- EclipseLink metadata exposes each intended hierarchy-wide index and unique constraint on the root table.
- Generated declarations contain no ignored subclass `@Table` constraints.
- Duplicate declarations collapse without changing effective schema intent.
- Conflicting declarations fail generation or validation with an actionable diagnostic.
- Composite primary-key round trips pass under JPA.

## Open Questions

- Should constraint names be preserved exactly when several subclass declarations are merged onto one table?
- Which constraints are informational mirrors of Flyway and which must also be valid for annotation-driven DDL generation?
