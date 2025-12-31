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


from abc import abstractmethod, ABC
import base64
import hashlib
import textwrap
from typing import List, NamedTuple
from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.ids import Id
from polytope.common.stashable.stashable import JDict
from polytope.depot.storage.storage import Storage


class MergeConflict(NamedTuple):
    id: Id["MergeConflict"]
    artifact_id: Id[Artifact]
    artifact_type: str
    source_version: Id[ArtifactVersion]
    target_version: Id[ArtifactVersion]
    details: bytes

    def __repr__(self) -> str:
        return textwrap.dedent(f"""\
            Conflict: {self.id} on {self.artifact_id}
            Artifact type: {self.artifact_type}
            Source version: {self.source_version}
            Target version: {self.target_version}
            """)

    def to_dict(self) -> JDict:
        return {
            "_id": str(self.id),
            "artifact_id": str(self.artifact_id),
            "artifact_type": str(self.artifact_type),
            "source_version": str(self.source_version),
            "target_version": str(self.target_version),
            "details": base64.b64encode(self.details)
        }

    @classmethod
    def from_dict(cls, d: JDict) -> "MergeConflict":
        return MergeConflict(
            id=Id.from_string(d["_id"]),
            artifact_id=Id.from_string(d["artifact_id"]),
            artifact_type=d["artifact_type"],
            source_version=Id.from_string(d["source_version"]),
            target_version=Id.from_string(d["target_version"]),
            details=base64.b64decode(d["details"])
        )


class MergeResult(NamedTuple):
    artifact_type: str
    artifact_id: Id[Artifact]
    ancestor_version: Id[ArtifactVersion]
    source_version: Id[ArtifactVersion]
    target_version: Id[ArtifactVersion]
    proposed_merge: bytes
    conflicts: List[MergeConflict]

    def to_dict(self) -> JDict:
        return {
            "artifact_type": self.artifact_type,
            "artifact_id": str(self.artifact_id),
            "ancestor_version": str(self.ancestor_version),
            "source_version": str(self.source_version),
            "target_version": str(self.target_version),
            "proposed_merge": base64.b16encode(self.proposed_merge),
            "conflicts": list(l.to_dict() for l in self.conflicts)
        }

    @classmethod
    def from_dict(cls, d: JDict) -> "MergeResult":
        return MergeResult(
            artifact_type=d["artifact_type"],
            artifact_id=Id.from_string(d["artifact_id"]),
            ancestor_version=Id.from_string(d["ancestor_version"]),
            source_version=Id.from_string(d["source_version"]),
            target_version=Id.from_string(d["target_version"]),
            proposed_merge=base64.b64decode(d["proposed_merge"]),
            conflicts=list(MergeConflict.from_dict(mc) for mc in d["conflicts"])
        )


class Agent[T](ABC):
    def __init__(self, storage: Storage) -> None:
        self.storage = storage

    @abstractmethod
    def artifact_type(self) -> str: ...

    @abstractmethod
    def encode_to_bytes(self, content: T) -> bytes: ...

    @abstractmethod
    def decode_from_bytes(self, content: bytes) -> T: ...

    def content_hash(self, content: T) -> str:
        s = self.encode_to_bytes(content)
        sha = hashlib.sha256()
        sha.update(s)
        return sha.hexdigest()

    @abstractmethod
    def merge(
        self,
        ancestor: ArtifactVersion,
        source: ArtifactVersion,
        target: ArtifactVersion,
    ) -> MergeResult: ...


class FileAgent[T](Agent[T]):
    def __init(self, storage: Storage) -> None:
        super().__init__(storage)

    # Given a reference to a file, return "true" if the file is a type that
    # can be processed by the agent.
    @abstractmethod
    def can_handle(self, file: str) -> bool: ...

    @abstractmethod
    def read_from_disk(self, path: str) -> T: ...

    @abstractmethod
    def write_to_disk(self, path: str, value: T) -> None: ...

    def bytes_to_disk(self, path: str, content: bytes) -> None:
        with open(path, "wb") as out:
            out.write(content)
