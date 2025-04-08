package com.ecpnv.openrewrite.java;

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

import javax.annotation.Nullable;

import org.apache.commons.collections4.CollectionUtils;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeException;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.NoMissingTypes;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.style.ImportLayoutStyle;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Loop;
import org.openrewrite.java.tree.MethodCall;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import static org.openrewrite.java.style.ImportLayoutStyle.isPackageAlwaysFolded;
import static org.openrewrite.java.tree.TypeUtils.fullyQualifiedNamesAreEqual;
import static org.openrewrite.java.tree.TypeUtils.toFullyQualifiedName;

import static com.ecpnv.openrewrite.util.RewriteUtils.createImport;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A recipe that removes unused imports to a certain extent.
 * Based on {@link org.openrewrite.java.RemoveUnusedImports} and added deep scanning of usage of classes in annotations and methods.
 * <p>
 * This recipe will remove any imports for types that are not referenced within the compilation unit. This recipe
 * is aware of the import layout style and will correctly handle unfolding of wildcard imports if the import counts
 * drop below the configured values.
 * <p>
 * Because unit testing requires full qualified names testing using simple class name in annotation imports cannot be fully
 * tested.
 *
 * @author Wouter Veltmaat @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class RemovedUnusedImports extends Recipe {

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
        return Preconditions.check(new NoMissingTypes(), new RemovedUnusedImports.RemoveUnusedImportsVisitor());
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
                if (javaType instanceof JavaType.Parameterized parameterized) {
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
                } else if (javaType instanceof JavaType.FullyQualified fullyQualified) {
                    typesByPackage.computeIfAbsent(
                            fullyQualified.getOwningClass() == null ?
                                    fullyQualified.getPackageName() :
                                    toFullyQualifiedName(fullyQualified.getOwningClass().getFullyQualifiedName()),
                            f -> new HashSet<>()).add(fullyQualified);
                }
            }

            boolean changed = false;

            // the key is a list because a star import may get replaced with multiple unfolded imports
            List<RemovedUnusedImports.ImportUsage> importUsage = new ArrayList<>(cu.getPadding().getImports().size());
            for (JRightPadded<J.Import> anImport : cu.getPadding().getImports()) {
                // assume initially that all imports are unused
                RemovedUnusedImports.ImportUsage singleUsage = new RemovedUnusedImports.ImportUsage();
                singleUsage.imports.add(anImport);
                importUsage.add(singleUsage);
            }

            // Collects all annotation imports and translates them to full name qualifiers using the imports collection
            final Set<String> annotationImports = new HashSet<>();
            final Set<J.Annotation> annotations = findAllAnnotations(cu);

            if (CollectionUtils.isNotEmpty(annotations)) {
                for (J.Annotation annotation : annotations) {
                    if (CollectionUtils.isNotEmpty(annotation.getArguments())) {
                        for (Expression expression : annotation.getArguments()) {
                            if (expression instanceof J.NewArray newArray) {
                                for (Expression initializer : newArray.getInitializer()) {
                                    String className = null;
                                    if (initializer instanceof J.FieldAccess fieldAccess) {
                                        className = fieldAccess.getTarget().print(getCursor());
                                    } else if (initializer instanceof J.Literal literal) {
                                        className = literal.getValueSource();
                                    }
                                    if (className != null) {
                                        final String member = JavaType.ShallowClass.build(className).getClassName();
                                        final String packageName = JavaType.ShallowClass.build(className).getPackageName().isEmpty() ? getPackageName(importUsage, member) : JavaType.ShallowClass.build(className).getPackageName();
                                        if (!packageName.isEmpty()) {
                                            try {
                                                final J.Import importToAdd = createImport(packageName + "." + member);
                                                annotationImports.add(importToAdd.toString());
                                            } catch (Exception e) {
                                                throw new RecipeException("Cannot create import for class: {}", className, e);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // whenever an import statement is found to be used and not already in use it should be marked true
            Set<String> checkedImports = new HashSet<>();
            Set<String> usedWildcardImports = new HashSet<>();
            Set<String> usedStaticWildcardImports = new HashSet<>();
            for (RemovedUnusedImports.ImportUsage anImport : importUsage) {
                J.Import elem = anImport.imports.get(0).getElement();
                J.FieldAccess qualid = elem.getQualid();
                J.Identifier name = qualid.getName();

                if (checkedImports.contains(elem.toString())) {
                    anImport.used = false;
                    changed = true;
                } else if (annotationImports.contains(elem.toString())) {
                    anImport.used = true;
                } else if (elem.isStatic()) {
                    String outerType = elem.getTypeName();
                    SortedSet<String> methodsAndFields = methodsAndFieldsByTypeName.get(outerType);

                    // some class names are not handled properly by `getTypeName()`
                    // see https://github.com/openrewrite/rewrite/issues/1698 for more detail
                    String target = qualid.getTarget().toString();
                    String modifiedTarget = methodsAndFieldsByTypeName.keySet().stream()
                            .filter(fqn -> fullyQualifiedNamesAreEqual(target, fqn))
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

                    // look into methods and check if a qualifier is used
                    if (cu.getClasses().stream().anyMatch(classDeclaration -> scanStatement(classDeclaration, qualid.getSimpleName()))) {
                        anImport.used = true;
                    } else if (combinedTypes.isEmpty() || sourcePackage.equals(elem.getPackageName()) && qualidType != null && !qualidType.getFullyQualifiedName().contains("$")) {
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
            for (RemovedUnusedImports.ImportUsage anImport : importUsage) {
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
                for (RemovedUnusedImports.ImportUsage anImportGroup : importUsage) {
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

        private static Set<J.Annotation> findAllAnnotations(J.CompilationUnit cu) {
            Set<J.Annotation> annotations = new HashSet<>();
            cu.getClasses().stream()
                    .map(cl -> cl.getLeadingAnnotations())
                    .flatMap(List::stream)
                    .forEach(annotations::add);
            cu.getTypesInUse().getTypesInUse().stream().forEach(type -> {
                if (type instanceof JavaType.Class aClass && aClass.getKind().equals(JavaType.FullyQualified.Kind.Annotation)) {
                    FindAnnotations.find(cu, aClass.getFullyQualifiedName()).forEach(annotations::add);
                }
            });
            return annotations;
        }

        public static boolean scanStatements(List<Statement> statements, String simpleName) {
            if (CollectionUtils.isEmpty(statements)) {
                return false;
            }
            return statements.stream().anyMatch(statement -> scanStatement(statement, simpleName));
        }

        public static boolean scanStatement(@Nullable Statement statement, String simpleName) {
            if (statement == null || StringUtils.isBlank(simpleName)) {
                return false;
            }

            switch (statement) {
                case Expression expression -> {
                    return scanExpression(expression, simpleName);
                }
                case J.ClassDeclaration classDeclaration -> {
                    return scanAnnotations(classDeclaration.getLeadingAnnotations(), simpleName) ||
                            scanStatement(classDeclaration.getBody(), simpleName);
                }
                case J.Block jBlock -> {
                    return scanStatements(jBlock.getStatements(), simpleName);
                }
                case J.Case aCase -> {
                    return scanStatements(aCase.getStatements(), simpleName);
                }
                case J.If ifStatement -> {
                    return scanExpression(ifStatement.getIfCondition(), simpleName) ||
                            scanElse(ifStatement.getElsePart(), simpleName) ||
                            scanThen(ifStatement.getThenPart(), simpleName);
                }
                case J.Switch switchStatement -> {
                    return scanStatements(switchStatement.getCases().getStatements(), simpleName) ||
                            scanExpression(switchStatement.getSelector(), simpleName);
                }
                case J.Label label -> {
                    return label.getLabel().getSimpleName().equals(simpleName) ||
                            scanStatement(label.getStatement(), simpleName);
                }
                case J.Synchronized synchronizedStatement -> {
                    return scanStatement(synchronizedStatement.getBody(), simpleName);
                }
                case Loop loop -> {
                    return scanStatement(loop.getBody(), simpleName);
                }
                case J.Try jTry -> {
                    return scanStatement(jTry.getBody(), simpleName) ||
                            scanCatches(jTry.getCatches(), simpleName) ||
                            scanFinally(jTry.getFinally(), simpleName);
                }
                case J.Throw aThrow -> {
                    return scanExpression(aThrow.getException(), simpleName);
                }
                case J.Return returnStatement when returnStatement.getExpression() instanceof J.MethodInvocation methodInvocation -> {
                    return scanStatement(methodInvocation, simpleName);
                }
                case J.VariableDeclarations variableDeclarations -> {
                    return scanTypeTree(variableDeclarations.getTypeExpression(), simpleName) ||
                            variableDeclarations.getVariables().stream()
                                    .anyMatch(namedVariable -> scanExpression(namedVariable.getInitializer(), simpleName));
                }
                case J.MethodDeclaration methodDeclaration -> {
                    return scanStatements(methodDeclaration.getParameters(), simpleName) ||
                            scanStatement(methodDeclaration.getBody(), simpleName);
                }
                default -> {
                    return false;
                }
            }
        }

        private static boolean scanExpressions(@Nullable List<Expression> initializer, String simpleName) {
            if (CollectionUtils.isEmpty(initializer)) {
                return false;
            }
            return initializer.stream().anyMatch(expression -> scanExpression(expression, simpleName));
        }

        private static boolean scanExpression(@Nullable Expression expression, String simpleName) {
            if (expression == null || StringUtils.isBlank(simpleName)) {
                return false;
            }
            switch (expression) {
                case TypeTree typeTree -> {
                    return scanTypeTree(typeTree, simpleName);
                }
                case J.NewArray newArray -> {
                    if (newArray.getType() instanceof JavaType.Array arrayType &&
                            arrayType.getElemType() instanceof JavaType.Class classType) {
                        return simpleName.equals(classType.getClassName()) ||
                                scanExpressions(newArray.getInitializer(), simpleName);

                    }
                    return scanExpressions(newArray.getInitializer(), simpleName);
                }
                case J.Assignment assignment -> {
                    return scanExpression(assignment.getVariable(), simpleName) ||
                            scanExpression(assignment.getAssignment(), simpleName);
                }
                case J.Unary unary -> {
                    return scanExpression(unary.getExpression(), simpleName);
                }
                case J.Binary binary -> {
                    return scanExpression(binary.getRight(), simpleName) ||
                            scanExpression(binary.getLeft(), simpleName);
                }
                case J.Ternary ternary -> {
                    return scanExpression(ternary.getCondition(), simpleName) ||
                            scanExpression(ternary.getTruePart(), simpleName) ||
                            scanExpression(ternary.getFalsePart(), simpleName);
                }
                case J.Annotation annotation -> {
                    return scanAnnotation(annotation, simpleName);
                }
                case J.Lambda lambda when lambda.getBody() instanceof J.Block jBlock -> {
                    return scanStatement(jBlock, simpleName);
                }
                case J.Literal literal -> {
                    return !StringUtils.isBlank(literal.getValueSource()) && literal.getValueSource().equals(simpleName);
                }
                case J.MethodInvocation invocation -> {
                    return scanExpression(invocation.getSelect(), simpleName) ||
                            scanArguments(invocation.getArguments(), simpleName);
                }
                case MethodCall methodCall -> {
                    return scanArguments(methodCall.getArguments(), simpleName);
                }
                case J.AssignmentOperation assignmentOperation -> {
                    return scanExpression(assignmentOperation.getVariable(), simpleName) ||
                            scanExpression(assignmentOperation.getAssignment(), simpleName);
                }
                default -> {
                    return false;
                }
            }
        }

        private static boolean scanTypeTree(@Nullable TypeTree typeExpression, String simpleName) {
            if (typeExpression == null) {
                return false;
            }

            switch (typeExpression) {
                case J.Identifier identifier -> {
                    return identifier.getSimpleName().equals(simpleName) ||
                            scanAnnotations(identifier.getAnnotations(), simpleName);
                }
                case J.ArrayType arrayType -> {
                    return scanAnnotations(arrayType.getAnnotations(), simpleName) ||
                            scanTypeTree(arrayType.getElementType(), simpleName);
                }
                case J.FieldAccess fieldAccess -> {
                    return simpleName.equals(fieldAccess.getSimpleName()) ||
                            scanExpression(fieldAccess.getTarget(), simpleName);
                }
                case J.AnnotatedType annotatedType -> {
                    return scanAnnotations(annotatedType.getAnnotations(), simpleName);
                }
                case J.ParameterizedType parameterizedType -> {
                    if (CollectionUtils.isEmpty(parameterizedType.getTypeParameters())) {
                        return false;
                    }
                    return scanTypeParameters(parameterizedType.getTypeParameters(), simpleName);
                }
                default -> {
                    return false;
                }
            }
        }

        private static boolean scanThen(Statement thenPart, String simpleName) {
            return scanStatement(thenPart, simpleName);
        }

        private static boolean scanElse(@Nullable J.If.Else elsePart, String simpleName) {
            if (elsePart == null) {
                return false;
            }
            return scanStatement(elsePart.getBody(), simpleName);
        }

        private static boolean scanFinally(@Nullable J.Block aFinally, String simpleName) {
            if (aFinally == null || CollectionUtils.isEmpty(aFinally.getStatements())) {
                return false;
            }
            return scanStatements(aFinally.getStatements(), simpleName);
        }

        // It seems that scanning inside annotation of classes is somehow limited not to recognize everything
        private static boolean scanAnnotations(@Nullable List<J.Annotation> annotations, String simpleName) {
            if (CollectionUtils.isEmpty(annotations)) {
                return false;
            }
            return annotations.stream().anyMatch(annotation -> scanAnnotation(annotation, simpleName));
        }

        private static boolean scanAnnotation(J.Annotation annotation, String simpleName) {
            if (annotation.getType() instanceof JavaType.Class aClass) {
                return simpleName.equals(aClass.getClassName()) || scanArguments(annotation.getArguments(), simpleName);
            }
            return scanArguments(annotation.getArguments(), simpleName);
        }

        private static boolean scanTypeParameters(@Nullable List<Expression> typeParameters, String simpleName) {
            if (typeParameters == null || typeParameters.isEmpty()) {
                return false;
            }
            return typeParameters.stream().anyMatch(typeParameter -> scanExpression(typeParameter, simpleName));
        }

        private static boolean scanArguments(@Nullable List<Expression> arguments, String simpleName) {
            if (CollectionUtils.isEmpty(arguments)) {
                return false;
            }
            return arguments.stream().anyMatch(argument -> scanExpression(argument, simpleName));
        }

        private static boolean scanCatches(List<J.Try.Catch> catches, String simpleName) {
            return catches.stream().anyMatch(aCatch -> scanCatch(aCatch, simpleName));
        }

        private static boolean scanCatch(J.Try.Catch aCatch, String simpleName) {
            return scanStatement(aCatch.getBody(), simpleName);
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
