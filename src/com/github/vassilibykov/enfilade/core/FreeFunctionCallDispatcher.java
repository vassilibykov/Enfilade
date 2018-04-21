// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.core;

import com.github.vassilibykov.enfilade.builtins.BuiltinFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class FreeFunctionCallDispatcher implements CallDispatcher {
    @NotNull private final FreeFunction target;

    public FreeFunctionCallDispatcher(@NotNull FreeFunction target) {
        this.target = target;
    }

    @Override
    public Optional<EvaluatorNode> evaluatorNode() {
        return Optional.empty();
    }

    @Override
    public Object execute(CallNode call, EvaluatorNode.Visitor<Object> visitor) {
        return call.match(new CallNode.ArityMatcher<>() {
            @Override
            public Object ifNullary() {
                return target.invoke();
            }

            @Override
            public Object ifUnary(EvaluatorNode arg) {
                return target.invoke(arg.accept(visitor));
            }

            @Override
            public Object ifBinary(EvaluatorNode arg1, EvaluatorNode arg2) {
                return target.invoke(arg1.accept(visitor), arg2.accept(visitor));
            }
        });
    }

    @Override
    public JvmType generateCode(CallNode call, CodeGenerator generator) {
        if (target instanceof BuiltinFunction) {
            var name = ((BuiltinFunction) target).name();
            var callSiteType = generator.generateArgumentLoad(call);
            generator.writer().invokeDynamic(
                BuiltInFunctionCallInvokeDynamic.BOOTSTRAP,
                name,
                callSiteType);
            return JvmType.ofClass(callSiteType.returnType());
        } else if (target instanceof UserFunction) {
            var userFunction = (UserFunction) this.target;
            var id = userFunction.implementation().id();
            var callSiteType = generator.generateArgumentLoad(call);
            generator.writer().invokeDynamic(
                UserFunctionCallInvokeDynamic.BOOTSTRAP,
                userFunction.name(),
                callSiteType,
                id);
            return JvmType.ofClass(callSiteType.returnType());
        } else {
            throw new AssertionError("unexpected dispatcher target: " + target);
        }
    }
}