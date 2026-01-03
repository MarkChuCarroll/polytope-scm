# Copyright 2026 Mark C. Chu-Carroll‡ *
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

import base64
from typing import Dict, List, NamedTuple

from polytope.common.agents.agents import MergeConflict
from polytope.common.stashable import JDict
from polytope.common.stashable.artifact import Artifact
from polytope.common.stashable.change import Change, SavePoint
from polytope.common.stashable.ids import Id
from polytope.common.stashable.pvs import ProjectVersionSpecifier
from polytope.common.stashable.workspace import Workspace, WorkspaceDescriptor


## Basic workspace administration stuff.


class WorkspaceCreateRequest(NamedTuple):
    name: str
    history: str
    description: str

    @classmethod
    def from_dict(cls, d: JDict) -> "WorkspaceCreateRequest":
        return cls(name=d["name"], history=d["history"], description=d["description"])

    def to_dict(self) -> JDict:
        return {
            "type": "Workspace`Request",
            "name": self.name,
            "history": self.history,
            "description": self.description,
        }


type WorkspaceListRequest = None


class WorkspaceListResponse(NamedTuple):
    workspaces: List[WorkspaceDescriptor]

    @classmethod
    def from_dict(cls, d: JDict) -> "WorkspaceListResponse":
        return WorkspaceListResponse(
            workspaces=list(WorkspaceDescriptor.from_dict(wd) for wd in d["workspaces"])
        )

    def to_dict(self) -> JDict:
        return {
            "type": "WorkspaceListResponse",
            "workspaces": list(ws.to_dict() for ws in self.workspaces),
        }


class WsCreateChangeReq(NamedTuple):
    history: str
    change_name: str
    description: str

    @classmethod
    def from_dict(cls, d: JDict) -> "WsCreateChangeReq":
        return cls(
            history=d["history"],
            change_name=d["change_name"],
            description=d["description"],
        )

    def to_dict(self) -> JDict:
        return {
            "type": "WsCreateChangeReq",
            "history": self.history,
            "change_name": self.change_name,
            "description": self.description,
        }


type WsCreateChangeResp = Change


class WsAddFileReq(NamedTuple):
    path: str
    artifact_type: str
    content: bytes

    @classmethod
    def from_dict(cls, d: JDict) -> "WsAddFileReq":
        return WsAddFileReq(
            d["path"], d["artifact_type"], base64.b64decode(d["content"])
        )

    def to_dict(self) -> JDict:
        return {
            "type": "WsAddFileReq",
            "path": self.path,
            "artifact": self.artifact_type,
            "content": base64.b64encode(self.content),
        }


class WsAddFileResp(NamedTuple):
    id: Id[Artifact]

    @classmethod
    def from_dict(cls, d: JDict) -> "WsAddFileResp":
        return cls(id=Id.from_string(d["id"]))

    def to_dict(self) -> JDict:
        return {"type": "WsAddFileResp", "id": str(self.id)}


class WsCheckUpToDateReq:
    def to_dict(self) -> JDict:
        return {"type": "WsCheckUpToDateReq"}


class WsCheckUpToDateResp(NamedTuple):
    up_to_date: bool

    def to_dict(self) -> JDict:
        return {"type": "WsCheckUpToDateResp", "up_to_date": self.up_to_date}

    @classmethod
    def from_dict(cls, d: JDict) -> "WsCheckUpToDateResp":
        return cls(d["up_to_date"])


class WsOpenHistoryReq(NamedTuple):
    history: str
    step: int | None

    @classmethod
    def from_dict(cls, d: JDict) -> "WsOpenHistoryReq":
        return cls(history=d["history"], step=d["step"])

    def to_dict(self) -> JDict:
        return {"type": "WsOpenHistoryReq", "history": self.history, "step": self.step}


type WsOpenHistoryResp = Workspace


class WsOpenChangeReq(NamedTuple):
    history: str
    change: str
    save: int | None

    @classmethod
    def from_dict(cls, d: JDict) -> "WsOpenChangeReq":
        return WsOpenChangeReq(history=d["history"], change=d["change"], save=d["save"])

    def to_dict(self) -> JDict:
        return {
            "type": "WsOpenChangeReq",
            "history": self.history,
            "change": self.change,
            "save": self.save,
        }


type WsOpenChangeResp = Workspace


class WsMoveFileReq(NamedTuple):
    path_before: str
    path_after: str

    @classmethod
    def from_dict(cls, d: JDict) -> "WsMoveFileReq":
        return WsMoveFileReq(path_before=d["path_before"], path_after=d["path_after"])

    def to_dict(self) -> JDict:
        return {
            "type": "WsMoveFileReq",
            "path_before": self.path_before,
            "path_after": self.path_after,
        }


type WsMoveFileResp = Workspace


