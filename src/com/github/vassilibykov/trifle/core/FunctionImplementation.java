// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.trifle.core;

import com.github.vassilibykov.trifle.expression.Lambda;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.github.vassilibykov.trifle.core.JvmType.REFERENCE;

/**
 * An object holding together all executable representations of a function
 * (though not necessarily all of them are available at any given time). The
 * representations are: a tree of {@link EvaluatorNode}s (the definitive
 * executable form), a method handle of the compiled representation with generic
 * signature (if exists), and a method handle of the compiled representation
 * with specialized signature (if exists). This is <em>not</em> a function value
 * in the implemented language. For that, see {@link Closure}.
 *
 * <p>Each function implementation object corresponds to a lambda expression in
 * the source language. A top-level function with two nested closures would map
 * onto three {@link FunctionImplementation}s. A {@link Closure} instance
 * created when a lambda expression is evaluated references the corresponding
 * function implementation.
 *
 * <p>At the core of a function implementation's invocation mechanism is its
 * {@link #callSite}, referred to as "the core call site", and an invoker of
 * that call site stored in the {@link #callSiteInvoker} field. The target of
 * that call site is a method handle invoking which will execute the function
 * using the best currently available option. For a newly created function
 * implementation that would be execution by a profiling interpreter. Later the
 * target of the call site is changed to a faster non-profiling interpreter
 * while the function is being compiled, and eventually to a method handle to
 * the generic compiled form of the function.
 *
 * <p>In a top-level function with {@code n} declared parameters, the core call
 * site has the type
 *
 * <pre>{@code
 * (Object{n}) -> Object
 * }</pre>

 * <p>A non-top-level function may have additional synthetic parameters
 * prepended by the closure conversion process. The core call site of a function
 * with {@code k} parameters introduced by the closure converter has the type
 *
 * <pre>{@code
 * (Object{k} Object{n}) -> Object
 * }</pre>
 *
 * <p>When invoked by the {@code call} expression with a closure as the
 * function argument, executed by the interpreter, invocation is kicked off by
 * one of the {@link Closure#invoke} methods, receiving the call arguments
 * ({@code n} Objects).
 *
 * <p>When the same expression is executed by generic compiled code, the call
 * site in the caller has the signature
 *
 * <pre>{@code
 * (Object Object{n}) -> Object
 * }</pre>
 *
 * <p>The extra leading argument is the closure being called. Internally a
 * closure maintains an {@link Closure#genericInvoker} method handle which calls
 * its function implementation's {@link #callSiteInvoker} after inserting copied
 * values, if any, to be received by the synthetic parameters prepended by the
 * closure converter.
 *
 * <p>In addition to the generically-typed compiled form bound to the core {@link
 * #callSite}, a function implementation may have a specialized compiled form. A
 * specialized form is produced by the compiler if the profiling interpreter
 * observed at least one of the function arguments to always be of a primitive
 * type. In a specialized form is available, the {@link
 * #specializedImplementation} field is not null. It contains a method handle of
 * the specialized compiled form. Compared to the signature of the generic method,
 * the signature of the specialized one has some of the parameters of primitive
 * types, and may have a primitive return type.
 *
 * <p>There are two mechanisms of how a specialized implementation can be
 * invoked. One is from the "normal" generic invocation pipeline, which includes
 * both the interpreted and the compiled generic cases. If a function has a
 * specialization, the method handle of its core {@link #callSite} is a guard
 * testing the current arguments for applicability to the specialized form. For
 * example, if a unary function has an {@code (int)} specialization, the guard
 * would test the invocation argument for being an {@code Integer}. Depending on
 * the result of the test, either the specialized or the generic form is
 * invoked.
 *
 * <p>The other mechanism is an invocation from specialized code. The
 * specialized code compiler generates a call site with the signature matching
 * the specialized types of arguments. A binary call with both arguments
 * specialized as {@code int} and return typed observed to be {@code int} has
 * its call site typed as {@code (Object int int) -> int} (the leading argument
 * is again the closure typed as Object). The same function might be called
 * elsewhere from a call site typed as {@code (Object int Object) -> Object} if
 * those were the types observed at that call site.
 */
public class FunctionImplementation {

    /**
     * The number of times a function is executed as interpreted before it's
     * queued for compilation. The current value is picked fairly randomly.
     */
    public static final long PROFILING_TARGET = 10;

