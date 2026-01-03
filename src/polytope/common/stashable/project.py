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

from datetime import datetime
import textwrap
from typing import List, NamedTuple

from polytope.common.stashable.artifact import Artifact
from polytope.common.stashable.ids import Id
from polytope.common.stashable import JDict


class Project(NamedTuple):
    name: str
    creator: str
    timestamp: datetime
    description: str
    root_dir: Id[Artifact]
    baseline: Id[Artifact]
    histories: List[str]

    def to_dict(self) -> JDict:
        return {
            "type": "Project",
            "name": self.name,
            "creator": self.creator,
            "timestamp": self.timestamp,
            "description": self.description,
            "root_dir": str(self.root_dir),
            "baseline": str(self.baseline),
            "histories": self.histories,
        }

    @classmethod
    def from_dict(cls, dict: JDict) -> "Project":
        return cls(
            name=dict["name"],
            creator=dict["creator"],
            timestamp=dict["timestamp"],
            description=dict["description"],
            root_dir=Id.from_string(dict["root_dir"]),
            baseline=Id.from_string(dict["baseline"]),
            histories=dict["histories"],
        )

    def __repr__(self):
        hists = ", ".join([str(h) for h in self.histories])
        return textwrap.dedent(f"""\
            Project: {self.name}
            Created by {self.creator} at {self.timestamp}
            Description: {self.description}
            Histories: {hists}
            Baseline: {self.baseline}
            Rootdir: {self.root_dir}
            """)
