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
package org.apache.camel.quarkus.support.xalan.graal;

import java.security.ProtectionDomain;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;

@TargetClass(className = "com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl")
final class SunTemplatesImplSubstitution {

    @TargetClass(className = "com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl", innerClass = "TransletClassLoader", onlyWith = JDK8OrEarlier.class)
    static final class TransletClassLoader {
        @Substitute
        Class defineClass(final byte[] b) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetClass(className = "com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl", innerClass = "TransletClassLoader", onlyWith = JDK11OrLater.class)
    static final class TransletClassLoaderJDK11OrLater {
        @Substitute
        Class defineClass(final byte[] b) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        Class<?> defineClass(final byte[] b, ProtectionDomain pd) {
            throw new UnsupportedOperationException();
        }
    }
}
