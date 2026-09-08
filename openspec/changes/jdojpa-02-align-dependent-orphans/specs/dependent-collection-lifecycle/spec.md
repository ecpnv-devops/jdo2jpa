## ADDED Requirements

### Requirement: Collection dependency drives both cascade and orphan removal
The migration MUST emit `CascadeType.REMOVE` and `orphanRemoval = true` together, and only together, for a collection whose JDO metadata declares its elements dependent.

#### Scenario: Dependent collection with mandatory inverse
- **WHEN** a collection is declared `@Persistent(mappedBy = "parent", dependentElement = "true")` and the inverse field is non-nullable
- **THEN** the generated `@OneToMany` contains both `CascadeType.REMOVE` and `orphanRemoval = true`

#### Scenario: Dependent collection with optional inverse
- **WHEN** a collection is declared `@Persistent(mappedBy = "parent", dependentElement = "true")` and the inverse field is nullable
- **THEN** the generated `@OneToMany` contains both `CascadeType.REMOVE` and `orphanRemoval = true`

#### Scenario: Dependency declared on the element annotation
- **WHEN** a collection is declared `@Element(dependent = "true")` with no `dependentElement` attribute
- **THEN** the generated `@OneToMany` contains both `CascadeType.REMOVE` and `orphanRemoval = true`

#### Scenario: Both attribute spellings present
- **WHEN** a collection carries both `dependent` and `dependentElement`
- **THEN** `dependent` takes precedence, matching single-reference translation

### Requirement: Inverse-field optionality does not imply ownership
The migration SHALL NOT emit `orphanRemoval` for a collection whose JDO metadata does not declare its elements dependent, regardless of whether the inverse field is mandatory.

#### Scenario: Non-dependent collection with mandatory inverse
- **WHEN** a collection is declared `@Persistent(mappedBy = "parent")` with no dependency attribute and the inverse field is non-nullable
- **THEN** the generated `@OneToMany` contains neither `CascadeType.REMOVE` nor `orphanRemoval`

#### Scenario: Non-dependent collection with optional inverse
- **WHEN** a collection is declared `@Persistent(mappedBy = "parent")` with no dependency attribute and the inverse field is nullable
- **THEN** the generated `@OneToMany` contains neither `CascadeType.REMOVE` nor `orphanRemoval`

#### Scenario: Dependency explicitly disabled
- **WHEN** a collection is declared `dependentElement = "false"` or `dependent = "false"`
- **THEN** the generated `@OneToMany` contains neither `CascadeType.REMOVE` nor `orphanRemoval`, whatever the inverse field's optionality

### Requirement: Default cascade members are unaffected
Changing orphan-removal derivation SHALL NOT alter which non-`REMOVE` cascade members the migration emits.

#### Scenario: Dependent collection with a configured default cascade
- **WHEN** a dependent collection is migrated with a configured `defaultCascade`
- **THEN** the generated cascade set is `CascadeType.REMOVE` followed by the configured members, unchanged from prior behaviour

#### Scenario: Non-dependent collection with a configured default cascade
- **WHEN** a non-dependent collection is migrated with a configured `defaultCascade`
- **THEN** the generated cascade set contains exactly the configured members

### Requirement: Singular dependent references express only what JPA supports
For a dependent single-valued reference the migration MUST emit `CascadeType.REMOVE` and MUST NOT attempt to synthesize disassociation-time deletion.

#### Scenario: Dependent single reference
- **WHEN** a reference is declared `@Persistent(dependent = "true")` or `@Persistent(dependentElement = "true")`
- **THEN** the generated `@ManyToOne` contains `CascadeType.REMOVE` and no orphan-removal attribute

#### Scenario: Replacement semantics are not generated
- **WHEN** a dependent single reference is migrated
- **THEN** the migration generates no mutator logic, lifecycle callback, or listener to delete a displaced referent

### Requirement: Derivation is stable on repeated runs
Applying the migration repeatedly SHALL produce the same cascade and orphan-removal attributes.

#### Scenario: Migration runs twice
- **WHEN** source already carrying generated `@OneToMany` mappings is processed again
- **THEN** no cascade member or orphan-removal attribute is added, removed, or duplicated
