package org.babyfish.jimmer.sql.ast.impl.query;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.sql.ast.Selection;
import org.babyfish.jimmer.sql.ast.impl.Ast;
import org.babyfish.jimmer.sql.ast.impl.AstContext;
import org.babyfish.jimmer.sql.ast.impl.AstVisitor;
import org.babyfish.jimmer.sql.ast.impl.table.TableImplementor;
import org.babyfish.jimmer.sql.ast.impl.table.TableProxies;
import org.babyfish.jimmer.sql.ast.impl.table.TableSelection;
import org.babyfish.jimmer.sql.ast.table.Table;
import org.babyfish.jimmer.sql.ast.table.spi.PropExpressionImplementor;
import org.babyfish.jimmer.sql.dialect.OracleDialect;
import org.babyfish.jimmer.sql.fetcher.Field;
import org.babyfish.jimmer.sql.fetcher.impl.FetcherSelection;
import org.babyfish.jimmer.sql.meta.*;
import org.babyfish.jimmer.sql.runtime.SqlBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

class AbstractConfigurableTypedQueryImpl implements TypedQueryImplementor {

    private TypedQueryData data;

    private AbstractMutableQueryImpl baseQuery;

    public AbstractConfigurableTypedQueryImpl(
            TypedQueryData data,
            AbstractMutableQueryImpl baseQuery
    ) {
        this.data = data;
        this.baseQuery = baseQuery;
    }

    public AbstractMutableQueryImpl getBaseQuery() {
        return baseQuery;
    }

    public TypedQueryData getData() {
        return data;
    }

    @Override
    public List<Selection<?>> getSelections() {
        return data.getSelections();
    }

    @Override
    public void accept(@NotNull AstVisitor visitor) {
        AstContext astContext = visitor.getAstContext();
        astContext.pushStatement(getBaseQuery());
        try {
            Selection<?> idOnlySelection = idOnlyPropExprByOffset();
            if (idOnlySelection != null) {
                baseQuery.accept(visitor, Collections.singletonList(idOnlySelection), false);
            } else {
                for (Selection<?> selection : data.getSelections()) {
                    Ast.from(selection, visitor.getAstContext()).accept(visitor);
                }
                baseQuery.accept(visitor, data.getOldSelections(), data.isWithoutSortingAndPaging());
            }
        } finally {
            astContext.popStatement();
        }
    }

    @Override
    public void renderTo(@NotNull SqlBuilder builder) {
        AstContext astContext = builder.getAstContext();
        astContext.pushStatement(getBaseQuery());
        try {
            if (data.isWithoutSortingAndPaging() || data.getLimit() == Integer.MAX_VALUE) {
                renderWithoutPaging(builder, null);
            } else {
                PropExpressionImplementor<?> idPropExpr = idOnlyPropExprByOffset();
                if (idPropExpr != null) {
                    renderIdOnlyQuery(idPropExpr, builder);
                } else {
                    SqlBuilder subBuilder = builder.createChildBuilder();
                    renderWithoutPaging(subBuilder, null);
                    subBuilder.build(result -> {
                        PaginationContextImpl ctx = new PaginationContextImpl(
                                data.getLimit(),
                                data.getOffset(),
                                result.get_1(),
                                result.get_2(),
                                false
                        );
                        baseQuery.getSqlClient().getDialect().paginate(ctx);
                        return ctx.build();
                    });
                }
            }
            if (data.isForUpdate()) {
                builder.sql(" for update");
            }
        } finally {
            astContext.popStatement();
        }
    }

    private void renderWithoutPaging(SqlBuilder builder, PropExpressionImplementor<?> idPropExpr) {
        builder.sql("select ");
        if (data.isDistinct()) {
            builder.sql("distinct ");
        }
        if (idPropExpr != null) {
            TableImplementor<?> tableImplementor = TableProxies.resolve(
                    idPropExpr.getTable(),
                    builder.getAstContext()
            );
            tableImplementor.renderSelection(
                    tableImplementor.getImmutableType().getIdProp(),
                    builder,
                    null,
                    true,
                    OffsetOptimizationWriter::idAlias
            );
        } else {
            String separator = "";
            for (Selection<?> selection : data.getSelections()) {
                builder.sql(separator);
                if (selection instanceof TableSelection) {
                    TableSelection tableSelection = (TableSelection) selection;
                    renderAllProps(tableSelection, builder);
                } else if (selection instanceof Table<?>) {
                    TableSelection tableSelection = TableProxies.resolve(
                            (Table<?>) selection,
                            builder.getAstContext()
                    );
                    renderAllProps(tableSelection, builder);
                } else {
                    Ast ast = Ast.from(selection, builder.getAstContext());
                    if (ast instanceof PropExpressionImplementor<?>) {
                        ((PropExpressionImplementor<?>) ast).renderTo(builder, true);
                    } else {
                        ast.renderTo(builder);
                    }
                }
                separator = ", ";
            }
        }
        baseQuery.renderTo(builder, data.isWithoutSortingAndPaging());
    }

    private PropExpressionImplementor<?> idOnlyPropExprByOffset() {
        if (data.getOffset() >= baseQuery.getSqlClient().getOffsetOptimizingThreshold()) {
            return data.getIdOnlyExpression();
        }
        return null;
    }

