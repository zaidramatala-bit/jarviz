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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import com.vrbo.jarviz.model.MethodCoupling;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the couplings reported for dynamic call sites, where the method actually invoked is
 * described by the bootstrap method arguments rather than by the bootstrap method itself.
 */
public class FilteredMethodVisitorInvokeDynamicTest {

    private static final String CALLER_CLASS = "com/example/Caller";

    private static final Handle LAMBDA_METAFACTORY =
        new Handle(Opcodes.H_INVOKESTATIC,
                   "java/lang/invoke/LambdaMetafactory",
                   "metafactory",
                   "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                   + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                   + "Ljava/lang/invoke/CallSite;",
                   false);

    private static final Handle STRING_CONCAT_FACTORY =
        new Handle(Opcodes.H_INVOKESTATIC,
                   "java/lang/invoke/StringConcatFactory",
                   "makeConcatWithConstants",
                   "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                   + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                   false);

    @Test
    public void testLambdaIsAttributedToItsImplementationMethod() {
        final Handle implementation = new Handle(Opcodes.H_INVOKESTATIC,
                                                CALLER_CLASS,
                                                "lambda$callTarget$0",
                                                "()Ljava/lang/String;",
                                                false);

        assertThat(couplingsOf(LAMBDA_METAFACTORY,
                               Type.getMethodType("()Ljava/lang/Object;"),
                               implementation,
                               Type.getMethodType("()Ljava/lang/String;")))
            .containsExactly("com.example.Caller#callTarget -> com.example.Caller#lambda$callTarget$0");
    }

    @Test
    public void testMethodReferenceIsAttributedToTheReferencedMethod() {
        final Handle implementation = new Handle(Opcodes.H_INVOKEVIRTUAL,
                                                "com/example/Target",
                                                "run",
                                                "()Ljava/lang/String;",
                                                false);

        assertThat(couplingsOf(LAMBDA_METAFACTORY,
                               Type.getMethodType("()Ljava/lang/Object;"),
                               implementation,
                               Type.getMethodType("()Ljava/lang/String;")))
            .containsExactly("com.example.Caller#callTarget -> com.example.Target#run");
    }

    @Test
    public void testJdkBootstrapMethodWithoutImplementationIsNotReported() {
        assertThat(couplingsOf(STRING_CONCAT_FACTORY, "prefix\u0001")).isEmpty();
    }

    @Test
    public void testCustomBootstrapMethodIsReported() {
        final Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC,
                                            "com/example/Bootstraps",
                                            "bootstrap",
                                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                                            + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                                            false);

        assertThat(couplingsOf(bootstrap))
            .containsExactly("com.example.Caller#callTarget -> com.example.Bootstraps#bootstrap");
    }

    private static List<String> couplingsOf(final Handle bootstrapMethodHandle,
                                            final Object... bootstrapMethodArguments) {
        final List<String> couplings = new ArrayList<>();
        new FilteredClassVisitor("com.example.Caller",
                                 coupling -> couplings.add(describe(coupling)),
                                 callerClass(bootstrapMethodHandle, bootstrapMethodArguments)).visit();

        return couplings;
    }

    private static String describe(final MethodCoupling coupling) {
        return coupling.getSource().getClassName() + '#' + coupling.getSource().getMethodName()
               + " -> "
               + coupling.getTarget().getClassName() + '#' + coupling.getTarget().getMethodName();
    }

    /**
     * Builds a class holding a single {@code callTarget} method with one dynamic call site.
     */
    private static byte[] callerClass(final Handle bootstrapMethodHandle,
                                      final Object... bootstrapMethodArguments) {
        final ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V1_8,
                          Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                          CALLER_CLASS,
                          null,
                          "java/lang/Object",
                          null);

        final MethodVisitor method = classWriter.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                                                             "callTarget",
                                                             "()V",
                                                             null,
                                                             null);
        method.visitCode();
        method.visitInvokeDynamicInsn("get",
                                      "()Ljava/lang/Object;",
                                      bootstrapMethodHandle,
                                      bootstrapMethodArguments);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        classWriter.visitEnd();

        return classWriter.toByteArray();
    }
}
