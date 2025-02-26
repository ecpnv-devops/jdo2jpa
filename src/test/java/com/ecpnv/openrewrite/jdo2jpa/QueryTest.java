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
class QueryTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources(
                "com.ecpnv.openrewrite.jdo2jpa.v2x.Query");
    }

    @DocumentExample
    @Test
    void migrateQueryAnnotations() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Query;
                                
                                @Query(name = "findByEmail", language = "JDOQL", value = "SELECT FROM Person WHERE email == :email", unique = "false")
                                @Query(name = "findByName", language = "SQL", value = "SELECT * FROM Person WHERE name = :name")
                                public class Person {
                                        private String name, email;
                                }
                                """,
                        """
                                import javax.persistence.NamedNativeQuery;
                                import javax.persistence.NamedQuery;
                                
                                @NamedQuery(name = "findByEmail", query = "SELECT FROM Person WHERE email == :email")
                                @NamedNativeQuery(name = "findByName", query = "SELECT * FROM Person WHERE name = :name")
                                public class Person {
                                        private String name, email;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void migrateQueriesAnnotation() {
        rewriteRun(
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Queries;
                                import javax.jdo.annotations.Query;
                                
                                @Queries({
                                    @Query(name = "findByEmail", language = "JDOQL", value = "SELECT FROM Person WHERE email == :email", unique = "false"),
                                    @Query(name = "findByName", language = "SQL", value = "SELECT * FROM Person WHERE name = :name")
                                })
                                public class Person {
                                        private String name, email;
                                }
                                """,
                        """
                                import javax.persistence.NamedNativeQueries;
                                import javax.persistence.NamedNativeQuery;
                                import javax.persistence.NamedQueries;
                                import javax.persistence.NamedQuery;
                                
                                @NamedQueries({
                                  @NamedQuery(name = "findByEmail", query = "SELECT FROM Person WHERE email == :email")
                                })
                                @NamedNativeQueries({
                                  @NamedNativeQuery(name = "findByName", query = "SELECT * FROM Person WHERE name = :name")
                                })
                                public class Person {
                                        private String name, email;
                                }
                                """
                )
        );
    }
}
