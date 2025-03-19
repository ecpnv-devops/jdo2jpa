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


/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
class AddMethodToVariableDeclarationConditionallyTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipe(new AddMethodToVariableDeclarationConditionally(
                "@.*Persistent\\(.*mappedBy.*",
                "*..* addTo$varNameC$($varGType$)",
                "@Programmatic\npublic void addTo$varNameC$($varGType$ element) {\n" +
                        "$varName$.add(element);\nelement.set$className$(this);\n}",
                "org.apache.isis.applib.annotation.Programmatic"));
    }

    @DocumentExample
    @Test
    void addMethodToVariableDeclaration() {
        rewriteRun(//language=java
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {
                                    public void setSomeEntity(SomeEntity someEntity) {}
                                }
                                public class SomeEntity {
                                    @Persistent( mappedBy = "person")
                                    private List<Person> persons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;
                                import org.apache.isis.applib.annotation.Programmatic;
                                
                                public class Person {
                                    public void setSomeEntity(SomeEntity someEntity) {}
                                }
                                public class SomeEntity {
                                    @Persistent( mappedBy = "person")
                                    private List<Person> persons;
                                
                                    @Programmatic
                                    public void addPersons(Person element){
                                        persons.add(element);
                                        person.setSomeEntity(this);
                                    }
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void doNotAddMethodToVariableDeclaration() {
        rewriteRun(//language=java
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {
                                    public void setSomeEntity(SomeEntity someEntity) {}
                                }
                                public class SomeEntity {
                                    @Persistent()
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }
}
