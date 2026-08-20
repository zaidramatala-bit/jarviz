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

public class FilteredClassVisitorTest {

    private static final String CLASS_NAME = "com.vrbo.jarviz.util.couplingtest.GeneratedSource";

    private static final String INTERNAL_CLASS_NAME = "com/vrbo/jarviz/util/couplingtest/GeneratedSource";

    @Test
    public void testVisitJava8ClassFile() {
        assertCouplingIsCollected(Opcodes.V1_8);
    }

    @Test
    public void testVisitJava11ClassFile() {
        assertCouplingIsCollected(Opcodes.V11);
    }

    @Test
    public void testVisitJava17ClassFile() {
        assertCouplingIsCollected(Opcodes.V17);
    }

    @Test
    public void testVisitJava21ClassFile() {
        assertCouplingIsCollected(Opcodes.V21);
    }

    /**
     * Records and sealed types are only visitable with the ASM8 and ASM9 APIs respectively,
     * so a class carrying both attributes fails on older API levels.
     */
    @Test
    public void testVisitClassFileWithRecordAndSealedTypeAttributes() {
        final byte[] classData = generateClass(Opcodes.V21, true);

        assertThat(collectCouplings(classData))
            .containsExactly(coupling("run", "java.lang.Math", "max"));
    }

    private void assertCouplingIsCollected(final int classFileVersion) {
        final byte[] classData = generateClass(classFileVersion, false);

        assertThat(classData[7]).isEqualTo((byte) classFileVersion);
        assertThat(collectCouplings(classData))
            .containsExactly(coupling("run", "java.lang.Math", "max"));
    }

    private List<MethodCoupling> collectCouplings(final byte[] classData) {
        final List<MethodCoupling> couplings = new ArrayList<>();
        final Collector collector = couplings::add;

        new FilteredClassVisitor(CLASS_NAME, collector, classData).visit();

        return couplings;
    }

    private static MethodCoupling coupling(final String sourceMethodName,
                                           final String targetClassName,
                                           final String targetMethodName) {
        return new MethodCoupling.Builder()
                   .source(new Method.Builder()
                               .className(CLASS_NAME)
                               .methodName(sourceMethodName)
                               .build())
                   .target(new Method.Builder()
                               .className(targetClassName)
                               .methodName(targetMethodName)
                               .build())
                   .build();
    }

    /**
     * Generates a class of the given class file version, containing a single method
     * {@code int run(int, int)} that calls {@code java.lang.Math.max}.
     *
     * @param classFileVersion   The class file version, eg: {@link Opcodes#V21}.
     * @param modernTypeMetadata Whether to add record and sealed type attributes to the class.
     * @return The bytes of the generated class.
     */
    private static byte[] generateClass(final int classFileVersion, final boolean modernTypeMetadata) {
        final ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(classFileVersion,
                          Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                          INTERNAL_CLASS_NAME,
                          null,
                          "java/lang/Object",
                          null);

        if (modernTypeMetadata) {
            classWriter.visitRecordComponent("value", "I", null).visitEnd();
            classWriter.visitPermittedSubclass(INTERNAL_CLASS_NAME + "$Impl");
        }

        final MethodVisitor methodVisitor =
            classWriter.visitMethod(Opcodes.ACC_PUBLIC, "run", "(II)I", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 1);
        methodVisitor.visitVarInsn(Opcodes.ILOAD, 2);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false);
        methodVisitor.visitInsn(Opcodes.IRETURN);
        methodVisitor.visitMaxs(2, 3);
        methodVisitor.visitEnd();

        classWriter.visitEnd();

        return classWriter.toByteArray();
    }
}
