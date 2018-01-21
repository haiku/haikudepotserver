/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.cayenne;

import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;

import java.util.Optional;
import java.util.stream.Collector;

/**
 * <p>Can be used with Java streams in order to coalesce Cayenne Expression objects in either an AND or an OR
 * operation to create a new Cayenne Expression.</p>
 */

public class ExpressionCollector {

    public static Collector<Expression, OrExpressionHolder, Expression> orExp() {
        //noinspection Convert2MethodRef
        return Collector.of(
                OrExpressionHolder::new,
                (h, i) -> h.combine(i),
                (h1,h2) -> new OrExpressionHolder(h1,h2),
                (h1) -> h1.tryGet().orElse(ExpressionFactory.expFalse())
        );
    }

    public static Collector<Expression, AndExpressionHolder, Expression> andExp() {
        //noinspection Convert2MethodRef
        return Collector.of(
                AndExpressionHolder::new,
                (h, i) -> h.combine(i),
                (h1,h2) -> new AndExpressionHolder(h1,h2),
                (h1) -> h1.tryGet().orElse(ExpressionFactory.expFalse())
        );
    }

    private abstract static class AbstractExpressionHolder {

        Expression value = null;

        AbstractExpressionHolder() {
        }

        AbstractExpressionHolder(AbstractExpressionHolder a, AbstractExpressionHolder b) {
            Optional<Expression> aeO = a.tryGet();
            Optional<Expression> beO = b.tryGet();

            if (!aeO.isPresent() && beO.isPresent()) {
                value = beO.get();
            }

            if (aeO.isPresent() && !beO.isPresent()) {
                value = aeO.get();
            }

            if (aeO.isPresent() && beO.isPresent()) {
                value = aeO.get();
                combine(beO.get());
            }
        }

        Optional<Expression> tryGet() {
            return Optional.ofNullable(value);
        }

        abstract void combine(Expression other);

    }


    private static class OrExpressionHolder extends AbstractExpressionHolder {

        OrExpressionHolder() {
            super();
        }

        OrExpressionHolder(OrExpressionHolder a, OrExpressionHolder b) {
            super(a,b);
        }

        @Override
        void combine(Expression other) {
            value = null == value ? other : value.orExp(other);
        }

    }

    private static class AndExpressionHolder extends AbstractExpressionHolder {

        AndExpressionHolder() {
            super();
        }

        AndExpressionHolder(AndExpressionHolder a, AndExpressionHolder b) {
            super(a,b);
        }

        @Override
        void combine(Expression other) {
            value = null == value ? other : value.andExp(other);
        }

    }

}
