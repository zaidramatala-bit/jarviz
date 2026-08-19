/*
* Copyright 2024 Expedia, Inc.
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

import java.io.File;
import java.net.URL;
import java.util.List;

import org.junit.Test;

import com.vrbo.jarviz.config.JarvizConfig;
import com.vrbo.jarviz.model.Artifact;
import com.vrbo.jarviz.model.Method;
import com.vrbo.jarviz.model.MethodCoupling;
import com.vrbo.jarviz.model.ShadowClass;
import com.vrbo.jarviz.service.JarClassLoaderService;
import com.vrbo.jarviz.service.MavenArtifactDiscoveryService;
import com.vrbo.jarviz.service.UsageCollector;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Jarviz can analyze bytecode compiled for Java 21 (class file major version 65).
 * The fixture jar is compiled by a JDK 21 compiler and checked in, since this project is built
 * with a Java 8 target. See src/test/resources/java21/README.md.
 */
public class Java21BytecodeAnalysisTest {

    private static final String FIXTURE_RESOURCE = "java21/java21-fixture-1.0.0.jar";

    private static final int JAVA_21_CLASS_FILE_MAJOR_VERSION = 65;

    private static final String PACKAGE = "com.vrbo.jarviz.java21.";

    @Test
    public void testAnalyzeJava21Artifact() throws Exception {
        final List<ShadowClass> classes = loadFixtureClasses();

        assertThat(classes).extracting(ShadowClass::getClassName)
                           .containsExactly(PACKAGE + "Java21Record",
                                            PACKAGE + "Java21Source",
                                            PACKAGE + "Java21Target");

        // Guards against the fixture being silently recompiled for an older Java version.
        for (ShadowClass shadowClass : classes) {
            assertThat(classFileMajorVersion(shadowClass)).isEqualTo(JAVA_21_CLASS_FILE_MAJOR_VERSION);
        }

        final UsageCollector usageCollector = new UsageCollector();
        for (ShadowClass shadowClass : classes) {
            new FilteredClassVisitor(shadowClass.getClassName(), usageCollector, shadowClass.getClassBytes()).visit();
        }

        assertThat(usageCollector.getMethodCouplings())
            .contains(methodCoupling(PACKAGE + "Java21Source", "callTarget",
                                     PACKAGE + "Java21Target", "getMessage"),
                      methodCoupling(PACKAGE + "Java21Source", "lambda$callTargetFromLambda$0",
                                     PACKAGE + "Java21Target", "getMessage"));
    }

    private static List<ShadowClass> loadFixtureClasses() {
        final URL fixtureUrl = Thread.currentThread().getContextClassLoader().getResource(FIXTURE_RESOURCE);
        assertThat(fixtureUrl).as("Java 21 fixture jar %s", FIXTURE_RESOURCE).isNotNull();

        final File fixtureJar = new File(fixtureUrl.getPath());
        final JarvizConfig config = new JarvizConfig.Builder()
                                        .artifactDirectory(fixtureJar.getParent())
                                        .build();
        final JarClassLoaderService classLoaderService =
            new JarClassLoaderService(new MavenArtifactDiscoveryService(config));

        return classLoaderService.getAllClasses(new Artifact.Builder()
                                                    .groupId("com.vrbo.jarviz")
                                                    .artifactId("java21-fixture")
                                                    .version("1.0.0")
                                                    .build());
    }

    private static int classFileMajorVersion(final ShadowClass shadowClass) {
        final byte[] classBytes = shadowClass.getClassBytes();
        return ((classBytes[6] & 0xFF) << 8) | (classBytes[7] & 0xFF);
    }

    private static MethodCoupling methodCoupling(final String sourceClass,
                                                 final String sourceMethod,
                                                 final String targetClass,
                                                 final String targetMethod) {
        return new MethodCoupling.Builder()
                   .source(new Method.Builder().className(sourceClass).methodName(sourceMethod).build())
                   .target(new Method.Builder().className(targetClass).methodName(targetMethod).build())
                   .build();
    }
}
