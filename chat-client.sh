#!/usr/bin/env bash
# ==============================================================================
# chat-client.sh: gRPC Chat Client interaction script using grpcurl
# ==============================================================================
# This script demonstrates how to interact with the gRPC ChatService.
#
# Prerequisite: grpcurl must be installed and the gRPC server must be running.
# Default target is localhost:9090
#
# ==============================================================================
# USAGE EXAMPLES WITH GRPCURL:
# ==============================================================================
#
# 1. Join a room (server streaming - keeps receiving messages):
#    grpcurl -plaintext -d '{"room_id":"general","user_id":"user-1","username":"Alice"}' localhost:9090 chat.ChatService/JoinRoom
#
# 2. Send a chat message (bidirectional stream):
#    echo '{"room_id":"general","user_id":"user-1","username":"Alice","content":"Hello World","type":"TEXT"}' | grpcurl -plaintext -d @ localhost:9090 chat.ChatService/ChatStream
#
# 3. List users currently in a room:
#    grpcurl -plaintext -d '{"room_id":"general"}' localhost:9090 chat.ChatService/GetRoomUsers
#
# 4. Leave a room:
#    grpcurl -plaintext -d '{"room_id":"general","user_id":"user-1"}' localhost:9090 chat.ChatService/LeaveRoom
#
# 5. List available gRPC services via reflection:
#    grpcurl -plaintext localhost:9090 list
#
# ==============================================================================

set -e

HOST="${GRPC_HOST:-localhost}"
PORT="${GRPC_PORT:-9090}"
SERVER="${HOST}:${PORT}"

echo "========================================================"
echo " gRPC Chat Client (Target: ${SERVER})"
echo "========================================================"

# Check if grpcurl is available
if command -v grpcurl &> /dev/null; then
    GRPCURL_CMD="grpcurl"
elif command -v grpcurl.exe &> /dev/null; then
    GRPCURL_CMD="grpcurl.exe"
elif [ -f "/c/Users/hp/AppData/Local/Microsoft/WindowsApps/grpcurl.exe" ]; then
    GRPCURL_CMD="/c/Users/hp/AppData/Local/Microsoft/WindowsApps/grpcurl.exe"
elif [ -f "/c/Users/hp/AppData/Local/Microsoft/WinGet/Packages/fullstorydev.grpcurl_Microsoft.Winget.Source_8wekyb3d8bbwe/grpcurl.exe" ]; then
    GRPCURL_CMD="/c/Users/hp/AppData/Local/Microsoft/WinGet/Packages/fullstorydev.grpcurl_Microsoft.Winget.Source_8wekyb3d8bbwe/grpcurl.exe"
else
    echo "Error: grpcurl is not installed or not in PATH."
    exit 1
fi

COMMAND="${1:-demo}"

case "$COMMAND" in
    join)
        ROOM_ID="${2:-general}"
        USER_ID="${3:-user-1}"
        USERNAME="${4:-Alice}"
        echo "Joining room '${ROOM_ID}' as ${USERNAME} (${USER_ID})..."
        # Command to join a room:
        $GRPCURL_CMD -plaintext -d "{\"room_id\":\"${ROOM_ID}\",\"user_id\":\"${USER_ID}\",\"username\":\"${USERNAME}\"}" "${SERVER}" chat.ChatService/JoinRoom
        ;;

    send)
        ROOM_ID="${2:-general}"
        USER_ID="${3:-user-1}"
        USERNAME="${4:-Alice}"
        CONTENT="${5:-Hello from chat-client.sh!}"
        echo "Sending message to room '${ROOM_ID}': '${CONTENT}'..."
        # Command to send a chat message:
        echo "{\"room_id\":\"${ROOM_ID}\",\"user_id\":\"${USER_ID}\",\"username\":\"${USERNAME}\",\"content\":\"${CONTENT}\",\"type\":\"TEXT\"}" | \
            $GRPCURL_CMD -plaintext -d @ "${SERVER}" chat.ChatService/ChatStream
        ;;

    users)
        ROOM_ID="${2:-general}"
        echo "Listing users in room '${ROOM_ID}'..."
        # Command to list users in a room:
        $GRPCURL_CMD -plaintext -d "{\"room_id\":\"${ROOM_ID}\"}" "${SERVER}" chat.ChatService/GetRoomUsers
        ;;

    leave)
        ROOM_ID="${2:-general}"
        USER_ID="${3:-user-1}"
        echo "User '${USER_ID}' leaving room '${ROOM_ID}'..."
        # Command to leave a room:
        $GRPCURL_CMD -plaintext -d "{\"room_id\":\"${ROOM_ID}\",\"user_id\":\"${USER_ID}\"}" "${SERVER}" chat.ChatService/LeaveRoom
        ;;

    demo|*)
        echo "[1/3] Querying available gRPC services via reflection..."
        $GRPCURL_CMD -plaintext "${SERVER}" list

        echo ""
        echo "[2/3] Sending test chat message to 'general' via ChatStream..."
        echo '{"room_id":"general","user_id":"client-script-user","username":"ClientScript","content":"Test message from chat-client.sh","type":"TEXT"}' | \
            $GRPCURL_CMD -plaintext -d @ "${SERVER}" chat.ChatService/ChatStream

        echo ""
        echo "[3/3] Querying users in room 'general' via GetRoomUsers..."
        $GRPCURL_CMD -plaintext -d '{"room_id":"general"}' "${SERVER}" chat.ChatService/GetRoomUsers

        echo ""
        echo "All interaction steps completed successfully!"
        ;;
esac
