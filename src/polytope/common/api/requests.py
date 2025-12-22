# Copyright 2025 Mark C. Chu-Carroll‡ *
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
 
from argparse import Action
from enum import Enum
from typing import List, NamedTuple

from polytope.common.agents.agents import MergeConflict
from polytope.common.stashable.change import Change, SavePoint
from polytope.common.stashable.history import History, HistoryStep
from polytope.common.stashable.ids import Id
from polytope.common.stashable.project import Project
from polytope.common.stashable.pvs import ProjectVersionSpecifier
from polytope.common.stashable.user import User
from polytope.common.stashable.workspace import WorkspaceDescriptor


class LoginRequest(NamedTuple):
    user_id: str
    password: str


class Token(NamedTuple):
    user_id: str
    token: str


class ProjectCreateRequest(NamedTuple):
    name: str
    description: str

class ProjectListResponse(NamedTuple):
    projects: List[Project]


class UserCreateRequest(NamedTuple):
    userId: str
    fullName: str
    email: str
    password: str
    permittedActions: List[Action]

class UserUpdateKind(Enum):
    Reactivate = "reactivate"
    Deactivate = "deactivate"
    Grant = "grant"
    Revoke = "revoke"
    Password = "password"

class UserUpdateRequest(NamedTuple):
    kind: UserUpdateKind
    actions: List[Action] | None
    password: str | None

class ChangeListResponse(NamedTuple):
    changes: List[Change]


class UserListResponse(NamedTuple):
    users: List[User]

class SavesListResponse(NamedTuple):
    saves: List[SavePoint]

class HistoryCreateRequest(NamedTuple):
    name: str
    description: str
    parent_history: str
    step: int | None

class WorkspaceCreateRequest(NamedTuple):
    name: str
    history: str
    description: str

class WorkspaceCreateChangeRequest(NamedTuple):
    history: str
    change_name: str
    description: str

class WorkspaceAddFileRequest(NamedTuple):
    path: str
    artifact_type: str
    content: str

class HistoryListResponse(NamedTuple):
    histories: List[History]

class HistoryStepsResponse(NamedTuple):
    steps: List[HistoryStep]

class WorkspaceResetRequest(NamedTuple):
    reason: str
    step_index: int | None

class WorkspaceListResponse(NamedTuple):
    workspaces: List[WorkspaceDescriptor]

class PathListResponse(NamedTuple):
    paths: List[str]

class WorkspaceMoveFileRequest(NamedTuple):
    path_before: str
    path_after: str

class WorkspaceFileContents(NamedTuple):
    path: str
    artifact_type: str
    content: str

class WorkspaceGetMultiRequest(NamedTuple):
    paths: List[str]


class WorkspaceSaveRequest(NamedTuple):
    description: str
    resolvedConflicts: List[Id[MergeConflict]]


class WorkspaceDeliverRequest(NamedTuple):
    description: str

class WorkspaceIntegrateChangeRequest(NamedTuple):
    source_history: str
    changeName: str

class WorkspaceIntegrateDiffRequest(NamedTuple):
    fromVersion: ProjectVersionSpecifier
    toVersion: ProjectVersionSpecifier
    