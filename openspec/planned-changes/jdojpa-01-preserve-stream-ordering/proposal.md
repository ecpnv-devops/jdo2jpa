> **DRAFT.** Parked from finding 1 in `JDO-JPA-EQUIVALENCE-REVIEW-PI-CONSOLIDATED.md`.
> Promote this draft to the sole active OpenSpec change before implementation.

## Why

`AddSortedMethodToStreamMethods` adds natural-order `.sorted()` calls while translating JDO collection access into JPA stream access.
In `Lease`, the added sort occurs after a deliberate preference comparator and before `findFirst()`, so the JPA branch can select a different invoice address from the JDO branch.
The problem is a generic transformation defect rather than a reason to alter shared business logic or add an `OrmUtil.isJdo()` branch.

## What Changes

- Change `AddSortedMethodToStreamMethods` so it only reconstructs ordering for streams directly derived from persistent collections whose JDO ordering would otherwise be lost.
- Do not append natural sorting when an explicit comparator or another ordering helper already appears anywhere in the expression.
- Preserve the ordering of pipelines containing `flatMap`, helper-method ordering, or an earlier `sorted(Comparator)` call.
- Add recipe tests based on the `Lease` preference-comparator path and representative already-sorted and flattened streams.
- Add a cross-ORM regression test proving that preferred tenant invoice-address selection is identical.

## Scope

The generic fix belongs in `jdo2jpa`.
Prod supplies the domain regression fixture and consumes the corrected generated source.
Generated JPA business code must not contain a compensating conditional.

## Relationship to finding 8

The core recipe fix and cross-ORM regression test do not depend on the JPA architecture-test capability proposed by finding 8.
If ambiguous stream pipelines are to be rejected by an architecture rule, that additional guard must wait until finding 8 supplies substantive JPA rules.

## Validation

- Recipe tests demonstrate that required collection ordering is still generated.
- Recipe tests demonstrate that deliberate comparator ordering is never overridden.
- The same invoice-address fixture selects the same result under JDO/DataNucleus and JPA/EclipseLink.

## Open Questions

- Which source metadata or generated accessor patterns provide the most reliable signal that ordering reconstruction is required?
- Should ambiguous pipelines be left unchanged and rejected by an architecture check rather than sorted automatically?
