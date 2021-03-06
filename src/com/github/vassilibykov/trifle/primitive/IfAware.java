// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.trifle.primitive;

import com.github.vassilibykov.trifle.core.EvaluatorNode;
import com.github.vassilibykov.trifle.core.PrimitiveNode;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A primitive implementation class implements this interface if it's a
 * boolean-valued primitive, and for some combinations of argument types it can
 * compile an {@code if} expression using a JVM instruction performing the test
 * and a conditional jump as a single operation. For example, an {@code if}
 * expression whose test is the {@link LT} primitive can be implemented as
 * {@code IF_ICMPGE} if the arguments are {@code ints}.
 */
public interface IfAware {

    /**
     * Check if this primitive can produce an optimized form of {@code if}
     * for the given call. If it can, return a helper object that will
     * assist the compiler with generating the optimized form.
     */
    Optional<OptimizedIfForm> optimizedFormFor(PrimitiveNode ifCondition);

    /**
     * A helper object assisting the compiler in generating an optimized
     * form of an {@code if} expression.
     */
    interface OptimizedIfForm {

        /**
         * Invoke the supplied generator on each call argument in the order
         * they should be evaluated and placed on the stack.
         */
        void loadArguments(Consumer<EvaluatorNode> argumentGenerator);

        /**
         * Return the opcode of the instruction that performs the test and
         * branches <em>to the false branch.</em> Note that the instruction
         * is thus the opposite of what we would normally think as the
         * {@code if} condition. For an {@code if less than} expression
         * the instruction would be {@code IF_ICMPGE}.
         */
        int  jumpInstruction();
    }
}
