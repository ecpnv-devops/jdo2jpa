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
class InheritanceTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Inheritance");
    }

    @DocumentExample
    @Test
    void testAllSubRecipes() {
        rewriteRun(
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Discriminator;
                                import javax.jdo.annotations.Persistent;
                                
                                @Discriminator("Person", strategy = "special", column = "col", columns = {"cols"}, indexed = "true")
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
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.DiscriminatorColumn;
                                import javax.persistence.DiscriminatorValue;
                                
                                @DiscriminatorValue("Person")
                                @DiscriminatorColumn(name = "discriminator", length = 255)
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                
                                @DiscriminatorValue("Manager")
                                @DiscriminatorColumn(name = "discriminator", length = 255)
                                public class Manager extends Person {
                                        @Persistent( mappedBy = "person")
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }

}
