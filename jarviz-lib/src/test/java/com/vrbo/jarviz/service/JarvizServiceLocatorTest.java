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

import org.glassfish.hk2.api.ServiceLocator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vrbo.jarviz.config.JarvizConfig;

import static org.assertj.core.api.Assertions.assertThat;

public class JarvizServiceLocatorTest {

    private ServiceLocator serviceLocator;

    @Before
    public void setUp() {
        serviceLocator = JarvizServiceLocator.createServiceLocator(new JarvizConfig.Builder().build(),
                                                                  JarvizServiceLocatorTest.class.getName());
    }

    @After
    public void tearDown() {
        serviceLocator.shutdown();
    }

    @Test
    public void testJarvizConfigIsBound() {
        assertThat(serviceLocator.getService(JarvizConfig.class)).isNotNull();
    }

    @Test
    public void testArtifactDiscoveryServiceIsInjectable() {
        assertThat(serviceLocator.getService(ArtifactDiscoveryService.class))
            .isInstanceOf(MavenArtifactDiscoveryService.class);
    }

    @Test
    public void testClassLoaderServiceIsInjectable() {
        assertThat(serviceLocator.getService(ClassLoaderService.class))
            .isInstanceOf(JarClassLoaderService.class);
    }
}
