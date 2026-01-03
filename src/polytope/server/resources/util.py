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

import falcon

from polytope.common.api.user import LoginRequest, LoginResponse
from polytope.common.error import PtException
from polytope.server.depot import Depot


class PtResource:
    """
    Base class for all Polytope Depot resources
    """

    def __init__(self, depot: Depot):
        self.depot = depot


class LoginResource(PtResource):
    def __init__(self, depot: Depot):
        super().__init__(depot)
        self.user_stash = depot.user_stash

    def on_post(self, req: falcon.Request, resp: falcon.Response):
        print(f"LoginResource.on_post called with req: {req}")
        try:
            loginreq = LoginRequest.from_dict(req.media)
            print(f"Parsed login request: {loginreq}")
            user = self.user_stash.authenticate(
                loginreq.user_id, loginreq.passcode, loginreq.nonce
            )
            if user is None:
                resp.status = falcon.HTTP_401
                resp.media = {"error": "Invalid username or password"}
                return
            resp.media = LoginResponse(user.user_id, user.auth_token).to_dict()
            resp.status = falcon.HTTP_200
            resp.content_type = "application/json"
        except PtException as e:
            resp.status = falcon.HTTP_400
            resp.media = {"error": str(e)}
