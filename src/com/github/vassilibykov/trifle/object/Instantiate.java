// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.trifle.object;

import com.github.vassilibykov.trifle.core.ExpressionType;
import com.github.vassilibykov.trifle.core.GhostWriter;
import com.github.vassilibykov.trifle.core.JvmType;
import com.github.vassilibykov.trifle.core.RuntimeError;
import com.github.vassilibykov.trifle.primitive.Primitive1;

public class Instantiate extends Primitive1 {
    @Override
    public ExpressionType inferredType(ExpressionType argumentType) {
        return ExpressionType.known(JvmType.REFERENCE);
    }

    @Override
    public Object apply(Object argument) {
        if (!(argument instanceof FixedObjectDefinition)) {
            throw RuntimeError.message("not an object definition: " + argument);
        }
        return ((FixedObjectDefinition) argument).instantiate();
    }

    @Override
    protected JvmType generateForReference(GhostWriter writer) {
        writer
            .checkCast(FixedObjectDefinition.class)
            .invokeVirtual(FixedObjectDefinition.class, "instantiateAsObject", Object.class);
        return JvmType.REFERENCE;
    }

    @Override
    protected JvmType generateForInt(GhostWriter writer) {
        writer.throwError("cannot instantiate an integer");
        return JvmType.REFERENCE;
    }

    @Override
    protected JvmType generateForBoolean(GhostWriter writer) {
        writer.throwError("cannot instantiate a boolean");
        return JvmType.REFERENCE;
    }
}
