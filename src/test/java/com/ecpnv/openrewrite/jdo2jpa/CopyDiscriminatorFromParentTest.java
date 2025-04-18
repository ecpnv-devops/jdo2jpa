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
class CopyDiscriminatorFromParentTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER)
                .recipe(new CopyDiscriminatorFromParent());
    }

    /**
     * Tests the functionality of replicating non-inherited annotations from parent classes
     * to derived classes using the classname as an identifier.
     * <p>
     * In this case, the test verifies that the `@Discriminator` annotation from the `Person`
     * class is correctly copied to the derived `Manager` class. Non-inherited annotations are
     * those which are not automatically passed down to subclasses by Java, and this test
     * ensures that they are explicitly applied to the subclass.
     * <p>
     * Key Aspects Verified:
     * - Proper identification and replication of non-inherited annotations.
     * - Preservation of unrelated annotations (e.g., `@Persistent`) already present in
     * the subclass.
     * - Correct transformation of source code to include the non-inherited annotation
     * in the subclass.
     */
    @DocumentExample
    @Test
    void copyNonInheritedAnnotationsUsingClassname() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Discriminator;
                                import javax.jdo.annotations.Persistent;
                                
                                @Discriminator("Person")
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                public class Manager extends Person {
                                        @Persistent( mappedBy = "person")
                                        private List<Person> managedPersons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Discriminator;
                                import javax.jdo.annotations.Persistent;
                                
                                @Discriminator("Person")
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                
                                @Discriminator("Manager")
                                public class Manager extends Person {
                                        @Persistent( mappedBy = "person")
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }

    /**
     * Verifies that there is no change in the `@Discriminator` annotation when the discriminator
     * value is already explicitly defined in the child class.
     * <p>
     * This test checks that the `Manager` class retains its own defined discriminator value
     * (`Manager_discriminator`) and that it does not get overridden or altered by the `Person`
     * class's discriminator value (`Person_discriminator`).
     * <p>
     * Key aspects verified:
     * - The child class maintains its explicitly defined `@Discriminator` annotation value.
     * - The transformation does not alter existing valid annotations in the child class.
     * - Proper differentiation of discriminator values between parent and child classes.
     */
    @DocumentExample
    @Test
    void noChangeWithDiscriminatorValue() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Discriminator;
                                import javax.jdo.annotations.Persistent;
                                
                                @Discriminator("Person_discriminator")
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                @Discriminator(Manager.DISCRIMINATOR_VALUE)
                                public class Manager extends Person {
                                        public static final String DISCRIMINATOR_VALUE = "Manager_discriminator";
                                        @Persistent( mappedBy = "person")
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }

    /**
     * Verifies that there is no change in the `@Discriminator` annotation when a discriminator
     * value is explicitly assigned in the derived class as a static final field.
     * <p>
     * This test ensures the integrity of the explicitly defined discriminator value in the child class,
     * preventing it from being altered or overridden during any transformation or processing.
     * <p>
     * Key aspects verified:
     * - The child class retains the explicitly defined `@Discriminator` annotation value.
     * - Existing annotations and their specified attributes, such as `@Persistent`, remain unchanged.
     * - Proper handling and differentiation of annotations between parent and child classes during the process.
     */
    @DocumentExample
    @Test
    void noChangeWithDiscriminatorValueAssignment() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Discriminator;
                                import javax.jdo.annotations.Persistent;
                                
                                @Discriminator(value="Person_discriminator")
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                @Discriminator( value = Manager.DISCRIMINATOR_VALUE)
                                public class Manager extends Person {
                                        public static final String DISCRIMINATOR_VALUE = "Manager_discriminator";
                                        @Persistent( mappedBy = "person")
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }

    /**
     * Ensures that non-inherited annotations are correctly copied from a parent class
     * to a derived class when using a specific discriminator strategy.
     * <p>
     * In this test, the focus is on the `@Discriminator` annotation with a `DiscriminatorStrategy.CLASS_NAME`
     * applied in the parent class. The derived class should then explicitly receive a `@Discriminator` annotation
     * with a specific value representing its own class name, while maintaining the parent strategy.
     * <p>
     * Core aspects verified by this test:
     * - Correct replication of the `@Discriminator` annotation from the parent class to the derived class.
     * - Transformation of the derived class to assign an explicit discriminator value representing its class.
     * - Preservation of other existing annotations (e.g., `@Persistent`) in the derived class without modification.
     * <p>
     * This ensures proper annotation inheritance behavior in source code transformations,
     * especially when dealing with annotations that are not natively inherited in Java.
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
                                import javax.jdo.annotations.Persistent;
                                
                                @Discriminator(strategy = DiscriminatorStrategy.CLASS_NAME)
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                public class Manager extends Person {
                                        @Persistent( mappedBy = "person")
                                        private List<Person> managedPersons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Discriminator;
                                import javax.jdo.annotations.DiscriminatorStrategy;
                                import javax.jdo.annotations.Persistent;
                                
                                @Discriminator(value = "Person", strategy = DiscriminatorStrategy.CLASS_NAME)
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                
                                @Discriminator(value = "Manager", strategy = DiscriminatorStrategy.CLASS_NAME)
                                public class Manager extends Person {
                                        @Persistent( mappedBy = "person")
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }
}
