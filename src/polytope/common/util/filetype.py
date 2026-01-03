# Copyright 2026 Mark C. Chu-Carroll
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from enum import Enum
import unicodedata


class FileType(Enum):
    """
    This is a utility for helping to identify the type of a file.
    As polytope grows and learns to support more types, they'll
    get added to this file.

    Ideally, this should be somehow hooked into agents, so that it
    can look at a file, and return the correct agent. The agents
    should be able to provide filename hints and content tests
    to determine if they're the correct agent for the file.

    As a simple heuristic, to determine if a file is binary or text,
    we read the first 1k bytes of the file, and look for control characters
    other than carriage returns and tabs. If we see any of those, then we
    assume the file is binary; otherwise, we assume the file is text.
    """

    text = "text"
    binary = "binary"


def of(p: str) -> FileType:
    with open(p, "rb") as f:
        bytes = f.read(1024)
        return of_prefix(bytes)


def of_prefix(bytes: bytes) -> FileType:
    for b in bytes:
        if unicodedata.category(chr(b)) == "Cc" and b not in (b'\n', b'\r', b'\t'):
            return FileType.binary
    return FileType.text
