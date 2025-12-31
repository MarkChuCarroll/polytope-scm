from typing import Dict
from falcon_auth import JWTAuthBackend
from polytope.common.stashable.user import AuthenticatedUser
from polytope.depot.stashes.user_stash import AuthEntry, UserStash


class Authenticator:
    USERID_CLAIM = "http://goodmath.org/polytope/user_id"
    TOKEN_CLAIM = "http://goodmath.org/polytope/token"

    def __init__(self, user_stash: UserStash) -> None:
        self.user_stash = user_stash

    def user_loader(self, payload: Dict[str, str]) -> AuthenticatedUser | None:
        user_id = payload[Authenticator.USERID_CLAIM]
        user_auth: AuthEntry | None = self.user_stash.get_auth(user_id)
        if user_auth is None:
            return None
        if user_auth["token"] == payload[Authenticator.TOKEN_CLAIM]:
            return user_auth
        return None
