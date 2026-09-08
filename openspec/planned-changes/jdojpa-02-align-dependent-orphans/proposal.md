> **DRAFT.** Parked from finding 2 in `JDO-JPA-EQUIVALENCE-REVIEW-PI-CONSOLIDATED.md`.
> Promote this draft to the sole active OpenSpec change before implementation.

## Why

Generated JPA mappings derive `orphanRemoval` from foreign-key mandatoriness rather than faithfully translating JDO `dependentElement` metadata.
This creates divergence in both directions: JPA can delete children retained by JDO, and JPA can retain children deleted by JDO.
All affected collections expose removal methods, so the difference is executable domain behaviour rather than metadata-only drift.

## What Changes

- Change `ReplacePersistentWithOneToManyAnnotation` so `dependentElement = "true"` drives `CascadeType.REMOVE` and `orphanRemoval = true` wherever JPA supports orphan removal.
- Do not infer orphan removal merely because an inverse foreign key is mandatory.
- Treat foreign-key optionality and dependency ownership as separate mapping decisions.
- Add recipe coverage for dependent and non-dependent collections with optional and mandatory inverse references.
- Add cross-ORM disassociation, flush, re-parenting, parent-deletion, and child-row survival tests for the seven identified collection mappings.
- Make the singular dependent `CodaTransactionFetchOrchestration.codaTransaction` relationship explicitly immutable after construction.
- Remove its Lombok `@Setter`, or restrict the mutator if a framework requirement prevents removal, and ensure the rewrite does not introduce another mutator.
- Add an architecture or focused structural test that rejects a public or protected `setCodaTransaction` method and compiled reassignment call sites.

## Scope

Collection annotation generation belongs in `jdo2jpa`.

The singular dependent reference is latent rather than a current behavioural divergence.
The field is assigned only in the two constructors at `CodaTransactionFetchOrchestration.java:106` and `:139`.
Its Lombok-generated setter is not invoked on this type anywhere in the codebase because every current `setCodaTransaction(…)` caller targets `CodaTransactionProjection`, `InvoiceForLease2CodaTransactionLink`, or a test double.

The relationship is intentionally immutable after construction, so replacement and orphan deletion do not form part of its supported domain behaviour.
Make that constraint explicit by removing or restricting the setter instead of merely observing that no current caller uses it.
An architecture rule can reinforce the constraint, but while a public setter exists it can only establish the absence of known compiled callers rather than prove that reassignment is unreachable.

A `java-jdo` / `java-jpa` source-set split is not appropriate because it would duplicate an entity class to vary one mutator.
Neither an explicit displaced-referent delete nor an `OrmUtil.isJdo()` branch is required when reassignment is prohibited.

## Depends on

The collection recipe correction and removal of the singular setter can proceed independently.
Generic JPA architecture enforcement depends on a separate proposal for finding 8 because `estatio-base-archtestjpa` currently declares no substantive rules.
Until that capability exists, use a focused structural test to enforce the absence of the mutator.

## Validation

- Removing a dependent child deletes it under both ORMs.
- Removing or re-parenting a non-dependent child does not delete it under either ORM.
- Deleting a parent cascades only where JDO dependency metadata requires it.
- `CodaTransactionFetchOrchestration` has no public or protected mutator for `codaTransaction`.
- Generated JPA source does not introduce a replacement path for the immutable relationship.

## Open Questions

- Does Apache Causeway or either persistence provider require a non-public setter for this field, or can the Lombok `@Setter` be removed outright?
- Are any mandatory inverse relationships intentionally ownership relationships despite lacking `dependentElement = "true"`?

## Note on finding 2a

The two collections in the "JPA deletes what JDO retained" direction — `InvoiceAbstract.items` and
`ReminderLetterBatch.letters` — each have exactly one removal path, and both already delete the child
explicitly and unconditionally (`InvoiceAbstract.removeItem()`, `ReminderLetter.remove()`). The
inferred `orphanRemoval` is therefore redundant rather than destructive at every current call site.
Aligning it with `dependentElement` is still correct and is covered by the recipe change above, but
this direction is **Low** severity and should not be described as an invoice-data risk.
