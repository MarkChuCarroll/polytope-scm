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

from typing import Any, Protocol, TypeAlias

JSON: TypeAlias = dict[str, "JSON"] | list["JSON"] | str | int | float | bool | None

JDict: TypeAlias = dict[str, Any]


class Stashable(Protocol):
    """
    Stashable is a quick-and-dirty (but functional) marshaling model for
    polytope. There's a couple of reasons why I built this instead of
    just using pickle directly.

    1. it would be nice to be able to read the data, and have
      the flexibily to switch away from using shelve. That's
      a lot easier if you've got an easy JSON format for the data.
    2. Pickle notoriously has all sorts of wierd issues with
      conflicting imports producing obscure errors (type doesn't match typing.T).
    3. json.dumps can work, but getting object reification working correctly
      can be tricky. I've wasted countless hours trying to debug code that
      I wrote using json.dumps/json.loads; I'd rather write this
      repetetive boilerplace stuff once and know how it works.

    Adding further annoyance, we can't even really use this... because
    you can't have mixins added to a NamedTuple. But it's still useful
    to have for documentation, I guess?
    """

    def to_dict(self) -> JDict: ...

    @classmethod
    def from_dict(self, dict: JDict) -> "Stashable": ...
