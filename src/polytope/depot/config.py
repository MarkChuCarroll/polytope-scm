
from typing import NamedTuple

class Config(NamedTuple):
    root_user: str
    root_email: str
    password: str
    db_dir: str

