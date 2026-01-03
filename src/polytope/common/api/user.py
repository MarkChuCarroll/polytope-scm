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

import base64
from enum import Enum
from typing import List, NamedTuple

from polytope.common.stashable import JDict
from polytope.common.stashable.user import Action, User


class UserCreateRequest(NamedTuple):
    user_id: str
    full_name: str
    email: str
    password: str
    permitted_actions: List[Action]

    @classmethod
    def from_dict(cls, d: JDict) -> "UserCreateRequest":
        return cls(
            user_id=d["user_id"],
            full_name=d["full_name"],
            email=d["email"],
            password=d["password"],
            permitted_actions=list(Action.from_dict(a) for a in d["permitted_actions"]),
        )

    def to_dict(self) -> JDict:
        return {
            "type": "UserCreateRequest",
            "user_id": self.user_id,
            "full_name": self.full_name,
            "email": self.email,
            "password": self.password,
            "permitted_actions": list(p.to_dict() for p in self.permitted_actions),
        }


type UserCreateResponse = User


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

    @classmethod
    def from_dict(cls, d: JDict) -> "UserUpdateRequest":
        return cls(
            kind=UserUpdateKind(d["kind"]),
            actions=list(Action.from_dict(a) for a in d["actions"])
            if "actions" in d
            else None,
            password=d["password"] if "password" in d else None,
        )

    def to_dict(self) -> JDict:
        return {
            "type": "UserUpdateRequest",
            "kind": self.kind.value,
            "actions": list(a.to_dict() for a in self.actions)
            if self.actions is not None
            else None,
            "password": self.password,
        }


type UserUpdateResponse = User


class LoginRequest(NamedTuple):
    user_id: str
    passcode: str
    nonce: str

    @classmethod
    def from_dict(cls, d: JDict) -> "LoginRequest":
        return cls(
            user_id=d["user_id"],
            passcode=d["passcode"],
            nonce=d["nonce"],
        )

    def to_dict(self) -> JDict:
        return {
            "type": "LoginRequest",
            "user_id": self.user_id,
            "passcode": self.passcode,
            "nonce": self.nonce,
        }


class LoginResponse(NamedTuple):
    user_id: str
    auth: str

    def to_dict(self) -> JDict:
        return {
            "type": "LoginResponse",
            "user_id": self.user_id,
            "auth": self.auth,
        }

    @classmethod
    def from_dict(cls, d: JDict) -> "LoginResponse":
        return cls(
            user_id=d["user_id"],
            auth=d["auth"],
        )

    def to_header(self) -> str:
        token_str = f"{self.user_id}:{self.auth}"
        token_bytes = token_str.encode("utf-8")
        b64_bytes = base64.b64encode(token_bytes)
        return b64_bytes.decode("utf-8")


class LoginHeader(NamedTuple):
    user_id: str
    auth: str

    @classmethod
    def from_header(cls, header: str) -> "LoginHeader":
        b64_bytes = header.encode("utf-8")
        token_bytes = base64.b64decode(b64_bytes)
        token_str = token_bytes.decode("utf-8")
        user_id, auth = token_str.split(":", 1)
        return cls(user_id=user_id, auth=auth)
