> **DRAFT.** Parked from finding 3 in `JDO-JPA-EQUIVALENCE-REVIEW-PI-CONSOLIDATED.md`.
> Promote this draft to the sole active OpenSpec change before implementation.

## Why

`ReplacePersistentWithManyToOneAnnotation` initially emits `FetchType.LAZY`, but a later recipe stage removes it from every generated `@ManyToOne`.
JPA therefore falls back to its eager default for 461 relationships, including references that JDO did not place in the default fetch group.
This changes graph loading, query volume, cycle exposure, and refresh behaviour across the application.

## What Changes

- Remove or narrow the later recipe stage that strips `FetchType.LAZY` from generated `@ManyToOne` mappings.
- Translate explicit JDO `defaultFetchGroup = "true"` references to `FetchType.EAGER`.
- Translate other references to explicit `FetchType.LAZY` mappings.
- Generate an explicit fetch policy for every to-one relationship so provider defaults cannot silently change behaviour.
- Add recipe tests for eager opt-in, lazy default, and interactions with other relationship annotations.
- Add JPA architecture rules and query-count tests covering representative aggregate graphs.

## Scope

The annotation correction belongs in `jdo2jpa`.
Prod owns architecture and integration tests that demonstrate the operational loading behaviour under EclipseLink static weaving.
No shared production `OrmUtil` conditional is required.

## Depends on

The recipe correction and query-count tests can proceed independently.
The generated-source architecture rule depends on a separate proposal for finding 8 because `estatio-base-archtestjpa` currently declares no substantive rules.
The finding-8 capability should land before this proposal is considered complete if the architecture rule remains part of its acceptance criteria.

## Validation

- Generated-source inspection finds no `@ManyToOne` without an explicit fetch policy.
- Explicit JDO default-fetch-group references remain eager.
- Representative ordinary references remain unloaded until accessed.
- Query-count tests show bounded graph loading for the selected domain scenarios.

## Open Questions

- Are there references that are effectively eager through named fetch plans despite lacking explicit `defaultFetchGroup = "true"`?
- Which production flows provide the most stable query-count approval tests?
