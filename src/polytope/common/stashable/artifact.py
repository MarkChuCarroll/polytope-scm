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
from typing import Any, NamedTuple, List, Dict
from polytope.common.stashable.ids import Id
from polytope.common.stashable.stashable import JDict, Stashable


class Artifact(NamedTuple):
    id: Id["Artifact"]
    artifact_type: str
    timestamp: datetime
    creator: str
    project: str
    metadata: Dict[str, str]
    versions: List[Id["ArtifactVersion"]]

    def to_dict(self) -> JDict:
        return {
            "id": self.id,
            "artifact_type": self.artifact_type,
            "timestamp": self.timestamp,
            "creator": self.creator,
            "project": self.project,
            "metadata": self.metadata,
            "versions": [ str(i) for i in self.versions ]
        }

    @classmethod
    def from_dict(self, dict: JDict) -> "Artifact":
        return Artifact(
            id=Id.from_string(dict["id"]),
            artifact_type=dict["artifact_type"],
            timestamp=dict["timestamp"],
            creator=dict["creator"],
            project=dict["project"],
            metadata=dict["metadata"],
            versions=[Id.from_string(i) for i in dict["versions"]]
        )


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
    content: bytes
    parents: List[Id["ArtifactVersion"]]
    metadata: Dict[str, str]
    status: VersionStatus

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": str(self.id),
            "artifact_id": str(self.artifact_id),
            "artifact_type": self.artifact_type,
            "timestamp": self.timestamp,
            "creator": self.creator,
            "content": self.content,
            "parents": [str(p) for p in self.parents],
            "metadata": self.metadata,
            "status": self.status.value
        }

    @classmethod
    def from_dict(cls, dict: Dict[str, Any]) -> "ArtifactVersion":
        return ArtifactVersion(
            id=Id.from_string(dict["id"]),
            artifact_id=Id.from_string(dict["artifact_id"]),
            artifact_type=dict["artifact_type"],
            timestamp=dict["timestamp"],
            creator=dict["creator"],
            content=dict["content"],
            parents=[Id.from_string(p) for p in dict["parents"]],
            metadata=dict["metadata"],
            status=VersionStatus[dict["status"]]
        )