    private enum State {
        INVALID,
        PROFILING,
        COMPILING,
        COMPILED
    }

    private static final List<FunctionImplementation> registry = new ArrayList<>(100);

    private static synchronized int register(FunctionImplementation implementation) {
        var id = registry.size();
        registry.add(implementation);
        return id;
    }

    static synchronized FunctionImplementation withId(int id) {
        return registry.get(id);
    }

    /*
        Instance
     */

    @NotNull private final Lambda definition;
    @Nullable private UserFunction userFunction;
    /**
     * For an implementation of a non-top level lambda expression, contains the
     * implementation of the topmost lambda expression containing this one.
     * For an implementation of a top-level expression, contains this function
     * implementation.
     */
    @NotNull private final FunctionImplementation topImplementation;
    /**
     * Apparent parameters from the function definition. Does not include synthetic
     * parameters introduced by closure conversion to copy down free variables.
     */
    private List<VariableDefinition> declaredParameters;
    /**
     * All copied variables created during closure conversion.
     */
    private List<CopiedVariable> syntheticParameters;
    /**
     * The actual parameter list, beginning with synthetic parameters for copied down
     * values followed by apparent parameters.
     */
    private AbstractVariable[] allParameters;
    /**
     * In a top-level function, contains function implementations of closures defined in
     * it. Empty in non-top-level functions even if they do contain closures. Functions
     * are listed in tree traversal encounter order, so they are topologically sorted
     * with respect to their nesting.
     */
    private final List<FunctionImplementation> closureImplementations = new ArrayList<>();
    private final int arity;
    private EvaluatorNode body;
    private int frameSize = -1;
    /*internal*/ FunctionProfile profile;
    private JvmType specializedReturnType;
    /**
     * The unique ID of the function in the function registry.
     */
    private final int id;
    /**
     * A call site invoking which will execute the function using the currently
     * appropriate execution mode (profiled vs compiled). The call site has the
     * generic signature of {@code (Closure Object{n}) -> Object}.
     */
    private MutableCallSite callSite;
    /**
     * The dynamic invoker of {@link #callSite}.
     */
    private MethodHandle callSiteInvoker;
    private MethodHandle genericImplementation;
    private MethodHandle specializedImplementation;
    /**
     * Internal representation of this function's code from which recovery
     * portion of the bytecode can be generated. It's the same for generic and
     * specialized forms, so it's cached here. Lazily computed by the getter.
     */
    private RecoveryCodeGenerator.Instruction[] recoveryCode;
    private volatile State state;

    FunctionImplementation(@NotNull Lambda definition, @Nullable FunctionImplementation topFunction) {
        this.definition = definition;
        this.id = register(this);
        this.topImplementation = topFunction != null ? topFunction : this;
        this.arity = definition.arguments().size();
        this.state = State.INVALID;
    }

    /*
        Initialization

        Instance initialization is a multi-stage process. The following restricted
        methods are for the various tools that participate in it.
     */

    /** RESTRICTED. Intended for {@link FunctionTranslator}. */
    void partiallyInitialize(@NotNull List<VariableDefinition> parameters, @NotNull EvaluatorNode body) {
        this.declaredParameters = parameters;
        this.profile = new FunctionProfile(parameters);
        this.body = body;
    }

    /** RESTRICTED. Intended for {@link FunctionTranslator}. */
    void addClosureImplementations(Collection<FunctionImplementation> functions) {
        closureImplementations.addAll(functions);
    }

    /** RESTRICTED. Intended for {@link FunctionAnalyzer.ClosureConverter}. */
    void setSyntheticParameters(Collection<CopiedVariable> variables) {
        this.syntheticParameters = new ArrayList<>(variables);
        this.allParameters = Stream.concat(syntheticParameters.stream(), declaredParameters.stream())
            .toArray(AbstractVariable[]::new);
    }

    /** RESTRICTED. Intended for {@link FunctionAnalyzer.Indexer}. */
    void finishInitialization(int frameSize) {
        this.callSite = new MutableCallSite(profilingInterpreterInvoker());
        this.callSiteInvoker = callSite.dynamicInvoker();
        this.frameSize = frameSize;
        this.state = State.PROFILING;
    }

    /*
        Accessors
     */

