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

import java.util.List;

import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.vrbo.jarviz.model.Method;
import com.vrbo.jarviz.model.MethodCoupling;
import com.vrbo.jarviz.service.UsageCollector;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests verifying that {@link FilteredClassVisitor} and {@link FilteredMethodVisitor}
 * can scan Java 17 (class file version 61) bytecode, including records (record components)
 * and sealed classes (permitted subclasses). The class bytes are generated directly with ASM
 * instead of compiling Java 17 sources, so no Java 17 syntax needs to be parsed at build time.
 */
public class Java17BytecodeVisitorTest {

    private static final String RECORD_INTERNAL_NAME = "com/vrbo/jarviz/visitor/testsubject/PointRecord";

    private static final String RECORD_CLASS_NAME = "com.vrbo.jarviz.visitor.testsubject.PointRecord";

    private static final String SEALED_INTERNAL_NAME = "com/vrbo/jarviz/visitor/testsubject/SealedShape";

    private static final String SEALED_CLASS_NAME = "com.vrbo.jarviz.visitor.testsubject.SealedShape";

    @Test
    public void testScanJava17RecordClassBytes() {
        final UsageCollector collector = new UsageCollector();
        final FilteredClassVisitor classVisitor =
            new FilteredClassVisitor(RECORD_CLASS_NAME, collector, generateRecordClassBytes());

        classVisitor.visit();

        final List<MethodCoupling> couplings = collector.getMethodCouplings();

        // ordinary static method call from the record's sum() method
        assertThat(couplings).contains(
            new MethodCoupling.Builder()
                .source(new Method.Builder().className(RECORD_CLASS_NAME).methodName("sum").build())
                .target(new Method.Builder().className("java.lang.Math").methodName("addExact").build())
                .build()
        );

        // record constructor delegates to java.lang.Record
        assertThat(couplings).contains(
            new MethodCoupling.Builder()
                .source(new Method.Builder().className(RECORD_CLASS_NAME).methodName("<init>").build())
                .target(new Method.Builder().className("java.lang.Record").methodName("<init>").build())
                .build()
        );

        // invokedynamic in toString() resolves to the ObjectMethods bootstrap handle
        assertThat(couplings).contains(
            new MethodCoupling.Builder()
                .source(new Method.Builder().className(RECORD_CLASS_NAME).methodName("toString").build())
                .target(new Method.Builder().className("java.lang.runtime.ObjectMethods").methodName("bootstrap").build())
                .build()
        );
    }

    @Test
    public void testScanJava17SealedClassBytes() {
        final UsageCollector collector = new UsageCollector();
        final FilteredClassVisitor classVisitor =
            new FilteredClassVisitor(SEALED_CLASS_NAME, collector, generateSealedClassBytes());

        classVisitor.visit();

        final List<MethodCoupling> couplings = collector.getMethodCouplings();

        // ordinary virtual method call from the sealed class's describe() method
        assertThat(couplings).contains(
            new MethodCoupling.Builder()
                .source(new Method.Builder().className(SEALED_CLASS_NAME).methodName("describe").build())
                .target(new Method.Builder().className("java.lang.String").methodName("length").build())
                .build()
        );

        // lambda-style invokedynamic in run() resolves to the LambdaMetafactory bootstrap handle
        assertThat(couplings).contains(
            new MethodCoupling.Builder()
                .source(new Method.Builder().className(SEALED_CLASS_NAME).methodName("run").build())
                .target(new Method.Builder().className("java.lang.invoke.LambdaMetafactory").methodName("metafactory").build())
                .build()
        );

        // the synthetic lambda body's ordinary call is still collected
        assertThat(couplings).contains(
            new MethodCoupling.Builder()
                .source(new Method.Builder().className(SEALED_CLASS_NAME).methodName("lambda$run$0").build())
                .target(new Method.Builder().className("java.lang.String").methodName("trim").build())
                .build()
        );
    }

