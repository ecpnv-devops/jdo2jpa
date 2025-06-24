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
class AddAnnotationConditionallyTest extends BaseRewriteTest {

    public static final String MATCH_COLUMN_JDBC = "@Column\\(.*jdbcType\\s*=\\s*\"CLOB\".*\\)";
    public static final String LOB_TYPE = "javax.persistence.Lob";
    public static final String LOB = "@Lob";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(PARSER).recipe(new AddAnnotationConditionally(MATCH_COLUMN_JDBC, LOB_TYPE, LOB, AddAnnotationConditionally.DeclarationType.VAR));
    }

    /**
     * Adds a `@Lob` annotation to fields annotated with `@Column` where the `@Column` annotation
     * specifies a `jdbcType` of "CLOB". This transformation also ensures that the necessary
     * `javax.persistence.Lob` import is added to the class if not already present.
     */
    @DocumentExample
    @Test
    void addLobForVar() {
        rewriteRun(//language=java
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                
                                @Column(jdbcType = "CLOB")
                                public class SomeEntity {
                                    private int id;
                                    @Column(allowsNull = "true", jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                    private String notes;
                                    @Column(allowsNull = "true", sqlType = "LONGVARCHAR", jdbcType = "CLOB")
                                    public String getHelp(){ return "Help";}
                                }
                                """,
                        """
                                import javax.jdo.annotations.Column;
                                import javax.persistence.Lob;
                                
                                @Column(jdbcType = "CLOB")
                                public class SomeEntity {
                                    private int id;
                                    @Column(allowsNull = "true", jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                    @Lob
                                    private String notes;
                                    @Column(allowsNull = "true", sqlType = "LONGVARCHAR", jdbcType = "CLOB")
                                    public String getHelp(){ return "Help";}
                                }
                                """
                )
        );
    }

    /**
     * Tests the functionality of the `AddAnnotationConditionally` recipe by verifying its behavior
     * when adding the `@Lob` annotation to a class declaration. The operation is tested under the
     * condition that the class already contains an annotation that matches the specified criteria.
     */
    @DocumentExample
    @Test
    void addLobForClass() {
        rewriteRun(r -> r.recipe(new AddAnnotationConditionally(MATCH_COLUMN_JDBC, LOB_TYPE, LOB, AddAnnotationConditionally.DeclarationType.CLASS)),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                
                                @Column(jdbcType = "CLOB")
                                public class SomeEntity {
                                    private int id;
                                    @Column(allowsNull = "true", jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                    private String notes;
                                    @Column(allowsNull = "true", sqlType = "LONGVARCHAR", jdbcType = "CLOB")
                                    public String getHelp(){ return "Help";}
                                }
                                """,
                        """
                                import javax.jdo.annotations.Column;
                                import javax.persistence.Lob;
                                
                                @Column(jdbcType = "CLOB")
                                @Lob
                                public class SomeEntity {
                                    private int id;
                                    @Column(allowsNull = "true", jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                    private String notes;
                                    @Column(allowsNull = "true", sqlType = "LONGVARCHAR", jdbcType = "CLOB")
                                    public String getHelp(){ return "Help";}
                                }
                                """
                )
        );
    }

    /**
     * Verifies the functionality of adding an `@Lob` annotation to methods based on
     * conditions defined in the `AddAnnotationConditionally` recipe.
     */
    @DocumentExample
    @Test
    void addLobForMethod() {
        rewriteRun(r -> r.recipe(new AddAnnotationConditionally(MATCH_COLUMN_JDBC, LOB_TYPE, LOB, AddAnnotationConditionally.DeclarationType.METHOD)),
                //language=java
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                
                                @Column(jdbcType = "CLOB")
                                public class SomeEntity {
                                    private int id;
                                    @Column(allowsNull = "true", jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                    private String notes;
                                    @Column(allowsNull = "true", sqlType = "LONGVARCHAR", jdbcType = "CLOB")
                                    public String getHelp(){ return "Help";}
                                }
                                """,
                        """
                                import javax.jdo.annotations.Column;
                                import javax.persistence.Lob;
                                
                                @Column(jdbcType = "CLOB")
                                public class SomeEntity {
                                    private int id;
                                    @Column(allowsNull = "true", jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                    private String notes;
                                
                                    @Column(allowsNull = "true", sqlType = "LONGVARCHAR", jdbcType = "CLOB")
                                    @Lob
                                    public String getHelp() { return "Help";}
                                }
                                """
                )
        );
    }

    /**
     * Verifies the functionality of modifying Java classes by transforming annotations related to
     * JDO to their corresponding JPA annotations using the `Column` recipe.
     * <p>
     * This test specifically checks the conversion of fields and methods annotated with
     * `@javax.jdo.annotations.Column` to have the appropriate `@javax.persistence.Column` and
     * optionally `@javax.persistence.Lob` annotations based on the original annotation attributes.
     * <p>
     * Key behaviors validated:
     * - Fields or methods with `@Column` having `jdbcType` set to "CLOB" are updated to include the `@Lob` annotation.
     * - The `@Column` annotation itself is subjected to changes such as adapting attributes like `allowsNull`
     * to `nullable`.
     * - Ensures proper handling of edge cases where `Column` attributes may not require significant changes.
     * - Verifies that required imports (e.g., `javax.persistence.Column`, `javax.persistence.Lob`) are correctly added.
     * - Confirms that unnecessary attributes from the original annotation are excluded in the transformed result.
     * <p>
     * The test uses the Rewrite framework to run the recipe and assert the transformation results by comparing
     * the input Java code with the expected transformed output.
     */
    @DocumentExample
    @Test
    void testColumnComplete() {
        rewriteRun(r -> r.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Column"),
                //language=java
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                
                                public class SomeEntity {
                                    private int id;
                                    @Column(allowsNull = "true", jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                    private String notes;
                                    @Column(allowsNull = "false", sqlType = "LONGVARCHAR", jdbcType = "CLOB")
                                    public String getHelp(){ return "Help";}
                                    @Column(name = "name")
                                    private String name;
                                }
                                """,
                        """
                                import javax.persistence.Column;
                                import javax.persistence.Lob;
                                
                                public class SomeEntity {
                                    private int id;
                                    @Column
                                    @Lob
                                    private String notes;
                                
                                    @Column(nullable = false)
                                    @Lob
                                    public String getHelp() { return "Help";}
                                    @Column(name = "name")
                                    private String name;
                                }
                                """
                )
        );
    }

    /**
     * Tests the functionality of the `AddAnnotationConditionally` recipe by verifying whether
     * a `@Lob` annotation is correctly added to annotation declarations. This transformation
     * operates under the condition that the original declaration includes a `@javax.jdo.annotations.Column`
     * annotation with specific attributes that meet the criteria for modification.
     */
    @DocumentExample
    @Test
    void isLobAddedToAnnotationDeclaration() {
        rewriteRun(r -> r.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.Column"),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                
                                @Column(length = Notes.MAX_LEN, allowsNull = "true", jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                public @interface Notes {
                                    int MAX_LEN = 4000;
                                }
                                """,
                        """
                                import javax.persistence.Column;
                                import javax.persistence.Lob;
                                
                                @Column(length = Notes.MAX_LEN)
                                @Lob
                                public @interface Notes {
                                    int MAX_LEN = 4000;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void noChangeWhenTargetAlreadyExist() {
        rewriteRun(r -> r.recipe(new AddAnnotationConditionally(MATCH_COLUMN_JDBC, LOB_TYPE, LOB, AddAnnotationConditionally.DeclarationType.CLASS)),
                //language=java
                java(
                        """
                                import javax.persistence.Column;
                                import javax.persistence.Lob;
                                
                                @Column(jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                @Lob
                                public @interface Notes {
                                    int MAX_LEN = 4000;
                                }
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void addNoArgsConstructorForce() {
        rewriteRun(r -> r.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.post.causeway"),
                //language=java
                java(
                        """
                                import javax.inject.Named;
                                import javax.persistence.Entity;
                                
                                @Entity
                                @Named(PermitForIndex.LOGICAL_TYPE_NAMED)
                                public class SomeClass {}
                                """,
                        """
                                import lombok.NoArgsConstructor;
                                
                                import javax.inject.Named;
                                import javax.persistence.Entity;
                                
                                @Entity
                                @Named(PermitForIndex.LOGICAL_TYPE_NAMED)
                                @NoArgsConstructor(force = true)
                                public class SomeClass {}
                                """
                )
        );
    }
}
