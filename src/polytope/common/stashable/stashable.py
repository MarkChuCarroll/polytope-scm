
from typing import Any, Dict, Protocol, TypeAlias


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
      can be tricky.

    Adding further annoyance, we can't even really use this... because
    you can't have mixins added to a NamedTuple. But it's still useful
    to have for documentation, I guess?
    """

    def to_dict(self) -> JDict: ...

    @classmethod
    def from_dict(self, dict: JDict) -> "Stashable": ...
