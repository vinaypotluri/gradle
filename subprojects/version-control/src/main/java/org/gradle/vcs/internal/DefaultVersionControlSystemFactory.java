/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.vcs.internal;

import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.VersionControlSystem;
import org.gradle.vcs.VersionRef;
import org.gradle.vcs.git.GitVersionControlSpec;
import org.gradle.vcs.git.internal.GitVersionControlSystem;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultVersionControlSystemFactory implements VersionControlSystemFactory {
    private final CacheRepository cacheRepository;
    private final Map<Class<? extends VersionControlSpec>, VersionControlSystem> versionControlSystems;

    DefaultVersionControlSystemFactory(CacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
        // TODO: Move this map to a registry and inject the registry.
        this.versionControlSystems = new HashMap<Class<? extends VersionControlSpec>, VersionControlSystem>();
        versionControlSystems.put(DirectoryRepositorySpec.class, new SimpleVersionControlSystem());
        versionControlSystems.put(GitVersionControlSpec.class, new GitVersionControlSystem());
    }

    @Override
    public VersionControlSystem create(VersionControlSpec spec) {
        return new ThreadSafeVersionControlSystem(versionControlSystems.get(spec.getClass()), cacheRepository);
    }

    private static final class ThreadSafeVersionControlSystem implements VersionControlSystem {
        private final VersionControlSystem delegate;
        private final CacheRepository cacheRepository;

        private ThreadSafeVersionControlSystem(VersionControlSystem delegate, CacheRepository cacheRepository) {
            this.delegate = delegate;
            this.cacheRepository = cacheRepository;
        }

        @Override
        public Set<VersionRef> getAvailableVersions(VersionControlSpec spec) {
            return delegate.getAvailableVersions(spec);
        }

        @Override
        public File populate(final File versionDir, final VersionRef ref, final VersionControlSpec spec) {
            PersistentCache cache = cacheRepository
                .cache(versionDir)
                .withLockOptions(mode(FileLockManager.LockMode.Exclusive))
                .open();
            DelegatePopulator populator = new DelegatePopulator(delegate, versionDir, ref, spec);
            cache.useCache(populator);
            cache.close();
            return populator.workingDir;
        }

        private static final class DelegatePopulator implements Runnable {
            private final VersionControlSystem delegate;
            private final File versionDir;
            private final VersionRef ref;
            private final VersionControlSpec spec;
            File workingDir;

            DelegatePopulator(VersionControlSystem delegate, File versionDir, VersionRef ref, VersionControlSpec spec) {
                this.delegate = delegate;
                this.ref = ref;
                this.versionDir = versionDir;
                this.spec = spec;
            }

            @Override
            public void run() {
                workingDir = delegate.populate(versionDir, ref, spec);
            }
        }
    }
}
