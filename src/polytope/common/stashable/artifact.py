# Copyright 2025 Mark C. Chu-Carroll
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# The record for an artifact in the depot
from datetime import datetime
from enum import Enum
from typing import Any, NamedTuple, List, Dict, TypedDict
from polytope.common.stashable.ids import Id
from polytope.common.stashable.stashable import JDict, Stashable
from polytope.depot.storage.storage import Content


class Artifact(NamedTuple):
    id: Id["Artifact"]
    artifact_type: str
    timestamp: datetime
    creator: str
    project: str
    metadata: Dict[str, str]
    versions: List[Id["ArtifactVersion"]]

    @classmethod
    def from_dict(cls, d: JDict) -> "Artifact":
        return Artifact(
            id=Id.from_string(d["_id"]),
            artifact_type=d["artifact_type"],
            timestamp=datetime.fromisoformat(d["timestamp"]),
            creator=d["creator"],
            project=d["project"],
            metadata=d["metadata"],
            versions=list(Id.from_string(v) for v in d["versions"])
        )

    def to_dict(self) -> JDict:
        return {
            "_id": str(self.id),
            "artifact_type": self.artifact_type,
            "timestamp": self.timestamp.isoformat(),
            "creator": self.creator,
            "project": self.project,
            "metadata": self.metadata,
            "versions": [str(v) for v in self.versions]
        }


class VersionStatus(Enum):
    Working = "Working"
    Committed = "Committed"
    Aborted = "Aborted"


class ArtifactVersion(NamedTuple):
    id: Id["ArtifactVersion"]
    artifact_id: Id[Artifact]
    artifact_type: str
    timestamp: datetime
    creator: str
    content_id: Id[Content]
    parents: List[Id["ArtifactVersion"]]
    metadata: Dict[str, str]
    status: VersionStatus

    def to_dict(self) -> JDict:
        return {
            "_id": str(self.id),
            "artifact_id": str(self.artifact_id),
            "artifact_type": self.artifact_type,
            "timestamp": self.timestamp.isoformat(),
            "creator": self.creator,
            "content_id": str(self.content_id),
            "parents": list(str(p) for p in self.parents),
            "metadata": self.metadata,
            "status": self.status.value
        }

    @classmethod
    def from_dict(cls, d: JDict) -> "ArtifactVersion":
        return ArtifactVersion(
            id=Id.from_string(d["_id"]),
            artifact_id=Id.from_string(d["artifact_id"]),
            artifact_type=d["artifact_type"],
            timestamp=datetime.fromisoformat(d["timestamp"]),
            creator=d["creator"],
            content_id=Id.from_string(d["content_id"]),
            parents=list(Id.from_string(i) for i in d["parents"]),
            metadata=d["metadata"],
            status=VersionStatus(d["status"])
        )
