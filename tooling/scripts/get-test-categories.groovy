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
import org.apache.groovy.yaml.util.YamlConverter
import groovy.yaml.YamlSlurper 
import groovy.json.JsonBuilder 

def yaml = new File("${args[0]}").text
def testsToInclude = args.length > 1 ? args[1].split("\n") : []

def slurper = new YamlSlurper()

// Parse the test categories
def testCategories = slurper.parseText(yaml)
// Keep a copy that we can iterate over but not modify
def testCategoriesCopy = slurper.parseText(yaml)

// Figure out which native test categories we need to build
testCategoriesCopy.each { categories ->
    categories.value.each { testModule ->
        if (!testsToInclude.contains(testModule)) {
            testCategories[categories.key].remove(testModule)
        }
    }

    if (testCategories[categories.key].isEmpty()) {
        testCategories.remove(categories.key)
    }
}


def json = new JsonBuilder(testCategories).toString().replace('"', '\'')

// Set output variable for GitHub actions
println "::set-output name=matrix::{'category': ${json}}"
