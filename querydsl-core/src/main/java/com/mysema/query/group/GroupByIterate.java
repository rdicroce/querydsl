/*
 * Copyright 2013, Mysema Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mysema.query.group;

import java.util.NoSuchElementException;

import com.google.common.base.Objects;
import com.mysema.commons.lang.CloseableIterator;
import com.mysema.query.Projectable;
import com.mysema.query.Tuple;
import com.mysema.query.types.Expression;
import com.mysema.query.types.FactoryExpression;
import com.mysema.query.types.FactoryExpressionUtils;
import com.mysema.query.types.QTuple;

/**
 * Provides aggregated results as an iterator
 *
 * @author tiwe
 *
 * @param <K>
 * @param <V>
 */
public class GroupByIterate<K, V> extends AbstractGroupByTransformer<K, CloseableIterator<V>> {

    GroupByIterate(Expression<K> key, Expression<?>... expressions) {
        super(key, expressions);
    }

    @Override
    public CloseableIterator<V> transform(Projectable projectable) {
        // create groups
        FactoryExpression<Tuple> expr = FactoryExpressionUtils.wrap(new QTuple(expressions));
        boolean hasGroups = false;
        for (Expression<?> e : expr.getArgs()) {
            hasGroups |= e instanceof GroupExpression;
        }
        if (hasGroups) {
            expr = withoutGroupExpressions(expr);
        }
        final CloseableIterator<Tuple> iter = projectable.iterate(expr);

        return new CloseableIterator<V>() {

            GroupImpl group;

            @Override
            public boolean hasNext() {
                return group != null || iter.hasNext();
            }

            @Override
            public V next() {
                if (!iter.hasNext()) {
                    if (group != null) {
                        Group current = group;
                        group = null;
                        return transform(current);
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                Object[] row = iter.next().toArray();
                K groupId = (K) row[0];
                if (group == null) {
                    group = new GroupImpl(groupExpressions, maps);
                }
                group.add(row);
                while (iter.hasNext()) {
                    row = iter.next().toArray();
                    if (Objects.equal(groupId, row[0])) {
                        group.add(row);
                    } else {
                        Group current = group;
                        group = new GroupImpl(groupExpressions, maps);
                        group.add(row);
                        return transform(current);
                    }
                }
                Group current = group;
                group = null;
                return transform(current);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() {
                iter.close();
            }

        };
    }

    protected V transform(Group group) {
        return (V)group;
    }
}