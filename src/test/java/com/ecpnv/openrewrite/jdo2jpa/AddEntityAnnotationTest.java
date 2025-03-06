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
 * Unit test class for validating the functionality of the `AddEntityAnnotation` recipe.
 * <p>
 * The `AddEntityAnnotationTest` ensures that the `AddEntityAnnotation` recipe correctly adds the
 * `@Entity` annotation to Java classes already annotated with `@PersistenceCapable`. It verifies
 * the expected behavior of the recipe within the scope of transforming source code while maintaining
 * existing functionality.
 * <p>
 * Implements the `RewriteTest` interface to follow the test requirements of the Rewrite testing framework.
 * <p>
 * Key components tested:
 * - Proper addition of the `@Entity` annotation where needed.
 * - Preservation of existing annotations (`@PersistenceCapable`).
 * - Correct handling of required imports for `javax.persistence.Entity`.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
class AddEntityAnnotationTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.PersistenceCapable");
    }

    /**
     * `addEntityAnnotationAlongPersistanceCapable()`: Verifies that the recipe successfully identifies
     * classes with the `@PersistenceCapable` annotation and adds the `@Entity` annotation with the required
     * import statements. Contains assertions to ensure the correctness of the transformation.
     */
    @DocumentExample
    @Test
    void addEntityAnnotationAlongPersistanceCapable() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.IdentityType;
                                import javax.jdo.annotations.PersistenceCapable;
                                
                                @PersistenceCapable(identityType = IdentityType.DATASTORE)
                                public class SomeEntity {
                                        private int id;
                                        private List<String> listofStrings;
                                }
                                """,
                        """
                                import java.util.List;
                                
                                import org.estatio.base.prod.dom.EntityAbstract;
                                import javax.persistence.Entity;
                                import javax.persistence.Table;
                                
                                @Entity
                                @Table
                                public class SomeEntity extends EntityAbstract {
                                        private int id;
                                        private List<String> listofStrings;
                                }
                                """
                )
        );
    }


    /**
     * `addEntityAnnotationAlongPersistanceCapable()`: Verifies that the recipe successfully identifies
     * classes with the `@PersistenceCapable` annotation and adds the `@Entity` annotation with the required
     * import statements. Contains assertions to ensure the correctness of the transformation.
     */
    @DocumentExample
    @Test
    void addEntityAnnotationAlongPersistanceCapableMultiline() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.PersistenceCapable;
                                
                                @PersistenceCapable(
                                        schema = "schemaName", 
                                        table = "tableName",
                                        identityType = IdentityType.DATASTORE)
                                public class SomeEntity {
                                        private int id;
                                        private List<String> listofStrings;
                                }
                                """,
                        """
                                import java.util.List;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.Table;
                                
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                @Entity
                                @Table(schema = "schemaName", name = "tableName")
                                public class SomeEntity extends EntityAbstract {
                                        private int id;
                                        private List<String> listofStrings;
                                }
                                """
                )
        );
    }

    /**
     * `addEntityAnnotationAlongPersistanceCapable()`: Verifies that the recipe successfully identifies
     * classes with the fully qualified type name `@javax.jdo.annotations.PersistenceCapable` annotation and
     * adds the `@Entity` annotation with the required import statements. Contains assertions to ensure the
     * correctness of the transformation.
     */
    @DocumentExample
    @Test
    void addEntityAnnotationAlongPersistanceCapableFqn() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                
                                @javax.jdo.annotations.PersistenceCapable(identityType = IdentityType.DATASTORE)
                                public class SomeEntity {
                                        private int id;
                                        private List<String> listofStrings;
                                }
                                """,
                        """
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                import java.util.List;
                                
                                import javax.persistence.Entity;
                                import javax.persistence.Table;
                                
                                @Entity
                                @Table
                                public class SomeEntity extends EntityAbstract {
                                        private int id;
                                        private List<String> listofStrings;
                                }
                                """
                )
        );
    }
}
