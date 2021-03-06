// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.trifle.core;

import com.github.vassilibykov.trifle.expression.Expression;
import com.github.vassilibykov.trifle.expression.Lambda;
import org.junit.Test;

import java.util.List;

import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.*;
import static com.github.vassilibykov.trifle.primitive.StandardPrimitiveLanguage.*;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("Convert2MethodRef")
public class InterpreterTests extends LanguageFeaturesTest {

    @Override
    protected Object eval(Expression methodBody) {
        Lambda function = Lambda.with(List.of(), methodBody);
        return invoke(function);
    }

    @Override
    protected Object invoke(Lambda function, Object... args) {
        switch (args.length) {
            case 0:
                return Closure.with(FunctionTranslator.translate(function)).invoke();
            case 1:
                return Closure.with(FunctionTranslator.translate(function)).invoke(args[0]);
            case 2:
                return Closure.with(FunctionTranslator.translate(function)).invoke(args[0], args[1]);
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Test
    public void testEvilFibonacci() {
        UserFunction fibonacci = evilFibonacci();
        fibonacci.invoke(35); // enough to force compilation
        assertEquals(1, fibonacci.invoke(1));
        assertEquals(8, fibonacci.invoke(5));
        assertEquals("error", fibonacci.invoke(-1));
    }

    @Test
    public void testVeryEvilFibonacci() {
        UserFunction fibonacci = veryEvilFibonacci();
        fibonacci.invoke(35); // enough to force compilation
        assertEquals(1, fibonacci.invoke(1));
        assertEquals(8, fibonacci.invoke(5));
        assertEquals("error", fibonacci.invoke(-1));
    }

    /*
        Support
     */

    /**
     * A version of fibonacci which profiles with all the same types as the
     * regular one, but includes an 'if' branch (not taken while being profiled)
     * which injects a value into computation incompatible with the profiled
     * types, and therefore with the specialized version of code.
     */
    static UserFunction evilFibonacci() {
        Library toplevel = new Library();
        toplevel.define("fibonacci",
            fibonacci -> lambda(n ->
                if_(lessThan(n, const_(0)),
                    const_("error"),
                    if_(lessThan(n, const_(2)),
                        const_(1),
                        bind(call(direct(fibonacci), sub(n, const_(1))), t1 ->
                            bind(call(direct(fibonacci), sub(n, const_(2))), t2 ->
                                add(t1, t2)))))));
        return toplevel.get("fibonacci");
    }

    /**
     * Here, unlike the simply evil version, the "error" constant is not in the
     * tail position, so the continuation which should but can't receive its
     * value lies within the function.
     */
    static UserFunction veryEvilFibonacci() {
        Library toplevel = new Library();
        toplevel.define("fibonacci",
            fibonacci -> lambda(n ->
                bind(
                    if_(lessThan(n, const_(0)),
                        const_("error"),
                        if_(lessThan(n, const_(2)),
                            const_(1),
                            bind(call(direct(fibonacci), sub(n, const_(1))), t1 ->
                                bind(call(direct(fibonacci), sub(n, const_(2))), t2 ->
                                    add(t1, t2))))),
                    t0 -> t0)));
        return toplevel.get("fibonacci");
    }
}