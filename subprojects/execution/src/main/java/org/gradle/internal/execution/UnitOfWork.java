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

package org.gradle.internal.execution;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.Describable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.execution.OutputSnapshotter.OutputFileSnapshottingException;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.fingerprint.InputFingerprinter;
import org.gradle.internal.execution.fingerprint.InputFingerprinter.InputFileFingerprintingException;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.execution.workspace.WorkspaceProvider;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public interface UnitOfWork extends Describable {
    /**
     * Determine the identity of the work unit that uniquely identifies it
     * among the other work units of the same type in the current build.
     */
    Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs);

    interface Identity {
        /**
         * The identity of the work unit that uniquely identifies it
         * among the other work units of the same type in the current build.
         */
        String getUniqueId();
    }

    /**
     * Executes the work synchronously.
     */
    WorkOutput execute(ExecutionRequest executionRequest);

    interface ExecutionRequest {
        File getWorkspace();

        Optional<InputChangesInternal> getInputChanges();

        Optional<ImmutableSortedMap<String, FileSystemSnapshot>> getPreviouslyProducedOutputs();
    }

    interface WorkOutput {
        WorkResult getDidWork();

        Object getOutput();
    }

    enum WorkResult {
        DID_WORK,
        DID_NO_WORK
    }

    default Object loadRestoredOutput(File workspace) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the {@link WorkspaceProvider} to allocate a workspace to execution this work in.
     */
    WorkspaceProvider getWorkspaceProvider();

    default Optional<Duration> getTimeout() {
        return Optional.empty();
    }

    default InputChangeTrackingStrategy getInputChangeTrackingStrategy() {
        return InputChangeTrackingStrategy.NONE;
    }

    /**
     * Capture the classloader of the work's implementation type.
     * There can be more than one type reported by the work; additional types are considered in visitation order.
     */
    default void visitImplementations(ImplementationVisitor visitor) {
        visitor.visitImplementation(getClass());
    }

    // TODO Move this to {@link InputVisitor}
    interface ImplementationVisitor {
        void visitImplementation(Class<?> implementation);
        void visitImplementation(ImplementationSnapshot implementation);
    }

    /**
     * Returns the fingerprinter used to fingerprint inputs.
     */
    InputFingerprinter getInputFingerprinter();

    /**
     * Visit identity inputs of the work.
     *
     * These are inputs that are passed to {@link #identify(Map, Map)} to calculate the identity of the work.
     * These are more expensive to calculate than regular inputs as they need to be calculated even if the execution of the work is short circuited by an identity cache.
     * They also cannot reuse snapshots taken during previous executions.
     * Because of these reasons only capture inputs as identity if they are actually used to calculate the identity of the work.
     * Any non-identity inputs should be visited when calling {@link #visitRegularInputs(InputVisitor)}.
     */
    default void visitIdentityInputs(InputVisitor visitor) {}

    /**
     * Visit regular inputs of the work.
     *
     * Regular inputs are inputs that are not used to calculate the identity of the work, but used to check up-to-dateness or to calculate the cache key.
     * To visit all inputs one must call both {@link #visitIdentityInputs(InputVisitor)} and {@link #visitRegularInputs(InputVisitor)}.
     */
    default void visitRegularInputs(InputVisitor visitor) {}

    interface InputVisitor {
        default void visitInputProperty(
            String propertyName,
            ValueSupplier value
        ) {}

        default void visitInputFileProperty(
            String propertyName,
            InputBehavior behavior,
            InputFileValueSupplier value
        ) {}
    }

    /**
     * Describes the behavior of an input property.
     */
    enum InputBehavior {
        /**
         * Non-incremental inputs.
         *
         * <ul>
         *     <li>Any change to the property value always triggers a full rebuild of the work</li>
         *     <li>Changes for the property cannot be queried via {@link org.gradle.work.InputChanges}</li>
         * </ul>
         */
        NON_INCREMENTAL(false, false),

        /**
         * Incremental inputs.
         *
         * <ul>
         *     <li>Changes to the property value can cause an incremental execution of the work</li>
         *     <li>Changes for the property can be queried via {@link org.gradle.work.InputChanges}</li>
         * </ul>
         */
        INCREMENTAL(true, false),

        /**
         * Primary (incremental) inputs.
         *
         * <ul>
         *     <li>Changes to the property value can cause an incremental execution</li>
         *     <li>Changes for the property can be queried via {@link org.gradle.work.InputChanges}</li>
         *     <li>When the property is empty, the work is skipped with any previous outputs removed</li>
         * </ul>
         */
        PRIMARY(true, true);

        private final boolean trackChanges;
        private final boolean skipWhenEmpty;

        InputBehavior(boolean trackChanges, boolean skipWhenEmpty) {
            this.trackChanges = trackChanges;
            this.skipWhenEmpty = skipWhenEmpty;
        }

        /**
         * Whether incremental changes should be tracked via {@link org.gradle.work.InputChanges}.
         */
        public boolean shouldTrackChanges() {
            return trackChanges;
        }

        /**
         * Whether the work should be skipped and outputs be removed if the property is empty.
         */
        public boolean shouldSkipWhenEmpty() {
            return skipWhenEmpty;
        }
    }

    interface ValueSupplier {
        @Nullable
        Object getValue();
    }

    interface FileValueSupplier extends ValueSupplier {
        FileCollection getFiles();
    }

    class InputFileValueSupplier implements FileValueSupplier {
        private final Object value;
        private final Class<? extends FileNormalizer> normalizer;
        private final DirectorySensitivity directorySensitivity;
        private final LineEndingSensitivity lineEndingSensitivity;
        private final Supplier<FileCollection> files;

        public InputFileValueSupplier(
            @Nullable Object value,
            Class<? extends FileNormalizer> normalizer,
            DirectorySensitivity directorySensitivity,
            LineEndingSensitivity lineEndingSensitivity,
            Supplier<FileCollection> files
        ) {
            this.value = value;
            this.normalizer = normalizer;
            this.directorySensitivity = directorySensitivity;
            this.lineEndingSensitivity = lineEndingSensitivity;
            this.files = files;
        }

        @Nullable
        @Override
        public Object getValue() {
            return value;
        }

        public Class<? extends FileNormalizer> getNormalizer() {
            return normalizer;
        }

        public DirectorySensitivity getDirectorySensitivity() {
            return directorySensitivity;
        }

        public LineEndingSensitivity getLineEndingNormalization() {
            return lineEndingSensitivity;
        }

        @Override
        public FileCollection getFiles() {
            return files.get();
        }
    }

    class OutputFileValueSupplier implements FileValueSupplier {
        private final File root;
        private final FileCollection files;

        public OutputFileValueSupplier(File root, FileCollection files) {
            this.root = root;
            this.files = files;
        }

        @Nonnull
        @Override
        public File getValue() {
            return root;
        }

        @Override
        public FileCollection getFiles() {
            return files;
        }
    }

    void visitOutputs(File workspace, OutputVisitor visitor);

    interface OutputVisitor {
        default void visitOutputProperty(
            String propertyName,
            TreeType type,
            OutputFileValueSupplier value
        ) {}

        default void visitLocalState(File localStateRoot) {}

        default void visitDestroyable(File destroyableRoot) {}
    }

    /**
     * Decorate input file fingerprinting errors when appropriate.
     */
    default RuntimeException decorateInputFileFingerprintingException(InputFileFingerprintingException ex) {
        return ex;
    }

    /**
     * Decorate output file fingerprinting errors when appropriate.
     */
    default RuntimeException decorateOutputFileSnapshottingException(OutputFileSnapshottingException ex) {
        return ex;
    }

    /**
     * Validate the work definition and configuration.
     */
    default void validate(WorkValidationContext validationContext) {}

    /**
     * Return a reason to disable caching for this work.
     * When returning {@link Optional#empty()} if caching can still be disabled further down the pipeline.
     */
    default Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
        return Optional.empty();
    }

    /**
     * Is this work item allowed to load from the cache, or if we only allow it to be stored.
     */
    // TODO Make this part of CachingState instead
    default boolean isAllowedToLoadFromCache() {
        return true;
    }

    /**
     * Whether overlapping outputs should be allowed or ignored.
     */
    default OverlappingOutputHandling getOverlappingOutputHandling() {
        return OverlappingOutputHandling.IGNORE_OVERLAPS;
    }

    enum OverlappingOutputHandling {
        /**
         * Overlapping outputs are detected and handled.
         */
        DETECT_OVERLAPS,

        /**
         * Overlapping outputs are not detected.
         */
        IGNORE_OVERLAPS
    }

    /**
     * Whether the outputs should be cleanup up when the work is executed non-incrementally.
     */
    default boolean shouldCleanupOutputsOnNonIncrementalExecution() {
        return true;
    }

    /**
     * Whether stale outputs should be cleanup up before execution.
     */
    default boolean shouldCleanupStaleOutputs() {
        return false;
    }

    enum InputChangeTrackingStrategy {
        /**
         * No incremental parameters, nothing to track.
         */
        NONE(false),
        /**
         * Only the incremental parameters should be tracked for input changes.
         */
        INCREMENTAL_PARAMETERS(true);

        private final boolean requiresInputChanges;

        InputChangeTrackingStrategy(boolean requiresInputChanges) {
            this.requiresInputChanges = requiresInputChanges;
        }

        public boolean requiresInputChanges() {
            return requiresInputChanges;
        }
    }

    /**
     * This is a temporary measure for Gradle tasks to track a legacy measurement of all input snapshotting together.
     */
    default void markLegacySnapshottingInputsStarted() {}

    /**
     * This is a temporary measure for Gradle tasks to track a legacy measurement of all input snapshotting together.
     */
    default void markLegacySnapshottingInputsFinished(CachingState cachingState) {}

    /**
     * This is a temporary measure for Gradle tasks to track a legacy measurement of all input snapshotting together.
     */
    default void ensureLegacySnapshottingInputsClosed() {}

    /**
     * Returns a type origin inspector, which is used for diagnostics (e.g error messages) to provide
     * more context about the origin of types (for example in what plugin a type is defined)
     */
    default WorkValidationContext.TypeOriginInspector getTypeOriginInspector() {
        return WorkValidationContext.TypeOriginInspector.NO_OP;
    }
}
