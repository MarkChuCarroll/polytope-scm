# Copyright 2025 Mark C. Chu-Carroll
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
from typing import Any
from polytope.common.error import PtException, ErrorKind
import uuid


class IdKind(Enum):
    ID_PROJECT = "pr"
    ID_CHANGE = "chg"
    ID_CHANGE_SAVE = "save"
    ID_CONFLICT = "conflict"
    ID_ARTIFACT = "art"
    ID_VERSION = "ver"
    ID_HISTORY = "ver"
    ID_HISTORY_STEP = "step"
    ID_WORKSPACE = "ws"


class Id[T]:
    def __init__(self, kind: IdKind, id: str) -> None:
        self.kind = kind
        self.id = id

    def __eq__(self, other: Any) -> bool:
        if isinstance(other, Id):
            return self.kind == other.kind and self.id == other.id
        else:
            return False

    def __hash__(self) -> int:
        return hash((self.kind, self.id))

    def __repr__(self) -> str:
        return f"{self.kind.value}:{self.id}"

    @classmethod
    def new_id(cls, kind: IdKind) -> "Id[T]":
        id = uuid.uuid4().hex
        return Id[T](kind, id)

    @classmethod
    def from_string(cls, s: str) -> "Id[T]":
        print(f"Decoding {s}")
        parts = s.split(":")
        if len(parts) != 2:
            raise PtException(ErrorKind.Parsing, f"Invalid ID string: {s}")
        return Id(IdKind(value=parts[0]), parts[1])
