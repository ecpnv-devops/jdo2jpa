/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

/**
 * Isolated tests for {@link ReplacePersistentWithManyToOneAnnotation}.
 * <p>
 * Unlike {@link ReplacePersistentWithManyToOneAnnotationTest}, which runs the full
 * {@code v2x.Persistent} composite (this recipe plus several clean-up recipes), this class runs
 * <em>only</em> this recipe. That makes a failure attributable to this recipe and exercises the
 * most complex paths (bi-directional {@code @OneToOne} detection) which the composite tests do not
 * cover.
 * <p>
 * The cases below cover the behaviours that were previously untested and where bugs were found:
 * <ul>
 * <li>{@code dependent = "true"} on a single reference maps to a REMOVE cascade;
 * <li>a reference field without {@code @Persistent} still gets an explicit {@code FetchType.LAZY}
 * (JDO default), instead of silently becoming JPA's EAGER default;
 * <li>the owning side of a bi-directional one-to-one becomes {@code @OneToOne}, and multiple
 * inverse relationships to the <em>same</em> target type are all detected (no accumulator key
 * collision).
 * </ul>
 * <p>
 * Note: the inverse ({@code mappedBy}) side of a bi-directional relationship is intentionally
 * <em>not</em> handled by this recipe; {@link ReplacePersistentWithOneToManyAnnotation} migrates it
 * to {@code @OneToOne(mappedBy = ...)}. In these isolated runs the inverse fields therefore keep
 * their {@code @Persistent} annotation.
 *
 * @see ReplacePersistentWithManyToOneAnnotation
 */
class ReplacePersistentWithManyToOneAnnotationIsolatedTest extends BaseRewriteTest {

    private static final String DEFAULT_CASCADE =
            "CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipe(new ReplacePersistentWithManyToOneAnnotation(DEFAULT_CASCADE));
    }

    /**
     * A single reference marked {@code @Persistent(dependent = "true")} must translate to a
     * {@code CascadeType.REMOVE} cascade. Previously only {@code dependentElement} (the collection
     * attribute) was checked, so this cascade was silently dropped.
     */
    @Test
    void dependentSingleReferenceGetsRemoveCascade() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;

                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    @Persistent(dependent = "true")
                                    private Person person;
                                }
                                """,
                        """
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.ManyToOne;

                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    @ManyToOne(cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    private Person person;
                                }
                                """
                )
        );
    }

    /**
     * An entity reference without {@code @Persistent} in an entity class must still receive an
     * explicit {@code FetchType.LAZY}. JDO loads single references lazily by default, whereas JPA
     * defaults {@code @ManyToOne} to EAGER; without an explicit fetch the migration would silently
     * change the loading behaviour.
     */
    @Test
    void referenceWithoutPersistentGetsExplicitLazyFetch() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.persistence.Entity;

                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    private Person person;
                                }
                                """,
                        """
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.ManyToOne;

                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
                                    private Person person;
                                }
                                """
                )
        );
    }

    /**
     * The owning side of a bi-directional one-to-one (the field that another entity's
     * {@code mappedBy} points to) must become {@code @OneToOne} rather than {@code @ManyToOne}.
     * Crucially, when two inverse relationships in different entities point at the <em>same</em>
     * target type ({@code Address}), both owning-side fields must be detected. With the previous
     * single-value accumulator the second mapping overwrote the first, so one owning-side field
     * ({@code Address.person}) was wrongly left as {@code @ManyToOne}.
     * <p>
     * Running this recipe in isolation, the inverse fields ({@code Person.address},
     * {@code Company.mainAddress}) keep their {@code @Persistent} annotation; the full composite
     * migrates them via {@link ReplacePersistentWithOneToManyAnnotation}.
     */
    @DocumentExample
    @Test
    void multipleInverseRelationsToSameTargetTypeAreAllDetected() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;

                                @Entity
                                public class Person {
                                    @Persistent(mappedBy = "person")
                                    private Address address;
                                }
                                @Entity
                                public class Company {
                                    @Persistent(mappedBy = "company")
                                    private Address mainAddress;
                                }
                                @Entity
                                public class Address {
                                    @Persistent
                                    private Person person;
                                    @Persistent
                                    private Company company;
                                }
                                """,
                        """
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.OneToOne;
                                import javax.jdo.annotations.Persistent;

                                @Entity
                                public class Person {
                                    @Persistent(mappedBy = "person")
                                    private Address address;
                                }
                                @Entity
                                public class Company {
                                    @Persistent(mappedBy = "company")
                                    private Address mainAddress;
                                }
                                @Entity
                                public class Address {
                                    @OneToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
                                    private Person person;
                                    @OneToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
                                    private Company company;
                                }
                                """
                )
        );
    }
}
