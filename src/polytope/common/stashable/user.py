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

from typing import List, NamedTuple
from datetime import datetime

from polytope.common.stashable.action import Action
from polytope.common.stashable.stashable import JDict


class AuthenticatedUser(NamedTuple):
    user_id: str
    auth_token: str
    permitted_actions: List[Action]

    def to_dict(self) -> JDict:
        return {
            "user_id": self.user_id,
            "auth_token": self.auth_token,
            "permitted_actions": [ p.to_dict() for p in self.permitted_actions]
        }

    @classmethod
    def from_dict(cls, dict: JDict) -> "AuthenticatedUser":
        return AuthenticatedUser(
            user_id = dict["user_id"],
            auth_token = dict["auth_token"],
            permitted_actions=[ Action.from_dict(a) for a in dict["permitted_actions"]]
        )


class User(NamedTuple):
    username: str
    full_name: str
    permitted_actions: List[Action]
    email: str
    password: str
    timestamp: datetime
    active: bool

    def to_dict(self) -> JDict:
        return {
            "username": self.username,
            "full_name": self.full_name,
            "permitted_actions": [ p.to_dict() for p in self.permitted_actions],
            "email": self.email,
            "password": self.password,
            "timestamp": self.timestamp,
            "active": self.active
        }

    @classmethod
    def from_dict(cls, dict: JDict) -> "User":
        return User(
            username=dict["username"],
            full_name = dict["full_name"],
            permitted_actions=[Action.from_dict(p) for p in dict["permitted_actions"]],
            email=dict["email"],
            password=dict["password"],
            timestamp=dict["timestamp"],
            active=dict["active"])