    private static void renderAllProps(TableSelection table, SqlBuilder builder) {
        String separator = "";
        Map<String, ImmutableProp> selectableProps = table
                .getImmutableType()
                .getSelectableProps();
        for (ImmutableProp prop : selectableProps.values()) {
            builder.sql(separator);
            table.renderSelection(prop, builder, null);
            separator = ", ";
        }
    }

    private void renderIdOnlyQuery(PropExpressionImplementor<?> idPropExpr, SqlBuilder builder) {
        OffsetOptimizationWriter writer =
                new OffsetOptimizationWriter(
                        builder,
                        builder.getAstContext().getSqlClient().getDatabaseMetadata()
                );
        TableImplementor<?> tableImplementor = TableProxies.resolve(
                idPropExpr.getTable(),
                builder.getAstContext()
        );
        DatabaseMetadata metadata = builder.getAstContext().getSqlClient().getDatabaseMetadata();
        builder.sql("select ");
        if (data.getSelections().get(0) instanceof FetcherSelection<?>) {
            for (Field field : ((FetcherSelection<?>)data.getSelections().get(0)).getFetcher().getFieldMap().values()) {
                writer.prop(field.getProp(), OffsetOptimizationWriter.ALIAS, false);
            }
        } else {
            for (ImmutableProp prop : tableImplementor.getImmutableType().getProps().values()) {
                writer.prop(prop, OffsetOptimizationWriter.ALIAS, false);
            }
        }
        builder.sql(" from (");
        SqlBuilder subBuilder = builder.createChildBuilder();
        renderWithoutPaging(subBuilder, idPropExpr);
        subBuilder.build(result -> {
            PaginationContextImpl ctx = new PaginationContextImpl(
                    data.getLimit(),
                    data.getOffset(),
                    result.get_1(),
                    result.get_2(),
                    true
            );
            baseQuery.getSqlClient().getDialect().paginate(ctx);
            return ctx.build();
        });
        writer.resetComma();
        builder.sql(") ")
                .sql(OffsetOptimizationWriter.CORE_ALIAS)
                .sql(" inner join ")
                .sql(metadata.getTableName(tableImplementor.getImmutableType()))
                .sql(" ")
                .sql(OffsetOptimizationWriter.ALIAS)
                .sql(" on ");
        writer.prop(
                tableImplementor.getImmutableType().getIdProp(),
                OffsetOptimizationWriter.ALIAS,
                true
        );
        builder.sql(" = ");
        int size = metadata.<ColumnDefinition>getStorage(tableImplementor.getImmutableType().getIdProp()).size();
        if (size == 1) {
            builder.sql(OffsetOptimizationWriter.CORE_ALIAS).sql(".");
            builder.sql(OffsetOptimizationWriter.idAlias(0));
        } else {
            builder.sql("(");
            for (int i = 0; i < size; i++) {
                if (i != 0) {
                    builder.sql(", ");
                }
                builder.sql(OffsetOptimizationWriter.CORE_ALIAS).sql(".");
                builder.sql(OffsetOptimizationWriter.idAlias(i));
            }
            builder.sql(")");
        }
        if (getBaseQuery().getSqlClient().getDialect().getOffsetOptimizationNumField() != null) {
            builder.sql(" order by ")
                    .sql(OffsetOptimizationWriter.CORE_ALIAS)
                    .sql(".")
                    .sql(OffsetOptimizationWriter.ROW_NUMBER_ALIAS);
        }
    }

    private static class OffsetOptimizationWriter {

        private static final String ALIAS = "optimize_";

        private static final String CORE_ALIAS = "optimize_core_";

        private static final String CORE_ID_ALIAS = "optimize_core_id_";

        private static final String ROW_NUMBER_ALIAS = OracleDialect.OPTIMIZE_CORE_ROW_NUMBER_ALIAS;

        private final SqlBuilder builder;

        private final DatabaseMetadata metadata;

        private boolean addComma;

        OffsetOptimizationWriter(SqlBuilder builder, DatabaseMetadata metadata) {
            this.builder = builder;
            this.metadata = metadata;
        }

        public void prop(ImmutableProp prop, String alias, boolean multiColumnsAsTuple) {
            SqlTemplate template = prop.getSqlTemplate();
            if (template instanceof FormulaTemplate) {
                appendComma();
                builder.sql(((FormulaTemplate)template).toSql(alias));
                return;
            }
            Storage storage = metadata.getStorage(prop);
            if (storage instanceof ColumnDefinition) {
                ColumnDefinition definition = (ColumnDefinition) storage;
                int size = definition.size();
                if (size == 1) {
                    appendComma();
                    builder.sql(alias).sql(".").sql(definition.name(0));
                } else if (multiColumnsAsTuple) {
                    builder.enterTuple();
                    for (int i = 0; i < size; i++) {
                        appendComma();
                        builder.sql(alias).sql(".").sql(definition.name(i));
                    }
                    builder.leaveTuple();
                } else {
                    for (int i = 0; i < size; i++) {
                        appendComma();
                        builder.sql(alias).sql(".").sql(definition.name(i));
                    }
                }
            }
        }

        private void appendComma() {
            if (addComma) {
                builder.sql(", ");
            } else {
                addComma = true;
            }
        }

        public static String idAlias(int index) {
            return index == 0 ? CORE_ID_ALIAS : CORE_ID_ALIAS + index + '_';
        }

        public void resetComma() {
            addComma = false;
        }
    }
}
