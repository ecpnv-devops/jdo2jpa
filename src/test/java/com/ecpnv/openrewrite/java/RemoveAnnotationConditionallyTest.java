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
class RemoveAnnotationConditionallyTest extends BaseRewriteTest {

    public static final String MATCH_MAPPED_BY = "(@.*Persistent\\(.*mappedBy.*)|(@.*OneToMany\\(.*mappedBy.*)";
    public static final String MATCH_GETTER = "@.*Getter.*";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipe(new RemoveAnnotationConditionally(MATCH_MAPPED_BY, MATCH_GETTER,
                RemoveAnnotationConditionally.DeclarationType.VAR, "java.util.Collection"));
    }

    @DocumentExample
    @Test
    void removeGetterForVar() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Persistent;
                                import lombok.Getter;
                                import java.util.List;
                                
                                public class Person {
                                    public void setSomeEntity(SomeEntity someEntity) {}
                                }
                                public class SomeEntity {
                                    @Getter
                                    private int id;
                                    @Getter
                                    @Persistent( mappedBy = "person")
                                    private List<Person> persons;
                                }
                                """,
                        """
                                import javax.jdo.annotations.Persistent;
                                import lombok.Getter;
                                import java.util.List;
                                
                                public class Person {
                                    public void setSomeEntity(SomeEntity someEntity) {}
                                }
                                public class SomeEntity {
                                    @Getter
                                    private int id;
                                """ + "    \n" + """
                                    @Persistent( mappedBy = "person")
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void noChangeWhenNoCollection() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Persistent;
                                import lombok.Getter;
                                
                                public class Person {
                                    public void setSomeEntity(SomeEntity someEntity) {}
                                }
                                public class SomeEntity {
                                    @Getter
                                    private int id;
                                    @Getter
                                    @Persistent( mappedBy = "person")
                                    private Person persons;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void noChangeWhenNoGetter() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Persistent;
                                import lombok.Getter;
                                
                                public class Person {
                                    public void setSomeEntity(SomeEntity someEntity) {}
                                }
                                public class SomeEntity {
                                    @Getter
                                    private int id;
                                    @Persistent( mappedBy = "person")
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void noChangeWhenNotMappedBy() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Persistent;
                                import lombok.Getter;
                                
                                public class Person {
                                    public void setSomeEntity(SomeEntity someEntity) {}
                                }
                                public class SomeEntity {
                                    @Getter
                                    private int id;
                                    @Getter
                                    @Persistent()
                                    private List<Person> persons;
                                }
                                """
                )
        );
    }

}
