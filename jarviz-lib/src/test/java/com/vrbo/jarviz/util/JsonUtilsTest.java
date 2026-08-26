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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.immutables.value.Value;
import org.junit.Test;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import static com.vrbo.jarviz.util.JsonUtils.fromJsonString;
import static com.vrbo.jarviz.util.JsonUtils.toJsonString;

public class JsonUtilsTest {

    @Test
    public void jsonRoundTripTest() {
        final TestFooClass testFooClass =
            new TestFooClass.Builder()
                .name("Hello World!")
                .number(731946825)
                .trueOrNot(true)
                .bar(new TestBarClass.Builder()
                         .id(UUID.fromString("f90ad4f8-beef-cafe-feed-7898d9e629a5"))
                         .type("Food")
                         .dateTime(LocalDateTime.of(2020, 7, 11, 12, 35, 46))
                         .build())
                .build();

        assertThat(fromJsonString(toJsonString(testFooClass), TestFooClass.class))
            .isEqualTo(testFooClass);
    }

    @Test
    public void testToJsonString_CollectionIsWrappedUnderResults() {
        assertThat(toJsonString(Arrays.asList("a", "b")))
            .isEqualTo("{\"results\":[\"a\",\"b\"]}");

        assertThat(toJsonString(Collections.emptyList()))
            .isEqualTo("{\"results\":[]}");
    }

    @Test
    public void testToJsonString_Null() {
        assertThat(toJsonString(null)).isEqualTo("null");
    }

    @Test
    public void testToJsonString_UnserializableObject() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> toJsonString(new Object()));
    }

    @Test
    public void testToJsonString_LocalDateTimeIsWrittenAsFormattedString() {
        assertThat(toJsonString(new TestBarClass.Builder()
                                    .id(UUID.fromString("f90ad4f8-beef-cafe-feed-7898d9e629a5"))
                                    .dateTime(LocalDateTime.of(2020, 7, 11, 12, 35, 46))
                                    .build()))
            .contains("\"2020-07-11T12:35:46\"");
    }

    @Test
    public void testFromJsonString_MalformedJson() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> fromJsonString("{not json", TestBarClass.class));
    }

    @Test
    public void testFromJsonString_MissingMandatoryAttribute() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> fromJsonString("{\"type\":\"Food\"}", TestBarClass.class));
    }

    @Test
    public void testFromJsonString_UnknownAttribute() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> fromJsonString("{\"id\":\"f90ad4f8-beef-cafe-feed-7898d9e629a5\","
                                            + "\"dateTime\":\"2020-07-11T12:35:46\",\"bogus\":1}",
                                            TestBarClass.class));
    }

    @Test
    public void testJsonRoundTrip_EmptyOptionalDefaultsToEmpty() {
        final TestBarClass bar = new TestBarClass.Builder()
                                     .id(UUID.fromString("f90ad4f8-beef-cafe-feed-7898d9e629a5"))
                                     .dateTime(LocalDateTime.of(2020, 7, 11, 12, 35, 46))
                                     .build();

        final TestBarClass roundTripped = fromJsonString(toJsonString(bar), TestBarClass.class);

        assertThat(roundTripped).isEqualTo(bar);
        assertThat(roundTripped.getType()).isEmpty();
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableTestFooClass.class)
    @JsonDeserialize(as = ImmutableTestFooClass.class)
    interface TestFooClass {

        String getName();

        int getNumber();

        boolean getTrueOrNot();

        TestBarClass getBar();

        class Builder extends ImmutableTestFooClass.Builder {}
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableTestBarClass.class)
    @JsonDeserialize(as = ImmutableTestBarClass.class)
    interface TestBarClass {

        UUID getId();

        Optional<String> getType();

        LocalDateTime getDateTime();

        class Builder extends ImmutableTestBarClass.Builder {}
    }
}
