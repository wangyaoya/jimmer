package org.babyfish.jimmer.dto.compiler;

import org.babyfish.jimmer.dto.compiler.spi.BaseProp;
import org.babyfish.jimmer.dto.compiler.spi.BaseType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

class DtoPropImpl<T extends BaseType, P extends BaseProp> implements DtoProp<T, P> {

    private final Map<String, P> basePropMap;

    @Nullable
    private final DtoProp<T, P> nextProp;

    private final int baseLine;

    private final int baseCol;

    @Nullable
    private final String alias;

    @Nullable
    private final PropConfig<P> config;

    private final int aliasLine;

    private final int aliasCol;

    private final List<Anno> annotations;

    @Nullable
    private final String doc;

    private final DtoType<T, P> targetType;

    private final EnumType enumType;

    private final Mandatory mandatory;

    private final DtoModifier inputModifier;

    private final String funcName;

    private final boolean recursive;

    private final String basePath;

    private final Set<LikeOption> likeOptions;

    private final DtoProp<T, P> tail;

    DtoPropImpl(
            Map<String, P> basePropMap,
            int baseLine,
            int baseCol,
            @Nullable String alias,
            int aliasLine,
            int aliasCol,
            @Nullable PropConfig<P> config,
            List<Anno> annotations,
            @Nullable String doc,
            @Nullable DtoType<T, P> targetType,
            @Nullable EnumType enumType,
            Mandatory mandatory,
            DtoModifier inputModifier,
            String funcName,
            boolean recursive,
            Set<LikeOption> likeOptions
    ) {
        if (inputModifier == null || !inputModifier.isInputStrategy()) {
            throw new IllegalArgumentException("Illegal input strategy: " + inputModifier);
        }
        this.basePropMap = basePropMap;
        this.nextProp = null;
        this.baseLine = baseLine;
        this.baseCol = baseCol;
        this.annotations = annotations;
        this.doc = doc;
        this.alias = alias;
        this.aliasLine = aliasLine;
        this.aliasCol = aliasCol;
        this.config = config;
        this.targetType = targetType;
        this.enumType = enumType;
        this.mandatory = mandatory;
        this.inputModifier = inputModifier;
        this.funcName = funcName;
        this.recursive = recursive;
        if (basePropMap.size() == 1) {
            this.basePath = getBaseProp().getName();
        } else {
            this.basePath = '(' +
                    basePropMap
                            .values()
                            .stream()
                            .map(BaseProp::getName)
                            .collect(Collectors.joining("|")) +
                    ')';
        }
        this.likeOptions = Collections.unmodifiableSet(likeOptions);
        this.tail = this;
    }

    DtoPropImpl(DtoProp<T, P> head, DtoProp<T, P> next, AliasPattern aliasPattern) {
        this.basePropMap = head.getBasePropMap();
        this.nextProp = next;
        this.baseLine = next.getBaseLine();
        this.baseCol = next.getBaseColumn();
        this.alias = aliasPattern != null ?
                aliasPattern.alias(next.getName(), next.getAliasLine(), next.getAliasColumn()) :
                next.getAlias();
        this.aliasLine = next.getAliasLine();
        this.aliasCol = next.getAliasColumn();
        this.config = next.getConfig();
        this.annotations = next.getAnnotations();
        this.doc = next.getDoc();
        this.targetType = next.getTargetType();
        this.enumType = next.getEnumType();
        if (head.isNullable() && next.getMandatory() != Mandatory.REQUIRED) {
            this.mandatory = Mandatory.OPTIONAL;
        } else {
            this.mandatory = next.getMandatory();
        }
        this.inputModifier = next.getInputModifier();
        this.funcName = next.getFuncName();
        this.recursive = false;
        StringBuilder builder = new StringBuilder();
        if (basePropMap.size() == 1) {
            builder.append(basePropMap.values().iterator().next().getName());
        } else {
            builder
                    .append('(')
                    .append(basePropMap.values().stream().map(BaseProp::getName).collect(Collectors.joining(", ")))
                    .append(')');
        }
        DtoProp<T, P> tail = this;
        for (DtoProp<T, P> n = next; n != null; n = n.getNextProp()) {
            builder.append('.').append(n.getBasePath());
            tail = n;
        }
        this.basePath = builder.toString();
        this.likeOptions = Collections.emptySet();
        this.tail = tail;
    }

    DtoPropImpl(DtoProp<T, P> original, DtoType<T, P> targetType) {
        this.basePropMap = original.getBasePropMap();
        this.nextProp = null;
        this.baseLine = original.getBaseLine();
        this.baseCol = original.getBaseColumn();
        this.annotations = original.getAnnotations();
        this.doc = original.getDoc();
        this.alias = getBaseProp().getName();
        this.aliasLine = original.getAliasLine();
        this.aliasCol = original.getAliasColumn();
        this.config = original.getConfig();
        this.targetType = targetType;
        this.enumType = null;
        this.mandatory = original.getMandatory();
        this.inputModifier = original.getInputModifier();
        this.funcName = "flat";
        this.recursive = false;
        this.basePath = getBaseProp().getName();
        this.likeOptions = original.getLikeOptions();
        this.tail = this;
    }

