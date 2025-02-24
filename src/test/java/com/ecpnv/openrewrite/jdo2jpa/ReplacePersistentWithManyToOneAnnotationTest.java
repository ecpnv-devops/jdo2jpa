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
 * @author Patrick Deenen @ Open Circle Solutions
 */
class ReplacePersistentWithManyToOneAnnotationTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent");
    }

    /**
     * Tests the transformation of a `@Persistent` annotation to a `@ManyToOne` annotation
     * with an eager fetch type, associated cascade types, and the required imports.
     * <p>
     * This method validates that:
     * - Fields annotated with `@Persistent` in a JDO-annotated class are correctly
     * replaced with `@ManyToOne` in a JPA-annotated class.
     * - Proper `FetchType.EAGER` is applied.
     * - Appropriate cascade types (`CascadeType.PERSIST, CascadeType.MERGE,
     * CascadeType.REFRESH, CascadeType.DETACH`) are included in the transformation.
     * - Necessary import statements are added to the resulting Java code.
     */
    @DocumentExample
    @Test
    void replacePersistentWithColumnEagerAnnotation() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;
                                
                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( defaultFetchGroup = "true")
                                    private Person person;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.ManyToOne;
                                
                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    private int id;
                                    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.EAGER)
                                    private Person person;
                                }
                                """
                )
        );
    }

    /**
     * Tests the transformation of a `@Persistent` annotation with `defaultFetchGroup`
     * set to `false` into a `@ManyToOne` annotation with `FetchType.LAZY` in a Java class.
     * <p>
     * This method validates the correct application of the transformation by:
     * - Replacing the `@Persistent` annotation with `@ManyToOne`.
     * - Ensuring that `FetchType.LAZY` is applied as the fetch strategy.
     * - Adding appropriate cascade types (`CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH`).
     * - Updating the import statements to include necessary JPA-related imports.
     */
    @DocumentExample
    @Test
    void replacePersistentWithColumnLazyAnnotation() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;
                                
                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( defaultFetchGroup = "false")
                                    private Person person;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.ManyToOne;
                                
                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    private int id;
                                    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    private Person person;
                                }
                                """
                )
        );
    }

    /**
     * Tests the transformation of a `@Persistent` annotation to a `@ManyToOne` annotation
     * with a lazy fetch type and associated cascade types in a Java class.
     * <p>
     * This method validates the transformation process by:
     * - Replacing the `@Persistent` annotation with `@ManyToOne`.
     * - Ensuring that `FetchType.LAZY` is applied as the fetch strategy.
     * - Including appropriate cascade types (`CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH`)
     * in the resulting annotation.
     * - Adding necessary JPA imports to the resulting Java code.
     */
    @DocumentExample
    @Test
    void replacePersistentWithColumnDefaultLazyAnnotation() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;
                                
                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    private int id;
                                    @Persistent
                                    private Person person;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.ManyToOne;
                                
                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    private int id;
                                    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    private Person person;
                                }
                                """
                )
        );
    }

    /**
     * Validates that the `@Persistent` annotation is not replaced during
     * the execution of the recipe in specific scenarios.
     * <p>
     * This test ensures that:
     * - Fields annotated with `@Persistent` in the input code remain unchanged.
     * - No transformation or replacement of annotations occurs.
     * - The structure and content of the Java code remain consistent with the input.
     */
    @DocumentExample
    @Test
    void noReplaceOfPersistentAnnotation() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( nullValue = "false")
                                    private String name;
                                }
                                """
                )
        );
    }

    /**
     * Validates that specific scenarios involving the `@Persistent` annotation
     * within the provided source code do not trigger any replacement or transformation
     * during the execution of the associated recipe.
     * <p>
     * This test ensures that:
     * - Code containing the `@Persistent` annotation alongside a method parameter
     * remains unchanged.
     * - No annotations are replaced or modified when the recipe is run.
     * - The structure and logic of the Java source code are preserved exactly
     * as in the input.
     */
    @DocumentExample
    @Test
    void noReplacePersistentWhenParameter() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;
                                
                                @Entity
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    public void setPerson( Person person){
                                        Person p2 = null;
                                    }
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void noManyToOneWhenNotEntity() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;
                                
                                @Entity
                                public class Person {}
                                public class SomeEntity {
                                    private Person person;
                                    public void setPerson( Person person){
                                        this.person = person;
                                    }
                                }
                                """
                )
        );
    }

    /**
     * Tests the transformation of a `@Persistent` annotation, paired with a `@Column` annotation
     * that includes the `allowsNull` attribute, to a `@ManyToOne` annotation in a JPA-annotated Java class.
     * <p>
     * This method validates the following:
     * - Fields annotated with `@Persistent` and `@Column` (`allowsNull = "false"`) are replaced with
     * a `@ManyToOne` annotation where the `optional` attribute is set to "false".
     * - The transformation applies a lazy fetch strategy (`FetchType.LAZY`) within the `@ManyToOne` annotation.
     * - Cascade types (`CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH`)
     * are included in the resulting `@ManyToOne` annotation.
     * - Necessary JPA import statements are added to the resulting Java code.
     */
    @DocumentExample
    @Test
    void replacePersistentWithColumnAllowsNullRemoved() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;
                                import javax.jdo.annotations.Column;
                                
                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    private int id;
                                    @Persistent
                                    @Column(allowsNull = "false")
                                    private Person person;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.CascadeType;
                                import javax.persistence.Entity;
                                import javax.persistence.FetchType;
                                import javax.persistence.ManyToOne;
                                
                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    private int id;
                                    @ManyToOne(optional = false, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    private Person person;
                                }
                                """
                )
        );
    }

    /**
     * Validates the transformation of a `@Persistent` annotation to a combination of `@Column`
     * and `@ManyToOne` annotations, ensuring compatibility with JPA annotations while preserving
     * semantic behavior.
     * <p>
     * This test specifically ensures the following:
     * - Fields annotated with `@Persistent` in the input code are replaced with the appropriate
     * JPA annotations (`@Column` and `@ManyToOne`) that adhere to the required constraints.
     * - The `allowsNull` attribute specified in the `@Column` annotation is correctly mapped to
     * the `optional` attribute in the `@ManyToOne` annotation (`optional = "false"` if `allowsNull` is "false").
     * - Necessary cascade types (`CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH`)
     * and `FetchType.LAZY` are applied in the generated `@ManyToOne` annotation.
     * - Proper JPA-related imports are added to the resulting Java code.
     */
    @DocumentExample
    @Test
    void replacePersistentWithColumnAllowsNull() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;
                                import javax.jdo.annotations.Column;
                                
                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    private int id;
                                    @Persistent
                                    @Column(name = "personId", allowsNull = "false")
                                    private Person person;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.*;
                                
                                @Entity
                                public class Person {}
                                @Entity
                                public class SomeEntity {
                                    private int id;
                                    @ManyToOne(optional = false, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    @JoinColumn(nullable = false, name = "personId")
                                    private Person person;
                                }
                                """
                )
        );
    }
}
