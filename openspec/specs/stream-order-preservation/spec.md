# stream-order-preservation Specification

## Purpose
TBD - created by archiving change jdojpa-01-preserve-stream-ordering. Update Purpose after archive.
## Requirements
### Requirement: Require evidence before synthesizing ordering
The default JDO-to-JPA migration MUST synthesize stream ordering only when it can identify the source ordering, identify the migration transformation that loses that ordering, and reproduce the same ordering semantics.

#### Scenario: No order-losing transformation is present
- **WHEN** the default migration has not transformed an ordered source into an unordered representation
- **THEN** it does not add a stream sorting operation

#### Scenario: Future transformation demonstrably loses ordering
- **WHEN** a future migration transformation converts an ordered source into an unordered representation
- **THEN** any ordering compensation is coupled to that transformation and reproduces the source comparator or order expression

#### Scenario: Original ordering cannot be reproduced
- **WHEN** migration detects possible ordering loss but cannot establish the original ordering semantics
- **THEN** it does not substitute natural ordering

### Requirement: Default composite does not apply blanket natural sorting
The default JDO-to-JPA composite SHALL NOT invoke `AddSortedMethodToStreamMethods` or otherwise append natural-order `.sorted()` based only on an annotation, a `Stream` return type, or a collection stream source.

#### Scenario: Annotated helper returns an ordered stream
- **WHEN** an annotated method delegates to a helper that returns a stream with comparator-defined encounter order
- **THEN** the default migration leaves the returned invocation unchanged

#### Scenario: Direct ordinary set stream
- **WHEN** an annotated method directly streams an ordinary `Set`
- **THEN** the default migration does not invent natural ordering for the unspecified encounter order

### Requirement: Preserve collection encounter-order semantics
The default migration SHALL leave stream ordering unchanged for collection types whose source encounter order is meaningful or whose lack of ordering is part of the source contract.

#### Scenario: Ordered list
- **WHEN** an annotated method streams a `List`
- **THEN** the default migration does not replace list encounter order with natural order

#### Scenario: Insertion-ordered set
- **WHEN** an annotated method streams a `LinkedHashSet` or another insertion-ordered set
- **THEN** the default migration does not replace insertion order with natural order

#### Scenario: Naturally ordered sorted set
- **WHEN** an annotated method streams a naturally ordered `SortedSet`
- **THEN** the default migration does not append redundant natural sorting

#### Scenario: Custom-comparator sorted set
- **WHEN** an annotated method streams a `SortedSet` whose comparator differs from natural order
- **THEN** the default migration preserves the set comparator's encounter order

#### Scenario: Current SortedSet migration
- **WHEN** the current composite changes a JPA `@OneToMany` field declaration from `SortedSet` to `TreeSet` while leaving related methods unchanged
- **THEN** the migration treats ordering as preserved and adds no compensating stream sort

### Requirement: Preserve stream-operation ordering semantics
The default migration SHALL NOT add natural sorting where stream operations establish, combine, or explicitly discard ordering semantics.

#### Scenario: Flattened pipeline
- **WHEN** an annotated method returns a pipeline containing `flatMap`
- **THEN** the default migration does not append natural sorting based solely on the final `Stream` type

#### Scenario: Visible comparator sorting
- **WHEN** an annotated method returns a pipeline containing `sorted(comparator)`
- **THEN** the default migration leaves the comparator-defined ordering unchanged

#### Scenario: Explicitly unordered pipeline
- **WHEN** an annotated method returns a pipeline containing `.unordered()`
- **THEN** the default migration does not append `.sorted()` after it

### Requirement: Standalone recipe remains an explicit deprecated opt-in
The standalone `AddSortedMethodToStreamMethods` recipe SHALL remain available during the deprecation period, SHALL be marked deprecated, and SHALL NOT be registered by a bundled default migration composite.

#### Scenario: Default composite is activated
- **WHEN** a caller activates the default JDO-to-JPA migration
- **THEN** `AddSortedMethodToStreamMethods` is not activated

#### Scenario: Standalone recipe is explicitly activated
- **WHEN** a caller explicitly activates `AddSortedMethodToStreamMethods`
- **THEN** its documented direct behavior remains available during the deprecation period

#### Scenario: Recipe metadata is inspected
- **WHEN** a caller inspects the standalone recipe description or deprecation metadata
- **THEN** it is warned that the recipe introduces natural ordering without proving source-order loss

### Requirement: Composite ordering behavior remains stable on repeated runs
Applying the default JDO-to-JPA composite repeatedly SHALL NOT introduce stream sorting on a later run.

#### Scenario: Default composite runs twice
- **WHEN** source is processed by the corrected default composite more than once
- **THEN** no run introduces a synthetic stream sorting operation