    DtoPropImpl(DtoProp<T, P> original) {
        if (!original.isRecursive()) {
            throw new IllegalArgumentException("original property must be recursive");
        }
        this.basePropMap = original.getBasePropMap();
        this.nextProp = null;
        this.baseLine = original.getBaseLine();
        this.baseCol = original.getBaseColumn();
        this.annotations = original.getAnnotations();
        this.doc = original.getDoc();
        this.alias = getBaseProp().getName();
        this.aliasLine = original.getAliasLine();
        this.aliasCol = original.getAliasColumn();
        this.config = original.getConfig();
        if (original.getTargetType() == null) {
            this.targetType = null;
        } else {
            this.targetType = original.getTargetType().recursiveOne(original);
        }
        this.enumType = original.getEnumType();
        this.mandatory = original.getMandatory();
        this.inputModifier = original.getInputModifier();
        this.funcName = original.getFuncName();
        this.recursive = original.isRecursive();
        this.basePath = getBaseProp().getName();
        this.likeOptions = original.getLikeOptions();
        this.tail = this;
    }

    @Override
    public P getBaseProp() {
        return basePropMap.values().iterator().next();
    }

    @Override
    public Map<String, P> getBasePropMap() {
        return basePropMap;
    }

    @Override
    public String getBasePath() {
        return basePath;
    }

    @Nullable
    @Override
    public DtoProp<T, P> getNextProp() {
        return nextProp;
    }

    @Override
    public DtoProp<T, P> toTailProp() {
        return tail;
    }

    @Override
    public int getBaseLine() {
        return baseLine;
    }

    @Override
    public int getBaseColumn() {
        return baseCol;
    }

    @Override
    public List<Anno> getAnnotations() {
        return annotations;
    }

    public String getDoc() {
        return doc;
    }

    @Override
    public String getName() {
        return alias != null ? alias : getBaseProp().getName();
    }

    @Override
    public int getAliasLine() {
        return aliasLine;
    }

    @Override
    public int getAliasColumn() {
        return aliasCol;
    }

    @Override
    public boolean isNullable() {
        switch (mandatory) {
            case OPTIONAL:
                return true;
            case REQUIRED:
                return false;
            default:
                return isBaseNullable();
        }
    }

    @Override
    public boolean isBaseNullable() {
        for (DtoProp<T, P> p = this; p != null; p = p.getNextProp()) {
            if (p.getBaseProp().isNullable()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Mandatory getMandatory() {
        return mandatory;
    }

    @Override
    public DtoModifier getInputModifier() {
        return inputModifier;
    }

    @Override
    public boolean isIdOnly() {
        return "id".equals(funcName);
    }

    @Override
    public boolean isFlat() {
        return "flat".equals(funcName);
    }

    @Override
    public boolean isFunc(String ... funcNames) {
        if (funcName == null) {
            return false;
        }
        for (String fn : funcNames) {
            if (funcName.equals(fn)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public String getFuncName() {
        return funcName;
    }

    @Override
    @Nullable
    public String getAlias() {
        return alias;
    }

    @Nullable
    @Override
    public PropConfig<P> getConfig() {
        return config;
    }

    @Override
    @Nullable
    public DtoType<T, P> getTargetType() {
        return targetType;
    }

    @Override
    @Nullable
    public EnumType getEnumType() {
        return enumType;
    }

    @Override
    public boolean isRecursive() {
        return recursive;
    }

    @Override
    public Set<LikeOption> getLikeOptions() {
        return likeOptions;
    }

    @Override
    public String toString() {
        return toString(null);
    }

    String toString(@Nullable DtoModifier inputModifier) {
        StringBuilder builder = new StringBuilder();
        if (doc != null) {
            builder.append("@doc(").append(doc.replace("\n", "\\n")).append(") ");
        }
        if (inputModifier != null) {
            builder.append('@').append(inputModifier.name().toLowerCase()).append(' ');
        }
        if (mandatory == Mandatory.OPTIONAL) {
            builder.append("@optional ");
        } else if (mandatory == Mandatory.REQUIRED) {
            builder.append("@required ");
        }
        for (Anno anno : annotations) {
            builder.append(anno).append(' ');
        }
        if (config != null) {
            builder.append(config);
        }
        if (funcName != null) {
            builder.append(funcName).append('(').append(basePath).append(')');
        } else {
            builder.append(basePath);
        }
        if (alias != null && !alias.equals(tail.getBaseProp().getName())) {
            builder.append(" as ").append(alias);
        }
        if (targetType != null) {
            builder.append(": ");
            if (!recursive || targetType.isFocusedRecursion()) {
                builder.append(targetType);
            }
            if (recursive) {
                builder.append("...");
            }
        }
        return builder.toString();
    }
}
