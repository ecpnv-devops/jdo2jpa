## Plan: Bidirectional orphanRemoval inference

Add bidirectional-awareness to `@Persistent(mappedBy=...)` conversion so `@OneToMany` includes `orphanRemoval = true` when the inverse reference is mandatory (`@Persistent(allowsNull = "false")`).
Implement this by extending scan-time metadata in the recipe, then consulting it during `@OneToMany` template construction.
Cover both raw JDO input and already-migrated inverse-side annotations to stay robust with current recipe ordering.

### Steps
1. Extend scanner metadata in [`src/main/java/com/ecpnv/openrewrite/jdo2jpa/ReplacePersistentWithOneToManyAnnotation.java`](/Users/danhaywood/repos/github/ecpnv-devops/jdo2jpa/src/main/java/com/ecpnv/openrewrite/jdo2jpa/ReplacePersistentWithOneToManyAnnotation.java) `getScanner`/`Accumulator` to index inverse-field nullability by `ownerFqn#fieldName`.
2. Normalize mapped-by extraction via `RewriteUtils.findArgumentValueAsString(...)` to avoid quoted-name mismatches in `mappedBy` comparisons.
3. In `ReplacePersistentWithOneToManyAnnotationVisitor.visitVariableDeclarations`, when `mappedBy` exists, resolve collection element type + mapped field key and append `orphanRemoval = true` when inverse side is mandatory.
4. Implement mandatory detection from both JDO and JPA forms in scanner: `@Column(allowsNull = "false")` and `@ManyToOne(optional = false)` on inverse fields.
5. Add focused scenarios in [`src/test/java/com/ecpnv/openrewrite/jdo2jpa/ReplacePersistentWithOneToManyAnnotationTest.java`](/Users/danhaywood/repos/github/ecpnv-devops/jdo2jpa/src/test/java/com/ecpnv/openrewrite/jdo2jpa/ReplacePersistentWithOneToManyAnnotationTest.java) for mappedBy+mandatory (adds orphanRemoval) and mappedBy+optional (does not add orphanRemoval).

### Further Considerations
1. If inverse field is unresolved (missing type attribution or cross-source gap), then set `orphanRemoval=false`.
2. The logic should live only in `ReplacePersistentWithOneToManyAnnotation`.
