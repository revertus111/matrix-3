#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

if [[ "${MATRIX3_INHIBITED:-0}" != "1" ]] && command -v systemd-inhibit >/dev/null 2>&1; then
    exec env MATRIX3_INHIBITED=1 systemd-inhibit \
        --what=sleep:idle \
        --who=Matrix3 \
        --why="Local Matrix3 test session" \
        --mode=block \
        /bin/bash "${SCRIPT_DIR}/matrix3.sh" "$@"
fi

if ! java8_ready; then
    echo "Matrix3 Java 8 is not installed."
    echo "Run: ${SCRIPT_DIR}/setup.sh"
    exit 1
fi

if ! command -v setsid >/dev/null 2>&1; then
    echo "Matrix3 needs the 'setsid' command to manage server/client process groups safely."
    exit 1
fi

existing_server_pid="$(read_pid_file "${SERVER_PID_FILE}")"
existing_client_pid="$(read_pid_file "${CLIENT_PID_FILE}")"

if pid_alive "${existing_server_pid}" || pid_alive "${existing_client_pid}"; then
    echo "Matrix3 already appears to be running."
    echo "Use ${SCRIPT_DIR}/stop.sh first if this is a stale session."
    exit 1
fi

rm -f "${SERVER_PID_FILE}" "${CLIENT_PID_FILE}"

timestamp="$(date '+%Y%m%d-%H%M%S')"
server_log="${MATRIX3_LOG_DIR}/server-${timestamp}.log"
client_log="${MATRIX3_LOG_DIR}/client-${timestamp}.log"

server_pid=""
client_pid=""

cleanup() {
    local exit_code=$?
    trap - EXIT INT TERM HUP

    if [[ -n "${client_pid}" ]] && pid_alive "${client_pid}"; then
        stop_process_group "${client_pid}" "Matrix3 client"
    fi
    rm -f "${CLIENT_PID_FILE}"

    # Give the server a moment to observe the client disconnect before stopping it.
    if [[ -n "${server_pid}" ]] && pid_alive "${server_pid}"; then
        sleep 3
        stop_process_group "${server_pid}" "Matrix3 server"
    fi
    rm -f "${SERVER_PID_FILE}"

    exit "${exit_code}"
}
trap cleanup EXIT INT TERM HUP

echo "Starting Matrix3 server..."
setsid bash -c 'cd "$1" && exec ./gradlew --no-daemon runGame' bash "${REPO_ROOT}/Server" \
    >"${server_log}" 2>&1 &
server_pid=$!
printf '%s\n' "${server_pid}" > "${SERVER_PID_FILE}"

echo "Server log: ${server_log}"
echo "Waiting for local world 1 on 127.0.0.1:43594..."

server_ready=0
for _ in {1..300}; do
    if ! pid_alive "${server_pid}"; then
        echo "Matrix3 server exited before becoming ready."
        tail -n 80 "${server_log}" || true
        exit 1
    fi

    if port_open "127.0.0.1" "43594"; then
        server_ready=1
        break
    fi
    sleep 1
done

if [[ "${server_ready}" != "1" ]]; then
    echo "Timed out waiting for the Matrix3 game port."
    tail -n 80 "${server_log}" || true
    exit 1
fi

echo "Server is ready. Starting Matrix3 client..."
setsid bash -c 'cd "$1" && exec ./gradlew --no-daemon run' bash "${REPO_ROOT}/Client" \
    >"${client_log}" 2>&1 &
client_pid=$!
printf '%s\n' "${client_pid}" > "${CLIENT_PID_FILE}"

echo "Client log: ${client_log}"
echo "Close the Matrix3 client (or stop the game in Steam) to end this session."

set +e
wait "${client_pid}"
client_status=$?
set -e

client_pid=""
rm -f "${CLIENT_PID_FILE}"
exit "${client_status}"
