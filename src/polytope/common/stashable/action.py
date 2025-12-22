#
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

from enum import Enum
from typing import Any, Dict, NamedTuple

from polytope.common.error import PtException, ErrorKind
from polytope.common.stashable.user import AuthenticatedUser


class ActionLevel(Enum):
    Read = 0
    Write = 1
    Delete = 2
    Admin = 3

    def markerChar(self) -> str:
        match self:
            case ActionLevel.Read:
                return "R"
            case ActionLevel.Write:
                return "W"
            case ActionLevel.Delete:
                return "D"
            case ActionLevel.Admin:
                return "A"

    @classmethod
    def ofMarker(cls, marker: str) -> "ActionLevel":
        match marker:
            case "R":
                return ActionLevel.Read
            case "W":
                return ActionLevel.Write
            case "D":
                return ActionLevel.Delete
            case "A":
                return ActionLevel.Admin
            case _:
                raise PtException(
                    ErrorKind.Parsing, f"Invalid action level marker '{marker}'"
                )


class ActionScopeType(Enum):
    Project = 0
    Depot = 1
    Global = 2

    def markerChar(self) -> str:
        match self:
            case ActionScopeType.Project:
                return "P"
            case ActionScopeType.Depot:
                return "D"
            case ActionScopeType.Global:
                return "G"

    @classmethod
    def ofMarker(cls, marker: str) -> "ActionScopeType":
        match marker:
            case "P":
                return ActionScopeType.Project
            case "D":
                return ActionScopeType.Depot
            case "G":
                return ActionScopeType.Global
            case _:
                raise PtException(
                    ErrorKind.Parsing, f"Invalid action scope type marker '{marker}'"
                )


# For permissions:
# - a permission grant the ability to perform a set of operations in a particular
#   scope.
# - Scopes are hierarchical: depot scopes include all project scopes; Global scopes
#   include all project and depot scopes.
# - every scope has a scope name. The scope name "*" includes all scopes of that level.
# - The set of permitted operations is determined by the action level.
#   Action levels are ordered, and higher action levels include the levels beneath them.
class Action(NamedTuple):
    scopeType: ActionScopeType
    scopeName: str
    level: ActionLevel

    @classmethod
    def from_dict(cls, dict: Dict[str, Any]) -> "Action":
        return Action(ActionScopeType[dict["scopeType"]], dict["scopeName"],
                      ActionLevel[dict["level"]])

    def to_dict(self) -> Dict[str, Any]:
        return {
            "scopeType": self.scopeType.value,
            "level": self.level.value,
            "scopeName": self.scopeName
        }

    # A permission action includes a requested action if the requested action
    # is at a lower level; or if the requested action is at the same level as
    # the permission with the same scope name; and the action level of the
    # requested action is less than or equal to the action level of the permission
    #
    # @param requested a requested action.
    # @return true if this permitted action includes the requested action.
    #
    def includes(self, requested: "Action") -> bool:
        return self.level.value >= requested.level.value and (
            self.scopeType.value > requested.scopeType.value
            or (
                self.scopeType.value == requested.scopeType.value
                and (self.scopeName == requested.scopeName or self.scopeName == "*")
            )
        )

    def permitted_for(self, user: AuthenticatedUser) -> bool:
        permits = [
            user_permit
            for user_permit in user.permitted_actions
            if user_permit.includes(self)
        ]
        return len(permits) > 0

    def __repr__(self) -> str:
        return (
            f"{self.scopeType.markerChar()}{self.level.markerChar()}:{self.scopeName}"
        )

    @classmethod
    def admin_users(cls) -> "Action":
        return Action(ActionScopeType.Global, "users", ActionLevel.Admin)

    @classmethod
    def read_users(cls) -> "Action":
        return Action(ActionScopeType.Global, "users", ActionLevel.Read)

    @classmethod
    def write_project(cls, project: str) -> "Action":
        return Action(ActionScopeType.Project, project, ActionLevel.Write)

    @classmethod
    def delete_project(cls, project: str) -> "Action":
        return Action(ActionScopeType.Project, project, ActionLevel.Delete)

    @classmethod
    def read_project(cls, project: str) -> "Action":
        return Action(ActionScopeType.Project, project, ActionLevel.Read)

    @classmethod
    def admin_project(cls, project: str) -> "Action":
        return Action(ActionScopeType.Project, project, ActionLevel.Admin)

    @classmethod
    def read_depot(cls) -> "Action":
        return Action(ActionScopeType.Depot, "projects", ActionLevel.Read)

    @classmethod
    def write_depot(cls) -> "Action":
        return Action(ActionScopeType.Depot, "projects", ActionLevel.Write)

    @classmethod
    def create_project(cls) -> "Action":
        return Action(ActionScopeType.Depot, "*", ActionLevel.Write)

    # Parse an action from a human-friendly concise syntax.
    #
    # An action is written:
    #    SL:str where S is the abbreviation of the scope type,
    #       L is the abbreviation of the action level, and str
    #       is the scope name.
    #
    # Action levels Read, Write, Delete, and Admin are abbreviated to R,W,D, or A
    # Scope types are abbreviate to G for global, D for depot, and P for project.
    #
    # So global admin would be written GA:*, administration privileges on a project foo
    # would be PA:foo, etc.
    #
    @classmethod
    def parse(cls, act: str) -> "Action":
        scope_type = ActionScopeType.ofMarker(act[0])
        level = ActionLevel.ofMarker(act[1])
        if act[2] != ":":
            raise PtException(
                ErrorKind.Parsing,
                f"Action string should contain a colon at position 2, but received '{act}'",
            )
        focus = act[3:]
        return Action(scope_type, focus, level)
