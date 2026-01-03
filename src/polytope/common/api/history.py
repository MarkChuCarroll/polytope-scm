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
from polytope.common.stashable.change import Change, SavePoint
from polytope.common.stashable.history import History, HistoryStep
from polytope.common.stashable.ids import Id


class HistoryCreateRequest(NamedTuple):
    name: str
    description: str
    parent_history: str
    step: int | None

    @classmethod
    def from_dict(cls, d: JDict) -> "HistoryCreateRequest":
        return HistoryCreateRequest(
            name=d["name"],
            description=d["description"],
            parent_history=d["parent_history"],
            step=d["step"],
        )

    def to_dict(self) -> JDict:
        return {
            "type": "HistoryCreateRequest",
            "name": self.name,
            "description": self.description,
            "parent_history": self.parent_history,
            "step": self.step,
        }


type HistoryCreateResponse = History

type HistoryListRequest = None


class HistoryListResponse(NamedTuple):
    project: str
    histories: List[History]

    @classmethod
    def from_dict(cls, d: JDict) -> "HistoryListResponse":
        return cls(
            project=d["project"],
            histories=list(History.from_dict(h) for h in d["histories"]),
        )

    def to_dict(self) -> JDict:
        return {
            "type": "HistoryListResponse",
            "project": self.project,
            "histories": list(h.to_dict() for h in self.histories),
        }


type HistoryStepsRequest = None


class HistoryStepsResponse(NamedTuple):
    steps: List[HistoryStep]

    @classmethod
    def from_dict(cls, d: JDict) -> "HistoryStepsResponse":
        return HistoryStepsResponse(
            steps=list(HistoryStep.from_dict(h) for h in d["steps"])
        )

    def to_dict(self) -> JDict:
        return {
            "type": "HistoryStepsResponse",
            "steps": list(h.to_dict() for h in self.steps),
        }


type ChangeListRequest = None


class ChangeListResponse(NamedTuple):
    changes: List[Change]

    @classmethod
    def from_dict(cls, d: JDict) -> "ChangeListResponse":
        return cls(changes=list(Change.from_dict(c) for c in d["changes"]))

    def to_dict(self) -> JDict:
        return {
            "type": "ChangeListResponse",
            "changes": list(c.to_dict() for c in self.changes),
        }


type SavesListRequest = None


class SavesListResponse(NamedTuple):
    saves: List[Id[SavePoint]]

    @classmethod
    def from_dict(cls, d: JDict) -> "SavesListResponse":
        return SavesListResponse(saves=list(Id.from_string(s) for s in d["saves"]))

    def to_dict(self) -> JDict:
        return {
            "type": "SavesListResponse",
            "saves": list(str(s) for s in self.saves),
        }
