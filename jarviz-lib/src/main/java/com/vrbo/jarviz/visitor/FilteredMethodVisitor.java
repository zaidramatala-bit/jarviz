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

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.vrbo.jarviz.model.Collector;
import com.vrbo.jarviz.model.Method;
import com.vrbo.jarviz.model.MethodCoupling;

import static com.vrbo.jarviz.util.NamingUtils.toSourceCodeFormat;

public class FilteredMethodVisitor extends MethodVisitor {

    private final Method sourceMethod;

    private final Collector collect;

    public FilteredMethodVisitor(final Method sourceMethod,
                                 final MethodVisitor methodVisitor,
                                 final Collector collect) {
        super(Opcodes.ASM7, methodVisitor);
        this.sourceMethod = sourceMethod;
        this.collect = collect;
    }

    @Override
    public void visitMethodInsn(final int opcode,
                                final String owner,
                                final String name,
                                final String descriptor,
                                final boolean isInterface) {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        handleTargetMethod(owner, name, descriptor);
    }

    @Override
    public void visitInvokeDynamicInsn(final String name,
                                       final String descriptor,
                                       final Handle bootstrapMethodHandle,
                                       final Object... bootstrapMethodArguments) {
        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);

        // The implementation methods a dynamic call site resolves to (for example the body of a
        // lambda or the target of a method reference) are passed as method handles among the
        // bootstrap method arguments.
        boolean implementationFound = false;
        for (final Object argument : bootstrapMethodArguments) {
            if (argument instanceof Handle && isMethodHandle((Handle) argument)) {
                final Handle implementation = (Handle) argument;
                handleTargetMethod(implementation.getOwner(), implementation.getName(), implementation.getDesc());
                implementationFound = true;
            }
        }

        // Bootstrap methods provided by the JDK (LambdaMetafactory, StringConcatFactory, ...) are
        // linkage machinery rather than a dependency of the enclosing method, so they are only
        // reported when a call site uses a custom bootstrap method and no implementation method
        // handle is available.
        if (!implementationFound && !isJdkBootstrapMethod(bootstrapMethodHandle)) {
            handleTargetMethod(bootstrapMethodHandle.getOwner(),
                               bootstrapMethodHandle.getName(),
                               bootstrapMethodHandle.getDesc());
        }
    }

    static boolean isMethodHandle(final Handle handle) {
        switch (handle.getTag()) {
            case Opcodes.H_INVOKEVIRTUAL:
            case Opcodes.H_INVOKESTATIC:
            case Opcodes.H_INVOKESPECIAL:
            case Opcodes.H_NEWINVOKESPECIAL:
            case Opcodes.H_INVOKEINTERFACE:
                return true;
            default:
                return false;
        }
    }

    static boolean isJdkBootstrapMethod(final Handle handle) {
        return handle.getOwner().startsWith("java/lang/invoke/");
    }

    private void handleTargetMethod(final String targetClassName,
                                    final String targetMethodName,
                                    final String targetMethodDescriptor) {
        final Method targetMethod = new Method.Builder()
                                        .className(cleanseClassName(toSourceCodeFormat(targetClassName)))
                                        .methodName(targetMethodName)
                                        .build();

        collect.collectMethodCoupling(
            new MethodCoupling.Builder()
                .source(sourceMethod)
                .target(targetMethod)
                .build()
        );
    }

    static String cleanseClassName(final String methodName) {
        if (methodName.startsWith("WEB-INF.classes.")) {
            return methodName.substring(16);
        }

        return methodName;
    }
}
