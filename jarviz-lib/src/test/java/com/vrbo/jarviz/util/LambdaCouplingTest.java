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

package com.vrbo.jarviz.util;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vrbo.jarviz.model.MethodCoupling;
import com.vrbo.jarviz.service.UsageCollector;
import com.vrbo.jarviz.util.couplingtest.Foo;
import com.vrbo.jarviz.util.couplingtest.MyLambdaSource;
import com.vrbo.jarviz.visitor.FilteredClassVisitor;

import static org.assertj.core.api.Assertions.assertThat;

public class LambdaCouplingTest {

    @Test
    public void testLambdaCouplingsDoNotPointToBootstrapMethod() throws IOException {
        final List<MethodCoupling> couplings = collectCouplings();

        assertThat(couplings.stream()
                            .filter(c -> c.getTarget().getClassName().equals("java.lang.invoke.LambdaMetafactory"))
                            .collect(Collectors.toList()))
            .isEmpty();
    }

    @Test
    public void testLambdaCouplingPointsToImplementationMethod() throws IOException {
        final List<MethodCoupling> couplings = collectCouplings();

        assertThat(targetsOf(couplings, "useLambda"))
            .anyMatch(m -> m.getClassName().equals(MyLambdaSource.class.getName())
                           && m.getMethodName().startsWith("lambda$useLambda$"));
    }

    @Test
    public void testMethodReferenceCouplingPointsToReferencedMethod() throws IOException {
        final List<MethodCoupling> couplings = collectCouplings();

        assertThat(targetsOf(couplings, "useMethodReference"))
            .anyMatch(m -> m.getClassName().equals(Foo.class.getName())
                           && m.getMethodName().equals("getFooLongVal"));
    }

    private static List<com.vrbo.jarviz.model.Method> targetsOf(final List<MethodCoupling> couplings,
                                                                final String sourceMethodName) {
        return couplings.stream()
                        .filter(c -> c.getSource().getMethodName().equals(sourceMethodName))
                        .map(MethodCoupling::getTarget)
                        .collect(Collectors.toList());
    }

    private static List<MethodCoupling> collectCouplings() throws IOException {
        final UsageCollector collector = new UsageCollector();
        new FilteredClassVisitor(MyLambdaSource.class.getName(), collector).visit();

        return collector.getMethodCouplings();
    }
}
