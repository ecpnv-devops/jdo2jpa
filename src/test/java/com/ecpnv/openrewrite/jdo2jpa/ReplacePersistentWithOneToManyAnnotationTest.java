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
 * Test class to validate the functionality of the `ReplacePersistentWithOneToManyAnnotation` recipe.
 * <p>
 * This class implements the `RewriteTest` interface and defines a unit test to evaluate the transformation of
 * `@Persistent` annotations into `@OneToMany` annotations compliant with JPA standards. The test ensures that:
 * <p>
 * - The migration correctly identifies and replaces `@Persistent` annotations.
 * - The attributes, such as `mappedBy`, are translated accurately into the equivalent JPA `@OneToMany` annotation.
 * - Relevant imports are added or updated appropriately.
 * - The transformation process aligns with predefined specifications outlined in the recipe.
 * <p>
 * The `defaults` method configures the test parser and associates it with the `ReplacePersistentWithOneToManyAnnotation`
 * recipe, ensuring that the correct class paths for JPA and JDO are loaded. The test case `replacePersistentWithOneToManyAnnotation`
 * validates that valid transformations are applied to a sample Java source code.
 * <p>
 * The test leverages the Rewrite framework's utilities to simulate the parsing, visiting, and transformation of source code.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
class ReplacePersistentWithOneToManyAnnotationTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipe(new ReplacePersistentWithOneToManyAnnotation(null));
    }

    /**
     * Validates the transformation of the `@Persistent` annotation to the JPA-compliant `@OneToMany` annotation.
     */
    @DocumentExample
    @Test
    void replacePersistentWithOneToManyAnnotation() {
        rewriteRun(//language=java
            //language=java
            java(
        """
                import java.util.List;
                import javax.jdo.annotations.Persistent;
                
                public class Person {}
                public class SomeEntity {
                    private int id;
                    @Persistent( mappedBy = "person")
                    private List<Person> persons;
                }
                """,
        """
                import java.util.List;
                import javax.jdo.annotations.Persistent;
                import javax.persistence.OneToMany;
                
                public class Person {}
                public class SomeEntity {
                    private int id;
                    @OneToMany(mappedBy = "person")
                    private List<Person> persons;
                }
                """
            )
        );
    }


    /**
     * Validates the transformation of the `@Persistent` annotation to the JPA-compliant `@OneToMany` annotation
     * when multiple annotations are present on the field. The other annotations should be preserved.
     */
    @DocumentExample
    @Test
    void replacePersistentWithOneToManyAnnotationWithMultipleAnnotations() {
        rewriteRun(//language=java
            //language=java
            java(
        """
                import java.util.List;
                import javax.jdo.annotations.Persistent;
                import java.lang.Deprecated;
                
                public class Person {}
                public class SomeEntity {
                    private int id;
                    @Persistent( mappedBy = "person")
                    @Deprecated
                    private List<Person> persons;
                }
                """,
        """
                import java.util.List;
                import javax.jdo.annotations.Persistent;
                import javax.persistence.OneToMany;
                
                import java.lang.Deprecated;
                
                public class Person {}
                public class SomeEntity {
                    private int id;
                    @OneToMany(mappedBy = "person")
                    @Deprecated
                    private List<Person> persons;
                }
                """
            )
        );
    }

    /**
     * Validates the transformation of the `@Persistent` annotation to the JPA-compliant `@OneToMany` annotation
     * when the dependentElement argument is defined. This should translate into the remove cascadeType when true.
     */
    @DocumentExample
    @Test
    void replacePersistentWithOneToManyAnnotationWithDependentElementArgument() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( mappedBy = "person", dependentElement = "true")
                                    private List<Person> persons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.CascadeType;
                                import javax.persistence.OneToMany;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @OneToMany(mappedBy = "person", cascade = {CascadeType.REMOVE})
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

    /**
     * Validates the transformation of the `@Persistent` annotation to the JPA-compliant `@OneToMany` annotation
     * when the dependentElement argument is defined with false. This should not translate into the remove cascadeType.
     */
    @DocumentExample
    @Test
    void replacePersistentWithOneToManyAnnotationWithDependentElementArgumentFalse() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( mappedBy = "person", dependentElement = "false")
                                    private List<Person> persons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.OneToMany;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @OneToMany(mappedBy = "person")
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

    /**
     * Validates the transformation of the `@Persistent` annotation to the JPA-compliant `@OneToMany` annotation
     * when the dependentElement argument is defined. This should translate into the remove cascadeType when true.
     * Additionally the default cascade should be defined.
     */
    @DocumentExample
    @Test
    void replacePersistentWithOneToManyAnnotationWithDependentElementArgumentAndDefault() {
        rewriteRun(spec -> spec.recipe(new ReplacePersistentWithOneToManyAnnotation("CascadeType.MERGE, CascadeType.DETACH")),
                //language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( mappedBy = "person", dependentElement = "true")
                                    private List<Person> persons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.CascadeType;
                                import javax.persistence.OneToMany;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @OneToMany(mappedBy = "person", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.DETACH})
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

}
