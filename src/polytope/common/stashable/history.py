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

from datetime import datetime
import textwrap
from typing import List, NamedTuple

from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.change import Change
from polytope.common.stashable.ids import Id
from polytope.common.stashable.pvs import ProjectVersionSpecifier
from polytope.common.stashable.stashable import JDict


class History(NamedTuple):
    project: str
    name: str
    description: str
    timestamp: datetime
    basis: ProjectVersionSpecifier
    steps: List[Id["HistoryStep"]]

    def to_dict(self) -> JDict:
        return {
            "project": self.project,
            "name": self.name,
            "description": self.description,
            "timestamp": self.timestamp,
            "basis": self.basis.to_dict(),
            "steps": [str(s) for s in self.steps]
        }

    @classmethod
    def from_dict(cls, dict: JDict) -> "History":
        return History(
            project=dict["project"],
            name=dict["name"],
            description=dict["description"],
            timestamp=dict["timestamp"],
            basis=ProjectVersionSpecifier.from_dict(dict["basis"]),
            steps=[Id.from_string(i) for i in dict["steps"]]
        )

    def __repr__(self) -> str:
        return textwrap.dedent(f"""\
            History: {self.name}
            Created at: {self.timestamp}
            Description: {self.description}
            Basis: {self.basis}
            Contains {len(self.steps)} steps
            """)


class HistoryStep(NamedTuple):
    id: Id["HistoryStep"]
    project: str
    change: Id[Change] | None
    history_name: str
    idx: int
    baseline_id: Id[Artifact]
    baseline_version_id: Id[ArtifactVersion]
    description: str

    def to_dict(self) -> JDict:
        if self.change is None:
            change = None
        else:
            change = str(self.change)
        return {
            "_id": str(self.id),
            "project": self.project,
            "change": change,
            "history_name": self.history_name,
            "idx": self.idx,
            "baseline_id": str(self.baseline_id),
            "baseline_version_id": str(self.baseline_version_id),
            "description": self.description
        }

    @classmethod
    def from_dict(cls, dict: JDict) -> "HistoryStep":
        return HistoryStep(
            id=Id.from_string(dict["_id"]),
            project=dict["project"],
            change=None if dict["change"] is None else Id.from_string(dict["change"]),
            history_name=dict["history_name"],
            idx=dict["idx"],
            baseline_id=Id.from_string(dict["baseline_id"]),
            baseline_version_id=Id.from_string(dict["baseline_version_id"]),
            description=dict["description"]
        )

    def __repr__(self) -> str:
        return textwrap.dedent(f"""\
            Step: {self.project}::{self.history_name}@{self.index}
            Baseline: {self.baseline_version_id}
            Description: {self.description}
            """)
