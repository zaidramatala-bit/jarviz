#!/bin/sh

#####################################################################
# Copyright 2020 Expedia, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#####################################################################

# Tests for the JDK/Node version preflight of the Jarviz CLI.
# Usage: sh jarviz-cli/test/preflight_test.sh

set -u

TEST_DIR=$(cd "$(dirname "$0")" && pwd)
CLI_SCRIPT="${TEST_DIR}/../jarviz"
WORK_DIR=$(mktemp -d -t jarviz-preflight-XXXX)
PREFLIGHT="${WORK_DIR}/preflight.sh"
STUB_DIR="${WORK_DIR}/bin"
failures=0

trap 'rm -rf "${WORK_DIR}"' EXIT
mkdir -p "${STUB_DIR}"

# Extract only the version helpers from the CLI script, so they can be
# exercised without running the whole analysis pipeline.
sed -n '/^# Minimum supported major versions/,/^jarviz_show_logo()/p' "${CLI_SCRIPT}" \
    | sed '$d' > "${PREFLIGHT}"

stub_java() {
    cat > "${STUB_DIR}/java" <<EOF
#!/bin/sh
echo '$1' >&2
EOF
    chmod +x "${STUB_DIR}/java"
}

stub_node() {
    cat > "${STUB_DIR}/node" <<EOF
#!/bin/sh
echo '$1'
EOF
    chmod +x "${STUB_DIR}/node"
}

report() {
    # report <name> <expected> <actual>
    if [ "$2" = "$3" ] ; then
        echo "PASS: $1 (got '$3')"
    else
        echo "FAIL: $1 (expected '$2', got '$3')"
        failures=$((failures + 1))
    fi
}

run_helper() {
    # run_helper <function and args...>
    PATH="${STUB_DIR}:${PATH}" sh -c "( . '${PREFLIGHT}' && $* ) ; echo \"exit=\$?\"" 2>&1
}

# --- jarviz_parse_major_version ----
for case in '1.8.0_292 8' '1.7.0 7' '11 11' '11.0.20 11' '17-ea 17' '21.0.4 21' \
            '21+35 21' 'v20.18.1 20' 'v14.21.3 14' 'not-a-version ' ; do
    input=$(echo "${case}" | cut -d' ' -f1)
    expected=$(echo "${case}" | cut -d' ' -f2)
    actual=$(sh -c ". '${PREFLIGHT}' && jarviz_parse_major_version '${input}'")
    report "parse '${input}'" "${expected}" "${actual}"
done

# --- jarviz_assert_java_version ----
stub_java 'openjdk version "1.8.0_292" 2021-04-20'
out=$(run_helper jarviz_assert_java_version)
report 'Java 8 is rejected' 'exit=1' "$(echo "${out}" | tail -n 1)"
case "${out}" in
    *'needs Java 11 or newer, but found Java 1.8.0_292'*)
        echo "PASS: Java 8 error message is actionable" ;;
    *)
        echo "FAIL: Java 8 error message was: ${out}"
        failures=$((failures + 1)) ;;
esac

stub_java 'openjdk version "11.0.20" 2023-07-18'
report 'Java 11 is accepted' 'exit=0' "$(run_helper jarviz_assert_java_version | tail -n 1)"

stub_java 'openjdk version "21.0.4" 2024-07-16'
report 'Java 21 is accepted' 'exit=0' "$(run_helper jarviz_assert_java_version | tail -n 1)"

stub_java 'gibberish'
out=$(run_helper jarviz_assert_java_version)
report 'Unparseable Java version does not fail' 'exit=0' "$(echo "${out}" | tail -n 1)"

# --- Real JDK on this machine ----
out=$(sh -c "( . '${PREFLIGHT}' && jarviz_assert_java_version ) ; echo \"exit=\$?\"" 2>&1)
echo "Real JDK on PATH: $(java -version 2>&1 | head -n 1)"
report 'Installed JDK passes the preflight' 'exit=0' "$(echo "${out}" | tail -n 1)"

# --- jarviz_assert_node_version ----
stub_node 'v12.22.12'
out=$(run_helper jarviz_assert_node_version)
report 'Node 12 is rejected' 'exit=1' "$(echo "${out}" | tail -n 1)"

stub_node 'v14.21.3'
report 'Node 14 is accepted' 'exit=0' "$(run_helper jarviz_assert_node_version | tail -n 1)"

stub_node 'v20.18.1'
report 'Node 20 is accepted' 'exit=0' "$(run_helper jarviz_assert_node_version | tail -n 1)"

echo ""
if [ ${failures} -eq 0 ] ; then
    echo "All preflight tests passed."
else
    echo "${failures} preflight test(s) failed."
fi
exit ${failures}
