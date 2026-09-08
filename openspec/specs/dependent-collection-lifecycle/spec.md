# dependent-collection-lifecycle Specification

## Purpose
Define how JDO dependency metadata is translated into JPA cascade and orphan-removal semantics for generated relationship mappings, without inferring ownership from unrelated mapping facts.

## Requirements

### Requirement: Dependency drives both cascade and orphan removal
The migration MUST emit `CascadeType.REMOVE` and `orphanRemoval = true` together for a relationship whose JDO metadata declares it dependent, and MUST NOT emit a dependency-derived `CascadeType.REMOVE` without also emitting `orphanRemoval = true`.

This invariant governs the `CascadeType.REMOVE` derived from dependency metadata. It does not constrain cascade members supplied through the `defaultCascade` parameter.

#### Scenario: Dependent collection with mandatory inverse
- **WHEN** a collection is declared `@Persistent(mappedBy = "parent", dependentElement = "true")` and the inverse field is non-nullable
- **THEN** the generated `@OneToMany` contains both `CascadeType.REMOVE` and `orphanRemoval = true`

#### Scenario: Dependent collection with optional inverse
- **WHEN** a collection is declared `@Persistent(mappedBy = "parent", dependentElement = "true")` and the inverse field is nullable
- **THEN** the generated `@OneToMany` contains both `CascadeType.REMOVE` and `orphanRemoval = true`

#### Scenario: Caller configures REMOVE in the default cascade
- **WHEN** a non-dependent relationship is migrated with a `defaultCascade` that itself contains `CascadeType.REMOVE`
- **THEN** the generated mapping contains `CascadeType.REMOVE` and does not contain `orphanRemoval`

### Requirement: Dependency is resolved by cardinality across both annotations
The migration MUST resolve dependency from the attributes JDO defines for the relationship's cardinality, consulting both `@Persistent` and `@Element` rather than a single pre-resolved annotation.

For a collection the order is `@Element(dependent)` then `@Persistent(dependentElement)`. For a single-valued field the order is `@Persistent(dependent)` then `@Persistent(dependentElement)`. Where both sources are present and disagree, the first in the applicable order wins.

#### Scenario: Dependency declared alongside a persistent mapping
- **WHEN** a collection is declared `@Persistent(mappedBy = "parent")` and also `@Element(dependent = "true")`
- **THEN** the generated `@OneToMany` contains both `CascadeType.REMOVE` and `orphanRemoval = true`

#### Scenario: Dependency declared only on the element annotation
- **WHEN** a collection is declared `@Element(dependent = "true", table = "PARENT_CHILD")` with no `@Persistent` annotation
- **THEN** the generated `@OneToMany` contains both `CascadeType.REMOVE` and `orphanRemoval = true`

#### Scenario: Element and persistent dependency disagree
- **WHEN** a collection is declared `@Persistent(mappedBy = "parent", dependentElement = "true")` and `@Element(dependent = "false")`
- **THEN** the element declaration wins and the generated `@OneToMany` contains neither `CascadeType.REMOVE` nor `orphanRemoval`

#### Scenario: Singular dependency attribute on a collection
- **WHEN** a collection is declared `@Persistent(mappedBy = "parent", dependent = "true")` with no element-level dependency
- **THEN** the attribute is ignored and the generated `@OneToMany` contains neither `CascadeType.REMOVE` nor `orphanRemoval`

### Requirement: Generated one-to-one mappings express dependency
The migration MUST apply the same dependency-derived cascade and orphan-removal pair to the `@OneToOne` mappings it generates for single-valued fields.

#### Scenario: Dependent single-valued field
- **WHEN** a single-valued field is declared `@Persistent(mappedBy = "owner", dependent = "true")`
- **THEN** the generated `@OneToOne` contains both `CascadeType.REMOVE` and `orphanRemoval = true`

