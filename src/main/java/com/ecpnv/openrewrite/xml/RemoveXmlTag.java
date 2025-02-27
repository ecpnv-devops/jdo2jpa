package com.ecpnv.openrewrite.xml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.RemoveContentVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A recipe that removes specific XML tags from files. This class allows for targeting
 * XML tags that match a specified regular expression. An optional file matcher can
 * be provided to restrict modification to specific files, specified through a glob
 * pattern.
 * <p>
 * Class behavior:
 * - Searches for XML tags in files.
 * - Removes tags that match the provided regular expression.
 * - Optionally limits the scope to specific files if a file matcher is provided.
 * <p>
 * NOTE: This is copy of org.openrewrite.xml.RemoveXmlTag, but uses regular expression matching
 * instead of org.openrewrite.xml.XPathMatcher. This implementation of XPathMatcher can not
 * handle XML namespaces, hence this class can.
 *
 * @author the original Open Write authors.
 * @author Patrick Deenen @ Open Circle Solutions
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class RemoveXmlTag extends Recipe {

    @Option(displayName = "Regular expression to match",
            description = "Only xml tags that match the regular expression will be changed.",
            example = "action(.|\\s|\\n)*id(.|\\s|\\n)*someId(.|\\s|\\n)*")
    String matchByRegularExpression;

    @Option(displayName = "File matcher",
            description = "If provided only matching files will be modified. This is a glob expression.",
            required = false,
            example = "'**/application-*.xml'")
    @Nullable
    String fileMatcher;

    @JsonCreator
    public RemoveXmlTag(
            @JsonProperty("matchByRegularExpression") String matchByRegularExpression,
            @Nullable @JsonProperty("fileMatcher") String fileMatcher) {
        this.matchByRegularExpression = matchByRegularExpression;
        this.fileMatcher = fileMatcher;
    }

    @Override
    public String getDisplayName() {
        return "Remove XML tag";
    }

    @Override
    public String getDescription() {
        return "Removes XML tags matching the provided expression.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(fileMatcher), new XmlIsoVisitor<>() {

            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                if (tag.toString().matches(matchByRegularExpression)) {
                    doAfterVisit(new RemoveContentVisitor<>(tag, true, true));
                }
                return super.visitTag(tag, ctx);
            }
        });
    }
}