    /**
     * The name of the top-level {@link UserFunction} implemented by this,
     * if this implementation does in fact implement a user function.
     */
    public Optional<String> name() {
        return userFunction != null ? Optional.of(userFunction.name()) : Optional.empty();
    }

    /**
     * The definition from which this implementation was built.
     */
    public Lambda definition() {
        return definition;
    }

    /**
     * The top-level named {@link UserFunction} implemented by this, or null
     * if this does not implement a top-level named user function.
     */
    public UserFunction userFunction() {
        return userFunction;
    }

    void setUserFunction(UserFunction userFunction) {
        this.userFunction = userFunction;
    }

    /**
     * The unique ID of this function implementation. The ID is unique within
     * a particular JVM session.
     */
    public int id() {
        return id;
    }

    /**
     * A list of variables corresponding to the parameters of this function
     * as declared in its source {@link #definition()}.
     */
    List<VariableDefinition> declaredParameters() {
        return declaredParameters;
    }

    /**
     * A list of variables prepended to the parameter list of this function
     * by closure conversion process.
     */
    List<CopiedVariable> syntheticParameters() {
        return syntheticParameters;
    }

    /**
     * All parameters of this function implementation. The contents of this
     * array are a concatenation of {@link #syntheticParameters()} and
     * {@link #declaredParameters()}.
     */
    AbstractVariable[] allParameters() {
        return allParameters;
    }

    /**
     * For a top-level function implementation, return a list with a
     * transitive closure of implementations of all nested functions.
     * For an implementation of a non-top-level function, return an
     * empty list even if the function has nested functions. The list
     * is topologically sorted w.r.t. the nesting of functions.
     */
    List<FunctionImplementation> closureImplementations() {
        return closureImplementations;
    }

    /*internal*/ boolean canBeSpecialized() {
        return Stream.of(allParameters).anyMatch(some -> some.specializedType() != REFERENCE);
    }

    public EvaluatorNode body() {
        return body;
    }

    /**
     * The arity of the underlying abstract definition (before closure conversion).
     */
    int declarationArity() {
        return arity;
    }

    /**
     * The arity of the closure-converted function, which includes both
     * synthetic and declared parameters.
     */
    int implementationArity() {
        return allParameters.length;
    }

    int frameSize() {
        return frameSize;
    }

    public boolean isTopLevel() {
        return topImplementation == this;
    }

    public boolean isCompiled() {
        return state == State.COMPILED;
    }

    MethodHandle callSiteInvoker() {
        return callSiteInvoker;
    }

    JvmType specializedReturnType() {
        return specializedReturnType;
    }

    void setSpecializedReturnType(JvmType specializedReturnType) {
        this.specializedReturnType = specializedReturnType;
    }

    MethodHandle genericImplementation() {
        return genericImplementation;
    }

    MethodHandle specializedImplementation() {
        return specializedImplementation;
    }

    RecoveryCodeGenerator.Instruction[] recoveryCode() {
        if (recoveryCode == null) {
            recoveryCode = RecoveryCodeGenerator.EvaluatorNodeToACodeTranslator.translate(body);
        }
        return recoveryCode;
    }

    /*
        Invocation
     */

    public MethodHandle invoker(MethodType callSiteType) {
        if (specializedImplementation != null && callSiteType == specializedImplementation.type()) {
            return specializedImplementation;
        }
        if (genericImplementation != null) {
            return JvmType.adaptToCallSite(callSiteType, genericImplementation);
        }
        return JvmType.adaptToCallSite(callSiteType, callSiteInvoker);
    }

    private MethodHandle profilingInterpreterInvoker() {
        return PROFILE_METHOD.bindTo(this).asCollector(Object[].class, implementationArity());
    }

    private MethodHandle simpleInterpreterInvoker() {
        return MethodHandles.insertArguments(INTERPRET_METHOD, 0, Interpreter.INSTANCE, this)
            .asCollector(Object[].class, implementationArity());
    }

    public Object profile(Object[] args) {
        Object result = ProfilingInterpreter.INSTANCE.interpret(this, args);
        if (profile.invocationCount() > PROFILING_TARGET) {
            scheduleCompilation();
        }
        return result;
    }

    /*
        Compilation
     */

    private void scheduleCompilation() {
        topImplementation.scheduleCompilationAtTop();
    }

    private synchronized void scheduleCompilationAtTop() {
        if (state == State.PROFILING) {
            markAsBeingCompiled();
            for (var each : closureImplementations) each.markAsBeingCompiled();
            forceCompile();
        }
    }

