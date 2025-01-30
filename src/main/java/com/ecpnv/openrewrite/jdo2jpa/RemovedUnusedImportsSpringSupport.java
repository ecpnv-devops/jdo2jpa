package com.ecpnv.openrewrite.jdo2jpa;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.NoMissingTypes;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.style.ImportLayoutStyle;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.style.ImportLayoutStyle.isPackageAlwaysFolded;
import static org.openrewrite.java.tree.TypeUtils.fullyQualifiedNamesAreEqual;
import static org.openrewrite.java.tree.TypeUtils.toFullyQualifiedName;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * This recipe will remove any imports for types that are not referenced within the compilation unit. This recipe
 * is aware of the import layout style and will correctly handle unfolding of wildcard imports if the import counts
 * drop below the configured values.
 *
 * Because unit testing requires full qualified names testing using simple class name in spring imports cannot be fully
 * tested.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class RemovedUnusedImportsSpringSupport extends Recipe {

    public static final String SPRING_IMPORT_ANNOTATION = "org.springframework.context.annotation.Import";
    public static final String CLASS = ".class";
    public static final String COMMENT_SINGLE_LINE = "//";

    @Override
    public String getDisplayName() {
        return "Remove unused imports";
    }

    @Override
    public String getDescription() {
        return "Remove imports for types that are not referenced. As a precaution against incorrect changes no imports " +
                "will be removed from any source where unknown types are referenced. The most common cause of unknown " +
                "types is the use of annotation processors not supported by OpenRewrite, such as lombok.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S1128");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new NoMissingTypes(), new RemovedUnusedImportsSpringSupport.RemoveUnusedImportsVisitor());
    }

    public static class RemoveUnusedImportsVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
            ImportLayoutStyle layoutStyle = Optional.ofNullable(cu.getStyle(ImportLayoutStyle.class))
                    .orElse(IntelliJ.importLayout());
            String sourcePackage = cu.getPackageDeclaration() == null ? "" :
                    cu.getPackageDeclaration().getExpression().printTrimmed(getCursor()).replaceAll("\\s", "");
            Map<String, TreeSet<String>> methodsAndFieldsByTypeName = new HashMap<>();
            Map<String, Set<JavaType.FullyQualified>> typesByPackage = new HashMap<>();

            for (JavaType.Method method : cu.getTypesInUse().getUsedMethods()) {
                if (method.hasFlags(Flag.Static)) {
                    methodsAndFieldsByTypeName.computeIfAbsent(method.getDeclaringType().getFullyQualifiedName(), t -> new TreeSet<>())
                            .add(method.getName());
                }
            }

            for (JavaType.Variable variable : cu.getTypesInUse().getVariables()) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(variable.getOwner());
                if (fq != null) {
                    methodsAndFieldsByTypeName.computeIfAbsent(fq.getFullyQualifiedName(), f -> new TreeSet<>())
                            .add(variable.getName());
                }
            }

            for (JavaType javaType : cu.getTypesInUse().getTypesInUse()) {
                if (javaType instanceof JavaType.Parameterized) {
                    JavaType.Parameterized parameterized = (JavaType.Parameterized) javaType;
                    typesByPackage.computeIfAbsent(parameterized.getType().getPackageName(), f -> new HashSet<>())
                            .add(parameterized.getType());
                    for (JavaType typeParameter : parameterized.getTypeParameters()) {
                        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(typeParameter);
                        if (fq != null) {
                            typesByPackage.computeIfAbsent(
                                    fq.getOwningClass() == null ?
                                            fq.getPackageName() :
                                            toFullyQualifiedName(fq.getOwningClass().getFullyQualifiedName()),
                                    f -> new HashSet<>()).add(fq);
                        }
                    }
                } else if (javaType instanceof JavaType.FullyQualified) {
                    JavaType.FullyQualified fq = (JavaType.FullyQualified) javaType;
                    typesByPackage.computeIfAbsent(
                            fq.getOwningClass() == null ?
                                    fq.getPackageName() :
                                    toFullyQualifiedName(fq.getOwningClass().getFullyQualifiedName()),
                            f -> new HashSet<>()).add(fq);
                }
            }

            boolean changed = false;

            // the key is a list because a star import may get replaced with multiple unfolded imports
            List<RemovedUnusedImportsSpringSupport.ImportUsage> importUsage = new ArrayList<>(cu.getPadding().getImports().size());
            for (JRightPadded<J.Import> anImport : cu.getPadding().getImports()) {
                // assume initially that all imports are unused
                RemovedUnusedImportsSpringSupport.ImportUsage singleUsage = new RemovedUnusedImportsSpringSupport.ImportUsage();
                singleUsage.imports.add(anImport);
                importUsage.add(singleUsage);
            }

            // Collects all spring imports and translates them to full name qualifiers using the imports collection
            final Set<String> springImports = new HashSet<>();
            final Set<J.Annotation> annotations = FindAnnotations.find(cu, SPRING_IMPORT_ANNOTATION);
            if (!annotations.isEmpty()) {
                for (J.Annotation annotation : annotations) {
                    for (Expression expression : annotation.getArguments()) {
                        if (expression instanceof J.NewArray newArray) {
                            for (Expression initializer : newArray.getInitializer()) {
                                String className;
                                if (initializer instanceof J.FieldAccess fieldAccess) {
                                    className = fieldAccess.getTarget().print();
                                } else {
                                    className = stripClass(initializer.print());
                                }
                                final String member = JavaType.ShallowClass.build(className).getClassName();
                                final String packageName = JavaType.ShallowClass.build(className).getPackageName().isEmpty() ? getPackageName(importUsage, member) : JavaType.ShallowClass.build(className).getPackageName();
                                if (!packageName.isEmpty()) {
                                    try {
                                        final J.Import importToAdd = new J.Import(randomId(),
                                                Space.EMPTY,
                                                Markers.EMPTY,
                                                new JLeftPadded<>(Space.SINGLE_SPACE, Boolean.FALSE, Markers.EMPTY),
                                                TypeTree.build(packageName + "." + member).withPrefix(Space.SINGLE_SPACE),
                                                null);

                                        springImports.add(importToAdd.toString());
                                    } catch (Exception e) {
                                        // keep calm and move on
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /**
             * For enums there are several ways you can use imported classes.
             * For now processing that and make a list of used imports it too much work.
             * So removal of imports for enum classes is skipped.
             */
            // skill removal of imports for all enums
            boolean isEnum = cu.getClasses().stream()
                    .anyMatch(aClass -> aClass.getType().getKind().equals(JavaType.FullyQualified.Kind.Enum));

            // whenever an import statement is found to be used and not already in use it should be marked true
            Set<String> checkedImports = new HashSet<>();
            Set<String> usedWildcardImports = new HashSet<>();
            Set<String> usedStaticWildcardImports = new HashSet<>();
            for (RemovedUnusedImportsSpringSupport.ImportUsage anImport : importUsage) {
                J.Import elem = anImport.imports.get(0).getElement();
                J.FieldAccess qualid = elem.getQualid();
                J.Identifier name = qualid.getName();

                if (checkedImports.contains(elem.toString())) {
                    anImport.used = false;
                    changed = true;
                } else if (springImports.contains(elem.toString()) || isEnum) {
                    anImport.used = true;
                    changed = false;
                } else if (elem.isStatic()) {
                    String outerType = elem.getTypeName();
                    SortedSet<String> methodsAndFields = methodsAndFieldsByTypeName.get(outerType);

                    // some class names are not handled properly by `getTypeName()`
                    // see https://github.com/openrewrite/rewrite/issues/1698 for more detail
                    String target = qualid.getTarget().toString();
                    String modifiedTarget = methodsAndFieldsByTypeName.keySet().stream()
                            .filter((fqn) -> fullyQualifiedNamesAreEqual(target, fqn))
                            .findFirst()
                            .orElse(target);
                    SortedSet<String> targetMethodsAndFields = methodsAndFieldsByTypeName.get(modifiedTarget);

                    Set<JavaType.FullyQualified> staticClasses = null;
                    for (JavaType.FullyQualified maybeStatic : typesByPackage.getOrDefault(target, emptySet())) {
                        if (maybeStatic.getOwningClass() != null && outerType.startsWith(maybeStatic.getOwningClass().getFullyQualifiedName())) {
                            if (staticClasses == null) {
                                staticClasses = new HashSet<>();
                            }
                            staticClasses.add(maybeStatic);
                        }
                    }

                    if (methodsAndFields == null && targetMethodsAndFields == null && staticClasses == null) {
                        anImport.used = false;
                        changed = true;
                    } else if ("*".equals(qualid.getSimpleName())) {
                        if (isPackageAlwaysFolded(layoutStyle.getPackagesToFold(), elem)) {
                            anImport.used = true;
                            usedStaticWildcardImports.add(elem.getTypeName());
                        } else if (((methodsAndFields == null ? 0 : methodsAndFields.size()) +
                                (staticClasses == null ? 0 : staticClasses.size())) < layoutStyle.getNameCountToUseStarImport()) {
                            // replacing the star with a series of unfolded imports
                            anImport.imports.clear();

                            // add each unfolded import
                            if (methodsAndFields != null) {
                                for (String method : methodsAndFields) {
                                    anImport.imports.add(new JRightPadded<>(elem
                                            .withQualid(qualid.withName(name.withSimpleName(method)))
                                            .withPrefix(Space.format("\n")), Space.EMPTY, Markers.EMPTY));
                                }
                            }

                            if (staticClasses != null) {
                                for (JavaType.FullyQualified fqn : staticClasses) {
                                    anImport.imports.add(new JRightPadded<>(elem
                                            .withQualid(qualid.withName(name.withSimpleName(fqn.getClassName().contains(".") ? fqn.getClassName().substring(fqn.getClassName().lastIndexOf(".") + 1) : fqn.getClassName())))
                                            .withPrefix(Space.format("\n")), Space.EMPTY, Markers.EMPTY));
                                }
                            }

                            // move whatever the original prefix of the star import was to the first unfolded import
                            anImport.imports.set(0, anImport.imports.get(0).withElement(anImport.imports.get(0)
                                    .getElement().withPrefix(elem.getPrefix())));

                            changed = true;
                        } else {
                            usedStaticWildcardImports.add(elem.getTypeName());
                        }
                    } else if (staticClasses != null && staticClasses.stream().anyMatch(c -> elem.getTypeName().equals(c.getFullyQualifiedName())) ||
                            (methodsAndFields != null && methodsAndFields.contains(qualid.getSimpleName())) ||
                            (targetMethodsAndFields != null && targetMethodsAndFields.contains(qualid.getSimpleName()))) {
                        anImport.used = true;
                    } else {
                        anImport.used = false;
                        changed = true;
                    }
                } else {
                    String target = qualid.getTarget().toString();
                    Set<JavaType.FullyQualified> types = typesByPackage.getOrDefault(target, new HashSet<>());
                    Set<JavaType.FullyQualified> typesByFullyQualifiedClassPath = typesByPackage.getOrDefault(toFullyQualifiedName(target), new HashSet<>());
                    Set<JavaType.FullyQualified> combinedTypes = Stream.concat(types.stream(), typesByFullyQualifiedClassPath.stream())
                            .collect(Collectors.toSet());
                    JavaType.FullyQualified qualidType = TypeUtils.asFullyQualified(elem.getQualid().getType());
                    if (combinedTypes.isEmpty() || sourcePackage.equals(elem.getPackageName()) && qualidType != null && !qualidType.getFullyQualifiedName().contains("$")) {
                        anImport.used = false;
                        changed = true;
                    } else if ("*".equals(elem.getQualid().getSimpleName())) {
                        if (isPackageAlwaysFolded(layoutStyle.getPackagesToFold(), elem)) {
                            anImport.used = true;
                            usedWildcardImports.add(elem.getPackageName());
                        } else if (combinedTypes.size() < layoutStyle.getClassCountToUseStarImport()) {
                            // replacing the star with a series of unfolded imports
                            anImport.imports.clear();

                            // add each unfolded import
                            combinedTypes.stream().map(JavaType.FullyQualified::getClassName).sorted().distinct().forEach(type ->
                                    anImport.imports.add(new JRightPadded<>(elem
                                            .withQualid(qualid.withName(name.withSimpleName(type.substring(type.lastIndexOf('.') + 1))))
                                            .withPrefix(Space.format("\n")), Space.EMPTY, Markers.EMPTY))
                            );

                            // move whatever the original prefix of the star import was to the first unfolded import
                            anImport.imports.set(0, anImport.imports.get(0).withElement(anImport.imports.get(0)
                                    .getElement().withPrefix(elem.getPrefix())));

                            changed = true;
                        } else {
                            usedWildcardImports.add(target);
                        }
                    } else if (combinedTypes.stream().noneMatch(c -> {
                        if ("*".equals(elem.getQualid().getSimpleName())) {
                            return elem.getPackageName().equals(c.getPackageName());
                        }
                        return fullyQualifiedNamesAreEqual(c.getFullyQualifiedName(), elem.getTypeName());
                    })) {
                        anImport.used = false;
                        changed = true;
                    }
                }
                checkedImports.add(elem.toString());
            }

            // Do not use direct imports that are imported by a wildcard import
            Set<String> ambiguousStaticImportNames = getAmbiguousStaticImportNames(cu);
            for (RemovedUnusedImportsSpringSupport.ImportUsage anImport : importUsage) {
                J.Import elem = anImport.imports.get(0).getElement();
                if (!"*".equals(elem.getQualid().getSimpleName())) {
                    if (elem.isStatic()) {
                        if (usedStaticWildcardImports.contains(elem.getTypeName()) &&
                                !ambiguousStaticImportNames.contains(elem.getQualid().getSimpleName())) {
                            anImport.used = false;
                            changed = true;
                        }
                    } else {
                        if (usedWildcardImports.size() == 1 && usedWildcardImports.contains(elem.getPackageName()) && !elem.getTypeName().contains("$") && !conflictsWithJavaLang(elem)) {
                            anImport.used = false;
                            changed = true;
                        }
                    }
                }
            }

            if (changed) {
                List<JRightPadded<J.Import>> imports = new ArrayList<>();
                Space lastUnusedImportSpace = null;
                for (RemovedUnusedImportsSpringSupport.ImportUsage anImportGroup : importUsage) {
                    if (anImportGroup.used) {
                        List<JRightPadded<J.Import>> importGroup = anImportGroup.imports;
                        for (int i = 0; i < importGroup.size(); i++) {
                            JRightPadded<J.Import> anImport = importGroup.get(i);
                            if (i == 0 && lastUnusedImportSpace != null && anImport.getElement().getPrefix().getLastWhitespace()
                                    .chars().filter(c -> c == '\n').count() <= 1) {
                                anImport = anImport.withElement(anImport.getElement().withPrefix(lastUnusedImportSpace));
                            }
                            imports.add(anImport);
                        }
                        lastUnusedImportSpace = null;
                    } else if (lastUnusedImportSpace == null) {
                        lastUnusedImportSpace = anImportGroup.imports.get(0).getElement().getPrefix();
                    }
                }

                cu = cu.getPadding().withImports(imports);
                if (cu.getImports().isEmpty() && !cu.getClasses().isEmpty()) {
                    cu = autoFormat(cu, cu.getClasses().get(0).getName(), ctx, getCursor().getParentOrThrow());
                }
            }

            return cu;
        }

        public static String stripClass(final String string) {
            final String[] lines = string.split("\n");
            for (String line : lines) {
                String result = line.trim();
                if (result.contains(CLASS)) {
                    if (!result.contains(COMMENT_SINGLE_LINE) || result.indexOf(COMMENT_SINGLE_LINE) > result.indexOf(CLASS)) {
                        return result.substring(0, result.indexOf(CLASS));
                    }
                }
            }
            return string;
        }

        private static String getPackageName(List<ImportUsage> importUsage, String member) {
            for (ImportUsage anImport : importUsage) {
                J.Import elem = anImport.imports.get(0).getElement();
                if (member.equals(elem.getClassName())) {
                    return elem.getPackageName();
                }
            }
            return "";
        }

        private static Set<String> getAmbiguousStaticImportNames(J.CompilationUnit cu) {
            Set<String> typesWithWildcardImport = new HashSet<>();
            for (J.Import elem : cu.getImports()) {
                if ("*".equals(elem.getQualid().getSimpleName())) {
                    typesWithWildcardImport.add(elem.getTypeName());
                }
            }
            Set<JavaType.FullyQualified> qualifiedTypes = new HashSet<>();
            for (JavaType.Variable variable : cu.getTypesInUse().getVariables()) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(variable.getOwner());
                if (fq != null && typesWithWildcardImport.contains(fq.getFullyQualifiedName())) {
                    qualifiedTypes.add(fq);
                }
            }
            Set<String> seen = new HashSet<>();
            Set<String> ambiguous = new HashSet<>();
            for (JavaType.FullyQualified fq : qualifiedTypes) {
                for (JavaType.Variable member : fq.getMembers()) {
                    if (!seen.add(member.getName())) {
                        ambiguous.add(member.getName());
                    }
                }
            }
            return ambiguous;
        }

        private static final Set<String> JAVA_LANG_CLASS_NAMES = new HashSet<>(Arrays.asList(
                "AbstractMethodError",
                "Appendable",
                "ArithmeticException",
                "ArrayIndexOutOfBoundsException",
                "ArrayStoreException",
                "AssertionError",
                "AutoCloseable",
                "Boolean",
                "BootstrapMethodError",
                "Byte",
                "Character",
                "CharSequence",
                "Class",
                "ClassCastException",
                "ClassCircularityError",
                "ClassFormatError",
                "ClassLoader",
                "ClassNotFoundException",
                "ClassValue",
                "Cloneable",
                "CloneNotSupportedException",
                "Comparable",
                "Deprecated",
                "Double",
                "Enum",
                "EnumConstantNotPresentException",
                "Error",
                "Exception",
                "ExceptionInInitializerError",
                "Float",
                "FunctionalInterface",
                "IllegalAccessError",
                "IllegalAccessException",
                "IllegalArgumentException",
                "IllegalCallerException",
                "IllegalMonitorStateException",
                "IllegalStateException",
                "IllegalThreadStateException",
                "IncompatibleClassChangeError",
                "IndexOutOfBoundsException",
                "InheritableThreadLocal",
                "InstantiationError",
                "InstantiationException",
                "Integer",
                "InternalError",
                "InterruptedException",
                "Iterable",
                "LayerInstantiationException",
                "LinkageError",
                "Long",
                "MatchException",
                "Math",
                "Module",
                "ModuleLayer",
                "NegativeArraySizeException",
                "NoClassDefFoundError",
                "NoSuchFieldError",
                "NoSuchFieldException",
                "NoSuchMethodError",
                "NoSuchMethodException",
                "NullPointerException",
                "Number",
                "NumberFormatException",
                "Object",
                "OutOfMemoryError",
                "Override",
                "Package",
                "Process",
                "ProcessBuilder",
                "ProcessHandle",
                "Readable",
                "Record",
                "ReflectiveOperationException",
                "Runnable",
                "Runtime",
                "RuntimeException",
                "RuntimePermission",
                "SafeVarargs",
                "ScopedValue",
                "SecurityException",
                "SecurityManager",
                "Short",
                "StackOverflowError",
                "StackTraceElement",
                "StackWalker",
                "StrictMath",
                "String",
                "StringBuffer",
                "StringBuilder",
                "StringIndexOutOfBoundsException",
                "StringTemplate",
                "SuppressWarnings",
                "System",
                "Thread",
                "ThreadDeath",
                "ThreadGroup",
                "ThreadLocal",
                "Throwable",
                "TypeNotPresentException",
                "UnknownError",
                "UnsatisfiedLinkError",
                "UnsupportedClassVersionError",
                "UnsupportedOperationException",
                "VerifyError",
                "VirtualMachineError",
                "Void",
                "WrongThreadException"
        ));

        private static boolean conflictsWithJavaLang(J.Import elem) {
            return JAVA_LANG_CLASS_NAMES.contains(elem.getClassName());
        }
    }

    private static class ImportUsage {
        final List<JRightPadded<J.Import>> imports = new ArrayList<>();
        boolean used = true;
    }
}
