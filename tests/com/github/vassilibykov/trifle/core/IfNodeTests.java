// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.trifle.core;

import org.junit.Before;
import org.junit.Test;

import static com.github.vassilibykov.trifle.core.JvmType.INT;
import static com.github.vassilibykov.trifle.core.JvmType.REFERENCE;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.const_;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.if_;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.lambda;
import static org.junit.Assert.*;

@SuppressWarnings("ConstantConditions")
public class IfNodeTests {

    private UserFunction intReturningClosure;
    private FunctionImplementation intReturningFunction;
    private IfNode intReturningNode;
    private UserFunction mixedClosure;
    private FunctionImplementation mixedFunction;
    private IfNode mixedNode;

    @Before
    public void setUp() throws Exception {
        var topLevel = new Library();
        topLevel.define("intReturning",
            lambda(arg ->
                if_(arg, const_(1), const_(0))));
        intReturningClosure = topLevel.get("intReturning");
        intReturningFunction = intReturningClosure.implementation();
        intReturningNode = (IfNode) intReturningFunction.body();
        topLevel.define("mixed",
            lambda(arg ->
                if_(arg, const_(1), const_("false"))));
        mixedClosure = topLevel.get("mixed");
        mixedFunction = mixedClosure.implementation();
        mixedNode = (IfNode) mixedFunction.body();
    }

    @Test
    public void profiledInterpretedEvaluation() {
        assertEquals(1, intReturningClosure.invoke(true));
        assertEquals(0, intReturningClosure.invoke(false));
    }

    @Test
    public void profiledEvaluationWithBadCondition() {
        try {
            intReturningClosure.invoke(0);
            fail("expected exception not thrown");
        } catch (RuntimeError e) {
            // good
        }
    }

    @Test
    public void simpleInterpretedEvaluation() {
        intReturningFunction.useSimpleInterpreter();
        assertEquals(1, intReturningClosure.invoke(true));
        assertEquals(0, intReturningClosure.invoke(false));
    }

    @Test
    public void simpleEvaluationWithBadCondition() {
        intReturningFunction.useSimpleInterpreter();
        try {
            intReturningClosure.invoke(0);
            fail("expected exception not thrown");
        } catch (RuntimeError e) {
            // good
        }
    }

    @Test
    public void inferredType() {
        intReturningFunction.forceCompile();
        assertEquals(INT, intReturningNode.inferredType().jvmType().get());
        mixedFunction.forceCompile();
        assertEquals(REFERENCE, mixedNode.inferredType().jvmType().get());
    }

    @Test
    public void branchCounters() {
        intReturningClosure.invoke(true);
        intReturningClosure.invoke(true);
        intReturningClosure.invoke(false);
        intReturningClosure.invoke(false);
        intReturningClosure.invoke(false);
        assertEquals(2, intReturningNode.trueBranchCount.intValue());
        assertEquals(3, intReturningNode.falseBranchCount.intValue());
    }

    @Test
    public void specializedType() {
        intReturningClosure.invoke(true);
        intReturningFunction.forceCompile();
        assertEquals(INT, intReturningNode.specializedType());
    }

    @Test
    public void specializedTypeMixedTrueCase() {
        mixedClosure.invoke(true);
        mixedFunction.forceCompile();
        assertEquals(INT, mixedNode.specializedType());
    }

    @Test
    public void specializedTypeMixedFalseCase() {
        mixedClosure.invoke(false);
        mixedFunction.forceCompile();
        assertEquals(REFERENCE, mixedNode.specializedType());
    }

    @Test
    public void compiledEvaluationWithoutProfileData() {
        intReturningFunction.forceCompile();
        assertEquals(1, intReturningClosure.invoke(true));
        assertEquals(0, intReturningClosure.invoke(false));
    }

    @Test
    public void compiledEvaluationWithProfileData() {
        intReturningClosure.invoke(true);
        intReturningFunction.forceCompile();
        assertEquals(1, intReturningClosure.invoke(true));
        assertEquals(0, intReturningClosure.invoke(false));
    }

    @Test
    public void compiledEvaluationWithBadCondition() {
        intReturningClosure.invoke(true);
        intReturningFunction.forceCompile();
        try {
            intReturningClosure.invoke(0);
            fail("expected exception not thrown");
        } catch (RuntimeError e) {
            // good
        }
    }

    @Test
    public void compiledEvaluationMixedFailureCase() {
        mixedClosure.invoke(true);
        mixedFunction.forceCompile();
        assertEquals("false", mixedClosure.invoke(false));
    }
}