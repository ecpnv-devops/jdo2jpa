package com.ecpnv.openrewrite.java;

/** A migration failure, not evidence that a parent has no listeners. */
public final class UnresolvedEntityHierarchyException extends RuntimeException {
    private final String entityName;
    private final String parentName;
    private final String recipeName;
    private final String remediation;

    public UnresolvedEntityHierarchyException(String entityName, String parentName, String recipeName) {
        super("Cannot resolve " + parentName + " while processing " + entityName + " in " + recipeName
                + ". Supply the current module's compiled parent and transitive annotation/listener dependencies "
                + "in its resolved application dependency classpath; regenerate and install parent modules first.");
        this.entityName = entityName;
        this.parentName = parentName;
        this.recipeName = recipeName;
        this.remediation = "Supply the current module's resolved application dependency classpath and install migrated parents first.";
    }

    public String getEntityName() {
        return entityName;
    }

    public String getParentName() {
        return parentName;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public String getRemediation() {
        return remediation;
    }
}