#### Scenario: Non-dependent single-valued field
- **WHEN** a single-valued field is declared `@Persistent(mappedBy = "owner")` with no dependency attribute
- **THEN** the generated `@OneToOne` contains neither `CascadeType.REMOVE` nor `orphanRemoval`

#### Scenario: Collection-element attribute on a single-valued field
- **WHEN** a single-valued field is declared `@Persistent(mappedBy = "owner", dependentElement = "true")`
- **THEN** the attribute is honoured as a fallback and the generated `@OneToOne` contains both `CascadeType.REMOVE` and `orphanRemoval = true`

### Requirement: Inverse-field optionality does not imply ownership
The migration SHALL NOT emit `orphanRemoval` for a relationship whose JDO metadata does not declare it dependent, regardless of whether the inverse field is mandatory.

#### Scenario: Non-dependent collection with mandatory inverse
- **WHEN** a collection is declared `@Persistent(mappedBy = "parent")` with no dependency attribute and the inverse field is non-nullable
- **THEN** the generated `@OneToMany` contains neither `CascadeType.REMOVE` nor `orphanRemoval`

#### Scenario: Non-dependent collection with optional inverse
- **WHEN** a collection is declared `@Persistent(mappedBy = "parent")` with no dependency attribute and the inverse field is nullable
- **THEN** the generated `@OneToMany` contains neither `CascadeType.REMOVE` nor `orphanRemoval`

#### Scenario: Dependency explicitly disabled
- **WHEN** a relationship declares its applicable dependency attribute as `"false"`
- **THEN** the generated mapping contains neither `CascadeType.REMOVE` nor `orphanRemoval`, whatever the inverse field's optionality

### Requirement: Conversion triggers are unchanged
Dependency metadata SHALL NOT by itself cause a field to be converted, and the choice between `@OneToMany` and `@OneToOne` SHALL continue to follow the field's cardinality.

#### Scenario: Dependency without relationship context
- **WHEN** a collection is declared `@Element(dependent = "true")` with no `mappedBy`, no `table`, and no `@Join`
- **THEN** no relationship annotation is generated for that field

#### Scenario: Cardinality selects the mapping shape
- **WHEN** a dependent field with relationship context is migrated
- **THEN** a collection-typed field yields `@OneToMany` and a single-valued field yields `@OneToOne`

### Requirement: Default cascade members are otherwise unaffected
Changing orphan-removal derivation SHALL NOT alter which non-`REMOVE` cascade members the migration emits.

#### Scenario: Dependent relationship with a configured default cascade
- **WHEN** a dependent relationship is migrated with a configured `defaultCascade`
- **THEN** the generated cascade set is `CascadeType.REMOVE` followed by the configured members, unchanged from prior behaviour

#### Scenario: Non-dependent relationship with a configured default cascade
- **WHEN** a non-dependent relationship is migrated with a configured `defaultCascade`
- **THEN** the generated cascade set contains exactly the configured members

### Requirement: Many-to-one mappings express only what JPA supports
For a dependent single-valued reference translated to `@ManyToOne`, the migration MUST emit `CascadeType.REMOVE` and MUST NOT attempt to synthesize disassociation-time deletion.

#### Scenario: Dependent many-to-one reference
- **WHEN** a reference is translated to `@ManyToOne` and its JDO metadata declares it dependent
- **THEN** the generated mapping contains `CascadeType.REMOVE` and no orphan-removal attribute

#### Scenario: Replacement semantics are not generated
- **WHEN** a dependent reference is translated to `@ManyToOne`
- **THEN** the migration generates no mutator logic, lifecycle callback, or listener to delete a displaced referent

### Requirement: Derivation is stable on repeated runs
Applying the migration repeatedly SHALL produce the same cascade and orphan-removal attributes.

#### Scenario: Migration runs twice
- **WHEN** source already carrying generated relationship mappings is processed again
- **THEN** no cascade member or orphan-removal attribute is added, removed, or duplicated
