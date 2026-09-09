## ADDED Requirements

### Requirement: Constraints are emitted where JPA honours them
The migration MUST emit `indexes` and `uniqueConstraints` on the `@Table` of the entity that owns the physical table for the declaring class's inheritance strategy.

#### Scenario: Subclass of an explicit single-table root
- **WHEN** a class declaring an index becomes a subclass of a root annotated `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)`
- **THEN** the index is emitted on the root's `@Table` and not on the subclass's

#### Scenario: Subclass of an implicit single-table root
- **WHEN** a class declaring an index becomes a subclass of a root that has no `@Inheritance` annotation and relies on JPA's default
- **THEN** the index is emitted on the root's `@Table` and not on the subclass's

#### Scenario: Joined hierarchy
- **WHEN** a class declaring an index becomes a subclass in a `JOINED` hierarchy
- **THEN** the index remains on that subclass's own `@Table`

#### Scenario: Table-per-class hierarchy
- **WHEN** a class declaring an index becomes a subclass in a `TABLE_PER_CLASS` hierarchy
- **THEN** the index remains on that subclass's own `@Table`

#### Scenario: Entity outside any hierarchy
- **WHEN** a class declaring an index has no persistent supertype and no subtypes
- **THEN** the index remains on its own `@Table`

### Requirement: The relocation target is the highest entity, never a mapped superclass
Root resolution MUST traverse `@MappedSuperclass` types without selecting one, and MUST select the highest `@Entity` on the supertype chain.

#### Scenario: Mapped superclass above the root
- **WHEN** the chain is `@MappedSuperclass Base` → `@Entity Root` → `@Entity Child`, and `Child` declares a constraint
- **THEN** the constraint is emitted on `Root` and `Base` is left unannotated

#### Scenario: Several mapped superclasses above the root
- **WHEN** two or more `@MappedSuperclass` types sit above the highest entity
- **THEN** none of them is selected and the constraint is emitted on that entity

#### Scenario: Intervening mapped superclass
- **WHEN** a `@MappedSuperclass` sits between the declaring subclass and the root entity
- **THEN** it is traversed and the constraint reaches the root entity

#### Scenario: Multi-level hierarchy
- **WHEN** a constraint is declared three entity levels below a single-table root
- **THEN** it is emitted on that root, not on any intermediate class

#### Scenario: Strategy declared above the immediate parent
- **WHEN** the inheritance strategy is declared on the root but not on the immediate parent of the declaring class
- **THEN** the strategy of the root governs placement

### Requirement: Root detection does not depend on the discriminator column
Root detection MUST determine strategy from `@Inheritance` and subtype presence, and MUST NOT treat `@DiscriminatorColumn` as evidence that a class is a root.

#### Scenario: Discriminator column present on a subclass
- **WHEN** a subclass carries `@DiscriminatorColumn` at the point relocation runs
- **THEN** it is not treated as a root and its constraints are still relocated to the true root

#### Scenario: Implicit root without a discriminator column
- **WHEN** the highest entity has no `@Inheritance` and no `@DiscriminatorColumn`, but has subtypes
- **THEN** it is treated as an implicit `SINGLE_TABLE` root

### Requirement: Relocated declarations merge with the root's own
Relocation MUST preserve each declaration's name and definition, and MUST combine relocated declarations with any the root already declares.

#### Scenario: Root already declares constraints
- **WHEN** constraints are relocated onto a root whose `@Table` already declares its own
- **THEN** the resulting `@Table` contains both sets, and the root's own are unchanged

#### Scenario: Multiple subclasses contribute
- **WHEN** several subclasses of the same root each declare constraints
- **THEN** all are emitted on the root

#### Scenario: Names are preserved
- **WHEN** a declaration carrying an explicit name is relocated
- **THEN** the emitted declaration carries the same name

#### Scenario: Both constraint kinds
- **WHEN** a subclass declares both an index and a unique constraint
- **THEN** each is relocated into the corresponding attribute of the root's `@Table`

### Requirement: Constraint identity is by explicit name, with unnamed constraints compared by definition
Conflict detection MUST apply only to declarations sharing a non-blank explicit name. Declarations whose name is omitted or blank MUST be compared by normalized definition instead.

Normalized definition is per annotation type and applies defaults before comparison, so an omitted attribute and an explicitly-default one compare equal. For `@Index` it is `columnList` with separator whitespace normalized, plus `unique` defaulting to `false`. For `@UniqueConstraint` it is `columnNames` as an ordered list. An `@Index` is never compared with a `@UniqueConstraint`.

#### Scenario: Equivalent named duplicates
- **WHEN** two classes in one hierarchy declare constraints with the same name and the same normalized definition
- **THEN** one declaration is emitted

#### Scenario: Conflicting named definitions
- **WHEN** two classes in one hierarchy declare constraints with the same non-blank name but different normalized definitions
- **THEN** generation fails with a diagnostic naming both declaring classes and both definitions

#### Scenario: Two unnamed constraints with different definitions
- **WHEN** two classes each declare an unnamed constraint over different columns
- **THEN** both are emitted and generation does not fail

#### Scenario: Two unnamed constraints with the same definition
- **WHEN** two classes each declare an unnamed constraint with the same normalized definition
- **THEN** one declaration is emitted

#### Scenario: Omitted versus explicitly-default attribute
- **WHEN** one declaration omits `unique` and another sets `unique = false`, with the same `columnList`
- **THEN** they are treated as equivalent

#### Scenario: Same columns under different names
- **WHEN** two classes declare constraints over the same columns under different explicit names
- **THEN** both are emitted, because the names are part of the schema contract

### Requirement: Subclass annotations are stripped only of relocated attributes
The migration MUST leave non-relocated `@Table` attributes in place, and MUST remove the annotation only when relocation empties it.

#### Scenario: Table carrying only constraints
- **WHEN** all attributes of a subclass `@Table` are relocated
- **THEN** the annotation is removed from the subclass

#### Scenario: Table also carrying schema or name
- **WHEN** a subclass `@Table` declares `schema` or `name` alongside relocated constraints
- **THEN** those attributes remain and only the constraint attributes are removed

### Requirement: Emission is deterministic and stable on repeated runs
Relocated declarations MUST be emitted in a defined order, and re-running the migration MUST NOT change the result.

#### Scenario: Ordering
- **WHEN** constraints from several subclasses are relocated onto one root
- **THEN** they are ordered by declaring class name, then by constraint name, with unnamed declarations last in declaration order

#### Scenario: Migration runs twice
- **WHEN** source that already carries relocated constraints is processed again
- **THEN** no declaration is duplicated, reordered, or moved again

#### Scenario: Placement within the composite
- **WHEN** the full `v2x` composite is run in one cycle over a single-table hierarchy
- **THEN** relocation observes the JPA `@Inheritance` produced by the inheritance phase and produces the same result as running it in isolation
