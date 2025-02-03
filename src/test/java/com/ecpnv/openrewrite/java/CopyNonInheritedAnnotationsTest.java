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
package com.ecpnv.openrewrite.java;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;
import com.ecpnv.openrewrite.jdo2jpa.Constants;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
class CopyNonInheritedAnnotationsTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .parser(PARSER)
                .recipe(new CopyNonInheritedAnnotations(Set.of(Constants.Jdo.DISCRIMINATOR_ANNOTATION_FULL)));
    }

    /**
     * Tests the functionality of copying non-inherited annotations from a parent class to a subclass.
     * <p>
     * This method validates the transformation where annotations present on a parent class
     * but not inherited by the subclass due to their nature are explicitly copied to
     * the subclass. The test checks that annotations such as `@Discriminator`
     * are applied to both the parent and subclass appropriately after processing.
     * <p>
     * The test uses a sample input for Java source code where the subclass does not initially
     * have the annotation from the parent class. After the method is executed,
     * the subclass reflects the presence of the non-inherited annotation.
     */
    @DocumentExample
    @Test
    void copyNonInheritedAnnotations1o1() {
        rewriteRun(//language=java
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
                                
                                @Discriminator("Person")
                                public class Manager extends Person {
                                        @Persistent( mappedBy = "person")
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }
}
