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


from datetime import datetime
import textwrap
from typing import Dict, List, NamedTuple, Set

from polytope.common.agents.agents import MergeConflict
from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.ids import Id
from polytope.common.stashable.pvs import ProjectVersionSpecifier

class WorkspaceDescriptor(NamedTuple):
    wsName: str
    project: str
    creator: str
    description: str
    createdAt: datetime
    lastModified: datetime

    def __repr__(self) -> str:
        return textwrap.dedent(f"""\
            Workspace: {self.project}::{self.wsName}
            Created at: {self.createdAt} by: {self.creator}
            Description: {self.description}
            ModifiedAt: {self.modified}
            """)

class Workspace(NamedTuple):
    id: Id["Workspace"]
    project: str
    name: str
    creator: str
    createdAt: datetime
    lastModified: datetime
    description: str
    basis: ProjectVersionSpecifier
    baselineId: Id[Artifact]
    baselineVersion: Id[ArtifactVersion]
    history: str
    change: str | None
    workingVersions: Dict[Id[Artifact], Id[ArtifactVersion]]
    modifiedArtifacts: Set[Id[Artifact]] = set()
    conflicts: List[MergeConflict] = []


    # fun render(): str {
    #     created = toLocalDateTime(createdAt)
    #     modified = toLocalDateTime(lastModified)
    #     result = strBuilder()
    #     result.append("Workspace: $project::$name\n")
    #         .append("Created at: $created by: $creator\n")
    #         .append("ModifiedAt: $modified")
    #         .append("Description: $description\n")
    #         .append("Basis: $basis\n")
    #         .append("Conflicts:\n")
    #     for (c in conflicts) {
    #         result.append(c.render(indent=1))
    #         result.append("\t----")
    #     }
    #     return result.tostr()
