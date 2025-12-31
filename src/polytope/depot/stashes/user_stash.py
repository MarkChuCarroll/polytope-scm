# Copyright 2025 Mark C. Chu-Carroll
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http: // www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


import copy
from datetime import datetime, timedelta
import hashlib
from typing import List, TypedDict
import uuid
from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.user import (
    Action,
    ActionLevel,
    ActionScopeType,
    AuthenticatedUser,
    User,
)
from polytope.depot.config import Config
from polytope.depot.depot import Depot
from polytope.depot.stashes.stash import Stash


class Authentication(TypedDict):
    _id: str
    token: str
    exp: str


class UserStash(Stash):
    def __init__(self, depot: Depot) -> None:
        self.depot = depot
        self.db = depot.db
        self.users = self.db.get_collection("users")
        self.auths = self.db.get_collection("auths")

    def init_storage(self, config: Config) -> None:
        u = self.users.find_one({"_id": config["user"]["root_user"]})
        if u is None:
            self.users.insert_one(
                User(
                    user_id=config["user"]["root_user"],
                    full_name="The Dreaded Administrator",
                    email=config["user"]["root_email"],
                    password=self._salted_hash(
                        config["user"]["password"], config["user"]["root_user"]
                    ),
                    permitted_actions=[
                        Action(ActionScopeType.Global, "*", ActionLevel.Admin)
                    ],
                    timestamp=datetime.now(),
                    active=True,
                ).to_dict()
            )

    def validate_permissions(self, auth: AuthenticatedUser, action: Action) -> None:
        """
        Test if a user has permission to perform an action. Raises
        a permission exception if not.

        Arguments:
        auth -- the authenticated user.
        action -- the requested action.

         TODO: this should check that the auth token is valid.
        """
        if not action.permitted_for(auth):
            raise PtException(ErrorKind.Permission, "User permission denied")

    def _salted_hash(self, key: str, *parts: str) -> str:
        """
        Compute the salted hash of a user password for authentication.

        Arguments:
        username -- the name of the user to authenticate
        password -- the plaintext, unsalted password.
        """
        salt: int = 0
        for part in parts:
            for c in part:
                salt = int(salt * 37 + ord(c) / 211)
        sha = hashlib.sha256()
        sha.update(str(salt).encode())
        sha.update(key.encode())
        return sha.hexdigest()

    def _generate_auth_token(self, user_id: str) -> Authentication:
        auth = self.auths.find_one({"_id": user_id})

        if auth is None or (
            datetime.fromisoformat(auth["expiration"])
            < (datetime.now() + timedelta(days=1))
        ):
            token_id = uuid.uuid4().hex
            token = f"{user_id}/{token_id}"
            exp = datetime.now() + timedelta(weeks=1)
            auth = Authentication(_id=user_id, token=token, exp=exp.isoformat())
            self.auths.insert_one(auth)
        return auth

    def get_auth(self, user_id: str) -> Authentication | None:
        return self.auths.find_one({"_id": user_id})

    def validate_auth_token(self, user_id: str, token: str) -> AuthenticatedUser:
        entry = self.get_auth(user_id)
        if (
            entry is not None
            and entry["token"] == token
            and datetime.fromisoformat(entry["exp"]) > datetime.now()
        ):
            user = self.get_user(user_id)
            return AuthenticatedUser(user_id, entry["token"], user.permitted_actions)
        else:
            raise PtException(ErrorKind.Authentication, "authentication failed")

    def get_user(self, user_id: str) -> User:
        user_dict = self.users.find_one({"_id": user_id})
        if user_dict is None:
            raise PtException(ErrorKind.NotFound, f"User {user_id} not found")
        else:
            return User.from_dict(user_dict)

    def authenticate(self, user_id: str, code: str, nonce: str) -> AuthenticatedUser:
        """
        Authenticate a user.

        We want to be at least a little bit smart about authentication.
        We definitely don't want to be storing the user's password in
        plaintext, and we don't want to be sending a plaintext password
        in the API request. So instead, we're going to play some
        games with hashing.

        What we store in the DB for the user's password is going
        to be a hash computed from the user's password with a salt:
            password_in_db = hash(salt + password)

            where salt=sum(c in username)

        Then for authentication, we're going to compute an authentication
        hash:
            auth_hash = hash(username + password_in_db + nonce)
        """

        user: User = self.get_user(user_id)
        if not user.active:
            raise PtException(
                ErrorKind.Authentication, f"Authentication failed for user {user_id}"
            )

        sha = hashlib.sha256()
        sha.update(user_id.encode())
        sha.update(user.password.encode())
        sha.update(nonce.encode())
        if code != sha.hexdigest():
            raise PtException(ErrorKind.Authentication, "Authentication failed")
        auth = self._generate_auth_token(user_id)
        return AuthenticatedUser(
            user_id=user_id,
            auth_token=auth["token"],
            permitted_actions=user.permitted_actions,
        )

    def retrieve_user(self, auth: AuthenticatedUser, user_id: str) -> User:
        """
        Return a user record for a user of the system.
        The returned record will have its password field redacted.
        """
        if auth.user_id != user_id:
            self.validate_permissions(auth, Action.admin_users())
        result: User = self.get_user(auth.user_id)
        return copy.replace(result, password="<redacted>")

    def create(
        self,
        auth: AuthenticatedUser,
        user_id: str,
        full_name: str,
        email: str,
        permitted_actions: List[Action],
        password: str,
    ) -> User:
        self.validate_permissions(auth, Action.admin_users())
        user = self.users.find_one({"_id": user_id})
        if user is not None:
            raise PtException(
                ErrorKind.InvalidParameter,
                f"User with username '{user_id}' already exists",
            )
        encodedPassword = self._salted_hash(password, user_id)
        new_user = User(
            user_id=user_id,
            full_name=full_name,
            email=email,
            password=encodedPassword,
            timestamp=datetime.now(),
            active=True,
            permitted_actions=permitted_actions,
        )
        self.users.insert_one(new_user.to_dict())
        return copy.replace(new_user, password="<redacted>")

    def deactivate_user(self, auth: AuthenticatedUser, user_id: str) -> User:
        self.validate_permissions(auth, Action.admin_users())
        result = self.users.update_one({"_id": user_id}, {"$set": {"active": False}})
        if result.modified_count != 1:
            raise PtException(ErrorKind.NotFound, f"User {user_id} does not exist")
        return copy.replace(self.get_user(user_id), password="<redacted>")

    def reactivateUser(self, auth: AuthenticatedUser, user_id: str) -> User:
        self.validate_permissions(auth, Action.admin_users())
        update = self.users.update_one({"_id": user_id}, {"$set": {"active": True}})
        if update.modified_count != 1:
            raise PtException(ErrorKind.NotFound, f"User {user_id} does not exist")
        return copy.replace(self.get_user(user_id), password="<redacted>")

    def grantPermissions(
        self, auth: AuthenticatedUser, user_id: str, perms: List[Action]
    ) -> User:
        self.validate_permissions(auth, Action.admin_users())
        user = self.get_user(user_id)
        updated_perms: List[Action] = [*user.permitted_actions]
        for p in perms:
            if p not in user.permitted_actions:
                updated_perms.append(p)
        self.users.update_one(
            {"_id": user_id},
            {"$set": {"permitted_actions": list(p.to_dict() for p in updated_perms)}},
        )
        return copy.replace(self.get_user(user_id), password="<redacted>")

    def revokePermission(
        self, auth: AuthenticatedUser, user_id: str, perms: List[Action]
    ) -> User:
        self.validate_permissions(auth, Action.admin_users())
        user = self.get_user(user_id)
        updated_perms: List[Action] = [*user.permitted_actions]
        for p in perms:
            if p in user.permitted_actions:
                updated_perms.remove(p)
        self.users.update_one(
            {"_id": user_id},
            {"$set": {"permitted_actions": list(p.to_dict() for p in updated_perms)}},
        )
        return copy.replace(self.get_user(user_id), password="<redacted>")

    def updatePassword(
        self, auth: AuthenticatedUser, user_id: str, password: str
    ) -> User:
        if auth.user_id != user_id:
            self.validate_permissions(auth, Action.admin_users())
        salted = self._salted_hash(password, user_id)
        result = self.users.update_one({"_id": user_id}, {"$set": {"password": salted}})
        if result.modified_count == 0:
            raise PtException(ErrorKind.NotFound, f"User {user_id} not found")
        return copy.replace(self.get_user(user_id), password="<redacted>")
