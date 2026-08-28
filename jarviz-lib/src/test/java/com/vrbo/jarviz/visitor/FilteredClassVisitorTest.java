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

public class FilteredClassVisitorTest {

    private static final String RECORD_CLASS = "com/foo/bar/MyRecord";

    private static final String SEALED_CLASS = "com/foo/bar/MySealed";

    /**
     * A record carries a Record attribute, which ASM only exposes at api level ASM8 and above.
     */
    @Test
    public void testVisitRecordClass() {
        final List<MethodCoupling> couplings = visit(RECORD_CLASS, recordClass());

        assertThat(couplings).hasSize(1);
        assertThat(couplings.get(0).getSource().getClassName()).isEqualTo("com.foo.bar.MyRecord");
        assertThat(couplings.get(0).getTarget().getClassName()).isEqualTo("com.foo.bar.Other");
    }

    /**
     * A sealed class carries a PermittedSubclasses attribute, which ASM only exposes at api level ASM9 and above.
     */
    @Test
    public void testVisitSealedClass() {
        final List<MethodCoupling> couplings = visit(SEALED_CLASS, sealedClass());

        assertThat(couplings).hasSize(1);
        assertThat(couplings.get(0).getSource().getClassName()).isEqualTo("com.foo.bar.MySealed");
        assertThat(couplings.get(0).getTarget().getClassName()).isEqualTo("com.foo.bar.Other");
    }

    private static List<MethodCoupling> visit(final String className, final byte[] classData) {
        final List<MethodCoupling> couplings = new ArrayList<>();
        new FilteredClassVisitor(className, couplings::add, classData).visit();

        return couplings;
    }

    private static byte[] recordClass() {
        final ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V17, Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_RECORD,
                          RECORD_CLASS, null, "java/lang/Record", null);
        classWriter.visitRecordComponent("value", "I", null).visitEnd();
        writeCallerMethod(classWriter);
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    private static byte[] sealedClass() {
        final ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V17, Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER,
                          SEALED_CLASS, null, "java/lang/Object", null);
        classWriter.visitPermittedSubclass("com/foo/bar/MyPermittedSubclass");
        writeCallerMethod(classWriter);
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    private static void writeCallerMethod(final ClassWriter classWriter) {
        final MethodVisitor methodVisitor =
            classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "caller", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "com/foo/bar/Other", "callee", "()V", false);
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }
}
