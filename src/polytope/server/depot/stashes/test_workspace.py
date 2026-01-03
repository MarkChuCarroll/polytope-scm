#
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


import hashlib
import tempfile
import uuid
from typing import Generator

import pytest

from polytope.common.agents.directory import Directory
from polytope.common.agents.text import TextContent
from polytope.common.stashable.user import Action, AuthenticatedUser
from polytope.server.depot.config import Config
from polytope.server.depot import Depot
from polytope.server.depot.storage import FileStorage, Storage


class TestWorkspaceBasics:
    @pytest.fixture
    def storage(self) -> Generator[Storage]:
        with tempfile.TemporaryDirectory(dir="tmp") as tmpdir:
            yield FileStorage(tmpdir)

    @pytest.fixture
    def depot(self, storage) -> Generator[Depot]:
        db_name = uuid.uuid4().hex
        cfg: Config = {
            "user": {
                "root_user": "root",
                "root_email": "root@root.root",
                "password": "rootable",
            },
            "storage": {"storage_path": storage.dir},
            "db": {"connection_str": "mongodb://localhost:27017", "db_name": db_name},
            "server": {"jwt_key": "polytope_test", "port": 21211},
        }
        d = Depot(cfg)
        yield d
        d.mongo.drop_database(cfg["db"]["db_name"])

    def get_passcode(
        self, depot: Depot, user_id: str, password: str, nonce: str
    ) -> str:
        coded_pw = depot.user_stash._salted_hash(password, user_id)
        sha = hashlib.sha256()
        sha.update(user_id.encode())
        sha.update(coded_pw.encode())
        sha.update(nonce.encode())
        return sha.hexdigest()

    @pytest.fixture
    def test_user(self, depot: Depot) -> AuthenticatedUser:
        nonce = "nonce"
        code = self.get_passcode(depot, "root", "rootable", nonce)

        root_auth = depot.user_stash.authenticate("root", code, nonce)
        depot.user_stash.create(
            root_auth,
            "tester",
            "tester",
            "tester@tester.test",
            [Action.create_project(), Action.write_depot()],
            "tester tests",
        )
        return depot.user_stash.authenticate(
            "tester", self.get_passcode(depot, "tester", "tester tests", nonce), nonce
        )

    def test_create_new_workspace(
        self, depot: Depot, test_user: AuthenticatedUser
    ) -> None:
        depot.project_stash.create_project(test_user, "test", "a test project")
        ws = depot.workspace_stash.create_workspace(
            test_user, "test", "main", "mytest", "a test workspace"
        )
        assert ws.name == "mytest"

    def test_create_and_save_ws_files(
        self, depot: Depot, test_user: AuthenticatedUser
    ) -> None:
        project = depot.project_stash.create_project(
            test_user, "test", "a test project"
        )
        ws = depot.workspace_stash.create_workspace(
            test_user, "test", "main", "mytest", "a test workspace"
        )
        ch = depot.workspace_stash.create_change(
            test_user, "test", ws.id, "main", "test-change", "just a test"
        )
        new_file_id = depot.workspace_stash.add_file(
            test_user,
            "test",
            ws.id,
            "foo",
            "text",
            depot.agents["text"].encode_to_bytes(TextContent(["11\n", "22\n", "33\n"])),
        )
        sp = depot.workspace_stash.save(test_user, "test", ws.id, "testing changes", [])

        paths = depot.workspace_stash.list_paths(test_user, "test", ws.id)
        assert set(["foo"]) == set(paths)
        rsp = depot.change_stash.retrieve_save_point(
            test_user, project.name, "main", "test-change", sp.id
        )
        assert new_file_id in rsp.modified_artifacts

        assert rsp.change_id == ch.id
        baseline = depot.workspace_stash._current_baseline(test_user, ws)
        assert set([new_file_id, baseline.root_dir]) == set(rsp.modified_artifacts)
        rch = depot.change_stash.retrieve_change_by_name(
            test_user, project.name, "main", "test-change"
        )
        assert rsp.id in rch.save_points

    def test_directory_heirarchy_in_workspace(
        self, test_user: AuthenticatedUser, depot: Depot
    ) -> None:
        depot.project_stash.create_project(test_user, "test", "a test project")
        ws = depot.workspace_stash.create_workspace(
            test_user, "test", "main", "mytest", "a test workspace"
        )
        depot.workspace_stash.create_change(
            test_user, "test", ws.id, "main", "test-change", "just a test"
        )
        dirDir = depot.workspace_stash.add_file(
            test_user,
            "test",
            ws.id,
            "dir",
            "directory",
            depot.agents["directory"].encode_to_bytes(Directory()),
        )
        dirRid = depot.workspace_stash.add_file(
            test_user,
            "test",
            ws.id,
            "rid",
            "directory",
            depot.agents["directory"].encode_to_bytes(Directory()),
        )
        depot.workspace_stash.add_file(
            test_user,
            "test",
            ws.id,
            "dir/boo",
            "directory",
            depot.agents["directory"].encode_to_bytes(Directory()),
        )
        depot.workspace_stash.add_file(
            test_user,
            "test",
            ws.id,
            "dir/boo/text.txt",
            "text",
            depot.agents["text"].encode_to_bytes(
                TextContent(["just some text\t", "boring\n", "not interesting\n"])
            ),
        )
        dull = depot.workspace_stash.add_file(
            test_user,
            "test",
            ws.id,
            "rid/blah.txt",
            "text",
            depot.agents["text"].encode_to_bytes(
                TextContent(
                    [
                        "I didn't realize\n",
                        "but that last one\n",
                        "was more interesting than this one\n",
                    ]
                )
            ),
        )

        depot.workspace_stash.save(test_user, "test", ws.id, "just a test", [])
        paths = depot.workspace_stash.list_paths(test_user, "test", ws.id)
        assert set(
            ["dir", "dir/boo", "dir/boo/text.txt", "rid", "rid/blah.txt"]
        ) == set(paths)
        depot.workspace_stash.move_file(test_user, "test", ws.id, "dir/boo", "rid/boo")

        postMovePaths = depot.workspace_stash.list_paths(test_user, "test", ws.id)
        print(f"Actual postMovepaths  = {postMovePaths}")
        assert set(
            ["dir", "rid/boo", "rid/boo/text.txt", "rid", "rid/blah.txt"]
        ) == set(postMovePaths)

        depot.workspace_stash.move_file(
            test_user, "test", ws.id, "rid/blah.txt", "rid/bleh.txt"
        )
        paths3 = set(depot.workspace_stash.list_paths(test_user, "test", ws.id))
        assert (
            set(["dir", "rid/boo", "rid/boo/text.txt", "rid", "rid/bleh.txt"]) == paths3
        )

        depot.workspace_stash.delete_file(test_user, "test", ws.id, "rid/bleh.txt")
        paths4 = set(depot.workspace_stash.list_paths(test_user, "test", ws.id))
        assert set(["dir", "rid/boo", "rid/boo/text.txt", "rid"]) == paths4

        sp2 = depot.workspace_stash.save(
            test_user, "test", ws.id, "another save point.", []
        )
        assert set([dull, dirDir, dirRid]) == set(sp2.modified_artifacts)
