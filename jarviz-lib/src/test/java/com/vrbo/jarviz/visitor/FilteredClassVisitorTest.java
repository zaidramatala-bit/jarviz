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
 * Verifies that the class visitor can read class files produced by modern Java releases.
 * Each class file format version is generated directly, so the tests do not depend on the
 * JDK used to build or run the project.
 */
public class FilteredClassVisitorTest {

    private static final int JAVA_8 = Opcodes.V1_8;

    private static final int JAVA_11 = Opcodes.V11;

    private static final int JAVA_17 = Opcodes.V17;

    private static final int JAVA_21 = Opcodes.V21;

    @Test
    public void testVisitJava8Class() {
        assertThat(couplingsOf(JAVA_8)).containsExactly("com.example.Caller#callTarget -> com.example.Target#run");
    }

    @Test
    public void testVisitJava11Class() {
        assertThat(couplingsOf(JAVA_11)).containsExactly("com.example.Caller#callTarget -> com.example.Target#run");
    }

    @Test
    public void testVisitJava17Class() {
        assertThat(couplingsOf(JAVA_17)).containsExactly("com.example.Caller#callTarget -> com.example.Target#run");
    }

    /**
     * Java 21 class files carry major version 65. Reading one used to fail with
     * {@code IllegalArgumentException: Unsupported class file major version 65}.
     */
    @Test
    public void testVisitJava21Class() {
        assertThat(couplingsOf(JAVA_21)).containsExactly("com.example.Caller#callTarget -> com.example.Target#run");
    }

    private static List<String> couplingsOf(final int classFileVersion) {
        final List<String> couplings = new ArrayList<>();
        final FilteredClassVisitor visitor =
            new FilteredClassVisitor("com.example.Caller",
                                     (final MethodCoupling coupling) -> couplings.add(describe(coupling)),
                                     callerClass(classFileVersion));
        visitor.visit();

        return couplings;
    }

    private static String describe(final MethodCoupling coupling) {
        return String.format("%s#%s -> %s#%s",
                             coupling.getSource().getClassName(),
                             coupling.getSource().getMethodName(),
                             coupling.getTarget().getClassName(),
                             coupling.getTarget().getMethodName());
    }

    /**
     * Generates {@code com.example.Caller}, whose only method calls {@code com.example.Target#run}.
     */
    private static byte[] callerClass(final int classFileVersion) {
        final ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(classFileVersion,
                          Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                          "com/example/Caller",
                          null,
                          "java/lang/Object",
                          null);

        final MethodVisitor method =
            classWriter.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "callTarget", "()V", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/Target", "run", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        classWriter.visitEnd();

        return classWriter.toByteArray();
    }
}
