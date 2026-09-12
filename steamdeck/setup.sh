#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

JAVA_API_URL="https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse"

if [[ "$(uname -s)" != "Linux" || "$(uname -m)" != "x86_64" ]]; then
    echo "This setup currently targets Steam Deck / Linux x86_64 only."
    exit 1
fi

for command_name in curl tar setsid; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
        echo "Missing required command: ${command_name}"
        exit 1
    fi
done

if ! java8_ready; then
    echo "Installing private Eclipse Temurin Java 8 runtime for Matrix3..."
    tmp_dir="$(mktemp -d)"
    trap 'rm -rf "${tmp_dir}"' EXIT

    curl -fL --retry 3 --retry-delay 2 "${JAVA_API_URL}" -o "${tmp_dir}/temurin8.tar.gz"

    rm -rf "${MATRIX3_JAVA_HOME}"
    mkdir -p "${MATRIX3_JAVA_HOME}"
    tar -xzf "${tmp_dir}/temurin8.tar.gz" -C "${MATRIX3_JAVA_HOME}" --strip-components=1

    if ! java8_ready; then
        echo "Java 8 install completed, but validation failed."
        exit 1
    fi
else
    echo "Matrix3 Java 8 runtime is already installed."
fi

chmod +x \
    "${REPO_ROOT}/Server/gradlew" \
    "${REPO_ROOT}/Client/gradlew" \
    "${SCRIPT_DIR}/common.sh" \
    "${SCRIPT_DIR}/setup.sh" \
    "${SCRIPT_DIR}/matrix3.sh" \
    "${SCRIPT_DIR}/stop.sh"

desktop_dir="${XDG_DATA_HOME:-${HOME}/.local/share}/applications"
mkdir -p "${desktop_dir}"
desktop_file="${desktop_dir}/matrix3.desktop"

cat > "${desktop_file}" <<EOF
[Desktop Entry]
Type=Application
Name=Matrix3
Comment=Run the local Matrix3 server and client
Exec=/bin/bash "${SCRIPT_DIR}/matrix3.sh"
Path=${REPO_ROOT}
Terminal=false
Icon=applications-games
Categories=Game;
EOF
chmod +x "${desktop_file}"

if [[ -d "${HOME}/Desktop" ]]; then
    cp "${desktop_file}" "${HOME}/Desktop/Matrix3.desktop"
    chmod +x "${HOME}/Desktop/Matrix3.desktop"
fi

echo
echo "Matrix3 Steam Deck setup is ready."
echo "Java: $("${JAVA_HOME}/bin/java" -version 2>&1 | head -n 1)"
echo "Launcher: ${SCRIPT_DIR}/matrix3.sh"
echo "Logs: ${MATRIX3_LOG_DIR}"
echo
echo "Next: launch Matrix3 once from Desktop Mode."
echo "After that, add Matrix3 (or matrix3.sh) to Steam as a Non-Steam Game."
