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

import com.ecpnv.openrewrite.java.MoveAnnotationsToAttribute;


/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
class IndexesTest extends BaseRewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Index");
    }

    @DocumentExample
    @Test
    void moveIndexConstraintAnnotation() {
        rewriteRun(spec -> spec.recipe(new MoveAnnotationsToAttribute(Constants.Jpa.INDEX_ANNOTATION_FULL,
                        Constants.Jpa.TABLE_ANNOTATION_FULL, Constants.Jpa.TABLE_ARGUMENT_INDEXES)),
                //language=java
                //language=java
                java(
                        """
                                import javax.persistence.Table;
                                import javax.persistence.Index;
                                
                                @Index(name = "SomeEntityNameIndex", columnList = {"name"})
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Table;
                                import javax.persistence.Index;
                                
                                
                                @javax.persistence.Table( schema = "schemaName", indexes = {@javax.persistence.Index( name = "SomeEntityNameIndex",
                                columnList = {"name"})})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void moveIndexConstraintAnnotationWithNoPreexistingTableAnnotation() {
        rewriteRun(spec -> spec.recipe(new MoveAnnotationsToAttribute(Constants.Jpa.INDEX_ANNOTATION_FULL,
                        Constants.Jpa.TABLE_ANNOTATION_FULL, Constants.Jpa.TABLE_ARGUMENT_INDEXES)),
                //language=java
                java(
                        """
                                import javax.persistence.Index;
                                
                                @Index(name = "SomeEntityNameIndex", columnList = {"name"})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Index;
                                import javax.persistence.Table;
                                
                                
                                
                                @javax.persistence.Table(indexes = {@javax.persistence.Index(name = "SomeEntityNameIndex",
                                        columnList = {"name"})})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void moveMultipleIndexConstraintAnnotation() {
        rewriteRun(spec -> spec.recipe(new MoveAnnotationsToAttribute(Constants.Jpa.INDEX_ANNOTATION_FULL,
                        Constants.Jpa.TABLE_ANNOTATION_FULL, Constants.Jpa.TABLE_ARGUMENT_INDEXES)),
                //language=java
                //language=java
                java(
                        """
                                import javax.persistence.Table;
                                import javax.persistence.Index;
                                
                                @Index(name = "SomeEntityIdIndex", columnList = {"id"})
                                @Index(name = "SomeEntityNameIndex", columnList = {"name"})
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Table;
                                import javax.persistence.Index;
                                
                                
                                @javax.persistence.Table( schema = "schemaName", indexes = {@javax.persistence.Index( name = "SomeEntityIdIndex",
                                columnList = {"id"}), @javax.persistence.Index( name = "SomeEntityNameIndex",
                                columnList = {"name"})})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void moveIndexAnnotation() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import javax.persistence.Table;
                                import javax.jdo.annotations.Index;
                                
                                @Index(name = "SomeEntityNameIndex", members = {"name"}, table = "table", 
                                unique = "false", columns = {"col1", "col2"}, extensions = "")
                                @Table(schema = "schemaName")
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """,
                        """
                                import javax.persistence.Index;
                                import javax.persistence.Table;
                                
                                
                                @javax.persistence.Table( schema = "schemaName", indexes = {@javax.persistence.Index( name = "SomeEntityNameIndex",
                                columnList = "name")})
                                public class SomeEntity {
                                        private int id;
                                        private String name;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void moveIndexAnnotationFromIndexes() {
        rewriteRun(
                spec -> spec.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x"),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.PersistenceCapable;
                                import javax.jdo.annotations.Index;
                                
                                @PersistenceCapable(schema = "schemaName")
                                @Index(name = "Person__name__IDX", members = {"firstName", "lastName"})
                                @Index(name = "Person__email__IDX", members = {"email"})
                                public class SomeEntity {
                                        private int id;
                                        private String firstName, lastName, email;
                                }
                                """,
                        """
                                import javax.persistence.Entity;
                                import javax.persistence.Index;
                                import javax.persistence.Table;
                                
                                @Entity
                                @Table( schema = "schemaName", indexes = {@Index( name = "Person__name__IDX",
                                columnList = "firstName, lastName"), @Index( name = "Person__email__IDX",
                                columnList = "email")})
                                public class SomeEntity {
                                        private int id;
                                        private String firstName, lastName, email;
                                }
                                """
                )
        );
    }
}
