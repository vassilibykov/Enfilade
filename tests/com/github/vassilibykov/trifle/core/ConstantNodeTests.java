// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.trifle.core;

import org.junit.Before;
import org.junit.Test;

import java.lang.invoke.MethodType;
import java.util.List;

import static com.github.vassilibykov.trifle.core.JvmType.BOOL;
import static com.github.vassilibykov.trifle.core.JvmType.INT;
import static com.github.vassilibykov.trifle.core.JvmType.REFERENCE;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.const_;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.lambda;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings({"ConstantConditions", "SimplifiableJUnitAssertion"})
public class ConstantNodeTests {

    public static final MethodType GENERIC_NULLARY_METHOD_TYPE = MethodType.genericMethodType(0);
    private UserFunction intClosure;
    private FunctionImplementation intFunction;
    private ConstantNode intNode;
    private UserFunction boolClosure;
    private FunctionImplementation boolFunction;
    private ConstantNode boolNode;
    private UserFunction stringClosure;
    private FunctionImplementation stringFunction;
    private ConstantNode stringNode;
    private UserFunction nullClosure;
    private FunctionImplementation nullFunction;
    private ConstantNode nullNode;
    private UserFunction complexLiteralFunction;
    private FunctionImplementation complexLiteralImpl;
    private ConstantNode complexLiteralNode;

    @Before
    public void setUp() throws Exception {
        var topLevel = new Library();
        topLevel.define("int", lambda(() -> const_(42)));
        intClosure = topLevel.get("int");
        intFunction = intClosure.implementation();
        intNode = (ConstantNode) intFunction.body();
        topLevel.define("bool", lambda(() -> const_(true)));
        boolClosure = topLevel.get("bool");
        boolFunction = boolClosure.implementation();
        boolNode = (ConstantNode) boolFunction.body();
        topLevel.define("string", lambda(() -> const_("hello")));
        stringClosure = topLevel.get("string");
        stringFunction = stringClosure.implementation();
        stringNode = (ConstantNode) stringFunction.body();
        topLevel.define("null", lambda(() -> const_(null)));
        nullClosure = topLevel.get("null");
        nullFunction = nullClosure.implementation();
        nullNode = (ConstantNode) nullFunction.body();
        complexLiteralFunction = topLevel.define("complexLiteral", lambda(() -> const_(List.of("hello", "bye"))));
        complexLiteralImpl = complexLiteralFunction.implementation();
        complexLiteralNode = (ConstantNode) complexLiteralImpl.body();
    }

    private void invokeAndCompileAll() {
        intClosure.invoke();
        boolClosure.invoke();
        stringClosure.invoke();
        nullClosure.invoke();
        complexLiteralFunction.invoke();
        intFunction.forceCompile();
        boolFunction.forceCompile();
        stringFunction.forceCompile();
        nullFunction.forceCompile();
        complexLiteralImpl.forceCompile();
    }

    private void assertProperList(Object object) {
        assertTrue(object instanceof List);
        assertArrayEquals(new Object[] {"hello", "bye"}, ((List) object).toArray());
    }

    @Test
    public void profiledInterpretedEvaluation() {
        assertEquals(42, intClosure.invoke());
        assertEquals(true, boolClosure.invoke());
        assertEquals("hello", stringClosure.invoke());
        assertEquals(null, nullClosure.invoke());
        assertProperList(complexLiteralFunction.invoke());
    }

    @Test
    public void simpleInterpretedEvaluation() {
        intFunction.useSimpleInterpreter();
        boolFunction.useSimpleInterpreter();
        stringFunction.useSimpleInterpreter();
        nullFunction.useSimpleInterpreter();
        complexLiteralImpl.useSimpleInterpreter();
        assertEquals(42, intClosure.invoke());
        assertEquals(true, boolClosure.invoke());
        assertEquals("hello", stringClosure.invoke());
        assertEquals(null, nullClosure.invoke());
        assertProperList(complexLiteralFunction.invoke());
    }

    @Test
    public void inferredTypes() {
        ExpressionTypeInferencer.inferTypesIn(intFunction);
        ExpressionTypeInferencer.inferTypesIn(boolFunction);
        ExpressionTypeInferencer.inferTypesIn(stringFunction);
        ExpressionTypeInferencer.inferTypesIn(nullFunction);
        ExpressionTypeInferencer.inferTypesIn(complexLiteralImpl);
        assertEquals(INT, intNode.inferredType().jvmType().get());
        assertEquals(BOOL, boolNode.inferredType().jvmType().get());
        assertEquals(REFERENCE, stringNode.inferredType().jvmType().get());
        assertEquals(REFERENCE, nullNode.inferredType().jvmType().get());
        assertEquals(REFERENCE, complexLiteralNode.inferredType().jvmType().get());
    }

    @Test
    public void specializedTypes() {
        invokeAndCompileAll();
        assertEquals(INT, intNode.specializedType());
        assertEquals(INT, intFunction.specializedReturnType());
        assertEquals(BOOL, boolNode.specializedType());
        assertEquals(BOOL, boolFunction.specializedReturnType());
        assertEquals(REFERENCE, stringNode.specializedType());
        assertEquals(REFERENCE, stringFunction.specializedReturnType());
        assertEquals(REFERENCE, nullNode.specializedType());
        assertEquals(REFERENCE, nullFunction.specializedReturnType());
        assertEquals(REFERENCE, complexLiteralImpl.specializedReturnType());
    }

    @Test
    public void compiledEvaluation() {
        invokeAndCompileAll();
        assertEquals(42, intClosure.invoke());
        assertEquals(true, boolClosure.invoke());
        assertEquals("hello", stringClosure.invoke());
        assertEquals(null, nullClosure.invoke());
        assertProperList(complexLiteralFunction.invoke());
    }

    @Test
    public void genericImplementation() throws Throwable {
        invokeAndCompileAll();
        assertEquals(GENERIC_NULLARY_METHOD_TYPE, intFunction.genericImplementation().type());
        assertEquals(GENERIC_NULLARY_METHOD_TYPE, boolFunction.genericImplementation().type());
        assertEquals(GENERIC_NULLARY_METHOD_TYPE, stringFunction.genericImplementation().type());
        assertEquals(GENERIC_NULLARY_METHOD_TYPE, nullFunction.genericImplementation().type());
        assertEquals(GENERIC_NULLARY_METHOD_TYPE, complexLiteralImpl.genericImplementation().type());
        assertEquals(42, intFunction.genericImplementation().invoke());
        assertEquals(true, boolFunction.genericImplementation().invoke());
        assertEquals("hello", stringFunction.genericImplementation().invoke());
        assertEquals(null, nullFunction.genericImplementation().invoke());
        assertProperList(complexLiteralImpl.genericImplementation().invoke());
    }
}