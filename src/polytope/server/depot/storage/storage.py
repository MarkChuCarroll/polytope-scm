# Copyright 2026 Mark C. Chu-Carroll
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


import hashlib
import os
from typing import Protocol, TypeAlias

from polytope.common.error import ErrorKind, PtException
from polytope.common.stashable.ids import Id, IdKind

Content: TypeAlias = bytes


class Storage(Protocol):
    def put(self, content: Content) -> Id[Content]: ...

    def get(self, id: Id[Content]) -> Content: ...

    def put_transient(self, content: Content) -> Id[Content]: ...

    def get_transient(self, id: Id[Content]) -> Content: ...

    def commit_transient(self, id: Id[Content]) -> Id[Content]: ...

    def delete_transient(self, id: Id[Content]) -> None: ...


class FileStorage(Storage):
    def __init__(self, dir: str) -> None:
        self.dir = dir
        self.perm = os.path.join(dir, "perm")
        self.trans = os.path.join(dir, "trans")
        if not os.path.exists(dir):
            os.makedirs(dir)
        if not os.path.exists(self.perm):
            os.makedirs(self.perm)
        if not os.path.exists(self.trans):
            os.makedirs(self.trans)

    def content_hash(self, content: bytes) -> str:
        sha = hashlib.sha256()
        sha.update(content)
        return sha.hexdigest()

    def is_transient(self, id: Id[Content]) -> bool:
        return id.kind == IdKind.ID_TRANSIENT

    def put(self, content: Content) -> Id[Content]:
        id: Id[Content] = Id(IdKind.ID_CONTENT, self.content_hash(content))
        with open(os.path.join(self.perm, str(id)), "wb") as out:
            out.write(content)
        return id

    def get(self, id: Id[Content]) -> Content:
        if id.kind == IdKind.ID_CONTENT:
            path = os.path.join(self.perm, str(id))
            if os.path.exists(path):
                with open(path, "rb") as inp:
                    c = inp.read()
                    return c
            else:
                raise PtException(
                    ErrorKind.NotFound, f"Content with id '{id}' not found"
                )
        elif id.kind == IdKind.ID_TRANSIENT:
            return self.get_transient(id)
        else:
            raise PtException(ErrorKind.UserError, "Invalid content ID")

    def put_transient(self, content: Content) -> Id[Content]:
        id: Id[Content] = Id(IdKind.ID_TRANSIENT, self.content_hash(content))
        with open(os.path.join(self.trans, str(id)), "wb") as out:
            out.write(content)
        return id

    def get_transient(self, id: Id[Content]) -> Content:
        path = os.path.join(self.trans, str(id))
        if os.path.exists(path):
            with open(path, "rb") as inp:
                return inp.read()
        else:
            raise PtException(
                ErrorKind.NotFound, f"Transient content with id '{id}' not found"
            )

    def commit_transient(self, id: Id[Content]) -> Id[Content]:
        content = self.get_transient(id)
        return self.put(content)

    def delete_transient(self, id: Id[Content]) -> None:
        if id.kind is not IdKind.ID_TRANSIENT:
            raise PtException(
                ErrorKind.Constraint, "Cannot delete a non-transient content object"
            )

        path = os.path.join(self.trans, str(id))
        if os.path.exists(path):
            os.remove(path)
