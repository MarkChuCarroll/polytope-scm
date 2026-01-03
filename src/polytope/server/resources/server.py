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

import json
import falcon
from falcon.request import Request
from falcon.response import Response
from falcon_auth import JWTAuthBackend, FalconAuthMiddleware

from polytope.common.error import PtException
from polytope.server.depot.config import Config
from polytope.server.depot import Depot
from polytope.server.resources.login import Authenticator
from polytope.server.depot.stashes.user_stash import UserStash


class Server:
    def __init__(self, config: Config) -> None:
        self.depot = Depot(config)
        self.auth_backend = JWTAuthBackend(
            Authenticator(self.depot.user_stash), config["server"]["jwt_key"]
        )
        self.auth_middleware = FalconAuthMiddleware(
            self.auth_backend, exempt_routes=["/login"]
        )

        self.app = falcon.App(middleware=[self.auth_middleware])
        self.app.req_options.default_media_type = "application/json"
        self.app.add_route("/login", LoginResource(self.depot.user_stash))


class LoginResource:
    def __init__(self, user_stash: UserStash) -> None:
        self.user_stash = user_stash

    def on_post(self, req: falcon.Request, resp: falcon.Response):
        doc = json.load(req.bounded_stream)
        user = doc["user_id"]
        code = doc["code"]
        nonce = doc["nonce"]
        try:
            auth = self.user_stash.authenticate(user, code, nonce)
            resp.text = json.dumps(auth.to_dict())
        except PtException as e:
            resp.text = str(e)
            resp.status_code = e.kind.to_status_code().value
