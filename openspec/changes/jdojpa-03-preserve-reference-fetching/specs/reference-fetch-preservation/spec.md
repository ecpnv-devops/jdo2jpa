## ADDED Requirements

### Requirement: Generated to-one mappings declare fetch explicitly
Every JPA `@ManyToOne` and `@OneToOne` mapping generated from a JDO relationship SHALL declare either `FetchType.LAZY` or `FetchType.EAGER`.
No composed recipe stage SHALL erase the generated fetch attribute and thereby delegate the decision to the JPA default.

#### Scenario: Ordinary reference is generated
- **WHEN** the recipe converts an ordinary single-valued entity reference
- **THEN** the generated `@ManyToOne` explicitly declares its resolved fetch strategy

#### Scenario: Owning one-to-one is inferred
- **WHEN** bidirectional metadata causes a single-valued reference to become the owning `@OneToOne`
- **THEN** the generated `@OneToOne` explicitly declares its resolved fetch strategy

#### Scenario: Optional recipe runs after conversion
- **WHEN** `v2x.optional` processes a generated to-one mapping with explicit fetch metadata
- **THEN** it preserves the fetch attribute and value unchanged

### Requirement: JDO default-fetch-group intent determines ordinary reference fetch
The recipe SHALL map `defaultFetchGroup = "true"` to `FetchType.EAGER` and SHALL map explicit `false`, an omitted attribute, or an ordinary reference without `@Persistent` metadata to `FetchType.LAZY`.
Named fetch-group membership SHALL NOT be treated as default-fetch-group eager intent.

#### Scenario: Reference explicitly joins the default fetch group
- **WHEN** a JDO single-valued relationship declares `defaultFetchGroup = "true"`
- **THEN** the generated to-one mapping declares `fetch = FetchType.EAGER`

#### Scenario: Reference explicitly leaves the default fetch group
- **WHEN** a JDO single-valued relationship declares `defaultFetchGroup = "false"`
- **THEN** the generated to-one mapping declares `fetch = FetchType.LAZY`

#### Scenario: Persistent annotation omits fetch-group metadata
- **WHEN** a JDO single-valued relationship has `@Persistent` without `defaultFetchGroup`
- **THEN** the generated to-one mapping declares `fetch = FetchType.LAZY`

#### Scenario: Ordinary reference has no Persistent annotation
- **WHEN** an entity has an ordinary single-valued entity reference without `@Persistent`
- **THEN** the generated `@ManyToOne` declares `fetch = FetchType.LAZY`

#### Scenario: Reference belongs only to a named fetch group
- **WHEN** a relationship is mentioned by named fetch-plan metadata but does not declare `defaultFetchGroup = "true"`
- **THEN** the global generated mapping is not made eager on the basis of named membership

### Requirement: Inferred owning one-to-one provider exception is explicit
An inferred owning `@OneToOne` without `@Persistent` metadata SHALL declare `FetchType.EAGER` to preserve the documented EclipseLink deletion workaround.
Explicit JDO default-fetch-group metadata SHALL take precedence over that fallback.

#### Scenario: Bare reference becomes owning one-to-one
- **WHEN** a relationship without `@Persistent` is identified as the owning side of a bidirectional one-to-one
- **THEN** the generated `@OneToOne` declares `fetch = FetchType.EAGER`

#### Scenario: Owning one-to-one explicitly requests lazy fetch
- **WHEN** an owning one-to-one source relationship declares `defaultFetchGroup = "false"`
- **THEN** the generated `@OneToOne` declares `fetch = FetchType.LAZY`

#### Scenario: Owning one-to-one explicitly requests eager fetch
- **WHEN** an owning one-to-one source relationship declares `defaultFetchGroup = "true"`
- **THEN** the generated `@OneToOne` declares `fetch = FetchType.EAGER`

#### Scenario: Owning one-to-one has Persistent without fetch-group metadata
- **WHEN** an owning one-to-one source relationship has `@Persistent` but omits `defaultFetchGroup`
- **THEN** the generated `@OneToOne` declares `fetch = FetchType.LAZY`

### Requirement: Fetch translation composes with relationship metadata
Resolving fetch strategy SHALL preserve the relationship kind, join-column metadata, optionality, cascade values, orphan-removal values, and unrelated leading annotations produced by other relationship rules.

#### Scenario: Non-null reference has column metadata
- **WHEN** a JDO reference combines fetch metadata with a non-null column and column name
- **THEN** the generated relationship contains the resolved fetch value together with unchanged `optional` and `@JoinColumn` semantics

#### Scenario: Dependent reference has fetch metadata
- **WHEN** a dependent single reference is converted
- **THEN** the generated cascade retains `CascadeType.REMOVE` and the resolved fetch strategy is also present

#### Scenario: Non-dependent reference uses default cascade
- **WHEN** a non-dependent reference is converted with configured default cascades
- **THEN** the generated cascade values and resolved fetch strategy are both present

#### Scenario: Inverse relationship is converted separately
- **WHEN** `ReplacePersistentWithOneToManyAnnotation` converts a non-collection inverse relationship to `@OneToOne(mappedBy = ...)`
- **THEN** its explicit fetch strategy remains consistent with the JDO default-fetch-group truth table

### Requirement: Fetch metadata remains stable under recipe composition and reruns
The base persistent composite, the optional composite, and the supported consumer composition SHALL produce the same fetch value for the same source relationship.
Rerunning applicable recipes over generated JPA SHALL NOT remove, duplicate, or change explicit fetch metadata.

#### Scenario: Persistent composite cleans up JDO annotations
- **WHEN** `v2x.Persistent` converts a relationship and removes obsolete JDO attributes
- **THEN** the generated explicit fetch value remains on the JPA annotation

#### Scenario: Full consumer sequence includes optional migrations
- **WHEN** the supported migration sequence runs relationship conversion followed by optional migrations
- **THEN** the final generated to-one annotation retains the strategy chosen from source JDO intent

#### Scenario: Generated mapping is processed again
- **WHEN** an applicable recipe is rerun over a to-one mapping already carrying explicit fetch metadata
- **THEN** the mapping still has exactly one unchanged fetch attribute

#### Scenario: FetchType import is required
- **WHEN** conversion introduces an explicit fetch attribute into a compilation unit without a `FetchType` import
- **THEN** the recipe adds the required import and leaves no fetch-related unused import after composition
