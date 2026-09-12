#!/usr/bin/env bash

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

MATRIX3_DATA_DIR="${XDG_DATA_HOME:-${HOME}/.local/share}/matrix3"
MATRIX3_STATE_DIR="${XDG_STATE_HOME:-${HOME}/.local/state}/matrix3"
MATRIX3_JAVA_HOME="${MATRIX3_DATA_DIR}/java8"
MATRIX3_LOG_DIR="${MATRIX3_STATE_DIR}/logs"
MATRIX3_RUN_DIR="${MATRIX3_STATE_DIR}/run"

SERVER_PID_FILE="${MATRIX3_RUN_DIR}/server.pid"
CLIENT_PID_FILE="${MATRIX3_RUN_DIR}/client.pid"

mkdir -p "${MATRIX3_DATA_DIR}" "${MATRIX3_LOG_DIR}" "${MATRIX3_RUN_DIR}"

export JAVA_HOME="${MATRIX3_JAVA_HOME}"
export PATH="${JAVA_HOME}/bin:${PATH}"

java8_ready() {
    [[ -x "${JAVA_HOME}/bin/java" ]] &&
        "${JAVA_HOME}/bin/java" -version 2>&1 | head -n 1 | grep -q 'version "1\.8'
}

read_pid_file() {
    local file="$1"
    if [[ -f "${file}" ]]; then
        cat "${file}"
    fi
}

pid_alive() {
    local pid="${1:-}"
    [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null
}

stop_process_group() {
    local pid="${1:-}"
    local label="${2:-process}"
    if ! pid_alive "${pid}"; then
        return 0
    fi

    echo "Stopping ${label}..."
    kill -TERM -- "-${pid}" 2>/dev/null || kill -TERM "${pid}" 2>/dev/null || true

    local i
    for i in {1..20}; do
        if ! pid_alive "${pid}"; then
            return 0
        fi
        sleep 0.5
    done

    echo "${label} did not stop cleanly; forcing it down."
    kill -KILL -- "-${pid}" 2>/dev/null || kill -KILL "${pid}" 2>/dev/null || true
}

port_open() {
    local host="$1"
    local port="$2"
    (exec 3<>"/dev/tcp/${host}/${port}") >/dev/null 2>&1
}
