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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;
import com.ecpnv.openrewrite.jdo2jpa.Constants;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
class CopyAnnotationAttributeFromSubclassToParentClassTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
                .parser(PARSER)
                .recipe(new CopyAnnotationAttributeFromSubclassToParentClass(Constants.Jdo.INHERITANCE_ANNOTATION_FULL,
                        Constants.Jdo.INHERITANCE_ARGUMENT_STRATEGY, null));
    }

    @DocumentExample
    @Test
    void copyAnnotationAttributeFromSubclassToParentClass() {
        rewriteRun(//language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Inheritance;
                                import javax.jdo.annotations.InheritanceStrategy;
                                
                                @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Inheritance;
                                import javax.jdo.annotations.InheritanceStrategy;
                                
                                @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void noCopy() {
        rewriteRun(//language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Inheritance;
                                import javax.jdo.annotations.InheritanceStrategy;
                                
                                @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                public class Person {
                                        private int id;
                                        private String name;
                                }
                                @Inheritance
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }
}
