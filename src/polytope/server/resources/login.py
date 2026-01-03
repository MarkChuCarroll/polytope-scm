# Copyright 2026 Mark C. Chu-Carroll
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

from typing import Dict
from falcon_auth import JWTAuthBackend
from polytope.common.stashable.user import AuthenticatedUser
from polytope.server.depot.stashes.user_stash import Authentication, UserStash


class Authenticator:
    USERID_CLAIM = "http://goodmath.org/polytope/user_id"
    TOKEN_CLAIM = "http://goodmath.org/polytope/token"

    def __init__(self, user_stash: UserStash) -> None:
        self.user_stash = user_stash

    def user_loader(self, payload: Dict[str, str]) -> AuthenticatedUser | None:
        user_id = payload[Authenticator.USERID_CLAIM]
        user_auth: Authentication | None = self.user_stash.get_auth(user_id)
        if user_auth is None:
            return None
        if user_auth["token"] == payload[Authenticator.TOKEN_CLAIM]:
            user = self.user_stash.get_user(user_id)
            return AuthenticatedUser(user_id, user_auth["token"],
                                     user.permitted_actions)
        return None
