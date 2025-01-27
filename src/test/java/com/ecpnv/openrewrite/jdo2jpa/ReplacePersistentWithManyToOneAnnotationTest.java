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

class ReplacePersistentWithManyToOneAnnotationTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent");
    }

    /**
     * Tests the transformation of a `@Persistent` annotation to a `@ManyToOne` annotation
     * with an eager fetch type, associated cascade types, and the required imports.
     *
     * This method validates that:
     * - Fields annotated with `@Persistent` in a JDO-annotated class are correctly
     *   replaced with `@ManyToOne` in a JPA-annotated class.
     * - Proper `FetchType.EAGER` is applied.
     * - Appropriate cascade types (`CascadeType.PERSIST, CascadeType.MERGE,
     *   CascadeType.REFRESH, CascadeType.DETACH`) are included in the transformation.
     * - Necessary import statements are added to the resulting Java code.
     *
     * Utilizes the Rewrite testing framework to apply the transformation and
     * verify that the output matches the specified expected result.
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
                                import javax.jdo.annotations.Persistent;
                                
                                @Entity
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @ManyToOne( cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.EAGER)
                                    private Person person;
                                }
                                """
                )
        );
    }

    /**
     * Tests the transformation of a `@Persistent` annotation with `defaultFetchGroup`
     * set to `false` into a `@ManyToOne` annotation with `FetchType.LAZY` in a Java class.
     *
     * This method validates the correct application of the transformation by:
     * - Replacing the `@Persistent` annotation with `@ManyToOne`.
     * - Ensuring that `FetchType.LAZY` is applied as the fetch strategy.
     * - Adding appropriate cascade types (`CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH`).
     * - Updating the import statements to include necessary JPA-related imports.
     *
     * Utilizes the Rewrite testing framework to verify that the input source code,
     * modified by the transformation recipe, aligns with the expected result.
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
                                import javax.jdo.annotations.Persistent;
                                
                                @Entity
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @ManyToOne( cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    private Person person;
                                }
                                """
                )
        );
    }

    /**
     * Tests the transformation of a `@Persistent` annotation to a `@ManyToOne` annotation
     * with a lazy fetch type and associated cascade types in a Java class.
     *
     * This method validates the transformation process by:
     * - Replacing the `@Persistent` annotation with `@ManyToOne`.
     * - Ensuring that `FetchType.LAZY` is applied as the fetch strategy.
     * - Including appropriate cascade types (`CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH`)
     *   in the resulting annotation.
     * - Adding necessary JPA imports to the resulting Java code.
     *
     * Utilizes the Rewrite testing framework to apply the transformation and
     * verifies that the modified output aligns with the expected results.
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
                                import javax.jdo.annotations.Persistent;
                                
                                @Entity
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @ManyToOne( cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
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
     * <p>
     * Utilizes the Rewrite testing framework to verify that the source code
     * remains unmodified after applying the recipe.
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
}
