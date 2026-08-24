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

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vrbo.jarviz.model.MethodCoupling;

import static org.assertj.core.api.Assertions.assertThat;

public class Java21VisitorTest {

    private static final String FIXTURE_RESOURCE =
        "com/vrbo/jarviz/visitor/java21/Java21Fixture.java";

    private static final String FIXTURE_CLASS =
        "com.vrbo.jarviz.visitor.java21.Java21Fixture";

    private static final String TARGET_CLASS = FIXTURE_CLASS + "$Target";

    private static Path compiledFixture;

    @BeforeClass
    public static void compileJava21Fixture() throws Exception {
        Assume.assumeTrue(javaFeatureVersion() >= 21);

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final URL source = Java21VisitorTest.class.getClassLoader().getResource(FIXTURE_RESOURCE);
        compiledFixture = Files.createTempDirectory("jarviz-java21-fixture");

        assertThat(compiler).isNotNull();
        assertThat(source).isNotNull();
        assertThat(compiler.run(null,
                                null,
                                null,
                                "--release",
                                "21",
                                "-d",
                                compiledFixture.toString(),
                                Paths.get(source.toURI()).toString()))
            .isEqualTo(0);
    }

    private static int javaFeatureVersion() {
        final String specificationVersion = System.getProperty("java.specification.version");
        return Integer.parseInt(specificationVersion.startsWith("1.")
                                ? specificationVersion.substring(2)
                                : specificationVersion);
    }

    @Test
    public void testVisitsRecordsAndSealedTypes() throws Exception {
        final List<MethodCoupling> recordCouplings = collectCouplings(FIXTURE_CLASS + "$Name");
        final List<MethodCoupling> interfaceCouplings = collectCouplings(FIXTURE_CLASS + "$Formatter");
        final List<MethodCoupling> sealedClassCouplings = collectCouplings(FIXTURE_CLASS + "$Operation");

        assertThat(hasCoupling(interfaceCouplings,
                               "format",
                               FIXTURE_CLASS + "$Formatter",
                               "normalize"))
            .isTrue();
        assertThat(hasCoupling(interfaceCouplings, "format", TARGET_CLASS, "decorate")).isTrue();
        assertThat(hasCoupling(sealedClassCouplings, "execute", TARGET_CLASS, "fallback")).isTrue();
        assertThat(hasTarget(recordCouplings, "java.lang.runtime.ObjectMethods")).isFalse();
    }

    @Test
    public void testResolvesLambdaImplementationMethods() throws Exception {
        final List<MethodCoupling> couplings = collectCouplings(FIXTURE_CLASS);

        assertThat(hasCoupling(couplings, "lambda", FIXTURE_CLASS, "lambda$lambda$0")).isTrue();
        assertThat(hasCoupling(couplings, "lambda$lambda$0", TARGET_CLASS, "decorate")).isTrue();
        assertThat(hasCoupling(couplings, "methodReference", TARGET_CLASS, "decorate")).isTrue();
        assertThat(hasTarget(couplings, "java.lang.invoke.LambdaMetafactory")).isFalse();
    }

    @Test
    public void testIgnoresStringConcatenationBootstrapMethod() throws Exception {
        final List<MethodCoupling> couplings = collectCouplings(FIXTURE_CLASS);

        assertThat(hasCoupling(couplings, "concatenate", TARGET_CLASS, "decorate")).isTrue();
        assertThat(hasTarget(couplings, "java.lang.invoke.StringConcatFactory")).isFalse();
    }

    @Test
    public void testVisitsNestmatesAndPrivateInterfaceMethods() throws Exception {
        final List<MethodCoupling> nestmateCouplings = collectCouplings(FIXTURE_CLASS + "$Nested");
        final List<MethodCoupling> interfaceCouplings = collectCouplings(FIXTURE_CLASS + "$Formatter");

        assertThat(hasCoupling(nestmateCouplings, "callPrivate", FIXTURE_CLASS, "privateValue")).isTrue();
        assertThat(hasCoupling(interfaceCouplings,
                               "format",
                               FIXTURE_CLASS + "$Formatter",
                               "normalize"))
            .isTrue();
    }

    @Test
    public void testExtractsPatternSwitchCouplingsWithoutBootstrapMethod() throws Exception {
        final List<MethodCoupling> couplings = collectCouplings(FIXTURE_CLASS);

        assertThat(hasCoupling(couplings, "patternSwitch", TARGET_CLASS, "decorate")).isTrue();
        assertThat(hasCoupling(couplings, "patternSwitch", TARGET_CLASS, "fallback")).isTrue();
        assertThat(hasTarget(couplings, "java.lang.runtime.SwitchBootstraps")).isFalse();
    }

    private static List<MethodCoupling> collectCouplings(final String className) throws Exception {
        final Path classFile =
            compiledFixture.resolve(className.replace('.', File.separatorChar) + ".class");
        final List<MethodCoupling> couplings = new ArrayList<>();

        assertThat(classFile).exists();
        final byte[] classData = Files.readAllBytes(classFile);
        assertThat(classFileMajorVersion(classData)).isEqualTo(65);

        new FilteredClassVisitor(className, couplings::add, classData).visit();

        return couplings;
    }

    private static int classFileMajorVersion(final byte[] classData) {
        return ((classData[6] & 0xFF) << 8) | (classData[7] & 0xFF);
    }

    private static boolean hasCoupling(final List<MethodCoupling> couplings,
                                       final String sourceMethod,
                                       final String targetClass,
                                       final String targetMethod) {
        return couplings.stream()
                        .anyMatch(coupling ->
                                      coupling.getSource().getMethodName().equals(sourceMethod)
                                      && coupling.getTarget().getClassName().equals(targetClass)
                                      && coupling.getTarget().getMethodName().equals(targetMethod));
    }

    private static boolean hasTarget(final List<MethodCoupling> couplings,
                                     final String targetClass) {
        return couplings.stream()
                        .anyMatch(coupling -> coupling.getTarget().getClassName().equals(targetClass));
    }
}
