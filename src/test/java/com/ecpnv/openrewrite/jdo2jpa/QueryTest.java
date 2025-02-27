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

    /**
     * Tests the migration process for converting JDO `@Query` annotations to corresponding JPA
     * `@NamedQuery` and `@NamedNativeQuery` annotations within Java classes.
     * <p>
     * This method validates that JDOQL and SQL queries annotated on class elements using the
     * `@Query` annotation are correctly transformed into the JPA annotations while preserving
     * their naming and query logic.
     * <p>
     * Key aspects evaluated include:
     * - The proper conversion of `name` and `value` attributes from the JDO `@Query` annotation
     * to respective attributes of the JPA annotations.
     * - Handling of query languages (`language = "JDOQL"`, `language = "SQL"`) to select the
     * appropriate JPA annotation (`@NamedQuery` for JPQL, `@NamedNativeQuery` for SQL).
     * - Ensuring only compatible transformations and valid annotation structures are produced in
     * the output.
     * <p>
     * The test relies on an input and an expected output structure, confirming the migration
     * adheres to intended functionality and output formatting.
     */
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

    /**
     * Tests the migration process of JDO `@Queries` containing multiple `@Query` elements
     * to their respective JPA `@NamedQueries` and `@NamedNativeQueries` annotations.
     *
     * This method ensures that collections of JDO queries defined using the `@Queries` annotation
     * are appropriately transformed into JPA query annotations while maintaining logical consistency
     * and proper separation between JPQL and SQL queries.
     *
     * Key aspects verified include:
     * - Proper breakdown of multiple JDO `@Query` annotations into JPA `@NamedQuery` and `@NamedNativeQuery` annotations.
     * - Accurate translation of attributes such as `name` and `value` into their JPA counterparts.
     * - Handling and differentiation of `language = "JDOQL"` and `language = "SQL"` to map to
     *   either `@NamedQuery` or `@NamedNativeQuery` as required.
     * - Preservation of query logic and correctness in the output structure.
     *
     * This migration process is validated by comparing the input Java class containing the
     * JDO `@Queries` annotation against the expected output class containing the equivalent
     * JPA annotations.
     */
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
                                    @NamedQuery(name = "findByEmail", query = "SELECT FROM Person WHERE email == :email")})
                                @NamedNativeQueries({
                                        @NamedNativeQuery(name = "findByName", query = "SELECT * FROM Person WHERE name = :name")})
                                public class Person {
                                        private String name, email;
                                }
                                """
                )
        );
    }
}