    /**
     * Generates the equivalent of: {@code public record PointRecord(int x, int y)}
     * with an extra {@code sum()} method, compiled at class file version 61 (Java 17).
     */
    private static byte[] generateRecordClassBytes() {
        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17,
                 Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_RECORD,
                 RECORD_INTERNAL_NAME,
                 null,
                 "java/lang/Record",
                 null);

        cw.visitRecordComponent("x", "I", null).visitEnd();
        cw.visitRecordComponent("y", "I", null).visitEnd();

        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "x", "I", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "y", "I", null, null).visitEnd();

        final MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(II)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Record", "<init>", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ILOAD, 1);
        init.visitFieldInsn(Opcodes.PUTFIELD, RECORD_INTERNAL_NAME, "x", "I");
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ILOAD, 2);
        init.visitFieldInsn(Opcodes.PUTFIELD, RECORD_INTERNAL_NAME, "y", "I");
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        final MethodVisitor sum = cw.visitMethod(Opcodes.ACC_PUBLIC, "sum", "()I", null, null);
        sum.visitCode();
        sum.visitVarInsn(Opcodes.ALOAD, 0);
        sum.visitFieldInsn(Opcodes.GETFIELD, RECORD_INTERNAL_NAME, "x", "I");
        sum.visitVarInsn(Opcodes.ALOAD, 0);
        sum.visitFieldInsn(Opcodes.GETFIELD, RECORD_INTERNAL_NAME, "y", "I");
        sum.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "addExact", "(II)I", false);
        sum.visitInsn(Opcodes.IRETURN);
        sum.visitMaxs(0, 0);
        sum.visitEnd();

        final Handle objectMethodsBootstrap = new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/runtime/ObjectMethods",
            "bootstrap",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;"
            + "Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;",
            false);

        final MethodVisitor toString =
            cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "toString", "()Ljava/lang/String;", null, null);
        toString.visitCode();
        toString.visitVarInsn(Opcodes.ALOAD, 0);
        toString.visitInvokeDynamicInsn(
            "toString", "(L" + RECORD_INTERNAL_NAME + ";)Ljava/lang/String;", objectMethodsBootstrap);
        toString.visitInsn(Opcodes.ARETURN);
        toString.visitMaxs(0, 0);
        toString.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates the equivalent of: {@code public sealed class SealedShape permits Circle}
     * with {@code describe()} and lambda-using {@code run()} methods,
     * compiled at class file version 61 (Java 17).
     */
    private static byte[] generateSealedClassBytes() {
        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17,
                 Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                 SEALED_INTERNAL_NAME,
                 null,
                 "java/lang/Object",
                 null);

        cw.visitPermittedSubclass("com/vrbo/jarviz/visitor/testsubject/Circle");

        final MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        final MethodVisitor describe = cw.visitMethod(Opcodes.ACC_PUBLIC, "describe", "()I", null, null);
        describe.visitCode();
        describe.visitLdcInsn("shape");
        describe.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        describe.visitInsn(Opcodes.IRETURN);
        describe.visitMaxs(0, 0);
        describe.visitEnd();

        final Handle lambdaMetafactoryBootstrap = new Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
            + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
            + "Ljava/lang/invoke/CallSite;",
            false);

        final MethodVisitor run =
            cw.visitMethod(Opcodes.ACC_PUBLIC, "run", "()Ljava/util/function/Function;", null, null);
        run.visitCode();
        run.visitInvokeDynamicInsn(
            "apply", "()Ljava/util/function/Function;", lambdaMetafactoryBootstrap);
        run.visitInsn(Opcodes.ARETURN);
        run.visitMaxs(0, 0);
        run.visitEnd();

        final MethodVisitor lambda = cw.visitMethod(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
            "lambda$run$0",
            "(Ljava/lang/String;)Ljava/lang/String;",
            null,
            null);
        lambda.visitCode();
        lambda.visitVarInsn(Opcodes.ALOAD, 0);
        lambda.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
        lambda.visitInsn(Opcodes.ARETURN);
        lambda.visitMaxs(0, 0);
        lambda.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
