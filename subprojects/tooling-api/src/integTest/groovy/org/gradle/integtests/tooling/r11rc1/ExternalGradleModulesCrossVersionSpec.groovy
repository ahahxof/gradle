/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.tooling.r11rc1

import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.ExternalDependency
import org.gradle.tooling.model.idea.IdeaProject

@MinToolingApiVersion('current')
@MinTargetGradleVersion('current')
class ExternalGradleModulesCrossVersionSpec extends ToolingApiSpecification {

    def "idea libraries contain gradle module information"() {
        def fakeRepo = dist.file("repo")
        new MavenRepository(fakeRepo).module("foo.bar", "coolLib", 2.0).publish()

        dist.file("yetAnotherJar.jar").createFile()

        dist.file('build.gradle').text = """
apply plugin: 'java'
apply plugin: 'idea'

repositories {
    maven { url "${fakeRepo.toURI()}" }
}

dependencies {
    compile 'foo.bar:coolLib:2.0'
    compile 'unresolved.org:funLib:1.0'
//    compile files('yetAnotherJar.jar')
}
"""
        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def module = project.modules[0]
        def libs = module.dependencies

        then:
        libs.size() == 2

        ExternalDependency coolLib = libs.find { it.externalGradleModule?.name == 'coolLib' }
        coolLib.externalGradleModule.group == 'foo.bar'
        coolLib.externalGradleModule.name == 'coolLib'
        coolLib.externalGradleModule.version == '2.0'

        ExternalDependency funLib = libs.find { it.externalGradleModule?.name == 'funLib' }
        funLib.externalGradleModule.group == 'unresolved.org'
        funLib.externalGradleModule.name == 'funLib'
        funLib.externalGradleModule.version == '1.0'
    }
}