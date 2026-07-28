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
import org.openrewrite.test.RecipeSpec;

import static org.openrewrite.java.Assertions.java;

import com.ecpnv.openrewrite.jdo2jpa.BaseRewriteTest;
import com.ecpnv.openrewrite.jdo2jpa.Constants;

/**
 * @author Patrick Deenen @ Open Circle Solutions
 */
class CopyAnnotationAttributeFromSubclassToParentClassTest {

    @Nested
    class CopyToBaseClassOnly extends BaseRewriteTest {

        @Override
        public void defaults(RecipeSpec spec) {
            spec
                    .parser(PARSER)
                    .recipe(new CopyAnnotationAttributeFromSubclassToParentClass(Constants.Jdo.INHERITANCE_ANNOTATION_FULL,
                            Constants.Jdo.INHERITANCE_ARGUMENT_STRATEGY, null, true));
        }

        /**
         * This test method verifies the correct behavior of a recipe that copies annotation attributes
         * from a subclass to its parent class within Java code.
         * <p>
         * The rewrite process is applied to the provided source code snippet, where a specific
         * annotation attribute (`strategy`) in the `@Inheritance` annotation exists in both
         * the subclass and the parent class but with differing values. The test ensures that
         * the annotation attribute from the subclass is correctly copied to the parent class,
         * resulting in uniformity between the two classes.
         */
        @DocumentExample
        @Test
        void copyAnnotationAttributeFromSubclassToParentClass() {
            rewriteRun(//language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """,
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """
                    )
            );
        }

        /**
         * This test method verifies that no modifications are made to the provided Java code
         * during the rewrite process. The method ensures that the source code remains
         * unaltered even when the rewrite functionality is applied.
         * <p>
         * The test uses a Java code snippet containing multiple classes with the `@Inheritance`
         * annotation. Specifically, the annotation attributes or structure are not subject to any changes,
         * and the input code remains identical to the output after the rewrite operation.
         */
        @DocumentExample
        @Test
        void noCopy() {
            rewriteRun(//language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """
                    )
            );
        }

