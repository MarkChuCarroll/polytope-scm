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

from typing import List, NamedTuple
from polytope.common.stashable import JDict
from polytope.common.stashable.project import Project


class ProjectCreateRequest(NamedTuple):
    name: str
    description: str

    @classmethod
    def from_dict(cls, d: JDict) -> "ProjectCreateRequest":
        return cls(name=d["name"], description=d["description"])

    def to_dict(self) -> JDict:
        return {
            "type": "ProjectCreateRequest",
            "name": self.name,
            "description": self.description,
        }


type ProjectCreateResponse = Project


class ProjectListResponse(NamedTuple):
    projects: List[Project]

    @classmethod
    def from_dict(cls, d: JDict) -> "ProjectListResponse":
        return cls(projects=list(Project.from_dict(p) for p in d["projects"]))

    def to_dict(self) -> JDict:
        return {
            "type": "ProjectListResponse",
            "projects": list(p.to_dict() for p in self.projects),
        }
