#!/usr/bin/env python3
"""Run a rewrite command in a disposable worktree and reject logged hierarchy failures."""
import argparse
import pathlib
import re
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", required=True, type=pathlib.Path)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("a command is required after --")
    args.log.parent.mkdir(parents=True, exist_ok=True)
    failed_hierarchy = False
    pattern = re.compile(r"UnresolvedEntityHierarchyException|Cannot resolve .+ while processing .+ in com\.ecpnv\.")
    with args.log.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, errors="replace")
        for line in process.stdout:
            log.write(line)
            log.flush()
            sys.stdout.write(line)
            failed_hierarchy |= bool(pattern.search(line))
        status = process.wait()
    if status or failed_hierarchy:
        print("REJECTED: do not publish any partial output from this worktree.", file=sys.stderr)
        return status if status > 0 else 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
