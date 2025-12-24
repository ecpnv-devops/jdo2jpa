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
class DiscriminatorTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources(
                "com.ecpnv.openrewrite.jdo2jpa.v2x.Discriminator",
                "com.ecpnv.openrewrite.jdo2jpa.v2x.cleanup");
    }

    /**
     * Tests the transformation of non-inherited annotations applied to classes
     * from JDO (@Discriminator) to JPA (@DiscriminatorValue and @DiscriminatorColumn)
     * with the strategy set to CLASS_NAME. This allows ensuring that class-level
     * annotations in the JDO model are correctly converted to reflect the JPA
     * discriminator strategy and values for the inheritance hierarchy.
     * <p>
     * The method validates that:
     * 1. The original @Discriminator annotation in JDO is replaced by the
     * corresponding @DiscriminatorValue and @DiscriminatorColumn annotations in JPA.
     * 2. Each class in the hierarchy correctly receives the expected configuration
     * for discriminator values and column definitions.
     */
    @DocumentExample
    @Test
    void copyNonInheritedAnnotationsUsingClassnameWithStrategy() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Discriminator;
                                import javax.jdo.annotations.DiscriminatorStrategy;
                                import javax.jdo.annotations.Inheritance;
                                
                                @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                @Discriminator(strategy = DiscriminatorStrategy.CLASS_NAME)
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Inheritance;
                                import javax.persistence.DiscriminatorColumn;
                                import javax.persistence.DiscriminatorValue;
                                
                                @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                @DiscriminatorValue(value = "Person")
                                @DiscriminatorColumn(name = "discriminator", length = 255)
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                
                                
                                @DiscriminatorValue(value = "Manager")
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }

    /**
     * Tests the transformation of JDO `@Discriminator` annotations to JPA `@DiscriminatorValue`
     * and `@DiscriminatorColumn` annotations for entities in an inheritance hierarchy.
     * <p>
     * The migration process validates the following aspects:
     * 1. Converts the `@Discriminator` annotation from JDO to the `@DiscriminatorValue`
     * and `@DiscriminatorColumn` annotations in JPA while preserving the discriminator
     * value's semantic meaning.
     * 2. Ensures that the converted JPA entity classes are correctly annotated with
     * `@DiscriminatorValue` to specify the discriminator value for each entity.
     * 3. Applies the `@DiscriminatorColumn` annotation to represent the column configuration
     * for the discriminator across the inheritance hierarchy.
     */
    @DocumentExample
    @Test
    void useDiscriminatorValue() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Discriminator;
                                import javax.jdo.annotations.DiscriminatorStrategy;
                                import javax.jdo.annotations.Inheritance;
                                
                                @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                @Discriminator("person_discriminator")
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                @Discriminator("manager_discriminator")
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Inheritance;
                                import javax.persistence.DiscriminatorColumn;
                                import javax.persistence.DiscriminatorValue;
                                
                                @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                @DiscriminatorValue("person_discriminator")
                                @DiscriminatorColumn(name = "discriminator", length = 255)
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                @DiscriminatorValue("manager_discriminator")
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }

    /**
     * Tests the addition of a `@DiscriminatorColumn` annotation when the `@Inheritance` annotation
     * is present on a Java class. This conversion facilitates the migration from JDO to JPA by
     * ensuring that classes using inheritance mappings receive the required discriminator column
     * definition in JPA for inheritance strategy definition.
     * <p>
     * The method validates the following aspects:
     * 1. Adds a `@DiscriminatorColumn` annotation to the class containing the `@Inheritance` annotation.
     * 2. Ensures the discriminator column definition includes the correct default attributes such as
     * `name` ("discriminator") and `length` (255).
     * 3. Preserves the existing structure and functionality of the class hierarchy while aligning
     * it with JPA requirements.
     */
    @DocumentExample
    @Test
    void addDiscriminatorColumnWhenInheritanceAnnotationPresent() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Inheritance;
                                
                                @Inheritance
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Inheritance;
                                import javax.persistence.DiscriminatorColumn;
                                
                                @DiscriminatorColumn(name = "discriminator", length = 255)
                                @Inheritance
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }
}
