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
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;


/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
class AddMethodToVariableDeclarationConditionallyTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.BestPractices.OneToMany");
    }

    /**
     * Validates the addition of a method to a variable declaration in a Java class if specific conditions are met.
     * <p>
     * This test ensures that:
     * - A new method `addToPersons` is correctly introduced into a target class (`SomeEntity`) when it contains a field
     * (`persons`) of type `List<Person>` annotated with `@Persistent`.
     * - The newly added method allows handling the association between a `Person` element and `SomeEntity` by
     * appending the element to the `persons` list and setting the `SomeEntity` instance as a reference in the
     * provided `Person` element.
     * <p>
     * The rewrite operation verifies that the class retains its original structure alongside the introduced method
     * while maintaining type validation configurations to resolve missing types dynamically.
     */
    @DocumentExample
    @Test
    void addMethodToVariableDeclaration() {
        rewriteRun(
                spec -> spec.typeValidationOptions(
                        TypeValidation.builder().allowMissingType(o -> true).build()),
                //language=java
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
                                import java.util.ArrayList;
                                import java.util.Collection;
                                import java.util.Collections;
                                import java.util.List;
                                import java.util.stream.Stream;
                                
                                import javax.persistence.Entity;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {
                                    public void setSomeEntity(SomeEntity someEntity) {}
                                }
                                public class SomeEntity {
                                    @Persistent( mappedBy = "person")
                                    private List<Person> persons;
                                
                                    @Programmatic
                                    public Collection<Person> getPersons() {
                                        return Collections.unmodifiableCollection(persons);
                                    }
                                
                                    @Programmatic
                                    public Stream<Person> streamPersons() {
                                        return new ArrayList<>(persons).stream();
                                    }
                                
                                    @Programmatic
                                    public void addToPersons(Person element) {
                                        element.setSomeEntity(this);
                                        persons.add(element);
                                    }
                                
                                    @Programmatic
                                    public void removeFromPersons(Person element) {
                                        persons.remove(element);
                                        element.setSomeEntity(null);
                                    }
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void noChangeWhenNotAnCollection() {
        rewriteRun(
                //language=java
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
                                    private Person person;
                                }
                                """
                )
        );
    }

    /**
     * Ensures that no method is added to the variable declaration during the rewrite process
     * in cases where the specified conditions for modification are not met in the input code.
     * <p>
     * This test validates the following:
     * - No additional methods are introduced to the target class (`SomeEntity`) when there is
     * no indication in the Java source code necessitating such modifications, such as the
     * absence of applicable fields or annotations like `@Persistent`.
     * - The structure and content of the original source code remain unchanged during the
     * rewrite operation.
     * <p>
     * The method performs a rewrite process on a given Java code snippet and verifies that the
     * resulting output matches the initial input, ensuring the correctness and integrity of the
     * transformation pipeline in non-modification scenarios.
     */
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
