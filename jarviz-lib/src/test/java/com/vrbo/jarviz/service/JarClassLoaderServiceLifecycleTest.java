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

package com.vrbo.jarviz.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import com.vrbo.jarviz.model.Artifact;
import com.vrbo.jarviz.model.ShadowClass;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the class loader created for an artifact is released once its classes have been read.
 */
public class JarClassLoaderServiceLifecycleTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final List<URLClassLoader> createdClassLoaders = new ArrayList<>();

    @Test
    public void testClassLoaderIsClosedAfterReadingClasses() throws IOException {
        final File jar = jarContaining("com/example/Sample");
        final JarClassLoaderService service = serviceFor(jar);

        final List<ShadowClass> classes = service.getAllClasses(artifact());

        assertThat(classes).extracting(ShadowClass::getClassName)
                           .containsExactly("com.example.Sample");
        assertThat(createdClassLoaders).hasSize(1);
        // A closed URLClassLoader no longer serves resources from its jar
        assertThat(createdClassLoaders.get(0).getResourceAsStream("com/example/Sample.class")).isNull();
    }

    @Test
    public void testClassLoaderIsClosedWhenReadingFails() throws IOException {
        final File jar = jarContaining("com/example/Sample");
        final JarClassLoaderService service = serviceFor(jar);

        assertThat(catchThrowableOfGetAllClasses(service)).isNotNull();
        assertThat(createdClassLoaders).hasSize(1);
        assertThat(createdClassLoaders.get(0).getResourceAsStream("com/example/Sample.class")).isNull();
    }

    private Throwable catchThrowableOfGetAllClasses(final JarClassLoaderService service) {
        try {
            service.getAllClasses(artifact(), className -> {
                throw new IllegalStateException("filter failed");
            });
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private JarClassLoaderService serviceFor(final File jar) {
        return new JarClassLoaderService(artifact -> jar) {
            @Override
            URLClassLoader creatClassLoaderForJar(final String jarFileName) throws MalformedURLException {
                final URLClassLoader classLoader = super.creatClassLoaderForJar(jarFileName);
                createdClassLoaders.add(classLoader);
                return classLoader;
            }
        };
    }

    private static Artifact artifact() {
        return new Artifact.Builder()
                   .groupId("com.example")
                   .artifactId("sample")
                   .version("1.0.0")
                   .build();
    }

    private File jarContaining(final String internalClassName) throws IOException {
        final File jar = temporaryFolder.newFile("sample-1.0.0.jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(jar))) {
            jarOutputStream.putNextEntry(new JarEntry(internalClassName + ".class"));
            jarOutputStream.write(classBytes(internalClassName));
            jarOutputStream.closeEntry();
        }

        return jar;
    }

    private static byte[] classBytes(final String internalClassName) {
        final ClassWriter classWriter = new ClassWriter(0);
        classWriter.visit(Opcodes.V1_8,
                          Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                          internalClassName,
                          null,
                          "java/lang/Object",
                          null);
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }
}
