#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

client_pid="$(read_pid_file "${CLIENT_PID_FILE}")"
server_pid="$(read_pid_file "${SERVER_PID_FILE}")"

if pid_alive "${client_pid}"; then
    stop_process_group "${client_pid}" "Matrix3 client"
fi
rm -f "${CLIENT_PID_FILE}"

if pid_alive "${server_pid}"; then
    # Allow the server to process the client disconnect first.
    sleep 3
    stop_process_group "${server_pid}" "Matrix3 server"
fi
rm -f "${SERVER_PID_FILE}"

echo "Matrix3 processes are stopped."
