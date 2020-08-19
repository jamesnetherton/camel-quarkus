/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

// TODO: Restore this to 'ubuntu'
def AGENT_LABEL = env.AGENT_LABEL ?: 'master'
def JDK_NAME = env.JDK_NAME ?: 'JDK 11 (latest)'
def MAVEN_PARAMS = '-B -U -V -B -e -ntp'
def SNAPSHOT_VERSION = ''

if (env.BRANCH_NAME == 'camel-master') {
    SNAPSHOT_VERSION = 'XXX-SNAPSHOT'
    MAVEN_PARAMS += ' -Papache-snapshots'
}

if (env.BRANCH_NAME == 'quarkus-master') {
    SNAPSHOT_VERSION = 'YYY-SNAPSHOT'
    MAVEN_PARAMS += ' -Poss-snapshots -Dquarkus.version=999-SNAPSHOT'
}

pipeline {

    agent {
        label AGENT_LABEL
    }

    tools {
        jdk JDK_NAME
    }

    options {
        buildDiscarder(
            logRotator(artifactNumToKeepStr: '5', numToKeepStr: '10')
        )
        disableConcurrentBuilds()
    }

    stages {
        stage('Set SNAPSHOT version') {
            when {
                expression { env.BRANCH_NAME ==~ /(.*-master)/ }
            }

            steps {
                sh "./mvnw ${MAVEN_PARAMS} versions:set -DnewVersion=${SNAPSHOT_VERSION}"
                sh "./mvnw ${MAVEN_PARAMS} versions:commit"
            }
        }

        stage('Build, Test & Deploy') {
            steps {
                // TODO: Enable tests
                sh "./mvnw ${MAVEN_PARAMS} -Denforce=false -Dformatter.skip -Dimpsort.skip -Dmaven.test.skip=true clean deploy"
            }
        }
    }
}
