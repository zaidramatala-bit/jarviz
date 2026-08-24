/*
* Copyright 2020 Expedia, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.vrbo.jarviz.visitor.java21;

import java.util.function.Function;
import java.util.function.Supplier;

public class Java21Fixture {

    public record Name(String value) {}

    public sealed interface Formatter permits FormatterImpl {

        private String normalize(final String value) {
            return value.trim();
        }

        default String format(final Target target, final String value) {
            return target.decorate(normalize(value));
        }
    }

    public static final class FormatterImpl implements Formatter {}

    public sealed abstract static class Operation permits OperationImpl {

        public String execute(final Target target) {
            return target.fallback();
        }
    }

    public static final class OperationImpl extends Operation {}

    public static final class Target {

        public String decorate(final String value) {
            return value;
        }

        public String fallback() {
            return "fallback";
        }
    }

    public final class Nested {

        public String callPrivate(final Target target) {
            return privateValue(target);
        }
    }

    private String privateValue(final Target target) {
        return target.decorate("nested");
    }

    public String lambda(final Target target) {
        final Supplier<String> supplier = () -> target.decorate("lambda");
        return supplier.get();
    }

    public String methodReference(final Target target, final String value) {
        final Function<String, String> function = target::decorate;
        return function.apply(value);
    }

    public String concatenate(final Target target, final String value) {
        return "value=" + target.decorate(value);
    }

    public String patternSwitch(final Target target, final Object value) {
        return switch (value) {
            case Name name -> target.decorate(name.value());
            case String text -> target.decorate(text);
            default -> target.fallback();
        };
    }
}
