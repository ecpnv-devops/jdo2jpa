## ADDED Requirements

### Requirement: Explicit datastore identity is matched structurally

The datastore superclass-insertion rule SHALL recognise an explicitly declared `javax.jdo.annotations.IdentityType.DATASTORE` value by annotation attribute and enum identity rather than by printed argument text.
Whitespace, comments, and supported enum-reference qualification SHALL not change eligibility.

#### Scenario: Spaced and compact attribute assignments

- **WHEN** otherwise identical eligible classes declare `identityType = IdentityType.DATASTORE` and `identityType=IdentityType.DATASTORE`
- **THEN** both receive the same configured superclass

#### Scenario: Comments and line breaks

- **WHEN** comments or line breaks occur around the identityType assignment
- **THEN** the explicit datastore identity is recognised with the same result as an ordinary assignment

#### Scenario: Qualified and statically imported constants

- **WHEN** the same datastore constant is expressed through an imported enum type, a fully qualified enum reference, or a static import
- **THEN** the structural condition recognises the same enum identity in each supported source form

#### Scenario: Different or omitted identity attribute

- **WHEN** identityType is explicitly APPLICATION or is omitted
- **THEN** the new explicit-datastore condition does not match
- **AND** it does not infer a new default identity policy

### Requirement: Superclass insertion preserves usable hierarchy metadata

For an eligible class without an existing superclass, insertion SHALL produce the configured extends clause with superclass metadata resolved from available application sources or attributed dependency types.
The extends expression and the class's attributed hierarchy SHALL agree sufficiently for subsequent recipes to determine actual superclass properties.
The implementation SHALL not fabricate abstractness based on a class name.

#### Scenario: Application superclass available outside template-global resources

- **WHEN** the configured abstract entity superclass is available in the application's source or dependency metadata but not among the JavaTemplate factory's fixed resource jars
- **THEN** the migration resolves and preserves the superclass identity and abstract modifier for downstream recipes
- **AND** successful output passes normal type-attribution validation

#### Scenario: Existing superclass

- **WHEN** a datastore entity already has a superclass
- **THEN** the insertion rule preserves that superclass rather than replacing it or adding another extends clause

#### Scenario: Non-abstract configured superclass

- **WHEN** the configured superclass resolves to a concrete class
- **THEN** its metadata remains concrete and is not labelled abstract to bypass later recipe guards

#### Scenario: Superclass is unavailable

- **WHEN** no available source or attributed dependency information can resolve the configured superclass
- **THEN** the recipe invokes the execution context error handler with `UnresolvedEntityHierarchyException` containing the affected class FQN, unresolved superclass FQN/reference, recipe identity, and source/classpath remediation advice
- **AND** if the handler returns, the recipe leaves the affected declaration and imports unchanged from its own input without inserting a superclass or additionally throwing
- **AND** if the handler throws, normal runner error propagation applies
- **AND** earlier recipe changes are not automatically rolled back, but the acceptance/consumer runner exits nonzero and rejects the entire run's partial output

### Requirement: Existing regex-based callers remain compatible

The structural condition SHALL be opt-in through the optional string options `annotationAttributeName` and `annotationAttributeValue`.
The datastore rule SHALL configure them as `identityType` and `javax.jdo.annotations.IdentityType.DATASTORE` respectively.
Existing callers configured only with `annotationCondition` SHALL retain the existing regex condition contract.
The public constructor `(String annotationPattern, String annotationCondition, String extendsFullClassName)` SHALL retain its signature, parameter order, and behaviour, delegating with null structured options.
The extended JSON creator SHALL take those existing three parameters followed by `annotationAttributeName` and `annotationAttributeValue`.
Existing YAML property names SHALL remain unchanged, and recipe descriptors SHALL expose the two new properties as optional.
Ambiguous or incomplete condition configurations SHALL be rejected explicitly.

#### Scenario: Legacy regex condition

- **WHEN** an existing recipe configuration supplies only annotationCondition
- **THEN** it continues to use the existing regex matching behaviour

#### Scenario: Existing direct Java construction

- **WHEN** a caller constructs the recipe using the existing three-argument constructor
- **THEN** the caller compiles unchanged, the constructor signature remains available, and legacy matching behaviour is preserved

#### Scenario: Legacy YAML descriptor compatibility

- **WHEN** an existing YAML recipe using only the original properties is loaded through the recipe environment
- **THEN** it deserializes and validates with the same behaviour
- **AND** the descriptor retains the original properties and exposes both added options as optional

#### Scenario: Structured YAML configuration

- **WHEN** YAML supplies both structured properties and omits annotationCondition
- **THEN** it deserializes, validates, and performs structural enum matching using the specified attribute and fully qualified constant

#### Scenario: Both condition modes supplied

- **WHEN** a configuration supplies both a regex condition and a structured attribute/enum condition
- **THEN** recipe validation reports the conflicting condition modes

#### Scenario: Incomplete structural condition

- **WHEN** a structured condition omits either the attribute name or the enum constant identity
- **THEN** recipe validation reports the incomplete configuration

### Requirement: Attribution mechanism is proven before feature implementation

The implementation SHALL begin with a gated spike proving consistent extends-expression and class-level supertype metadata for both source-defined and dependency-defined superclasses using the actual OpenRewrite version.
The next visitor SHALL be able to inspect the superclass FQN, modifiers, and listener annotations with normal type validation enabled.
The spike SHALL also demonstrate a separately invoked child module using a compiled migrated parent and the recording-handler/nonzero-runner diagnostic contract.
Remaining feature implementation SHALL not proceed until exact APIs, commands, and passing outcomes are recorded in the design.
A failed spike SHALL trigger design review rather than disabled validation or fabricated type flags.

#### Scenario: Source-defined superclass spike

- **WHEN** the spike inserts a superclass available as application source
- **THEN** both tree and class-level type metadata agree and the next visitor can inspect its actual modifiers and annotations
- **AND** normal type-attribution validation passes

#### Scenario: Dependency-defined superclass spike

- **WHEN** the spike inserts a superclass available only through an application dependency and not the template-global resource jars
- **THEN** the same metadata and downstream-inspection assertions pass

#### Scenario: Spike cannot demonstrate the mechanism

- **WHEN** attribution, module-boundary handling, or error-to-runner propagation cannot meet the spike criteria
- **THEN** subsequent implementation tasks remain blocked pending a reviewed design revision

### Requirement: Superclass insertion is stable across repeated migration

The superclass-insertion rule SHALL be idempotent and SHALL compose with the configured Causeway migration without requiring an external rewrite invocation to repair its output.

#### Scenario: Single execution followed by repeat run

- **WHEN** an eligible datastore entity is migrated through the configured superclass and Causeway recipes and the result is migrated again
- **THEN** the first execution supplies a usable superclass and the required listener integration
- **AND** the second execution makes no additional changes
