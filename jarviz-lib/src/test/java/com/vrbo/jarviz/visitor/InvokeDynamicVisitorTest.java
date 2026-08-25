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

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.vrbo.jarviz.model.MethodCoupling;
import com.vrbo.jarviz.service.UsageCollector;
import com.vrbo.jarviz.visitor.lambdatest.LambdaTestClass;

import static org.assertj.core.api.Assertions.assertThat;

public class InvokeDynamicVisitorTest {

    @Test
    public void testLambdaCouplingPointsToImplementationMethod() throws Exception {
        final List<String> targets = collectTargets();

        assertThat(targets)
            .as("the lambda implementation method should be reported as the dependency")
            .contains(LambdaTestClass.class.getName() + "#lambda$useLambda$0");

        assertThat(targets)
            .as("the LambdaMetafactory bootstrap method is a JVM detail, not a real dependency")
            .doesNotContain("java.lang.invoke.LambdaMetafactory#metafactory");
    }

    @Test
    public void testMethodReferenceCouplingPointsToReferencedMethod() throws Exception {
        assertThat(collectTargets())
            .as("the referenced method should be reported as the dependency")
            .contains(LambdaTestClass.class.getName() + "#makeValue");
    }

    private static List<String> collectTargets() throws Exception {
        final UsageCollector collector = new UsageCollector();
        new FilteredClassVisitor(LambdaTestClass.class.getName(), collector).visit();

        return collector.getMethodCouplings().stream()
                        .map(MethodCoupling::getTarget)
                        .map(m -> m.getClassName() + "#" + m.getMethodName())
                        .collect(Collectors.toList());
    }
}
