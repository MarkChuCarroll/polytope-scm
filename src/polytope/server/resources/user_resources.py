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

from falcon import Request, Response
import falcon

from polytope.common.api.user import (
    UserCreateRequest,
    UserUpdateKind,
    UserUpdateRequest,
)
from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable import JDict
from polytope.common.stashable.user import AuthenticatedUser, User

from polytope.server.depot import Depot
from polytope.server.resources.util import PtResource


class UsersResource(PtResource):
    """
    The /users resource.
    Supports get (retrieve list of all users) and post (create user)
    """

    def __init__(self, depot: Depot) -> None:
        super().__init__(depot)
        self.user_stash = depot.user_stash

    def on_get(self, req: Request, resp: Response) -> None:
        user: AuthenticatedUser | None = req.context["user"]
        if user is None:
            resp.status = falcon.HTTP_404
        else:
            try:
                users = self.user_stash.list_users(user)
                resp.media = users
                resp.status = falcon.HTTP_200
                resp.content_type = "application/json"
            except PtException as e:
                resp.status_code = e.kind.to_status_code()
                resp.media = str(e)

    def on_post(self, req: Request, resp: Response) -> None:
        auth: AuthenticatedUser | None = req.context["user"]
        if auth is None:
            resp.status = falcon.HTTP_404
            return
        try:
            req_body: JDict = req.get_media()
            user_to_create = UserCreateRequest.from_dict(req_body)
            new_user = self.user_stash.create(
                auth=auth,
                user_id=user_to_create.user_id,
                full_name=user_to_create.full_name,
                email=user_to_create.email,
                permitted_actions=user_to_create.permitted_actions,
                password=user_to_create.password,
            )
            resp.content_type = "application/json"
            resp.media = new_user.to_dict()
            resp.status = falcon.HTTP_200
        except PtException as e:
            resp.status_code = e.kind.to_status_code()
            resp.media = str(e)


class UserResource(PtResource):
    """
    Resource for /users/{user_id}
    Supports get (retrieve user), put (modify user)
    """

    def __init__(self, depot: Depot) -> None:
        super().__init__(depot)
        self.user_stash = depot.user_stash

    def on_get(self, req: Request, resp: Response, user_id: str) -> None:
        auth: AuthenticatedUser | None = req.context["user"]
        if auth is None:
            resp.status = falcon.HTTP_404
            return
        try:
            user = self.user_stash.retrieve_user(auth, user_id)
            resp.status = falcon.HTTP_200
            resp.content_type = "application/json"
            resp.media = json.dumps(user.to_dict())
        except PtException as e:
            resp.status_code = e.kind.to_status_code()
            resp.media = str(e)

    def on_put(self, req: Request, resp: Response, user_id: str) -> None:
        auth: AuthenticatedUser | None = req.context["user"]
        if auth is None:
            resp.status = falcon.HTTP_404
            return
        try:
            modify_user: UserUpdateRequest = UserUpdateRequest.from_dict(
                req.get_media()
            )
            updated_user: User
            match modify_user.kind:
                case UserUpdateKind.Reactivate:
                    updated_user = self.user_stash.reactivateUser(
                        auth=auth, user_id=user_id
                    )
                case UserUpdateKind.Deactivate:
                    updated_user = self.user_stash.deactivate_user(
                        auth=auth, user_id=user_id
                    )
                case UserUpdateKind.Password:
                    pw = modify_user.password
                    if pw is None:
                        raise PtException(
                            ErrorKind.UserError,
                            "Change password request does not include a new password",
                        )
                    updated_user = self.user_stash.updatePassword(auth, user_id, pw)
                case UserUpdateKind.Grant:
                    perms = modify_user.actions
                    if perms is None:
                        raise PtException(
                            ErrorKind.UserError,
                            "Grant permissions request does not include a permitted actions list",
                        )
                    updated_user = self.user_stash.grantPermissions(
                        auth, user_id, perms
                    )

                case UserUpdateKind.Revoke:
                    perms = modify_user.actions
                    if perms is None:
                        raise PtException(
                            ErrorKind.UserError,
                            "Revoke permissions request does not include a permitted actions list",
                        )
                    updated_user = self.user_stash.revokePermission(
                        auth, user_id, perms
                    )
            resp.status = falcon.HTTP_200
            resp.content_type = "application/json"
            resp.media = updated_user.to_dict()
        except PtException as e:
            resp.status_code = e.kind.to_status_code()
            resp.media = str(e)
