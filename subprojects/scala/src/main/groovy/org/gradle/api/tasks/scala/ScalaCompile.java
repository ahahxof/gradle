/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.AntBuilder;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.*;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.scala.*;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.Factory;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Compiles Scala source files, and optionally, Java source files.
 */
public class ScalaCompile extends AbstractCompile {
    private static final Logger LOGGER = Logging.getLogger(ScalaCompile.class);

    private FileCollection scalaClasspath;
    private FileCollection zincClasspath;
    private Compiler<ScalaJavaJointCompileSpec> compiler;
    private final CompileOptions compileOptions = new CompileOptions();
    private final ScalaCompileOptions scalaCompileOptions = new ScalaCompileOptions();

    @Inject
    public ScalaCompile() {
        ProjectInternal projectInternal = (ProjectInternal) getProject();
        IsolatedAntBuilder antBuilder = getServices().get(IsolatedAntBuilder.class);
        Factory<AntBuilder> antBuilderFactory = getServices().getFactory(AntBuilder.class);
        JavaCompilerFactory inProcessCompilerFactory = new InProcessJavaCompilerFactory();
        TemporaryFileProvider tempFileProvider = projectInternal.getServices().get(TemporaryFileProvider.class);
        DefaultJavaCompilerFactory javaCompilerFactory = new DefaultJavaCompilerFactory(projectInternal, tempFileProvider, antBuilderFactory, inProcessCompilerFactory);
        ScalaCompilerFactory scalaCompilerFactory = new ScalaCompilerFactory(projectInternal, antBuilder, antBuilderFactory, javaCompilerFactory);
        Compiler<ScalaJavaJointCompileSpec> delegatingCompiler = new DelegatingScalaCompiler(scalaCompilerFactory);
        compiler = new IncrementalScalaCompiler(delegatingCompiler, getOutputs());
    }

    /**
     * Returns the classpath to use to load the Scala compiler.
     */
    @InputFiles
    public FileCollection getScalaClasspath() {
        return scalaClasspath;
    }

    public void setScalaClasspath(FileCollection scalaClasspath) {
        this.scalaClasspath = scalaClasspath;
    }

    /**
     * Returns the classpath to use to load the Zinc incremental compiler.
     * This compiler in turn loads the Scala compiler.
     */
    @InputFiles
    public FileCollection getZincClasspath() {
        return zincClasspath;
    }

    public void setZincClasspath(FileCollection zincClasspath) {
        this.zincClasspath = zincClasspath;
    }

    /**
     * Returns the Scala compilation options.
     */
    @Nested
    public ScalaCompileOptions getScalaCompileOptions() {
        return scalaCompileOptions;
    }

    /**
     * Returns the Java compilation options.
     */
    @Nested
    public CompileOptions getOptions() {
        return compileOptions;
    }

    /**
     * For testing only.
     */
    void setCompiler(Compiler<ScalaJavaJointCompileSpec> compiler) {
        this.compiler = compiler;
    }

    @Override
    protected void compile() {
        DefaultScalaJavaJointCompileSpec spec = new DefaultScalaJavaJointCompileSpec();
        spec.setSource(getSource());
        spec.setDestinationDir(getDestinationDir());
        spec.setClasspath(getClasspath());
        spec.setScalaClasspath(getScalaClasspath());
        spec.setZincClasspath(getZincClasspath());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setCompileOptions(compileOptions);
        spec.setScalaCompileOptions(scalaCompileOptions);
        if (!scalaCompileOptions.isUseAnt()) {
            configureIncrementalCompilation(spec);
        }

        compiler.execute(spec);
    }

    private void configureIncrementalCompilation(ScalaCompileSpec spec) {
        Map<File, File> globalAnalysisMap = getOrCreateGlobalAnalysisMap();
        HashMap<File, File> filteredMap = filterForClasspath(globalAnalysisMap, spec.getClasspath());
        spec.setAnalysisMap(filteredMap);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Analysis file: {}", scalaCompileOptions.getIncrementalOptions().getAnalysisFile());
            LOGGER.debug("Published code: {}", scalaCompileOptions.getIncrementalOptions().getPublishedCode());
            LOGGER.debug("Analysis map: {}", filteredMap);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<File, File> getOrCreateGlobalAnalysisMap() {
        ExtraPropertiesExtension extraProperties = getProject().getRootProject().getExtensions().getExtraProperties();
        Map<File, File> analysisMap;

        if (extraProperties.has("scalaCompileAnalysisMap")) {
            analysisMap = (Map) extraProperties.get("scalaCompileAnalysisMap");
        } else {
            analysisMap = Maps.newHashMap();
            for (Project project : getProject().getRootProject().getAllprojects()) {
                for (ScalaCompile task : project.getTasks().withType(ScalaCompile.class)) {
                    if (task.getScalaCompileOptions().isUseAnt()) { continue; }
                    File publishedCode = task.getScalaCompileOptions().getIncrementalOptions().getPublishedCode();
                    File analysisFile = task.getScalaCompileOptions().getIncrementalOptions().getAnalysisFile();
                    analysisMap.put(publishedCode, analysisFile);
                }
            }
            extraProperties.set("scalaCompileAnalysisMap", Collections.unmodifiableMap(analysisMap));
        }
        return analysisMap;
    }

    private HashMap<File, File> filterForClasspath(Map<File, File> analysisMap, Iterable<File> classpath) {
        final Set<File> classpathLookup = Sets.newHashSet(classpath);
        return Maps.newHashMap(Maps.filterEntries(analysisMap, new Predicate<Map.Entry<File, File>>() {
            public boolean apply(Map.Entry<File, File> entry) {
                return classpathLookup.contains(entry.getKey());
            }
        }));
    }
}
