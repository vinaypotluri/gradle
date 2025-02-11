/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.testing.junitplatform;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.process.internal.worker.WorkerProcessBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;

@UsedByScanPlugin("test-retry")
public class JUnitPlatformTestFramework implements TestFramework {
    private final JUnitPlatformOptions options;
    private final DefaultTestFilter filter;
    private final boolean useImplementationDependencies;

    // Used by org.gradle.test-retry plugin.
    // TODO: Update plugin to pass in correct value for useImplementationDependencies when copying the framework
    // Or better yet, make it so the plugin doesn't need to access internal APIs.
    @Deprecated
    @SuppressWarnings("unused")
    public JUnitPlatformTestFramework(DefaultTestFilter filter) {
        this(filter, true);
    }

    public JUnitPlatformTestFramework(DefaultTestFilter filter, boolean useImplementationDependencies) {
        this.filter = filter;
        this.useImplementationDependencies = useImplementationDependencies;
        this.options = new JUnitPlatformOptions();
    }

    @Override
    public WorkerTestClassProcessorFactory getProcessorFactory() {
        if (!JavaVersion.current().isJava8Compatible()) {
            throw new UnsupportedJavaRuntimeException("Running JUnit Platform requires Java 8+, please configure your test java executable with Java 8 or higher.");
        }
        return new JUnitPlatformTestClassProcessorFactory(new JUnitPlatformSpec(options,
            filter.getIncludePatterns(), filter.getExcludePatterns(),
            filter.getCommandLineIncludePatterns()));
    }

    @Override
    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return new Action<WorkerProcessBuilder>() {
            @Override
            public void execute(@Nonnull WorkerProcessBuilder workerProcessBuilder) {
                workerProcessBuilder.sharedPackages("org.junit");
            }
        };
    }

    @Override
    public List<String> getTestWorkerImplementationClasses() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getTestWorkerImplementationModules() {
        return ImmutableList.of("junit-platform-engine", "junit-platform-launcher", "junit-platform-commons");
    }

    @Override
    public boolean getUseImplementationDependencies() {
        return useImplementationDependencies;
    }

    @Override
    public JUnitPlatformOptions getOptions() {
        return options;
    }

    @Override
    public TestFrameworkDetector getDetector() {
        return null;
    }

    @Override
    public void close() throws IOException {
        // this test framework doesn't hold any state
    }

    static class JUnitPlatformTestClassProcessorFactory implements WorkerTestClassProcessorFactory, Serializable {
        private final JUnitPlatformSpec spec;

        JUnitPlatformTestClassProcessorFactory(JUnitPlatformSpec spec) {
            this.spec = spec;
        }

        @Override
        public TestClassProcessor create(ServiceRegistry serviceRegistry) {
            try {
                IdGenerator<?> idGenerator = serviceRegistry.get(IdGenerator.class);
                Clock clock = serviceRegistry.get(Clock.class);
                ActorFactory actorFactory = serviceRegistry.get(ActorFactory.class);
                Class<?> clazz = getClass().getClassLoader().loadClass("org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestClassProcessor");
                Constructor<?> constructor = clazz.getConstructor(JUnitPlatformSpec.class, IdGenerator.class, ActorFactory.class, Clock.class);
                return (TestClassProcessor) constructor.newInstance(spec, idGenerator, actorFactory, clock);
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
