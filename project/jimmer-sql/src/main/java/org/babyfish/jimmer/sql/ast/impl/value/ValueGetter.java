package org.babyfish.jimmer.sql.ast.impl.value;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.TargetLevel;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.ast.PropExpression;
import org.babyfish.jimmer.sql.ast.impl.table.TableImplementor;
import org.babyfish.jimmer.sql.ast.table.Table;
import org.babyfish.jimmer.sql.ast.table.spi.PropExpressionImplementor;
import org.babyfish.jimmer.sql.ast.table.spi.TableProxy;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;

import java.util.ArrayList;
import java.util.List;

public interface ValueGetter {

    String columnName();

    Object get(Object value);

    static <T> List<ValueGetter> valueGetters(
            JSqlClientImplementor sqlClient,
            PropExpression<T> propExpression,
            T value
    ) {
        List<ImmutableProp> props = new ArrayList<>();
        for (PropExpressionImplementor<?> propExpressionImplementor =
             (PropExpressionImplementor<?>) propExpression;
             propExpressionImplementor != null;
             propExpressionImplementor = propExpressionImplementor.getBase()) {
            props.add(0, propExpressionImplementor.getDeepestProp());
        }
        if (props.get(0).isId()) {
            Table<?> table = ((PropExpressionImplementor<T>) propExpression).getTable();
            ImmutableProp prop;
            if (table instanceof TableProxy<?>) {
                prop = ((TableProxy<?>)table).__prop();
            } else {
                prop = ((TableImplementor<?>)table).getJoinProp();
            }
            if (prop != null) {
                props.add(0, prop);
            }
        }
        ImmutableProp rootProp = props.get(0);
        if (rootProp.isReference(TargetLevel.ENTITY)) {
            Object targetId = null;
            if (value != null) {
                targetId = ((ImmutableSpi)value).__get(rootProp.getTargetType().getIdProp().getId());
            }
            return AbstractValueGetter.createValueGetters(sqlClient, props, targetId);
        }
        return AbstractValueGetter.createValueGetters(sqlClient, props, value);
    }
}

