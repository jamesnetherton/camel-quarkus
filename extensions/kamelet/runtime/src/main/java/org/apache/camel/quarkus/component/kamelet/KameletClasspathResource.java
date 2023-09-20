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
package org.apache.camel.quarkus.component.kamelet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.apache.camel.spi.Resource;

/**
 * Mutable & Quarkus recorder serialization friendly implementation for Kamelet classpath resources
 */
public class KameletClasspathResource implements Resource {
    private String scheme;
    private String location;
    private boolean exists;
    private byte[] data;
    private InputStream inputStream;

    @Override
    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public String getLocation() {
        return this.location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public boolean exists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    public byte[] getData() {
        return this.data;
    }

    public void setData(byte[] data) {
        this.inputStream = null;
        this.data = data;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (this.data == null) {
            throw new IOException("No resource content was defined");
        }
        if (this.inputStream == null) {
            this.inputStream = new ByteArrayInputStream(this.data);
        }
        return inputStream;
    }

    @Override
    public String toString() {
        String prefix = scheme + ":";
        if (location.startsWith(prefix)) {
            return "Resource[" + location + "]";
        } else {
            return "Resource[" + prefix + location + "]";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Resource that = (Resource) o;
        return scheme.equals(that.getScheme()) && location.equals(that.getLocation());
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheme, location);
    }
}
