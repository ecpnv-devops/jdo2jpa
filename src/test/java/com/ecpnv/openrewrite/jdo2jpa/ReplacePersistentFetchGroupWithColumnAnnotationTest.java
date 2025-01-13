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
class ReplacePersistentFetchGroupWithColumnAnnotationTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipe(new ReplacePersistentFetchGroupWithColumnAnnotation());
    }

    /**
     * Validates the transformation of a `@Persistent` annotation with the `defaultFetchGroup = "true"`
     * property into a `@Column` annotation with `fetch = FetchType.EAGER`. This test ensures that the
     * `ReplacePersistentFetchGroupWithColumnAnnotation` recipe correctly performs the replacement and
     * maintains proper imports and class functionality.
     */
    @DocumentExample
    @Test
    void replacePersistentWithColumnEagerAnnotation() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( defaultFetchGroup = "true")
                                    private List<Person> persons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Column;
                                import javax.persistence.FetchType;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Column( fetch = FetchType.EAGER)
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

    /**
     * Validates the transformation of a `@Persistent` annotation with the `defaultFetchGroup = "false"`
     * property into a `@Column` annotation with `fetch = FetchType.EAGER`. This test ensures that the
     * `ReplacePersistentFetchGroupWithColumnAnnotation` recipe correctly performs the replacement and
     * maintains proper imports and class functionality.
     */
    @DocumentExample
    @Test
    void replacePersistentWithColumnLazyAnnotation() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( defaultFetchGroup = "false")
                                    private List<Person> persons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Column;
                                import javax.persistence.FetchType;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Column( fetch = FetchType.LAZY)
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

    /**
     * Validates that the `@Persistent` annotation on a field is not replaced when no specific conditions
     * for transformation are met. This test ensures that the `ReplacePersistentFetchGroupWithColumnAnnotation`
     * recipe does not make unnecessary changes for annotations lacking the `defaultFetchGroup` parameter.
     */
    @DocumentExample
    @Test
    void noReplaceOfPersistentAnnotation() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.Persistent;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void whenPersistentAndExistingColumnAnnotationThenAddFetchType() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Column;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( defaultFetchGroup = "false")
                                    @Column( name = "personId")
                                    private Person person;
                                }
                                """,
                        """
                                import javax.jdo.annotations.Persistent;
                                import javax.persistence.Column;
                                import javax.persistence.FetchType;
                                
                                public class Person {}
                                public class SomeEntity {
                                    private int id;
                                    @Persistent( defaultFetchGroup = "false")
                                    @Column( name = "personId", fetch = FetchType.LAZY)
                                    private Person person;
                                }
                                """
                )
        );
    }
}
