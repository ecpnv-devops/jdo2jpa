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
                                import javax.persistence.DiscriminatorColumn;
                                import javax.persistence.DiscriminatorValue;
                                
                                @DiscriminatorValue(value = "Person")
                                @DiscriminatorColumn(name = "discriminator", length = 255)
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                
                                @DiscriminatorValue(value = "Manager")
                                @DiscriminatorColumn(name = "discriminator", length = 255)
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }
}
