// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.acode;

import com.github.vassilibykov.enfilade.core.ExpressionLanguage;
import com.github.vassilibykov.enfilade.core.ExpressionTestUtilities;
import com.github.vassilibykov.enfilade.core.Function;
import com.github.vassilibykov.enfilade.core.Variable;
import org.junit.Test;

import static com.github.vassilibykov.enfilade.acode.AssemblyLanguage.*;
import static com.github.vassilibykov.enfilade.core.ExpressionLanguage.add;
import static com.github.vassilibykov.enfilade.core.ExpressionLanguage.const_;
import static com.github.vassilibykov.enfilade.core.ExpressionLanguage.lessThan;
import static com.github.vassilibykov.enfilade.core.ExpressionLanguage.ref;
import static com.github.vassilibykov.enfilade.core.ExpressionLanguage.var;
import static org.junit.Assert.assertEquals;

public class InterpreterTest {

    private Asm code;

    @Test
    public void testLoadConst() {
        code = Asm
            .vars()
            .code(
                load("hello"),
                ret());
        assertEquals("hello", code.interpretWith());
    }

    @Test
    public void testLoadArg() {
        Variable arg = var("arg");
        code = Asm
            .vars(arg)
            .code(
                load(arg),
                ret());
        assertEquals(42, code.interpretWith(42));
    }

    @Test
    public void testPrimitive1() {
        Variable arg = var("arg");
        code = Asm
            .vars(arg)
            .code(
                load(ExpressionLanguage.negate(ref(arg))),
                ret());
        assertEquals(-42, code.interpretWith(42));
    }

    @Test
    public void testPrimitive2() {
        code = Asm
            .vars()
            .code(
                load(ExpressionLanguage.add(const_(3), const_(4))),
                ret());
        assertEquals(7, code.interpretWith());
    }

    @Test
    public void testGoto() {
        code = Asm
            .vars()
            .code(
                jump(3),
                load("hello"),
                ret(),
                load("goodbye"),
                ret());
        assertEquals("goodbye", code.interpretWith());
    }

    @Test
    public void testIf() {
        Variable arg = var("arg");
        code = Asm
            .vars(arg)
            .code(
                jump(lessThan(ref(arg), const_(0)), 3),
                load("positive"),
                jump(4),
                load("negative"),
                ret());
        assertEquals("positive", code.interpretWith(1));
        assertEquals("negative", code.interpretWith(-1));
    }

    @Test
    public void testStore() {
        Variable arg = var("arg");
        code = Asm
            .vars(arg)
            .code(
                load(4),
                store(arg),
                load(arg),
                ret());
        assertEquals(4, code.interpretWith(3));
    }

    @Test
    public void testCall() {
        Variable arg1 = var("arg1");
        Variable arg2 = var("arg2");
        Function adder = Function.with(new Variable[]{arg1, arg2}, add(ref(arg1), ref(arg2)));
        // In general variables must not be reused, but reuse here is ok because we know
        // the are in same positions in the frame and the code is executed only once so it's not being compiled.
        code = Asm
            .vars(arg1)
            .code(
                call(ExpressionLanguage.call(adder, ref(arg1), ref(arg1))),
                ret());
        assertEquals(6, code.interpretWith(3));
    }

    /** A helper for defining test methods. */
    private static class Asm {

        static Asm vars(Variable... vars) {
            return new Asm(vars);
        }

        private final Object[] frame;
        private Instruction[] instructions;

        private Asm(Variable... vars) {
            for (int i = 0; i < vars.length; i++) {
                ExpressionTestUtilities.setVariableIndex(vars[i], i);
            }
            frame = new Object[vars.length];
        }

        Asm code(Instruction... instructions) {
            this.instructions = instructions;
            return this;
        }

        Object interpretWith(Object... args) {
            System.arraycopy(args, 0, frame, 0, args.length);
            return new Interpreter(instructions, frame, 0).interpret();
        }
    }
}