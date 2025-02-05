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
class RemoveInheritedAnnotationsTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .parser(PARSER)
                .recipe(new RemoveInheritedAnnotations(Set.of(Constants.Jpa.INHERITANCE_ANNOTATION_FULL)));
    }

    /**
     * Tests the removal of inherited annotations from subclasses while retaining them in the parent class.
     * <p>
     * This method asserts that when an annotation, specifically {@code @Inheritance}, is present on a parent
     * class and also applied redundantly to a subclass, the annotation is removed from the subclass while
     * remaining on the parent class. The method validates this transformation by comparing the original source
     * code against the expected modified output.
     */
    @DocumentExample
    @Test
    void removeInheritedAnnotations() {
        rewriteRun(//language=java
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Inheritance;
                                
                                @Inheritance
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                @Inheritance
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.Inheritance;
                                
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