    private void markAsBeingCompiled() {
        state = State.COMPILING;
        callSite.setTarget(simpleInterpreterInvoker());
    }

    @TestOnly
    void useSimpleInterpreter() {
        markAsBeingCompiled();
    }

    void forceCompile() {
        if (this != topImplementation) throw new AssertionError("must be invoked on a top function implementation");
        var result = Compiler.compile(this);
        applyCompilationResult(result);
    }

    private synchronized void applyCompilationResult(Compiler.UnitResult result) {
        var implClass = GeneratedCode.defineClass(result);
        var callSitesToUpdate = new ArrayList<MutableCallSite>();
        for (var entry : result.results().entrySet()) {
            var functionImpl = entry.getKey();
            functionImpl.installCompiledForm(implClass, entry.getValue());
            callSitesToUpdate.add(functionImpl.callSite);
        }
        MutableCallSite.syncAll(callSitesToUpdate.toArray(new MutableCallSite[0]));
    }

    private void installCompiledForm(Class<?> generatedClass, Compiler.FunctionResult result) {
        MethodHandle specializedMethod = null;
        try {
            genericImplementation = MethodHandles.lookup().findStatic(
                generatedClass,
                result.genericMethodName(),
                MethodType.genericMethodType(implementationArity()));
            if (result.specializedMethodName() != null) {
                specializedMethod = MethodHandles.lookup().findStatic(
                    generatedClass,
                    result.specializedMethodName(),
                    result.specializedMethodType());
            }
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
        if (specializedMethod == null) {
            callSite.setTarget(genericImplementation);
            specializedImplementation = null;
            // this will not work if we allow de-specializing
        } else {
            callSite.setTarget(
                makeSpecializationGuard(genericImplementation, specializedMethod, result.specializedMethodType()));
            specializedImplementation = specializedMethod;
        }
        state = State.COMPILED;
    }

    private MethodHandle makeSpecializationGuard(MethodHandle generic, MethodHandle specialized, MethodType type) {
        MethodHandle checker = CHECK.bindTo(type);
        return MethodHandles.guardWithTest(
            checker.asCollector(Object[].class, type.parameterCount()),
            generify(specialized),
            generic);
    }

    /**
     * Take a method handle of a type involving some primitive types and wrap it
     * so that it accepts and returns all Objects.
     */
    private MethodHandle generify(MethodHandle specialization) {
        MethodHandle generic = specialization.asType(specialization.type().generic());
        // If return type is primitive, a return value not fitting the type will come back as SPE
        if (specialization.type().returnType().isPrimitive()) {
            return MethodHandles.catchException(generic, SquarePegException.class, EXTRACT_SQUARE_PEG);
        } else {
            return generic;
        }
    }

    @SuppressWarnings("unused") // called by generated code
    public static boolean checkSpecializationApplicability(MethodType specialization, Object[] args) {
        for (int i = 0; i < args.length; i++) {
            Class<?> type = specialization.parameterType(i);
            if (type.isPrimitive()) {
                Object arg = args[i];
                if (!JvmType.isCompatibleValue(type, arg)) return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unused") // called by generated code
    public static Object extractSquarePeg(SquarePegException exception) {
        return exception.value;
    }

    private static final MethodHandle CHECK;
    private static final MethodHandle EXTRACT_SQUARE_PEG;
    private static final MethodHandle INTERPRET_METHOD;
    private static final MethodHandle PROFILE_METHOD;

    static {
        try {
            var lookup = MethodHandles.lookup();
            CHECK = lookup.findStatic(
                FunctionImplementation.class,
                "checkSpecializationApplicability",
                MethodType.methodType(boolean.class, MethodType.class, Object[].class));
            EXTRACT_SQUARE_PEG = lookup.findStatic(
                FunctionImplementation.class,
                "extractSquarePeg", MethodType.methodType(Object.class, SquarePegException.class));
            INTERPRET_METHOD = lookup.findVirtual(
                Interpreter.class,
                "interpret",
                MethodType.methodType(Object.class, FunctionImplementation.class, Object[].class));
            PROFILE_METHOD = lookup.findVirtual(
                FunctionImplementation.class,
                "profile",
                MethodType.methodType(Object.class, Object[].class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        return definition.toString();
    }
}
