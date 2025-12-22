
import copy
from datetime import datetime, timedelta
import hashlib
import os
import shelve
from typing import List, NamedTuple, Tuple
import uuid
from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.action import Action, ActionLevel, ActionScopeType
from polytope.common.stashable.user import AuthenticatedUser, User
from polytope.depot.config import Config
from polytope.depot.depot import Depot
from polytope.depot.stashes.stash import Stash

TOKENS = "tokens"
USERS = "users"

class AuthEntry(NamedTuple):
    user: AuthenticatedUser
    token: str
    expiration: datetime

class UserStash(Stash):
    """
    Schema:
        shelf[USERS][Id[User]]: User
        shelf[TOKENS][user_id: str]: AuthEntry
    """
    def __init__(self, db_dir: str, depot: Depot) -> None:
        self.db_dir = db_dir
        self.depot = depot
        self.db_path = os.path.join("db_dir", "users'db")

    def validate_permissions(
            self, auth: AuthenticatedUser, action: Action) -> None:
        """
        Test if a user has permission to perform an action. Raises
        a permission exception if not.

        Arguments:
        auth -- the authenticated user.
        action -- the requested action.

         TODO: this should check that the auth token is valid.
         """
        if not action.permitted_for(auth):
            raise PtException(
                ErrorKind.Permission,
                "User permission denied"
            )

    def _salted_hash(self, username: str, pw: str) -> str:
        """
        Compute the salted hash of a user password for authentication.

        Arguments:
        username -- the name of the user to authenticate
        password -- the plaintext, unsalted password.
        """
        salt: int =  0
        for c in username:
            salt = int(salt * 37 + ord(c) / 211)

        sha = hashlib.sha256()
        sha.update(str(salt).encode())
        sha.update(pw.encode())
        return sha.hexdigest()


    def generate_auth_token(self, auth: AuthenticatedUser) -> Tuple[str, datetime]:
        with shelve.open(self.db_path) as shelf:
            existing_auth = shelf[TOKENS].get(auth.user_id)
            if existing_auth is not None:
                if existing_auth.expiration > (datetime.now() + timedelta(days=1)):
                    return (existing_auth.token, existing_auth.expiration)
                else:
                    del shelf[TOKENS][auth.user_id]

            token_id = uuid.uuid4().hex
            token = f"{auth.user_id}/{token_id}"
            exp = datetime.now() + timedelta(weeks=1)
            shelf[TOKENS][auth.user_id] = AuthEntry(auth, token, exp)
            return (token, exp)

    def validate_auth_token(self, user_id: str, token: str) -> AuthenticatedUser | None:
        with shelve.open(self.db_path) as shelf:
            entry: AuthEntry = shelf[TOKENS].get(user_id)
            if entry is not None:
                if entry.token == token and entry.expiration > datetime.now():
                    return entry.user
        return None


    def authenticate(
        self,
        username: str,
        password: str
    ) ->  AuthenticatedUser:
        """
        Authenticate a user.

        If the user password is correct, and the user is
         active, return an authenticated user containing
         an authentication token for the user.
         """
        with shelve.open(self.db_path) as shelf:
            user: User | None = shelf[USERS].get(username)
            if user is None:
                raise PtException(
                    ErrorKind.NotFound,
                    f"User {username} not found"
                )
            if not user.active:
                raise PtException(ErrorKind.Authentication,
                    f"Authentication failed for user {username}")
            hashed_from_user = self._salted_hash(username, password)
            if hashed_from_user != user.password:
                raise PtException(
                    ErrorKind.Authentication,
                    "Authentication failed"
                )
            return AuthenticatedUser(username, hashed_from_user, user.permitted_actions)

    def retrieve_user(
        self,
        auth: AuthenticatedUser,
        username: str
    ) -> User:
        """
        Return a user record for a user of the system.
        The returned record will have its password field redacted.
        """
        if auth.user_id != username:
            self.validate_permissions(
                auth, Action.admin_users())
        with shelve.open(self.db_path) as shelf:
            result = shelf[USERS].get(username)
            if result is None:
                raise PtException(ErrorKind.NotFound,
                    f"User {username} not found")
            return copy.replace(result, password = "<redacted>")

    def create(self,
        auth: AuthenticatedUser,
        username: str,
        full_name: str,
        email: str,
        permitted_actions: List[Action],
        password: str
    ) -> User:
        self.validate_permissions(auth, Action.admin_users())
        with shelve.open(self.db_path) as shelf:
            user = shelf[USERS].get(username)
            if user is not None:
                raise PtException(ErrorKind.InvalidParameter,
                    f"User with username '{username}' already exists"
                )
            encodedPassword = self._salted_hash(username, password)
            user = User(
                username,
                full_name,
                permitted_actions,
                email,
                encodedPassword,
                datetime.now(),
                True)
            shelf[USERS][username] = user
            return copy.replace(user, password = "<redacted>")


    def deactivate_user(self, auth: AuthenticatedUser, userid: str) -> User:
        self.validate_permissions(auth, Action.admin_users())
        with shelve.open(self.db_path) as shelf:
            user = shelf[USERS].get(userid)
            if user is None:
                raise PtException(ErrorKind.NotFound,
                    f"User {userid} not found")
            updated = copy.replace(user, active = False)
            shelf[USERS][userid] = updated
            return copy.replace(user, password = "<redacted>")

    def reactivateUser(self,
                       auth: AuthenticatedUser,
                       userid: str) -> User:
        self.validate_permissions(auth, Action.admin_users())
        with shelve.open(self.db_path) as shelf:
            user = shelf[USERS].get(userid)
            if user is None:
                raise PtException(ErrorKind.NotFound,
                    f"User {userid} not found")
            updated = copy.replace(user, active = True)
            shelf[USERS][userid] = updated
            return copy.replace(user, password = "<redacted>")

    def grantPermissions(self,
        auth: AuthenticatedUser,
        username: str, perms: List[Action]
    ) -> User:
        self.validate_permissions(auth, Action.admin_users())
        with shelve.open(self.db_path) as shelf:
            user = shelf[USERS].get(username)
            if user is None:
                raise PtException(ErrorKind.NotFound,
                    f"User {username} not found")

            new_perms = user.permitted_actions
            for act in perms:
                if act not in new_perms:
                    new_perms.append(act)
            user.permitted_actions = new_perms
            shelf[USERS][username] = new_perms
            return copy.replace(user, password = "<redacted>")

    def revokePermission(
        self,
        auth: AuthenticatedUser,
        username: str, perms: List[Action]) -> User:
        self.validate_permissions(auth, Action.admin_users())
        with shelve.open(self.db_path) as shelf:
            user: User = shelf[USERS].get(username)
            if user is None:
                raise PtException(ErrorKind.NotFound,
                    f"User {username} not found")
            new_permissions: List[Action] = user.permitted_actions
            for perm in perms:
                if perm in new_permissions:
                    new_permissions.remove(perm)
            user = copy.replace(user, permitted_actions = new_permissions)
            shelf[USERS][username] = user
            return copy.replace(user, password = "<redacted>")


    def list(self, auth: AuthenticatedUser) -> List[User]:
        self.validate_permissions(auth, Action.read_users())
        with shelve.open(self.db_path) as shelf:
            result: List[User] = []
            for key in shelf[USERS].keys():
                result.append(shelf[USERS][key])
            return result

    def updatePassword(self,
                       auth: AuthenticatedUser, userid: str, password: str)-> User:
        if auth.user_id != userid:
            self.validate_permissions(auth, Action.admin_users())
        with shelve.open(self.db_path) as shelf:
            user = shelf[USERS].get(userid)
            if user is None:
                raise PtException(ErrorKind.NotFound,
                    f"User {userid} not found")
            salted = self._salted_hash(userid, password)
            updated = copy.replace(user, password = salted)
            shelf[USERS][userid] = updated
            return copy.replace(user, password = "<redacted>")

    def init_storage(self, config: Config) -> None:
        with shelve.open(self.db_path) as shelf:
            if shelf.get(USERS) is None:
                root = User(
                    username = config.root_user,
                    full_name = "The Dreaded Administrator",
                    permitted_actions = [Action(ActionScopeType.Global, "*", ActionLevel.Admin)],
                    email = config.root_email,
                    password = self._salted_hash(config.root_user, config.password),
                    timestamp = datetime.now(),
                    active = True
                )
                shelf[USERS] =  { config.root_user: root }
            if shelf.get(TOKENS) is None:
                shelf[TOKENS] = {}
