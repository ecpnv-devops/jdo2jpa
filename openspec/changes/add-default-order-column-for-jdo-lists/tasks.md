## 1. Constants

- [ ] 1.1 Add `Jdo.ORDER_ANNOTATION_NAME` / `Jdo.ORDER_ANNOTATION_FULL` (`javax.jdo.annotations.Order`) and `Jdo.ORDER_ARGUMENT_COLUMN` (`column`) to `Constants.java`
- [ ] 1.2 Add `Jpa.ORDER_COLUMN_ANNOTATION_NAME` / `Jpa.ORDER_COLUMN_ANNOTATION_FULL` (`javax.persistence.OrderColumn`) and `Jpa.ORDER_COLUMN_ARGUMENT_NAME` (`name`) to `Constants.java`, if not already effectively present via the existing `@Order`→`@OrderColumn` code path

## 2. Recipe logic

- [ ] 2.1 In `ReplacePersistentWithOneToManyAnnotationVisitor.visitVariableDeclarations`, after the `@OneToMany` template is applied, detect: field type is `java.util.List` (via `hasCollection`/type inspection consistent with existing checks), relationship is `mappedBy`-driven, and no `@javax.jdo.annotations.Order` annotation is present on the field
- [ ] 2.2 When that condition holds, add `@OrderColumn(name = "<fieldName>_INTEGER_IDX")` using the same `AddAnnotationConditionally` + `JavaTemplate` approach used for `@JoinTable`/`@JoinColumn` in this file, and `maybeAddImport` the JPA `OrderColumn` type
- [ ] 2.3 Confirm the existing explicit-`@Order` → `@OrderColumn` path is untouched and takes precedence (no double `@OrderColumn` when `@Order` is present)
- [ ] 2.4 Confirm `Set`/`SortedSet`/other non-`List` collection fields are unaffected

## 3. Tests

- [ ] 3.1 Add a test case to `ReplacePersistentWithOneToManyAnnotationTest`: `List` field with `@Persistent(mappedBy = ...)` and no `@Order` produces `@OneToMany` + default `@OrderColumn(name = "<field>_INTEGER_IDX")`
- [ ] 3.2 Add/confirm a test case: `List` field with explicit `@Order(column = "...")` still produces only the explicit `@OrderColumn` (no duplicate default column)
- [ ] 3.3 Add a test case: `Set`/`SortedSet` field with `@Persistent(mappedBy = ...)` and no `@Order` produces `@OneToMany` with no `@OrderColumn`
- [ ] 3.4 Run the full recipe test suite (`mvn test` or module equivalent) to confirm no regressions in other `ReplacePersistentWithOneToManyAnnotation` scenarios
