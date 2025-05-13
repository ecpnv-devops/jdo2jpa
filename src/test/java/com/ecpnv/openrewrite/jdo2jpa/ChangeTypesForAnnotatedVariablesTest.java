package com.ecpnv.openrewrite.jdo2jpa;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;

import static org.openrewrite.java.Assertions.java;

class ChangeTypesForAnnotatedVariablesTest extends BaseRewriteTest {

    @DocumentExample
    @Test
    void happyPath() {
        rewriteRun(spec -> spec.parser(PARSER).recipes(new ChangeTypesForAnnotatedVariables(
                        "javax.persistence.OneToMany",
                        "java.util.SortedSet,java.util.TreeSet,java.util.Collections.unmodifiableSortedSet",
                        "java.util.Set,java.util.HashSet,java.util.Collections.unmodifiableSet",
                        false)),
                //language=java
                java(
                        """
                                package a;
                                
                                import java.util.HashSet;
                                import java.util.Set;
                                import java.util.SortedSet;
                                import java.util.TreeSet;
                                import java.util.Collections;
                                
                                import javax.persistence.CascadeType;
                                import javax.persistence.OneToMany;
                                
                                public class SomeClass {
                                    @OneToMany(mappedBy = "name", cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    private SortedSet<String> someSet = new TreeSet<>();
                                
                                    @OneToMany(mappedBy = "type", cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    private Set<String> someTypeSet = new HashSet<>();
                                
                                    private SortedSet<String> someOtherSet = new TreeSet<>();
                                
                                    public SortedSet<String> getSomeSet() {
                                        return Collections.unmodifiableSortedSet(someSet);
                                    }
                                
                                    public SortedSet<String> getSomeOtherSet() {
                                        return Collections.unmodifiableSortedSet(someOtherSet);
                                    }
                                }
                                """,
                        """
                                package a;
                                
                                import java.util.HashSet;
                                import java.util.Set;
                                import java.util.SortedSet;
                                import java.util.TreeSet;
                                import java.util.Collections;
                                
                                import javax.persistence.CascadeType;
                                import javax.persistence.OneToMany;
                                
                                public class SomeClass {
                                    @OneToMany(mappedBy = "name", cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    private Set<String> someSet = new HashSet<>();
                                
                                    @OneToMany(mappedBy = "type", cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH}, fetch = FetchType.LAZY)
                                    private Set<String> someTypeSet = new HashSet<>();
                                
                                    private SortedSet<String> someOtherSet = new TreeSet<>();
                                
                                    public Set<String> getSomeSet() {
                                        return Collections.unmodifiableSet(someSet);
                                    }
                                
                                    public SortedSet<String> getSomeOtherSet() {
                                        return Collections.unmodifiableSortedSet(someOtherSet);
                                    }
                                }
                                """
                )
        );
    }
}