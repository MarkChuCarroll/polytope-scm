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


import shelve
from typing import Any, List, Protocol

from polytope.common.error import ErrorKind, PtException
from polytope.depot.config import Config


class Stash(Protocol):
    def init_storage(self, config: Config) -> None: ...

    def get_in_shelf(self, shelf: shelve.Shelf, keys: List[Any], error: str) -> Any:
        layer: shelve.Shelf | None = shelf
        for key in keys:
            if layer is None:
                break
            layer = layer.get(key)
        if layer is None:
            raise PtException(ErrorKind.NotFound, error)
        return layer

    def set_in_shelf(self, shelf: shelve.Shelf, keys: List[Any], value: Any) -> None:
        layer: shelve.Shelf | None = shelf
        last = keys[-1]
        prefix = keys[:-1]
        for key in prefix:
            if layer is None:
                raise PtException(ErrorKind.Internal, f"Stash update at {keys} failed")
            if layer.get(key) is None:
                layer[key] = {}
            if layer is None:
                break
            layer = layer.get(key)
        if layer is None:
            raise PtException(ErrorKind.NotFound, "Stash update at {keys} failed")

        layer[last] = value
