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
from typing import Dict, List, NamedTuple, Set

from polytope.common.agents.agents import MergeConflict
from polytope.common.stashable import JDict
from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.ids import Id
from polytope.common.stashable.pvs import ProjectVersionSpecifier


class WorkspaceDescriptor(NamedTuple):
    id: "Id[Workspace]"
    ws_name: str
    project: str
    creator: str
    description: str
    created_at: datetime
    last_modified: datetime

    def to_dict(self) -> JDict:
        return {
            "type": "WorkspaceDescriptor",
            "_id": str(self.id),
            "ws_name": self.ws_name,
            "project": self.project,
            "creator": self.creator,
            "description": self.description,
            "created_at": self.created_at.isoformat(),
            "last_modified": self.last_modified.isoformat(),
        }

    @classmethod
    def from_dict(cls, d: JDict) -> "WorkspaceDescriptor":
        return WorkspaceDescriptor(
            id=Id.from_string(d["_id"]),
            ws_name=d["ws_name"],
            project=d["project"],
            creator=d["creator"],
            description=d["description"],
            created_at=datetime.fromisoformat(d["created_at"]),
            last_modified=datetime.fromisoformat(d["last_modified"]),
        )


class Workspace:
    def __init__(
        self,
        id: Id["Workspace"],
        project: str,
        name: str,
        creator: str,
        created_at: datetime,
        last_modified: datetime,
        description: str,
        basis: ProjectVersionSpecifier,
        baseline_id: Id[Artifact],
        baseline_version: Id[ArtifactVersion],
        history: str,
        change: str | None,
        working_versions: Dict[Id[Artifact], Id[ArtifactVersion]],
        modified_artifacts: Set[Id[Artifact]] = set(),
        conflicts: List[MergeConflict] = [],
    ) -> None:
        self.id = id
        self.project = project
        self.name = name
        self.creator = creator
        self.created_at = created_at
        self.last_modified = last_modified
        self.description = description
        self.basis = basis
        self.baseline_id = baseline_id
        self.baseline_version = baseline_version
        self.history = history
        self.change = change
        self.working_versions = working_versions
        self.modified_artifacts = modified_artifacts
        self.conflicts = conflicts

    def to_dict(self) -> JDict:
        return {
            "type": "Workspace",
            "_id": str(self.id),
            "project": self.project,
            "name": self.name,
            "creator": self.creator,
            "created_at": self.created_at.isoformat(),
            "last_modified": self.last_modified.isoformat(),
            "description": self.description,
            "basis": self.basis.to_dict(),
            "baseline_id": str(self.baseline_id),
            "baseline_version": str(self.baseline_version),
            "history": self.history,
            "change": self.change,
            "working_versions": dict(
                {str(aid): str(vid) for aid, vid in self.working_versions.items()}
            ),
            "modified_artifacts": list(str(s) for s in self.modified_artifacts),
            "conflicts": list(mc.to_dict() for mc in self.conflicts),
        }

    @classmethod
    def from_dict(cls, d: JDict) -> "Workspace":
        return Workspace(
            id=Id.from_string(d["_id"]),
            project=d["project"],
            name=d["name"],
            creator=d["creator"],
            created_at=datetime.fromisoformat(d["created_at"]),
            last_modified=datetime.fromisoformat(d["last_modified"]),
            description=d["description"],
            basis=ProjectVersionSpecifier.from_dict(d["basis"]),
            baseline_id=Id.from_string(d["baseline_id"]),
            baseline_version=Id.from_string(d["baseline_version"]),
            history=d["history"],
            change=d["change"],
            working_versions=dict(
                {
                    Id.from_string(aid): Id.from_string(vid)
                    for (aid, vid) in d["working_versions"]
                }
            ),
            conflicts=list(MergeConflict.from_dict(c) for c in d["conflicts"]),
        )
