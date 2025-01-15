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
 * Unit tests for verifying the functionality of the `ReplacePersistentFetchGroupWithColumnAnnotation` recipe.
 * This test class ensures the proper transformation, annotations, and behavior of fields annotated with
 * `@Persistent` in Java Data Objects (JDO) models to equivalent `@Column` annotations in Java Persistence API (JPA).
 * <p>
 * The primary focus of this class includes:
 * - Conversion of specific `@Persistent` annotations based on the `defaultFetchGroup` property.
 * - Validating that annotations are transformed correctly with appropriate fetch types (`FetchType.EAGER` or `FetchType.LAZY`).
 * - Verifying that unnecessary modifications are avoided for annotations missing the required conditions.
 * - Ensuring compatibility and correctness of imports, annotations, and overall structure of the Java code.
 * <p>
 * This class extends `BaseRewriteTest` to provide access to standard rewrite testing utilities and configurations.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
class ReplacePersistentFetchGroupWithColumnAnnotationTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Persistent");
    }

    /**
     * Validates the transformation of a `@Persistent` annotation with the `defaultFetchGroup = "true"`
     * property into a `@Column` annotation with `fetch = FetchType.EAGER`. This test ensures that the
     * `ReplacePersistentFetchGroupWithColumnAnnotation` recipe correctly performs the replacement and
     * maintains proper imports and class functionality.
     */
    @DocumentExample
    @Test
    void replacePersistentWithColumnEagerAnnotation() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( defaultFetchGroup = "true")
                                    private List<Person> persons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Column;
                                import javax.persistence.FetchType;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Column( fetch = FetchType.EAGER)
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

    /**
     * Validates the transformation of a `@Persistent` annotation with the `defaultFetchGroup = "false"`
     * property into a `@Column` annotation with `fetch = FetchType.EAGER`. This test ensures that the
     * `ReplacePersistentFetchGroupWithColumnAnnotation` recipe correctly performs the replacement and
     * maintains proper imports and class functionality.
     */
    @DocumentExample
    @Test
    void replacePersistentWithColumnLazyAnnotation() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( defaultFetchGroup = "false")
                                    private List<Person> persons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Column;
                                import javax.persistence.FetchType;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Column( fetch = FetchType.LAZY)
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

    /**
     * Validates that no modifications are made to a `@Persistent` annotation when it contains non-targeted
     * arguments or properties unrelated to specific replacement criteria.
     * <p>
     * This test ensures the preservation of the original `@Persistent` annotation in cases where its
     * attributes, such as `nullValue`, do not meet the conditions for transformation. Additionally, it
     * verifies that no unexpected changes are introduced to the class's structure or associated imports.
     */
    @DocumentExample
    @Test
    void noReplaceOfPersistentAnnotation() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( nullValue = "false")
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

    /**
     * Validates the removal of the `@Persistent` annotation from a field
     * when it does not contain any arguments or properties.
     * <p>
     * This test ensures that the field remains unmodified apart from
     * the removal of the `@Persistent` annotation, and that no unnecessary
     * changes are introduced to the class's structure or functionality.
     */
    @DocumentExample
    @Test
    void replacePersistentAnnotationWithNoArguments() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent
                                    private List<Person> persons;
                                }
                                """,
                        """
                                import java.util.List;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

    /**
     * Validates that when a field has both `@Persistent` and an existing `@Column` annotation,
     * a `fetch` parameter with the correct `FetchType` is added to the `@Column` annotation.
     * <p>
     * This ensures that the `ReplacePersistentFetchGroupWithColumnAnnotation` recipe correctly updates
     * annotations without removing or altering unrelated properties, maintaining class integrity and functionality.
     */
    @DocumentExample
    @Test
    void whenPersistentAndExistingColumnAnnotationThenAddFetchType() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Column;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( defaultFetchGroup = "false")
                                    @Column( name = "personId")
                                    private Person person;
                                }
                                """,
                        """
                                import javax.persistence.Column;
                                import javax.persistence.FetchType;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Column( name = "personId", fetch = FetchType.LAZY)
                                    private Person person;
                                }
                                """
                )
        );
    }
}
