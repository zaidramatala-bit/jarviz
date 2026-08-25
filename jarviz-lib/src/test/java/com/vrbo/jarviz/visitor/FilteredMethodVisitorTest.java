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

package com.vrbo.jarviz.visitor;

import java.util.ArrayList;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.vrbo.jarviz.model.Method;
import com.vrbo.jarviz.model.MethodCoupling;

import static org.assertj.core.api.Assertions.assertThat;

import static com.vrbo.jarviz.visitor.FilteredMethodVisitor.cleanseClassName;

public class FilteredMethodVisitorTest {

    private static final Handle LAMBDA_METAFACTORY = new Handle(
        Opcodes.H_INVOKESTATIC,
        "java/lang/invoke/LambdaMetafactory",
        "metafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
            + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
            + "Ljava/lang/invoke/CallSite;",
        false);

    private static final Handle ALT_METAFACTORY = new Handle(
        Opcodes.H_INVOKESTATIC,
        "java/lang/invoke/LambdaMetafactory",
        "altMetafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)"
            + "Ljava/lang/invoke/CallSite;",
        false);

    private static final Handle STRING_CONCAT_FACTORY = new Handle(
        Opcodes.H_INVOKESTATIC,
        "java/lang/invoke/StringConcatFactory",
        "makeConcatWithConstants",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
            + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
        false);

    @Test
    public void testCleanseClassName() {
        assertThat(cleanseClassName("WEB-INF.classes.com.foo.bar.MyClass")).isEqualTo("com.foo.bar.MyClass");
        assertThat(cleanseClassName("com.foo.bar.MyClass")).isEqualTo("com.foo.bar.MyClass");
    }

    @Test
    public void testVisitInvokeDynamicInsnCollectsLambdaImplementationMethod() {
        final Method sourceMethod = new Method.Builder()
                                        .className("com.foo.bar.MyClass")
                                        .methodName("doStuff")
                                        .build();
        final List<MethodCoupling> couplings = new ArrayList<>();
        final FilteredMethodVisitor visitor =
            new FilteredMethodVisitor(sourceMethod, null, couplings::add);

        // Equivalent of `Supplier<String> s = other::describe;` inside com.foo.bar.MyClass#doStuff
        visitor.visitInvokeDynamicInsn(
            "get",
            "(Lcom/foo/bar/Other;)Ljava/util/function/Supplier;",
            LAMBDA_METAFACTORY,
            Type.getMethodType("()Ljava/lang/Object;"),
            new Handle(Opcodes.H_INVOKEVIRTUAL, "com/foo/bar/Other", "describe", "()Ljava/lang/String;", false),
            Type.getMethodType("()Ljava/lang/String;"));

        assertThat(couplings)
            .extracting(c -> c.getTarget().getClassName(), c -> c.getTarget().getMethodName())
            .containsExactly(Tuple.tuple("com.foo.bar.Other", "describe"));
    }

    @Test
    public void testVisitInvokeDynamicInsnCollectsLambdaBodyMethod() {
        final Method sourceMethod = new Method.Builder()
                                        .className("com.foo.bar.MyClass")
                                        .methodName("doStuff")
                                        .build();
        final List<MethodCoupling> couplings = new ArrayList<>();
        final FilteredMethodVisitor visitor =
            new FilteredMethodVisitor(sourceMethod, null, couplings::add);

        visitor.visitInvokeDynamicInsn(
            "get",
            "()Ljava/util/function/Supplier;",
            LAMBDA_METAFACTORY,
            Type.getMethodType("()Ljava/lang/Object;"),
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "com/foo/bar/MyClass",
                "lambda$doStuff$0",
                "()Ljava/lang/String;",
                false),
            Type.getMethodType("()Ljava/lang/String;"));

        assertThat(couplings)
            .extracting(c -> c.getTarget().getClassName(), c -> c.getTarget().getMethodName())
            .containsExactly(Tuple.tuple("com.foo.bar.MyClass", "lambda$doStuff$0"));
    }

    @Test
    public void testVisitInvokeDynamicInsnCollectsAltMetafactoryImplementationMethod() {
        final Method sourceMethod = new Method.Builder()
                                        .className("com.foo.bar.MyClass")
                                        .methodName("doStuff")
                                        .build();
        final List<MethodCoupling> couplings = new ArrayList<>();
        final FilteredMethodVisitor visitor =
            new FilteredMethodVisitor(sourceMethod, null, couplings::add);

        visitor.visitInvokeDynamicInsn(
            "get",
            "()Ljava/util/function/Supplier;",
            ALT_METAFACTORY,
            Type.getMethodType("()Ljava/lang/Object;"),
            new Handle(
                Opcodes.H_INVOKESTATIC,
                "com/foo/bar/MyClass",
                "lambda$doStuff$0",
                "()Ljava/lang/String;",
                false),
            Type.getMethodType("()Ljava/lang/String;"));

        assertThat(couplings)
            .extracting(c -> c.getTarget().getClassName(), c -> c.getTarget().getMethodName())
            .containsExactly(Tuple.tuple("com.foo.bar.MyClass", "lambda$doStuff$0"));
    }

    @Test
    public void testVisitInvokeDynamicInsnWithoutHandleCollectsNoCoupling() {
        final Method sourceMethod = new Method.Builder()
                                        .className("com.foo.bar.MyClass")
                                        .methodName("doStuff")
                                        .build();
        final List<MethodCoupling> couplings = new ArrayList<>();
        final FilteredMethodVisitor visitor =
            new FilteredMethodVisitor(sourceMethod, null, couplings::add);

        visitor.visitInvokeDynamicInsn(
            "makeConcatWithConstants",
            "(Ljava/lang/String;)Ljava/lang/String;",
            STRING_CONCAT_FACTORY,
            "value=\u0001",
            "constant");

        assertThat(couplings).isEmpty();
    }

    @Test
    public void testVisitMethodInsnCollectsMethodCoupling() {
        final Method sourceMethod = new Method.Builder()
                                        .className("com.foo.bar.MyClass")
                                        .methodName("doStuff")
                                        .build();
        final List<MethodCoupling> couplings = new ArrayList<>();
        final FilteredMethodVisitor visitor =
            new FilteredMethodVisitor(sourceMethod, null, couplings::add);

        visitor.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "com/foo/bar/Other",
            "describe",
            "()Ljava/lang/String;",
            false);

        assertThat(couplings)
            .extracting(c -> c.getTarget().getClassName(), c -> c.getTarget().getMethodName())
            .containsExactly(Tuple.tuple("com.foo.bar.Other", "describe"));
    }
}
