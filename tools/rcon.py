#!/usr/bin/env python3
"""Minimal Minecraft RCON client for tools/server_smoke.sh (REL3), standard library only.

    rcon.py <port> <password> <command> [<command> ...]

Logs in once, sends every command in order and prints each reply as "> command" followed by the reply text.
Exits 2 when the login fails, 3 when the connection breaks; a command's own failure is only printed. A reply may take
RCON_TIMEOUT seconds (environment, default 120): a /locate of a sparse structure can run for minutes.
"""
import os
import socket
import struct
import sys

LOGIN, COMMAND = 3, 2
MARKER_ID, MARKER_TYPE = 0x7FFF0000, 100


def packet(request_id: int, kind: int, body: str) -> bytes:
    payload = struct.pack("<ii", request_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def read_exact(sock: socket.socket, n: int) -> bytes:
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError("RCON connection closed")
        data += chunk
    return data


def read_packet(sock: socket.socket) -> tuple[int, int, str]:
    (length,) = struct.unpack("<i", read_exact(sock, 4))
    data = read_exact(sock, length)
    request_id, kind = struct.unpack("<ii", data[:8])
    return request_id, kind, data[8:-2].decode("utf-8", errors="replace")


def main(argv: list[str]) -> int:
    if len(argv) < 4:
        print(__doc__, file=sys.stderr)
        return 64
    port, password, commands = int(argv[1]), argv[2], argv[3:]
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=float(os.environ.get("RCON_TIMEOUT", "120"))) as sock:
            sock.sendall(packet(1, LOGIN, password))
            request_id, _, _ = read_packet(sock)
            if request_id == -1:
                print("RCON login refused", file=sys.stderr)
                return 2
            for i, command in enumerate(commands, start=2):
                # Vanilla reads exactly one packet per read and closes the connection on anything else, so packets
                # go out one at a time. It splits a long reply into several packets of the command's id, all sent
                # before it reads again; a packet of an unknown type sent after the first part is answered with
                # "Unknown request" under its own id, which marks the reply's end.
                sock.sendall(packet(i, COMMAND, command))
                parts = [read_packet(sock)[2]]
                sock.sendall(packet(MARKER_ID, MARKER_TYPE, ""))
                while True:
                    request_id, _, part = read_packet(sock)
                    if request_id == MARKER_ID:
                        break
                    parts.append(part)
                body = "".join(parts)
                print(f"> {command}")
                print(body if body else "(no reply)")
    except (OSError, ConnectionError) as e:
        print(f"RCON error: {e}", file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
