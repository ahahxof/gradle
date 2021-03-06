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

package org.gradle.api.publish;

import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectSet;

/**
 * <p>A {@code PublicationContainer} is responsible for declaring and managing publications. See also {@link Publication}.</p>
 */
@Incubating
public interface PublicationContainer extends NamedDomainObjectSet<Publication> {

    /**
     * {@inheritDoc}
     */
    Publication getByName(String name) throws UnknownPublicationException;

    /**
     * {@inheritDoc}
     */
    Publication getAt(String name) throws UnknownPublicationException;

}
