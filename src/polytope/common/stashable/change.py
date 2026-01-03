# Copyright 2026 Mark C. Chu-Carroll
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


from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, NamedTuple
from polytope.common.stashable.ids import Id
from polytope.common.stashable.artifact import Artifact, ArtifactVersion

from polytope.common.stashable.pvs import ProjectVersionSpecifier


class ChangeStatus(Enum):
    Open = 0
    Closed = 1
    Aborted = 2


class Change(NamedTuple):
    id: Id["Change"]
    project: str
    name: str
    history: str
    basis: ProjectVersionSpecifier
    description: str
    timestamp: datetime
    baseline: Id[ArtifactVersion]
    save_points: List[Id["SavePoint"]]
    status: ChangeStatus

    def to_dict(self) -> Dict[str, Any]:
        return {
            "type": "Change",
            "_id": str(self.id),
            "project": self.project,
            "name": self.name,
            "description": self.description,
            "baseline": str(self.baseline),
            "history": self.history,
            "basis": self.basis.to_dict(),
            "save_points": [str(sp) for sp in self.save_points],
            "status": self.status.value,
            "timestamp": self.timestamp,
        }

    @classmethod
    def from_dict(cls, dict: Dict[str, Any]) -> "Change":
        return Change(
            id=Id.from_string(dict["_id"]),
            project=dict["project"],
            name=dict["name"],
            description=dict["description"],
            baseline=Id.from_string(dict["baseline"]),
            timestamp=dict["timestamp"],
            history=dict["history"],
            basis=ProjectVersionSpecifier.from_dict(dict["basis"]),
            save_points=[Id.from_string(sp) for sp in dict["save_points"]],
            status=ChangeStatus(dict["status"]),
        )


class SavePoint(NamedTuple):
    id: Id["SavePoint"]
    change_id: Id[Change]
    idx: int
    creator: str
    description: str
    basis: ProjectVersionSpecifier

    baseline_version: Id[ArtifactVersion]
    modified_artifacts: List[Id[Artifact]]
    timestamp: datetime

    def to_dict(self) -> Dict[str, Any]:
        return {
            "type": "SavePoint",
            "_id": str(self.id),
            "change_id": str(self.change_id),
            "creator": self.creator,
            "idx": self.idx,
            "description": self.description,
            "basis": self.basis.to_dict(),
            "baseline_version": str(self.baseline_version),
            "modified_artifacts": [str(aid) for aid in self.modified_artifacts],
            "timestamp": self.timestamp,
        }

    @classmethod
    def from_dict(cls, dict: Dict[str, Any]) -> "SavePoint":
        return cls(
            id=Id.from_string(dict["_id"]),
            change_id=Id.from_string(dict["change_id"]),
            idx=dict["idx"],
            creator=dict["creator"],
            description=dict["description"],
            basis=ProjectVersionSpecifier.from_dict(dict["basis"]),
            baseline_version=Id.from_string(dict["baseline_version"]),
            modified_artifacts=[
                Id.from_string(ma) for ma in dict["modified_artifacts"]
            ],
            timestamp=dict["timestamp"],
        )