class WsFileContents(NamedTuple):
    path: str
    artifact_type: str
    metadata: Dict[str, str]
    content: bytes

    @classmethod
    def from_dict(cls, d: JDict) -> "WsFileContents":
        return WsFileContents(
            path=d["path"],
            artifact_type=d["artifact_type"],
            metadata=d["metadata"],
            content=base64.b64decode(d["content"]),
        )

    def to_dict(self) -> JDict:
        return {
            "type": "WsFileContents",
            "path": self.path,
            "artifact_type": self.artifact_type,
            "metadata": self.metadata,
            "content": base64.b64encode(self.content),
        }


class WsGetManyReq(NamedTuple):
    paths: List[str]

    @classmethod
    def from_dict(cls, d: JDict) -> "WsGetManyReq":
        return cls(paths=d["paths"])

    def to_dict(self) -> JDict:
        return {"type": "WsGetManyReq", "paths": self.paths}


class WsGetManyResp(NamedTuple):
    files: List[WsFileContents]

    @classmethod
    def from_dict(cls, d: JDict) -> "WsGetManyResp":
        return WsGetManyResp(list(WsFileContents.from_dict(c) for c in d["files"]))

    def to_dict(self) -> JDict:
        return {"type": "WsGetManyResp", "files": list(f.to_dict() for f in self.files)}


class WsSaveReq(NamedTuple):
    description: str
    resolved_conflicts: List[Id[MergeConflict]]

    @classmethod
    def from_dict(cls, d: JDict) -> "WsSaveReq":
        return WsSaveReq(
            description=d["description"],
            resolved_conflicts=list(
                Id.from_string(id) for id in d["resolved_conflicts"]
            ),
        )

    def to_dict(self) -> JDict:
        return {
            "type": "WsSaveReq",
            "description": self.description,
            "resolved_conflicts": list(str(id) for id in self.resolved_conflicts),
        }


type WsSaveResp = SavePoint


class WsDeleteFileReq(NamedTuple):
    path: str

    def to_dict(self) -> JDict:
        return {
            "type": "WsDeleteFileReq",
            "path": self.path,
        }

    @classmethod
    def from_dict(cls, d: JDict) -> "WsDeleteFileReq":
        return WsDeleteFileReq(path=d["path"])


type WsDeleteFileResp = Workspace


class WsDeliverReq(NamedTuple):
    description: str

    @classmethod
    def from_dict(cls, d: JDict) -> "WsDeliverReq":
        return cls(description=d["description"])

    def to_dict(self) -> JDict:
        return {"type": "WorkspaceDeliverRequset", "description": self.description}


type WsDeliverResp = Workspace


class WsIntegrateReq(NamedTuple):
    source_history: str
    change_name: str

    @classmethod
    def from_dict(cls, d: JDict) -> "WsIntegrateReq":
        return cls(source_history=d["source_history"], change_name=d["change_name"])

    def to_dict(self) -> JDict:
        return {
            "type": "WsIntegrateReq",
            "source_history": self.source_history,
            "change_name": self.change_name,
        }


class WsConflictResp(NamedTuple):
    ws: Workspace
    conflicts: List[MergeConflict]

    @classmethod
    def from_dict(cls, d: JDict) -> "WsConflictResp":
        return cls(
            ws=Workspace.from_dict(d["ws"]),
            conflicts=list(MergeConflict.from_dict(con) for con in d["conflicts"]),
        )

    def to_dict(self) -> JDict:
        return {
            "type": "WsConflictResp",
            "ws": self.ws.to_dict(),
            "conflicts": list(c.to_dict() for c in self.conflicts),
        }


class WsIntegrateDiffReq(NamedTuple):
    from_version: ProjectVersionSpecifier
    to_version: ProjectVersionSpecifier

    @classmethod
    def from_dict(cls, d: JDict) -> "WsIntegrateDiffReq":
        return cls(
            from_version=ProjectVersionSpecifier.from_dict(d["from_version"]),
            to_version=ProjectVersionSpecifier.from_dict(d["to_version"]),
        )

    def to_dict(self) -> JDict:
        return {
            "type": "WsIntegrateDiffReq",
            "from_version": self.from_version.to_dict(),
            "to_version": self.to_version.to_dict(),
        }


class WsUpdateReq(NamedTuple):
    def to_dict(self) -> JDict:
        return {"type": "WsUpdateReq"}


class WsPathListReq(NamedTuple):
    def to_dict(self) -> JDict:
        return {"type": "WsPathListReq"}


class WsPathListResp(NamedTuple):
    paths: List[str]

    @classmethod
    def from_dict(cls, d: JDict) -> "WsPathListResp":
        return cls(paths=d["paths"])

    def to_dict(self) -> JDict:
        return {"type": "WsPathListResp:w", "paths": self.paths}


class WsAbandonReq(NamedTuple):
    reason: str

    @classmethod
    def from_dict(cls, d: JDict) -> "WsAbandonReq":
        return cls(reason=d["reason"])

    def to_dict(self) -> JDict:
        return {"type": "WsAbandonReq", "reason": self.reason}
