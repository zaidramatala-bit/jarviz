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

    @Test
    public void testCleanseClassName() {
        assertThat(cleanseClassName("WEB-INF.classes.com.foo.bar.MyClass")).isEqualTo("com.foo.bar.MyClass");
        assertThat(cleanseClassName("com.foo.bar.MyClass")).isEqualTo("com.foo.bar.MyClass");
    }

    @Test
    public void testInvokeDynamicRecordsLambdaImplementationMethod() {
        final List<MethodCoupling> couplings = new ArrayList<>();
        final Method sourceMethod = new Method.Builder()
                                        .className("com.foo.bar.MyClass")
                                        .methodName("doThat")
                                        .build();
        final FilteredMethodVisitor visitor =
            new FilteredMethodVisitor(sourceMethod, null, couplings::add);

        // Equivalent to `list.forEach(s -> Helper.process(s))` compiled into MyClass#doThat,
        // where the compiler generates the synthetic implementation method MyClass#lambda$doThat$0.
        visitor.visitInvokeDynamicInsn(
            "accept",
            "()Ljava/util/function/Consumer;",
            LAMBDA_METAFACTORY,
            Type.getType("(Ljava/lang/Object;)V"),
            new Handle(Opcodes.H_INVOKESTATIC,
                       "com/foo/bar/MyClass",
                       "lambda$doThat$0",
                       "(Ljava/lang/String;)V",
                       false),
            Type.getType("(Ljava/lang/String;)V"));

        assertThat(couplings).hasSize(1);
        assertThat(couplings.get(0).getTarget().toStringShort())
            .isEqualTo("com.foo.bar.MyClass#lambda$doThat$0");
    }

    @Test
    public void testInvokeDynamicRecordsMethodReferenceTarget() {
        final List<MethodCoupling> couplings = new ArrayList<>();
        final FilteredMethodVisitor visitor = newVisitor(couplings);

        // Equivalent to `list.forEach(Helper::process)`.
        visitor.visitInvokeDynamicInsn(
            "accept",
            "()Ljava/util/function/Consumer;",
            LAMBDA_METAFACTORY,
            Type.getType("(Ljava/lang/Object;)V"),
            new Handle(Opcodes.H_INVOKESTATIC,
                       "com/foo/bar/Helper",
                       "process",
                       "(Ljava/lang/String;)V",
                       false),
            Type.getType("(Ljava/lang/String;)V"));

        assertThat(couplings).hasSize(1);
        assertThat(couplings.get(0).getTarget().toStringShort()).isEqualTo("com.foo.bar.Helper#process");
    }

    @Test
    public void testInvokeDynamicWithAltMetafactory() {
        final List<MethodCoupling> couplings = new ArrayList<>();
        final FilteredMethodVisitor visitor = newVisitor(couplings);

        // Serializable lambda, compiled against LambdaMetafactory#altMetafactory.
        visitor.visitInvokeDynamicInsn(
            "accept",
            "()Lcom/foo/bar/SerializableConsumer;",
            new Handle(Opcodes.H_INVOKESTATIC,
                       "java/lang/invoke/LambdaMetafactory",
                       "altMetafactory",
                       "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                           + "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                       false),
            Type.getType("(Ljava/lang/Object;)V"),
            new Handle(Opcodes.H_INVOKESTATIC,
                       "com/foo/bar/MyClass",
                       "lambda$doThat$1",
                       "(Ljava/lang/String;)V",
                       false),
            Type.getType("(Ljava/lang/String;)V"),
            1);

        assertThat(couplings).hasSize(1);
        assertThat(couplings.get(0).getTarget().toStringShort())
            .isEqualTo("com.foo.bar.MyClass#lambda$doThat$1");
    }

    @Test
    public void testInvokeDynamicWithoutMethodHandleArgumentsIsIgnored() {
        final List<MethodCoupling> couplings = new ArrayList<>();
        final FilteredMethodVisitor visitor = newVisitor(couplings);

        // String concatenation, which carries no implementation method handle.
        visitor.visitInvokeDynamicInsn(
            "makeConcatWithConstants",
            "(Ljava/lang/String;)Ljava/lang/String;",
            new Handle(Opcodes.H_INVOKESTATIC,
                       "java/lang/invoke/StringConcatFactory",
                       "makeConcatWithConstants",
                       "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                           + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                       false),
            "prefix\u0001");

        assertThat(couplings).isEmpty();
    }

    private static FilteredMethodVisitor newVisitor(final List<MethodCoupling> couplings) {
        final Method sourceMethod = new Method.Builder()
                                        .className("com.foo.bar.MyClass")
                                        .methodName("doThat")
                                        .build();
        return new FilteredMethodVisitor(sourceMethod, null, couplings::add);
    }
}
