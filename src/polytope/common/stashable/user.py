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

# An authenticated user. This is passed around the server code to represent
# an authenticated user, and determine if they have the correct permissions
# to perform operations.
#
# From a security perspective, this needs work. The current auth token is
# the salted password retrieved from the depot. A bad actor, such as
# a rogue agent, could retrieve that, and have full access to anything they wanted.
# There should be some mechanism here that isn't accessible from outside the users
# stash for creating a token that we can quickly validate to ensure that
# the auth token is genuine.

from enum import Enum
from math import perm
import time
from typing import Any, Dict, List, NamedTuple
from datetime import datetime

from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.stashable import JDict


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


class Action(NamedTuple):
    """
    Actions are the data structure for the permission system.

    * Any operation on the depot is described by an action.
    * An action has three components:
        * scope type:
            * the scope type can be a project, a depot, or the entire
              server.
            * the scope types are ordered: depot > scope, global >depot.
        * action level: the severity of the action.
            * read, write, delete, admin.
        * scope name: the name of a project/entity type, or "*".
    * Actions are partially ordered:
        * scope "*" > any specific named scope;
        * admin > delete > wrote > read
        * global > depot > project
    * a user's privileges in polytope are defined as a set of
      the actions they're allowed to perform. Any action
      which is <= one of their permitted actions is allowed.
    """
    scope_type: ActionScopeType
    scope_name: str
    level: ActionLevel

    def includes(self, requested: "Action") -> bool:
        return self.level.value >= requested.level.value and (
            self.scope_type.value > requested.scope_type.value
            or (
                self.scope_type.value == requested.scope_type.value
                and (self.scope_name == requested.scope_name or self.scope_name == "*")
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
            f"{self.scope_type.markerChar()}{self.level.markerChar()}:{self.scope_name}"
        )

    def to_dict(self) -> JDict:
        return {
            "scope_type": self.scope_type.value,
            "scope_name": self.scope_name,
            "level": self.level.value
        }

    @classmethod
    def from_dict(cls, d: JDict) -> Action:
        return cls(scope_type=ActionScopeType(d["scope_type"]),
                   level=ActionLevel(d["level"]),
                   scope_name=d["scope_name"])

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

    @classmethod
    def parse(cls, act: str) -> "Action":
        """
        Parse an action from a human-friendly concise syntax.
        An action is written: "SL:str", where:
        - S is the abbreviation of the scope type,
        - L is the abbreviation of the action level
        - str is the scope name.

        Action levels Read, Write, Delete, and Admin are abbreviated to R,W,D, or A

        Scope types are abbreviate to G for global, D for depot, and P for project.

        E.g., global admin would be written GA:*,
        administration privileges on a project foo would be PA:foo, etc.
        """
        scope_type = ActionScopeType.ofMarker(act[0])
        level = ActionLevel.ofMarker(act[1])
        if act[2] != ":":
            raise PtException(
                ErrorKind.Parsing,
                f"Action string should contain a colon at position 2, but received '{act}'",
            )
        focus = act[3:]
        return Action(scope_type, focus, level)


class AuthenticatedUser(NamedTuple):
    user_id: str
    auth_token: str
    permitted_actions: List[Action]

    def to_dict(self) -> JDict:
        return {
            "_id": self.user_id,
            "auth_token": self.auth_token,
            "permitted_actions": list(act.to_dict() for act in self.permitted_actions)
        }

    @classmethod
    def from_dict(cls, d: JDict) -> AuthenticatedUser:
        return AuthenticatedUser(
            user_id=d["_id"],
            auth_token=d["auth_token"],
            permitted_actions=list(Action.from_dict(a) for a in d["permitted_actions"])
        )


class User(NamedTuple):
    user_id: str
    full_name: str
    permitted_actions: List[Action]
    email: str
    password: str
    timestamp: datetime
    active: bool

    def to_dict(self) -> JDict:
        return {
            "_id": self.user_id,
            "full_name": self.full_name,
            "email": self.email,
            "password": self.password,
            "timestamp": self.timestamp.isoformat(),
            "active": self.active if 1 else 0,
            "permitted_actions": list(
                p.to_dict() for p in self.permitted_actions
            )
        }

    @classmethod
    def from_dict(cls, d: JDict) -> "User":
        return User(
            user_id=d["_id"],
            full_name=d["full_name"],
            email=d["email"],
            password=d["password"],
            timestamp=datetime.fromisoformat(d["timestamp"]),
            active=d["active"] != 0,
            permitted_actions=list(Action.from_dict(a) for a in d["permitted_actions"])
        )
