
from typing import NamedTuple, TypedDict


class MongoConfig(TypedDict):
    connection_str: str
    db_name: str


class FileStorageConfig(TypedDict):
    storage_path: str


class UserConfig(TypedDict):
    root_user: str
    root_email: str
    password: str


class Config(TypedDict):
    user: UserConfig
    storage: FileStorageConfig
    db: MongoConfig