        /**
         * This test method verifies the correct behavior of a recipe that copies annotation attributes
         * from a subclass to its parent class within Java code.
         * <p>
         * The rewrite process is applied to the provided source code snippet, where a specific
         * annotation attribute (`strategy`) in the `@Inheritance` annotation exists in both
         * the subclass and the parent class but with differing values. The test ensures that
         * the annotation attribute from the subclass is correctly copied to the parent class,
         * ONLY when the parent class doesn't have a parent class with the @Inheritance annotation.
         * Hence, @Inheritance#strategy on the Manager class is not changed.
         */
        @DocumentExample
        @Test
        void onlyCopyToBaseClass() {
            rewriteRun(//language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUBCLASS_TABLE)
                                    public class Director extends Manager {}
                                    """,
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUBCLASS_TABLE)
                                    public class Director extends Manager {}
                                    """
                    )
            );
        }

        /**
         * When several subclasses declare <i>different</i> values for the attribute there is no single value
         * that can be copied safely (JPA supports only one strategy per hierarchy). The recipe must not guess:
         * it leaves the code unchanged apart from a {@link org.openrewrite.marker.SearchResult} marker that
         * flags the base class for manual resolution.
         */
        @Test
        void conflictingSubclassesAreFlaggedAndNotChanged() {
            rewriteRun(//language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    @Inheritance(strategy = InheritanceStrategy.SUBCLASS_TABLE)
                                    public class Employee extends Person {}
                                    """,
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    /*~~(Conflicting values for attribute 'strategy' on subclasses [InheritanceStrategy.SUBCLASS_TABLE, InheritanceStrategy.SUPERCLASS_TABLE]; JPA supports only one strategy per inheritance hierarchy - resolve manually.)~~>*/@Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    @Inheritance(strategy = InheritanceStrategy.SUBCLASS_TABLE)
                                    public class Employee extends Person {}
                                    """
                    )
            );
        }
    }

    @Nested
    class CopyAll extends BaseRewriteTest {

        @Override
        public void defaults(RecipeSpec spec) {
            spec
                    .parser(PARSER)
                    .recipe(new CopyAnnotationAttributeFromSubclassToParentClass(Constants.Jdo.INHERITANCE_ANNOTATION_FULL,
                            Constants.Jdo.INHERITANCE_ARGUMENT_STRATEGY, null, false));
        }

        /**
         * This test method verifies the correct behavior of a recipe that copies annotation attributes
         * from a subclass to its parent class within Java code.
         * <p>
         * The rewrite process is applied to the provided source code snippet, where a specific
         * annotation attribute (`strategy`) in the `@Inheritance` annotation exists in both
         * the subclass and the parent class but with differing values. The test ensures that
         * the annotation attribute from the subclass is correctly copied to the parent class,
         * resulting in uniformity between the two classes.
         */
        @DocumentExample
        @Test
        void copyAnnotationAttributeFromSubclassToParentClass() {
            rewriteRun(//language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """,
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """
                    )
            );
        }

        /**
         * This test method verifies that no modifications are made to the provided Java code
         * during the rewrite process. The method ensures that the source code remains
         * unaltered even when the rewrite functionality is applied.
         * <p>
         * The test uses a Java code snippet containing multiple classes with the `@Inheritance`
         * annotation. Specifically, the annotation attributes or structure are not subject to any changes,
         * and the input code remains identical to the output after the rewrite operation.
         */
        @DocumentExample
        @Test
        void noCopy() {
            rewriteRun(//language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    """
                    )
            );
        }

        /**
         * This test method verifies the correct behavior of a recipe that copies annotation attributes
         * from a subclass to its parent class within Java code.
         * <p>
         * The rewrite process is applied to the provided source code snippet, where a specific
         * annotation attribute (`strategy`) in the `@Inheritance` annotation exists in both
         * the subclass and the parent class but with differing values. The test ensures that
         * the annotation attribute from the subclass is correctly copied to the parent class.
         * In this configuration the annotation attribute 'strategy' is copied to every parent.
         * Hence, @Inheritance#strategy on the Manager class IS changed.
         */
        @DocumentExample
        @Test
        void copyAll() {
            rewriteRun(//language=java
                    java(
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUBCLASS_TABLE)
                                    public class Director extends Manager {}
                                    """,
                            """
                                    import java.util.List;
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Person {
                                            private int id;
                                            private String name;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUBCLASS_TABLE)
                                    public class Manager extends Person {
                                            private List<Person> managedPersons;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUBCLASS_TABLE)
                                    public class Director extends Manager {}
                                    """
                    )
            );
        }

        /**
         * Other leading annotations on the parent (and their order) must be preserved when the attribute is
         * copied. Regression test for the previous implementation, which moved the changed annotation to the
         * end of the annotation list.
         */
        @Test
        void preservesOtherLeadingAnnotationsAndTheirOrder() {
            rewriteRun(//language=java
                    java(
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    import javax.jdo.annotations.PersistenceCapable;

                                    @PersistenceCapable
                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """,
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;
                                    import javax.jdo.annotations.PersistenceCapable;

                                    @PersistenceCapable
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """
                    )
            );
        }

        /**
         * When the parent annotation already has other attributes, the copied attribute must be replaced in
         * place, keeping the position and whitespace of the surrounding attributes.
         */
        @Test
        void replacesAttributeInPlaceKeepingOtherAttributes() {
            rewriteRun(//language=java
                    java(
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE, customStrategy = "foo")
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """,
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE, customStrategy = "foo")
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """
                    )
            );
        }

        /**
         * When the parent annotation has attributes but not the one being copied, it must be appended with
         * correct separator whitespace (comma + single space).
         */
        @Test
        void appendsAttributeWhenParentHasOtherAttribute() {
            rewriteRun(//language=java
                    java(
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(customStrategy = "foo")
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """,
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(customStrategy = "foo", strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """
                    )
            );
        }

        /**
         * A parent annotation with an empty argument list {@code @Inheritance()} must receive the copied
         * attribute as its sole argument, without a stray leading comma.
         */
        @Test
        void populatesEmptyArgumentList() {
            rewriteRun(//language=java
                    java(
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance()
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """,
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """
                    )
            );
        }

        /**
         * Parent and subclass in separate source files (the realistic case): the value must be copied without
         * reusing the subclass' LST node (which would duplicate its id within a single tree).
         */
        @Test
        void copiesAcrossSeparateSourceFiles() {
            rewriteRun(//language=java
                    java(
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                    }
                                    """,
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Person {
                                            private int id;
                                    }
                                    """
                    ),
                    //language=java
                    java(
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """
                    )
            );
        }
    }

    @Nested
    class RegexFilter extends BaseRewriteTest {

        @Override
        public void defaults(RecipeSpec spec) {
            spec.parser(PARSER);
        }

        /**
         * When the regular expression matches the (printed) parent annotation, the attribute is copied.
         */
        @Test
        void copiesWhenRegexMatchesParentAnnotation() {
            rewriteRun(
                    spec -> spec.recipe(new CopyAnnotationAttributeFromSubclassToParentClass(
                            Constants.Jdo.INHERITANCE_ANNOTATION_FULL, Constants.Jdo.INHERITANCE_ARGUMENT_STRATEGY,
                            "NEW_TABLE", false)),
                    //language=java
                    java(
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """,
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """
                    )
            );
        }

        /**
         * When the regular expression does not match the parent annotation, nothing is copied.
         */
        @Test
        void doesNotCopyWhenRegexDoesNotMatchParentAnnotation() {
            rewriteRun(
                    spec -> spec.recipe(new CopyAnnotationAttributeFromSubclassToParentClass(
                            Constants.Jdo.INHERITANCE_ANNOTATION_FULL, Constants.Jdo.INHERITANCE_ARGUMENT_STRATEGY,
                            "customStrategy", false)),
                    //language=java
                    java(
                            """
                                    import javax.jdo.annotations.Inheritance;
                                    import javax.jdo.annotations.InheritanceStrategy;

                                    @Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
                                    public class Person {
                                            private int id;
                                    }
                                    @Inheritance(strategy = InheritanceStrategy.SUPERCLASS_TABLE)
                                    public class Manager extends Person {}
                                    """
                    )
            );
        }
    }

    @Nested
    class Limitations extends BaseRewriteTest {

        @Override
        public void defaults(RecipeSpec spec) {
            spec.parser(PARSER)
                    .recipe(new CopyAnnotationAttributeFromSubclassToParentClass("Marker", "value", null, false));
        }

        /**
         * The single-element {@code value} shorthand is not expressed as an assignment, so it is not copied.
         * This documents (and pins) the known limitation as a safe no-op rather than a silent surprise.
         */
        @Test
        void valueShorthandIsNotCopied() {
            rewriteRun(//language=java
                    java(
                            """
                                    import java.lang.annotation.Retention;
                                    import java.lang.annotation.RetentionPolicy;

                                    @Retention(RetentionPolicy.RUNTIME)
                                    @interface Marker {
                                            String value() default "";
                                    }

                                    @Marker
                                    class Base {
                                    }

                                    @Marker("child")
                                    class Child extends Base {
                                    }
                                    """
                    )
            );
        }
    }

}
