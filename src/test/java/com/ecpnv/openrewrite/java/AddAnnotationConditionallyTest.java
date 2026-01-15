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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.tree.J;
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
        spec.parser(PARSER).recipe(new AddAnnotationConditionally(MATCH_COLUMN_JDBC, null, LOB_TYPE, LOB,
                AddAnnotationConditionally.DeclarationType.VAR, null, null, null, null));
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
        rewriteRun(r -> r.recipe(new AddAnnotationConditionally(MATCH_COLUMN_JDBC, null, LOB_TYPE,
                        LOB, AddAnnotationConditionally.DeclarationType.CLASS, null, null, null, null)),
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

    @DocumentExample
    @Test
    void noAddForAbstractClass() {
        rewriteRun(r -> r.recipe(new AddAnnotationConditionally(MATCH_COLUMN_JDBC, null, LOB_TYPE, LOB,
                        AddAnnotationConditionally.DeclarationType.CLASS, J.Modifier.Type.Abstract, null, null, null)),
                //language=java
                java(
                        """
                                import javax.jdo.annotations.Column;
                                
                                @Column(jdbcType = "CLOB")
                                public abstract class SomeEntity {}
                                """
                )
        );
    }

    @DocumentExample
    @Test
    void noAddForAbstractClassCauseway() {
        rewriteRun(r -> r.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.causeway"),
                //language=java
                java(
                        """
                                import javax.persistence.Entity;
                                
                                @Entity
                                public abstract class SomeEntity {}
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
        rewriteRun(r -> r.recipe(new AddAnnotationConditionally(MATCH_COLUMN_JDBC, null, LOB_TYPE,
                        LOB, AddAnnotationConditionally.DeclarationType.METHOD, null, null, null, null)),
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
                java(
                        """
                                import javax.jdo.annotations.Column;
                                import org.joda.time.DateTime;
                                import org.joda.time.LocalDate;
                                
                                public class SomeEntity {
                                    private int id;
                                    @Column(allowsNull = "true", jdbcType = "CLOB", sqlType = "LONGVARCHAR")
                                    private String notes;
                                    @Column(allowsNull = "false", sqlType = "LONGVARCHAR", jdbcType = "CLOB")
                                    public String getHelp(){ return "Help";}
                                    @Column(name = "name")
                                    private String name;
                                    @Column(name = "dateTime")
                                    private DateTime dateTime;
                                    @Column(name = "dateTimeMandatory", allowsNull = "false")
                                    private DateTime dateTimeMandatory;
                                    @Column(name = "date")
                                    private LocalDate date;
                                    @Column(name = "dateMandatory", allowsNull = "false")
                                    private LocalDate dateMandatory;
                                }
                                """,
                        """
                                import javax.persistence.Column;
                                import javax.persistence.Lob;
                                
                                import org.joda.time.DateTime;
                                import org.joda.time.LocalDate;
                                
                                public class SomeEntity {
                                    private int id;
                                    @Column(columnDefinition = "VARCHAR(MAX)")
                                    @Lob
                                    private String notes;
                                
                                    @Column(columnDefinition = "VARCHAR(MAX) NOT NULL", nullable = false)
                                    @Lob
                                    public String getHelp() { return "Help";}
                                    @Column(name = "name")
                                    private String name;
                                    @Column(columnDefinition = "dateTime2", name = "dateTime")
                                    private DateTime dateTime;
                                    @Column(columnDefinition = "dateTime2 NOT NULL", name = "dateTimeMandatory", nullable = false)
                                    private DateTime dateTimeMandatory;
                                    @Column(columnDefinition = "date", name = "date")
                                    private LocalDate date;
                                    @Column(columnDefinition = "date NOT NULL", name = "dateMandatory", nullable = false)
                                    private LocalDate dateMandatory;
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
                                
                                @Column(columnDefinition = "VARCHAR(MAX)", length = Notes.MAX_LEN)
                                @Lob
                                public @interface Notes {
                                    int MAX_LEN = 4000;
                                }
                                """
                )
        );
    }

    /**
     * Verifies that no modifications are made when the target annotation (`@Lob` in this case)
     * already exists on a class, ensuring that the `AddAnnotationConditionally` recipe does not add
     * duplicate annotations or introduce unnecessary changes.
     * <p>
     * This test evaluates the behavior of the recipe when applied to an annotated class where
     * one of its existing annotations already fulfills the transformation's target condition.
     * <p>
     * Key behaviors validated:
     * - Ensures that no additional `@Lob` annotation is added if it already exists on the class.
     * - Confirms that the recipe does not alter unrelated annotations or class structure.
     * - Validates that no unnecessary imports are added or removed when the transformation
     * criteria are already satisfied.
     */
    @DocumentExample
    @Test
    void noChangeWhenTargetAlreadyExist() {
        rewriteRun(r -> r.recipe(new AddAnnotationConditionally(MATCH_COLUMN_JDBC, null, LOB_TYPE,
                        LOB, AddAnnotationConditionally.DeclarationType.CLASS, null, null, null, null)),
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

    /**
     * Tests the functionality of adding a `@NoArgsConstructor(force = true)` annotation
     * to a class when using the specified rewrite recipe.
     * <p>
     * The test validates the transformation of a class definition by ensuring that
     * the `@NoArgsConstructor(force = true)` annotation is added to the class.
     * This operation is tested in the context of a class already annotated with
     * `@Entity` and additional annotations such as `@Named`.
     * <p>
     * Key behaviors validated:
     * - Ensures that previously existing annotations, such as `@Entity` and `@Named`, are preserved.
     * - Adds the `@NoArgsConstructor` annotation with the `force = true` attribute when absent.
     * - Validates that necessary imports (e.g., `lombok.NoArgsConstructor`) are added properly.
     * - Confirms the final output matches the expected transformed Java code.
     */
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

    /**
     * Tests the functionality of a recipe that updates Java classes to include appropriate annotations
     * for compatibility with Causeway framework's JPA integration, specifically adding the
     * `@EntityListeners(org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener.class)` annotation
     * where required.
     * <p>
     * The test validates the transformation of a code example featuring inheritance with `@Entity` classes.
     * Key behaviors include:
     * - Ensuring that the `@EntityListeners` annotation is added to classes directly annotated
     * with `@Entity` and not abstract.
     * - Retaining the existing `@Entity` annotations without modification.
     * - Preserving the hierarchical structure and existing fields of the class.
     * - Verifying that only relevant imports are added to the classes influenced by the transformation.
     */
    @DocumentExample
    @Test
    void addAnnotationsForCauseway() {
        rewriteRun(s -> s.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.causeway"),
                //language=java
                java(
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                
                                @Entity
                                public abstract class Base {
                                        private int id;
                                }
                                
                                @Entity
                                public class Person extends Base {
                                        private String name;
                                }
                                
                                @Entity
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """,
                        """
                                import java.util.List;
                                import javax.persistence.Entity;
                                import javax.persistence.EntityListeners;
                                
                                @Entity
                                public abstract class Base {
                                        private int id;
                                }
                                
                                @Entity
                                @EntityListeners(org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener.class)
                                public class Person extends Base {
                                        private String name;
                                }
                                
                                @Entity
                                public class Manager extends Person {
                                        private List<Person> managedPersons;
                                }
                                """
                )
        );
    }

    @Nested
    class testWithKind {

        /**
         * Tests the functionality of adding an annotation to an enum field conditionally.
         * <p>
         * This method verifies that the `@Enumerated(EnumType.STRING)` annotation is
         * added to an enum field.
         * <p>
         * Features validated:
         * - Ensures that the `@Enumerated(EnumType.STRING)` annotation is applied on the specified enum field.
         * - Confirms the desired modification in the source code through a defined rewrite process.
         */
        @DocumentExample
        @Test
        void addToEnumField() {
            rewriteRun(r -> r.recipeFromResources("com.ecpnv.openrewrite.jdo2jpa.v2x.optional"),
                    //language=java
                    java(
                            """
                                    import javax.persistence.Entity;
                                    import javax.persistence.Transient;
                                    @Entity
                                    public class SomeClass {
                                        private System.Logger.Level someEnum;
                                        @Transient
                                        private System.Logger.Level someEnumA;
                                        public void someMethod(){
                                            boolean useNumerator = "reference" == null;
                                            System.Logger.Level someLocalEnum;
                                        }
                                    }
                                    """,
                            """
                                    import javax.persistence.Entity;
                                    import javax.persistence.Enumerated;
                                    import javax.persistence.Transient;
                                    
                                    @Entity
                                    public class SomeClass {
                                        @Enumerated(javax.persistence.EnumType.STRING)
                                        private System.Logger.Level someEnum;
                                        @Transient
                                        private System.Logger.Level someEnumA;
                                        public void someMethod(){
                                            boolean useNumerator = "reference" == null;
                                            System.Logger.Level someLocalEnum;
                                        }
                                    }
                                    """
                    )
            );
        }

        /**
         * Tests the functionality of conditionally adding an annotation to an enum field.
         * <p>
         * This method ensures that the `@Enumerated(EnumType.STRING)` annotation is added to
         * a specified enum field only when certain conditions are met, as defined by the recipe.
         * <p>
         * Features validated:
         * - Verifies that the `@Enumerated(EnumType.STRING)` annotation is applied to the proper field.
         * - Ensures that fields not meeting the specified condition remain unmodified.
         * - Confirms accurate modifications in the source code through a defined rewrite process.
         */
        @DocumentExample
        @Test
        void addToEnumFieldConditionally() {
            rewriteRun(r -> r.recipe(new AddAnnotationConditionally(
                            "@Column\\(name = \"someEnum\"\\)", null, "javax.persistence.Enumerated",
                            "@Enumerated(EnumType.STRING)", AddAnnotationConditionally.DeclarationType.VAR,
                            null, null, J.ClassDeclaration.Kind.Type.Enum, null)),
                    //language=java
                    java(
                            """
                                    import javax.persistence.Column;
                                    public class SomeClass {
                                        @Column(name = "someEnum")
                                        private System.Logger.Level someEnum;
                                        private System.Logger.Level someOtherEnum;
                                    }
                                    """,
                            """
                                    import javax.persistence.Column;
                                    import javax.persistence.Enumerated;
                                    
                                    public class SomeClass {
                                        @Column(name = "someEnum")
                                        @Enumerated(EnumType.STRING)
                                        private System.Logger.Level someEnum;
                                        private System.Logger.Level someOtherEnum;
                                    }
                                    """
                    )
            );
        }

        /**
         * Tests the functionality of adding an annotation to a method that returns an enum type.
         * <p>
         * This method verifies that the `@Enumerated(EnumType.STRING)` annotation is correctly added
         * to methods with a return type of an enum, based on specified conditions.
         * <p>
         * Features validated:
         * - Ensures that the `@Enumerated(EnumType.STRING)` annotation is applied correctly
         * to methods returning an enum.
         * - Confirms that methods returning non-enum types remain unmodified.
         * - Validates correct source code transformation through a defined rewrite process.
         */
        @DocumentExample
        @Test
        void addToEnumMethod() {
            rewriteRun(r -> r.recipe(new AddAnnotationConditionally(
                            null, null, "javax.persistence.Enumerated",
                            "@Enumerated(EnumType.STRING)", AddAnnotationConditionally.DeclarationType.METHOD,
                            null, null, J.ClassDeclaration.Kind.Type.Enum, null)),
                    //language=java
                    java(
                            """
                                    public class SomeClass {
                                        public System.Logger.Level someMethod(){
                                            return System.Logger.Level.INFO;
                                        }
                                    }
                                    """,
                            """
                                    import javax.persistence.Enumerated;
                                    
                                    public class SomeClass {
                                        @Enumerated(EnumType.STRING)
                                        public System.Logger.Level someMethod() {
                                            return System.Logger.Level.INFO;
                                        }
                                    }
                                    """
                    )
            );
        }

        /**
         * Verifies that no annotation is added to a method with a void return type.
         * <p>
         * This method ensures that the recipe does not apply the `@Enumerated(EnumType.STRING)`
         * annotation to methods that have a void return type. It validates that the source
         * code remains unmodified under these conditions.
         * <p>
         * Features validated:
         * - Ensures that void methods are unaffected by the annotation modification recipe.
         * - Confirms that no unintended annotation addition occurs for methods with incompatible return types.
         * - Validates the source code integrity through the rewrite process.
         */
        @DocumentExample
        @Test
        void noAddToVoidMethod() {
            rewriteRun(r -> r.recipe(new AddAnnotationConditionally(
                            null, null, "javax.persistence.Enumerated",
                            "@Enumerated(EnumType.STRING)", AddAnnotationConditionally.DeclarationType.METHOD,
                            null, null, J.ClassDeclaration.Kind.Type.Enum, null)),
                    //language=java
                    java(
                            """
                                    public class SomeClass {
                                        public void someMethod(){
                                            // do things
                                        }
                                    }
                                    """
                    )
            );
        }

        /**
         * Tests the functionality of adding an annotation to an enum class.
         * <p>
         * This method ensures that the `@Entity` annotation is added
         * to an enum class as specified by the recipe.
         * <p>
         * Features validated:
         * - Verifies that the `@Entity` annotation is applied to the specified enum class.
         * - Ensures accurate source code transformation through a defined rewrite process.
         * - Confirms that only the target enum class is modified while maintaining
         * the correctness of the remaining source code.
         */
        @DocumentExample
        @Test
        void addToEnumClass() {
            rewriteRun(r -> r.recipe(new AddAnnotationConditionally(
                            null, null, "javax.persistence.Entity",
                            "@Entity", AddAnnotationConditionally.DeclarationType.CLASS,
                            null, null, J.ClassDeclaration.Kind.Type.Enum, null)),
                    //language=java
                    java(
                            """
                                    public enum SomeEnum {
                                        V1,V2;
                                    }
                                    """,
                            """
                                    import javax.persistence.Entity;
                                    
                                    @Entity
                                    public enum SomeEnum {
                                        V1,V2;
                                    }
                                    """
                    )
            );
        }

        @DocumentExample
        @Test
        void addToPrimitivesForVarOnly() {
            rewriteRun(r -> r.recipe(new AddAnnotationConditionally(
                            null, null, "javax.persistence.Column",
                            "@Column", AddAnnotationConditionally.DeclarationType.VAR,
                            null, null, null, true)),
                    //language=java
                    java(
                            """
                                    public class SomeClass {
                                        int x;
                                        Integer y;
                                        public int intMethod(){
                                            // do things
                                        }
                                        public Integer someMethod(){
                                            // do things
                                        }
                                    }
                                    """,
                            """
                                    import javax.persistence.Column;
                                    
                                    public class SomeClass {
                                        @Column
                                        int x;
                                        Integer y;
                                        public int intMethod(){
                                            // do things
                                        }
                                        public Integer someMethod(){
                                            // do things
                                        }
                                    }
                                    """
                    )
            );
        }

        @DocumentExample
        @Test
        void addToPrimitivesForMethodOnly() {
            rewriteRun(r -> r.recipe(new AddAnnotationConditionally(
                            null, null, "javax.persistence.Column",
                            "@Column", AddAnnotationConditionally.DeclarationType.METHOD,
                            null, null, null, true)),
                    //language=java
                    java(
                            """
                                    public class SomeClass {
                                        int x;
                                        Integer y;
                                        public int intMethod(){
                                            // do things
                                        }
                                        public Integer someMethod(){
                                            // do things
                                        }
                                    }
                                    """,
                            """
                                    import javax.persistence.Column;
                                    
                                    public class SomeClass {
                                        int x;
                                        Integer y;
                                    
                                        @Column
                                        public int intMethod() {
                                            // do things
                                        }
                                        public Integer someMethod(){
                                            // do things
                                        }
                                    }
                                    """
                    )
            );
        }

    }
}
