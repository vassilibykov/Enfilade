// Copyright (c) 2018 Vassili Bykov. Licensed under the Apache License, Version 2.0.

package com.github.vassilibykov.enfilade.core;

import java.util.Optional;

/**
 * A type assigned to an expression by the type inferencer or an analyzer
 * processing profiled types. It is essentially a wrapper around {@link
 * TypeCategory}, adding a notion of an unknown type.
 *
 * <p>Known vs unknown types are modeled as two nested classes, however the
 * classes are private to not expose the distinction at the class membership
 * level. Instead, the distinction is exposed through the uniform API of the
 * abstract methods of this class. The {@link #match} method together with the
 * {@link Matcher} interface mimic patching of functional languages.
 * Alternatively, the {@link #typeCategory()} method returns an empty optional
 * for unknown types and an optional with a value for known types.
 */
public abstract class ExpressionType {

    public static ExpressionType unknown() {
        return new Unknown();
    }

    public static ExpressionType known(TypeCategory type) {
        return new Known(type);
    }

    /** A poor man's pattern matching. */
    public interface Matcher<T> {
        T ifUnknown();
        T ifKnown(TypeCategory category);
    }

    private static class Unknown extends ExpressionType {
        private Unknown() {}

        @Override
        public <T> T match(Matcher<T> matcher) {
            return matcher.ifUnknown();
        }

        @Override
        public boolean isUnknown() {
            return true;
        }

        @Override
        public Optional<TypeCategory> typeCategory() {
            return Optional.empty();
        }

        @Override
        public ExpressionType union(ExpressionType other) {
            return this; // unknown
        }

        @Override
        public ExpressionType opportunisticUnion(ExpressionType other) {
            return other;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Unknown;
        }

        @Override
        public int hashCode() {
            return 0xdb326341;
        }

        @Override
        public String toString() {
            return "<unknown>";
        }
    }

    private static class Known extends ExpressionType {
        private final TypeCategory type;

        private Known(TypeCategory type) {
            this.type = type;
        }

        @Override
        public <T> T match(Matcher<T> matcher) {
            return matcher.ifKnown(type);
        }

        @Override
        public boolean isUnknown() {
            return false;
        }

        @Override
        public Optional<TypeCategory> typeCategory() {
            return Optional.of(type);
        }

        @Override
        public ExpressionType union(ExpressionType other) {
            return other.match(new Matcher<ExpressionType>() {
                public ExpressionType ifUnknown() {
                    return other;
                }
                public ExpressionType ifKnown(TypeCategory otherType) {
                    return ExpressionType.known(type.union(otherType));
                }
            });
        }

        @Override
        public ExpressionType opportunisticUnion(ExpressionType other) {
            return other.match(new Matcher<ExpressionType>() {
                public ExpressionType ifUnknown() {
                    return Known.this;
                }
                public ExpressionType ifKnown(TypeCategory otherType) {
                    return ExpressionType.known(type.union(otherType));
                }
            });
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (obj == this) return true;
            return obj instanceof Known && ((Known) obj).type.equals(type);
        }

        @Override
        public int hashCode() {
            return type.hashCode();
        }

        @Override
        public String toString() {
            return type.toString();
        }
    }

    /**
     * Apply the matcher to this type. Depending on whether this type is known
     * or unknown, the proper method of the matcher is invoked.
     */
    public abstract <T> T match(Matcher<T> matcher);

    /**
     * Indicate whether this type is unknown.
     */
    public abstract boolean isUnknown();

    /**
     * Return this type's category as an optional, empty if this is an unknown
     * type.
     */
    public abstract Optional<TypeCategory> typeCategory();

    /**
     * Compute a union of this type with another type, such that if one of the
     * types is unknown the result is unknown.
     */
    public abstract ExpressionType union(ExpressionType other);

    /**
     * Compute a union of this type with anotherType, such that if at least one
     * of the types is known, the result is known.
     */
    public abstract ExpressionType opportunisticUnion(ExpressionType other);
}
