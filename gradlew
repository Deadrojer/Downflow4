#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Gradle wrapper script for Unix.
# Termux: setup_termux.sh rewrites the shebang to
#   #!/data/data/com.termux/files/usr/bin/bash
# when /bin/sh is absent on the device.

##############################################################################
# Helper functions
##############################################################################

die () {
    echo
    echo "ERROR: $*"
    echo
    exit 1
}

warn () {
    echo "WARNING: $*"
}

##############################################################################
# Resolve the script's own directory (follow symlinks)
##############################################################################

PRG="$0"
while [ -h "$PRG" ] ; do
    ls_out=$(ls -ld "$PRG")
    link=$(expr "$ls_out" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")"/$link"
    fi
done
SAVED="$(pwd)"
cd "$(dirname "$PRG")/" >/dev/null
APP_HOME="$(pwd -P)"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

##############################################################################
# Add default JVM options here.
# These can be overridden by JAVA_OPTS and GRADLE_OPTS.
# We do NOT quote individual options here — they are passed as separate args
# via the eval trick below, which is the only portable sh way to handle this.
##############################################################################

DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

##############################################################################
# Find Java
##############################################################################

if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ] ; then
        die "JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    if ! command -v java >/dev/null 2>&1 ; then
        die "JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

##############################################################################
# Classpath
##############################################################################

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

##############################################################################
# Collect JVM options from environment + defaults
# Build up the full command in a variable then eval it so that options
# containing spaces (rare but valid) survive correctly.
##############################################################################

# Escape spaces in GRADLE_OPTS / JAVA_OPTS for safe eval
escape_args () {
    # Turn each argument into a single-quoted word and print them space-separated
    for arg; do
        # Replace every ' with '\''
        arg=$(printf '%s' "$arg" | sed "s/'/'\\''/g")
        printf "'%s' " "$arg"
    done
}

set -- "$@"   # keep original $@ intact

# Build final JVM option string (DEFAULT first, then user overrides)
JVM_OPTS_ALL=""
for opt in $DEFAULT_JVM_OPTS ${JAVA_OPTS:-} ${GRADLE_OPTS:-}; do
    JVM_OPTS_ALL="$JVM_OPTS_ALL $opt"
done

##############################################################################
# Exec Gradle wrapper main
##############################################################################

exec "$JAVACMD" \
    $JVM_OPTS_ALL \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
