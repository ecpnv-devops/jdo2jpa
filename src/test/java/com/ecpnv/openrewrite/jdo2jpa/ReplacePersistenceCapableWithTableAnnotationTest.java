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
 * ReplacePersistenceCapableWithTableAnnotationTest is a test class that verifies the functionality
 * of the ReplacePersistenceCapableWithTableAnnotation recipe. This recipe is used to replace
 * the `@javax.jdo.annotations.PersistenceCapable` annotation in Java classes with the
 * corresponding `@javax.persistence.Table` annotation to ensure compatibility with JPA.
 * <p>
 * The test validates the following:
 * - The identification of classes annotated with `@PersistenceCapable`.
 * - The addition of the `@Table` annotation, including the transfer of properties like `schema`.
 * - Proper handling of imports when transforming the annotations.
 * <p>
 * This class implements RewriteTest, which provides the framework for specifying and running
 * rewrite tests. The test is designed to ensure the accurate and consistent application of
 * the recipe.
 *
 * @author Patrick Deenen @ Open Circle Solutions
 */
class ReplacePersistenceCapableWithTableAnnotationTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipe(new ReplacePersistenceCapableWithTableAnnotation());
    }

    /**
     * `replaceWithTableAnnotation()`: Tests the transformation of a Java class annotated with
     * `@PersistenceCapable` into a class annotated with both `@PersistenceCapable` and `@Table`,
     * ensuring schema information is correctly applied.
     */
    @DocumentExample
    @Test
    void replaceWithTableAnnotation() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.PersistenceCapable;
                                
                                @PersistenceCapable(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private List<String> listofStrings;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.Table;
                                
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private List<String> listofStrings;
                                }
                                """
                )
        );
    }

    /**
     * A version of {@link #replaceWithTableAnnotation} where the schema attribute is defined by a static value.
     */
    @DocumentExample
    @Test
    void replaceWithTableAnnotationForStatic() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.jdo.annotations.PersistenceCapable;
                                
                                @PersistenceCapable(schema = SomeEntity.SCHEMA, table = SomeEntity.TABLE)
                                public class SomeEntity {
                                        private static final String SCHEMA = "schemaName";
                                        private static final String TABLE = "tableName";
                                        private int id;
                                        private List<String> listofStrings;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.Table;
                                
                                @Table(schema = SomeEntity.SCHEMA, name = SomeEntity.TABLE)
                                public class SomeEntity {
                                        private static final String SCHEMA = "schemaName";
                                        private static final String TABLE = "tableName";
                                        private int id;
                                        private List<String> listofStrings;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void addEntityReplaceWithTableAnnotationForDatastore() {
        rewriteRun(spec -> spec.recipeFromResources(
                        "com.ecpnv.openrewrite.jdo2jpa.v2x.PersistenceCapable",
                        "com.ecpnv.openrewrite.jdo2jpa.v2x.cleanup"),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.IdentityType;
                                
                                @PersistenceCapable(schema = "schemaName", identityType = IdentityType.DATASTORE)
                                public class SomeEntity {
                                }
                                """,
                        """
                                import javax.persistence.Entity;
                                import javax.persistence.Table;
                                
                                import org.estatio.base.prod.dom.EntityAbstract;
                                
                                @Entity
                                @Table(schema = "schemaName")
                                public class SomeEntity extends EntityAbstract {
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void addEntityReplaceWithTableAnnotationForApplication() {
        rewriteRun(spec -> spec.recipeFromResources(
                        "com.ecpnv.openrewrite.jdo2jpa.v2x.PersistenceCapable",
                        "com.ecpnv.openrewrite.jdo2jpa.v2x.cleanup"),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.IdentityType;
                                
                                @PersistenceCapable(schema = "schemaName", identityType = IdentityType.APPLICATION)
                                public class SomeEntity {
                                }
                                """,
                        """
                                import javax.persistence.Entity;
                                import javax.persistence.Table;
                                
                                @Entity
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                }
                                """
                )
        );
    }
}
