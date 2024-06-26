/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.support.spring.graal;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.springframework.core.MethodParameter;

final class SpringKotlinSubstitutions {
}

@TargetClass(className = "org.springframework.core.KotlinDetector")
@Substitute
final class SubstituteKotlinDetector {
    @Substitute
    public static boolean isKotlinPresent() {
        return false;
    }

    @Substitute
    public static boolean isKotlinReflectPresent() {
        return false;
    }

    @Substitute
    public static boolean isKotlinType(Class<?> clazz) {
        return false;
    }

    @Substitute
    public static boolean isSuspendingFunction(Method method) {
        return false;
    }
}

@TargetClass(className = "org.springframework.core.KotlinReflectionParameterNameDiscoverer")
@Delete
final class SubstituteKotlinReflectionParameterNameDiscoverer {
}

@TargetClass(className = "org.springframework.beans.BeanUtils$KotlinDelegate")
final class SubstituteBeanUtilsKotlinDelegate {
    @Substitute
    public static <T> Constructor<T> findPrimaryConstructor(Class<T> clazz) {
        throw new UnsupportedOperationException("Kotlin is not supported");
    }

    @Substitute
    public static <T> T instantiateClass(Constructor<T> ctor, Object... args)
            throws IllegalAccessException, InvocationTargetException, InstantiationException {
        throw new UnsupportedOperationException("Kotlin is not supported");
    }
}

@TargetClass(className = "org.springframework.core.MethodParameter$KotlinDelegate")
final class SubstituteMethodParameterKotlinDelegate {
    @Substitute
    public static boolean isOptional(MethodParameter param) {
        throw new UnsupportedOperationException("Kotlin is not supported");
    }

    @Substitute
    static private Type getGenericReturnType(Method method) {
        throw new UnsupportedOperationException("Kotlin is not supported");
    }

    @Substitute
    private static Class<?> getReturnType(Method method) {
        throw new UnsupportedOperationException("Kotlin is not supported");
    }
}

@TargetClass(className = "org.springframework.aop.support.AopUtils$KotlinDelegate", onlyWith = SpringAopPresent.class)
final class SubstituteAopUtilsKotlinDelegate {
    @Substitute
    public static Object invokeSuspendingFunction(Method method, Object target, Object... args) {
        throw new UnsupportedOperationException("Kotlin is not supported");
    }
}

final class SpringAopPresent implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        try {
            Thread.currentThread().getContextClassLoader().loadClass("org.springframework.aop.support.AopUtils");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
