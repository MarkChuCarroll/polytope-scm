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

from enum import Enum
from http import HTTPStatus


class ErrorKind(Enum):
    Internal = (121,)  # Error 121 (EREMOTEIO)
    InvalidParameter = (22,)  # error 22 (EINVAL)
    Permission = (13,)  # 13 (EACCES)
    NotFound = (2,)  # error 2 (ENOENT)
    Conflict = (16,)  # error 16 (EBUSY)
    Authentication = (13,)  # Error 13 (EACCESS)
    Parsing = (5,)  # Error 5 (EIO)
    Constraint = (33,)  # Error 33 (EDOM)
    TypeError = (34,)  # Error 34 (ERANGE)
    UserError = (1,)  # Error 1(EPERM)
    Client = 10  # Error 10 (ECHILD)

    def prefix(self) -> str:
        if self == ErrorKind.Internal:
            return "Internal Error: "
        elif self == ErrorKind.InvalidParameter:
            return "Invalid parameter: "
        elif self == ErrorKind.Permission:
            return "Permission error: "
        elif self == ErrorKind.NotFound:
            return "Not found: "
        elif self == ErrorKind.Conflict:
            return "Error:  "
        elif self == ErrorKind.Authentication:
            return "Authentication error: "
        elif self == ErrorKind.Parsing:
            return "Syntax error: "
        elif self == ErrorKind.Constraint:
            return "Constraint violation: "
        elif self == ErrorKind.TypeError:
            return "Type error: "
        elif self == ErrorKind.UserError:
            return "Error: "
        elif self == ErrorKind.Client:
            return "Client error: "
        else:
            return "Unknown error: "

    def to_status_code(self) -> HTTPStatus:
        if self == ErrorKind.Internal:
            return HTTPStatus.INTERNAL_SERVER_ERROR
        elif self == ErrorKind.InvalidParameter:
            return HTTPStatus.BAD_REQUEST
        elif self == ErrorKind.Permission:
            return HTTPStatus.FORBIDDEN
        elif self == ErrorKind.NotFound:
            return HTTPStatus.NOT_FOUND
        elif self == ErrorKind.Conflict:
            return HTTPStatus.CONFLICT
        elif self == ErrorKind.Authentication:
            return HTTPStatus.UNAUTHORIZED
        elif self == ErrorKind.Constraint:
            return HTTPStatus.PRECONDITION_FAILED
        elif self == ErrorKind.Parsing:
            return HTTPStatus.UNPROCESSABLE_CONTENT
        elif self == ErrorKind.TypeError:
            return HTTPStatus.EXPECTATION_FAILED
        elif self == ErrorKind.UserError:
            return HTTPStatus.NOT_ACCEPTABLE
        else:
            return HTTPStatus.INTERNAL_SERVER_ERROR

    @classmethod
    def from_status_code(cls, code: int) -> "ErrorKind":
        if code == HTTPStatus.INTERNAL_SERVER_ERROR.value:
            return cls.Internal
        elif code == HTTPStatus.BAD_REQUEST.value:
            return cls.InvalidParameter
        elif code == HTTPStatus.FORBIDDEN.value:
            return cls.Permission
        elif code == HTTPStatus.NOT_FOUND.value:
            return cls.NotFound
        elif code == HTTPStatus.CONFLICT.value:
            return cls.Conflict
        elif code == HTTPStatus.UNAUTHORIZED.value:
            return cls.Authentication
        elif code == HTTPStatus.PRECONDITION_FAILED.value:
            return cls.Constraint
        elif code == HTTPStatus.UNPROCESSABLE_CONTENT.value:
            return cls.Parsing
        elif code == HTTPStatus.EXPECTATION_FAILED.value:
            return cls.TypeError
        elif code == HTTPStatus.NOT_ACCEPTABLE.value:
            return cls.UserError
        else:
            return cls.Internal


class PtException(Exception):
    def __init__(
        self, kind: ErrorKind, msg: str, cause: Exception | None = None
    ) -> None:
        self.kind = kind
        super(PtException, self).__init__(kind.prefix() + msg, cause)
