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

package org.gradle.api.internal

import org.gradle.api.Action
import org.gradle.api.UncheckedIOException
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class IoActionsTest extends Specification {

    @Rule TemporaryFolder tmp

    def "can convert IO action to action"() {
        given:
        def action = IoActions.toAction(new IoAction() {
            void execute(Object thing) {
                throw new IOException("!")
            }
        })

        when:
        action.execute("foo")

        then:
        thrown UncheckedIOException
    }

    def "can use file action to write to file"() {
        given:
        def file = tmp.file("foo.txt")

        when:
        IoActions.createFileWriteAction(file, "UTF-8").execute(new Action<Writer>() {
            void execute(Writer writer) {
                writer.write("bar")
            }
        })

        then:
        file.text == "bar"
    }

    def "fails to write to file when can't create parent dir"() {
        given:
        tmp.createFile("base")
        def file = tmp.file("base/foo.txt")
        def action = Mock(Action)

        when:
        IoActions.createFileWriteAction(file, "UTF-8").execute(action)

        then:
        0 * action.execute(_)
        def e = thrown UncheckedIOException
        e.cause instanceof IOException
        e.cause.message.startsWith("Unable to create directory")
    }

}
