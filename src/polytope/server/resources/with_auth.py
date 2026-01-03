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

from collections.abc import Callable
from typing import Concatenate

from falcon import Request, Response
import falcon

from polytope.common.error import PtException
from polytope.common.stashable.user import AuthenticatedUser


def with_auth[R, **P](
    f: Callable[Concatenate[R, AuthenticatedUser, Request, Response, P], None],
) -> Callable[Concatenate[R, Request, Response, P], None]:
    """
    A decorator to wrap resource methods that require authentication.

    :param f: a function, which takes an AuthenticatedUser, a request, a response,
        and any additional parameters, and calls that function with an
        authenticated user extracted from the request.
    :return: Nothing.
    """

    def inner(
        self: R, req: Request, resp: Response, *args: P.args, **kw: P.kwargs
    ) -> None:
        auth = req.context["user"]
        if auth is None:
            resp.status = falcon.HTTP_404
            return
        try:
            f(self, auth, req, resp, *args, **kw)
        except PtException as e:
            resp.status_code = e.kind.to_status_code()
            resp.media = str(e)

    return inner
