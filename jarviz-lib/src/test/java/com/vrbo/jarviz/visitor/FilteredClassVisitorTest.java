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

import com.vrbo.jarviz.model.MethodCoupling;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that classes compiled for Java 17 (records and sealed types) can be scanned.
 * The fixtures are generated with ASM instead of Java sources, so that this module can
 * still be compiled and style checked with older tooling.
 */
public class FilteredClassVisitorTest {

    private static final int ACC_RECORD = 0x10000;

    @Test
    public void testVisitJava17Record() {
        final byte[] classData = generateRecordClass();
        final List<MethodCoupling> couplings = new ArrayList<>();

        new FilteredClassVisitor("com.foo.bar.MyRecord", couplings::add, classData).visit();

        assertThat(couplings).isNotEmpty();
        assertThat(couplings.stream()
                            .anyMatch(c -> c.getSource().getClassName().equals("com.foo.bar.MyRecord")
                                           && c.getTarget().getClassName().equals("java.lang.String")
                                           && c.getTarget().getMethodName().equals("trim")))
            .isTrue();
    }

    @Test
    public void testVisitJava17SealedClass() {
        final byte[] classData = generateSealedClass();
        final List<MethodCoupling> couplings = new ArrayList<>();

        new FilteredClassVisitor("com.foo.bar.MySealed", couplings::add, classData).visit();

        assertThat(couplings).isNotEmpty();
        assertThat(couplings.stream()
                            .anyMatch(c -> c.getSource().getClassName().equals("com.foo.bar.MySealed")
                                           && c.getTarget().getClassName().equals("java.lang.String")
                                           && c.getTarget().getMethodName().equals("trim")))
            .isTrue();
    }

    /**
     * Generates the bytecode of a Java 17 record: {@code record MyRecord(String name) { ... }}.
     *
     * @return The class data.
     */
    private static byte[] generateRecordClass() {
        final ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V17,
                          Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | ACC_RECORD,
                          "com/foo/bar/MyRecord",
                          null,
                          "java/lang/Record",
                          null);
        classWriter.visitRecordComponent("name", "Ljava/lang/String;", null).visitEnd();
        classWriter.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "name", "Ljava/lang/String;", null, null)
                   .visitEnd();
        addTrimmedNameMethod(classWriter, "com/foo/bar/MyRecord");
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    /**
     * Generates the bytecode of a Java 17 sealed class:
     * {@code sealed class MySealed permits MySealedChild { ... }}.
     *
     * @return The class data.
     */
    private static byte[] generateSealedClass() {
        final ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V17,
                          Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                          "com/foo/bar/MySealed",
                          null,
                          "java/lang/Object",
                          null);
        classWriter.visitPermittedSubclass("com/foo/bar/MySealedChild");
        classWriter.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "name", "Ljava/lang/String;", null, null)
                   .visitEnd();
        addTrimmedNameMethod(classWriter, "com/foo/bar/MySealed");
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    /**
     * Adds a {@code String trimmedName()} method that calls {@link String#trim()},
     * so that the visitor has a method coupling to collect.
     *
     * @param classWriter     The class writer.
     * @param internalName    The internal name of the class being generated.
     */
    private static void addTrimmedNameMethod(final ClassWriter classWriter, final String internalName) {
        final MethodVisitor methodVisitor =
            classWriter.visitMethod(Opcodes.ACC_PUBLIC, "trimmedName", "()Ljava/lang/String;", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, internalName, "name", "Ljava/lang/String;");
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
    }
}
