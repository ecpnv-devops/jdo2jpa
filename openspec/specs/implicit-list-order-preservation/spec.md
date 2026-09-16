## Requirements

### Requirement: Default `@OrderColumn` for JDO `List` relationships without explicit `@Order`
When `ReplacePersistentWithOneToManyAnnotation` converts a `java.util.List`-typed field mapped via `mappedBy` (a JDO `@Persistent`/`@Element` one-to-many relationship) and the field has no `javax.jdo.annotations.Order` annotation, the recipe SHALL add `@javax.persistence.OrderColumn(name = "<fieldName>_INTEGER_IDX")` alongside the generated `@OneToMany` annotation, so the field's persisted list order is preserved.

#### Scenario: List field with no @Order annotation gets a default @OrderColumn
- **WHEN** a field is declared as `List<X> items` with `@Persistent(mappedBy = "owner")` and no `@Order` annotation
- **THEN** the recipe outputs `@OneToMany(mappedBy = "owner", ...)` together with `@OrderColumn(name = "items_INTEGER_IDX")` on the field, and adds the `javax.persistence.OrderColumn` import

#### Scenario: List field with explicit @Order is unaffected
- **WHEN** a field is declared as `List<X> items` with `@Persistent(mappedBy = "owner")` and an explicit `@Order(column = "custom_idx")` annotation
- **THEN** the recipe continues to produce `@OrderColumn(name = "custom_idx")` from the explicit `@Order` value, and does not additionally generate a default `<fieldName>_INTEGER_IDX` column

#### Scenario: Set-typed relationship is unaffected
- **WHEN** a field is declared as `Set<X> items` or `SortedSet<X> items` with `@Persistent(mappedBy = "owner")` and no `@Order` annotation
- **THEN** the recipe does not add any `@OrderColumn` annotation to the field
