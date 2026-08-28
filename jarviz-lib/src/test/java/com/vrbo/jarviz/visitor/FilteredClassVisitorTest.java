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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.vrbo.jarviz.model.Collector;
import com.vrbo.jarviz.model.Method;
import com.vrbo.jarviz.model.MethodCoupling;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the class visitor can scan Java 17 class files, which carry the
 * {@code Record} and {@code PermittedSubclasses} attributes.
 * The fixtures are generated as bytecode because the Checkstyle version used by
 * this build cannot parse record or sealed source syntax.
 */
public class FilteredClassVisitorTest {

    private static final String RECORD_CLASS = "com/vrbo/jarviz/visitor/GeneratedRecord";

    private static final String SEALED_CLASS = "com/vrbo/jarviz/visitor/GeneratedSealed";

    private static final String SEALED_SUB_CLASS = "com/vrbo/jarviz/visitor/GeneratedSealedChild";

    @Test
    public void testVisitRecordClass() {
        final List<MethodCoupling> couplings = visit(RECORD_CLASS, generateRecordClass());

        assertThat(couplings).contains(
            new MethodCoupling.Builder()
                .source(new Method.Builder().className("com.vrbo.jarviz.visitor.GeneratedRecord").methodName("trimmed").build())
                .target(new Method.Builder().className("java.lang.String").methodName("trim").build())
                .build());
    }

    @Test
    public void testVisitSealedClass() {
        final List<MethodCoupling> couplings = visit(SEALED_CLASS, generateSealedClass());

        assertThat(couplings).contains(
            new MethodCoupling.Builder()
                .source(new Method.Builder().className("com.vrbo.jarviz.visitor.GeneratedSealed").methodName("describe").build())
                .target(new Method.Builder().className("java.lang.String").methodName("trim").build())
                .build());
    }

    private static List<MethodCoupling> visit(final String internalClassName, final byte[] classData) {
        final List<MethodCoupling> couplings = new ArrayList<>();
        final Collector collector = couplings::add;

        new FilteredClassVisitor(internalClassName, collector, classData).visit();

        return couplings;
    }

    /**
     * Generates the equivalent of {@code public record GeneratedRecord(String value)}
     * with an extra {@code trimmed()} method calling {@link String#trim()}.
     */
    private static byte[] generateRecordClass() {
        final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V17,
                          Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_RECORD,
                          RECORD_CLASS,
                          null,
                          "java/lang/Record",
                          null);
        classWriter.visitRecordComponent("value", "Ljava/lang/String;", null).visitEnd();
        classWriter.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "value", "Ljava/lang/String;", null, null).visitEnd();

        final MethodVisitor constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Record", "<init>", "()V", false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitFieldInsn(Opcodes.PUTFIELD, RECORD_CLASS, "value", "Ljava/lang/String;");
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        addTrimMethod(classWriter, RECORD_CLASS, "trimmed");

        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    /**
     * Generates the equivalent of
     * {@code public abstract sealed class GeneratedSealed permits GeneratedSealedChild}
     * with a {@code describe()} method calling {@link String#trim()}.
     */
    private static byte[] generateSealedClass() {
        final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V17,
                          Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER,
                          SEALED_CLASS,
                          null,
                          "java/lang/Object",
                          null);
        classWriter.visitPermittedSubclass(SEALED_SUB_CLASS);

        addTrimMethod(classWriter, SEALED_CLASS, "describe");

        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    private static void addTrimMethod(final ClassWriter classWriter, final String owner, final String methodName) {
        final MethodVisitor methodVisitor =
            classWriter.visitMethod(Opcodes.ACC_PUBLIC, methodName, "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }
}
