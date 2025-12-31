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

from enum import Enum
import re
from typing import Any, Dict

from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.artifact import ArtifactVersion
from polytope.common.stashable.ids import Id


class PVSKind(Enum):
    History = "h"
    HistoryVersion = "hv"
    Change = "c"
    Baseline = "b"


class ProjectVersionSpecifier:
    def __init__(
        self,
        kind: PVSKind,
        project: str,
        history: str,
        change: str | None = None,
        idx: int | None = None,
        baseline: Id[ArtifactVersion] | None = None,
    ) -> None:
        self.kind = kind
        self.project = project
        self.history = history
        self.change = change
        self.idx = idx
        self.baseline = baseline

    @classmethod
    def make_history(
        cls, project: str, history: str, idx: int | None = None
    ) -> "ProjectVersionSpecifier":
        if idx is None:
            return cls(PVSKind.History, project=project, history=history)
        else:
            return cls(
                PVSKind.HistoryVersion, project=project, history=history, idx=idx
            )

    @classmethod
    def make_baseline(
        cls, project: str, history: str, baseline: Id[ArtifactVersion]
    ) -> "ProjectVersionSpecifier":
        return cls(
            PVSKind.Baseline, project=project, history=history, baseline=baseline
        )

    @classmethod
    def make_change(
        cls, project: str, history: str, change: str, idx: int | None = None
    ) -> "ProjectVersionSpecifier":
        return cls(PVSKind.Change, project, history, change, idx)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "kind": self.kind.value,
            "project": self.project,
            "history": self.history,
            "change": self.change,
            "idx": self.idx,
            "baseline": (None if self.baseline is None else str(self.baseline)),
        }

    @classmethod
    def from_dict(cls, dict: Dict[str, Any]) -> "ProjectVersionSpecifier":
        return ProjectVersionSpecifier(
            kind=PVSKind(dict["kind"]),
            project=dict["project"],
            history=dict["history"],
            change=dict["change"],
            idx=dict["idx"],
            baseline=None
            if dict["baseline"] is None
            else Id.from_string(dict["baseline"]),
        )

    def __repr__(self) -> str:
        match self.kind:
            case PVSKind.History:
                return f"h:{self.project}/{self.history}"
            case PVSKind.HistoryVersion:
                return f"hv:{self.project}/{self.history}@{self.idx}"
            case PVSKind.Change:
                if self.idx is not None:
                    return f"c:{self.project}/{self.history}/{self.change}@{self.idx}"
                else:
                    return f"c:{self.project}/{self.history}/{self.change}"
            case PVSKind.Baseline:
                return f"cs:{self.project}/{self.history}/{self.baseline}"

    @classmethod
    def parse(cls, s: str) -> "ProjectVersionSpecifier":
        parts = s.split(":")
        if len(parts) != 2:
            raise PtException(
                ErrorKind.Parsing, f"Invalid project version specifier: {s}"
            )
        match parts[0]:
            case "h":
                return cls.parseHistoryVersionSpecifier(parts[1])
            case "c":
                return cls.parseChangeVersionSpecifier(parts[1])
            case "hv":
                return cls.parseHistoryIndexVersionSpecifier(parts[1])
            case "cs":
                return cls.parseChangeStepVersionSpecifier(parts[1])
            case "b":
                return cls.parseBaselineVersionSpecifier(parts[1])
            case _:
                raise PtException(
                    ErrorKind.Parsing,
                    f"Invalid project version specifier kind: ${parts[0]}",
                )

    @classmethod
    def parseHistoryVersionSpecifier(cls, spec: str) -> "ProjectVersionSpecifier":
        regex = re.compile(r"([A-Za-z0-9_-]+)/([A-Za-z0-9]+")
        m = regex.fullmatch(spec)
        if m is not None:
            return ProjectVersionSpecifier(PVSKind.History, m.group(1), m.group(2))
        else:
            raise PtException(
                ErrorKind.Parsing,
                f"A history version specifier should have two parts,  recieved: '{spec}'",
            )

    @classmethod
    def parseHistoryIndexVersionSpecifier(cls, spec: str) -> "ProjectVersionSpecifier":
        regex = re.compile(r"([A-Za-z0-9_-]+)/([A-Za-z0-9]+)@([0-9]+)")
        m = regex.fullmatch(spec)
        if m is not None:
            return ProjectVersionSpecifier(
                PVSKind.HistoryVersion, m.group(1), m.group(2), idx=int(m.group(3))
            )
        else:
            raise PtException(
                ErrorKind.Parsing,
                "A history index version specifier must include an index",
            )

    @classmethod
    def parseChangeVersionSpecifier(cls, spec: str) -> "ProjectVersionSpecifier":
        regex = re.compile(r"([A-Za-z0-9_-]+)/([A-Za-z0-9]+)/([A-Za-z0-9]+)")
        m = regex.fullmatch(spec)
        if m is not None:
            return ProjectVersionSpecifier(
                PVSKind.Change, m.group(1), m.group(2), change=m.group(3)
            )
        else:
            raise PtException(
                ErrorKind.Parsing, "A change version specifier must include 3 parts"
            )

    @classmethod
    def parseChangeStepVersionSpecifier(cls, spec: str) -> "ProjectVersionSpecifier":
        regex = re.compile(r"([A-Za-z0-9_-]+)/([A-Za-z0-9]+)/([A-Za-z0-9]+)@([0-9]+)")
        m = regex.fullmatch(spec)
        if m is not None:
            return ProjectVersionSpecifier(
                PVSKind.Change,
                m.group(1),
                m.group(2),
                change=m.group(3),
                idx=int(m.group(4)),
            )
        else:
            raise PtException(
                ErrorKind.Parsing,
                "A Change step version specifier must include an index",
            )

    @classmethod
    def parseBaselineVersionSpecifier(cls, spec: str) -> "ProjectVersionSpecifier":
        regex = re.compile(r"([A-Za-z0-9_-]+)/([A-Za-z0-9]+)@(.*)")
        m = regex.fullmatch(spec)
        if m is not None:
            return ProjectVersionSpecifier(
                PVSKind.Baseline,
                m.group(1),
                m.group(2),
                baseline=Id.from_string(m.group(3)),
            )
        else:
            raise PtException(
                ErrorKind.Parsing, f"Invalid baseline specifier: `{spec}`"
            )
