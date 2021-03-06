// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.trifle.tmp;

import com.github.vassilibykov.trifle.core.Library;
import com.github.vassilibykov.trifle.core.UserFunction;

import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.bind;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.call;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.const_;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.direct;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.if_;
import static com.github.vassilibykov.trifle.expression.ExpressionLanguage.lambda;
import static com.github.vassilibykov.trifle.primitive.StandardPrimitiveLanguage.add;
import static com.github.vassilibykov.trifle.primitive.StandardPrimitiveLanguage.lessThan;
import static com.github.vassilibykov.trifle.primitive.StandardPrimitiveLanguage.sub;

public class TimeFib {

    public static void main(String[] args) {
        var n = 35;
        var fibonacci = fibonacci();
        System.out.print("Warming up");
        for (int i = 0; i < 20; i++) {
            fibonacci.invoke(n);
            System.out.print(".");
        }
        System.out.println("done.");
        var start = System.nanoTime();
        var result = (Integer) fibonacci.invoke(n);
        var elapsed = System.nanoTime() - start;
        System.out.format("fibonacci(%s) = %s in %s ms\n", n, result, elapsed / 1_000_000L);
    }

    private static UserFunction fibonacci() {
        Library toplevel = new Library();
        toplevel.define("fibonacci",
            fibonacci -> lambda(n ->
                if_(lessThan(n, const_(2)),
                    const_(1),
                    bind(call(direct(fibonacci), sub(n, const_(1))), t1 ->
                        bind(call(direct(fibonacci), sub(n, const_(2))), t2 ->
                            add(t1, t2))))));
        return toplevel.get("fibonacci");
    }

//    private static Closure altFib() {
//        var n = var("n");
//        var t1 = var("t1");
//        var t2 = var("t2");
//        return Library.define(
//            fibonacci -> Lambda.with(List.of(n),
//                If.with(PrimitiveCall.with(new PrimitiveKey("lessThan", LessThan::new), n, Const.value(2)),
//                    Const.value(1),
//                    Let.with(t1, Call.with(fibonacci, PrimitiveCall.with(new PrimitiveKey("sub", Sub::new), n, Const.value(1))),
//                        Let.with(t2, Call.with(fibonacci, PrimitiveCall.with(new PrimitiveKey("sub", Sub::new), n, Const.value(2))),
//                            PrimitiveCall.with(new PrimitiveKey("add", Add::new), t1, t2))))));
//    }

//    private static Closure fibonacci() {
//        var t1 = var("t1");
//        var t2 = var("t2");
//        return Library.define(
//            fibonacci -> lambda(n ->
//                if_(lessThan(n, const_(2)),
//                    const_(1),
//                    let(t1, call(fibonacci, sub(n, const_(1))),
//                        let(t2, call(fibonacci, sub(n, const_(2))),
//                            add(t1, t2))))));
//    }
//
//    private static Lambda fibonacci() {
//        var fibonacci = var("fibonacci");
//        var t1 = var("t1");
//        var t2 = var("t2");
//        return lambda(arg ->
//            letrec(fibonacci, lambda(n ->
//                    if_(lessThan(n, const_(2)),
//                        const_(1),
//                        let(t1, call(fibonacci, sub(n, const_(1))),
//                            let(t2, call(fibonacci, sub(n, const_(2))),
//                                add(t1, t2))))),
//                call(fibonacci, arg)));
//    }
}
