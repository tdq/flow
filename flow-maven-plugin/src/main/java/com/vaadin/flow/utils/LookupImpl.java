/*
 * Copyright 2000-2020 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.flow.utils;

import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.flow.di.Lookup;
import com.vaadin.flow.internal.ReflectTools;
import com.vaadin.flow.server.frontend.scanner.ClassFinder;

/**
 * An implementation of Lookup, which could be used
 * to find service(s) of a give type.
 */
public class LookupImpl implements Lookup {

    private ClassFinder classFinder;

    /**
     * Creates an implementation of Lookup.
     */
    public LookupImpl(ClassFinder classFinder) {
        this.classFinder = classFinder;
    }

    @Override
    public <T> T lookup(Class<T> serviceClass) {
        return lookupAll(serviceClass).stream().findFirst().orElse(null);
    }

    @Override
    public <T> List<T> lookupAll(Class<T> serviceClass) {
        return classFinder.getSubTypesOf(serviceClass).stream()
                .map(this::loadCassFromClassFindler)
                .filter(ReflectTools::isInstantiableService)
                .map(ReflectTools::createInstance)
                .map(instance -> (T) instance)
                .collect(Collectors.toList());
    }

    private Class<?> loadCassFromClassFindler(Class<?> clz){
        try {
            return classFinder.loadClass(clz.getName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Could not load " + clz.getName() + " class", e);
        }
    }

}
