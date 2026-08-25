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

package com.vrbo.jarviz.util.couplingtest;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LambdaSource {

    public long countNonEmpty(final List<String> values) {
        return values.stream().filter(a -> !a.isEmpty()).count();
    }

    public List<Long> mapWithMethodRef(final Stream<String> values) {
        final Foo foo = new Foo();
        return values.map(foo::getFooLongVal).collect(Collectors.toList());
    }

    public List<Bar> mapWithConstructorRef(final Stream<Integer> values) {
        return values.map(Bar::new).collect(Collectors.toList());
    }
}
