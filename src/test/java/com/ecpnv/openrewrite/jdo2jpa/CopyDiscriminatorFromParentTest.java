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
