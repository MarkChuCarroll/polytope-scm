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


import hashlib
import tempfile
from typing import Generator
import uuid
import pytest
from polytope.common.agents.directory import Directory
from polytope.common.agents.text import TextContent
from polytope.common.stashable.artifact import Artifact, ArtifactVersion
from polytope.common.stashable.change import ChangeStatus
from polytope.common.stashable.pvs import ProjectVersionSpecifier
from polytope.common.stashable.user import Action
from polytope.depot.config import Config
from polytope.depot.depot import Depot
from polytope.depot.stashes.user_stash import AuthenticatedUser
from polytope.depot.storage.storage import Content, FileStorage, Storage


class TestDepot:
    @pytest.fixture
    def storage(self) -> Generator[FileStorage]:
        with tempfile.TemporaryDirectory(dir="tmp") as tmpdir:
            yield FileStorage(tmpdir)

    @pytest.fixture
    def depot(self, storage: FileStorage) -> Generator[Depot]:
        db_name = uuid.uuid4().hex
        cfg: Config = {
            "user": {
                "root_user": "root",
                "root_email": "root@root.root",
                "password": "rootable",
            },
            "storage": {"storage_path": storage.dir},
            "db": {"connection_str": "mongodb://localhost:27017", "db_name": db_name},
        }
        d = Depot(cfg)
        yield d
        d.mongo.drop_database(cfg["db"]["db_name"])

    def make_and_store_base_text_version(
        self, auth: AuthenticatedUser, test_project: str, depot: Depot
    ) -> ArtifactVersion:
        text_content = TextContent(["hello\n", "there\n", "you\n", "bozo\n"])
        content = Content(depot.get_agent("text").encode_to_bytes(text_content))
        cid = depot.storage.put(content)
        (_, test_ver) = depot.artifact_stash.create_artifact(
            auth, test_project, "text", cid, {}
        )
        return test_ver

    def get_passcode(
        self, depot: Depot, user_id: str, password: str, nonce: str
    ) -> str:
        coded_pw = depot.user_stash._salted_hash(password, user_id)
        sha = hashlib.sha256()
        sha.update(user_id.encode())
        sha.update(coded_pw.encode())
        sha.update(nonce.encode())
        return sha.hexdigest()

    def create_test_user(self, depot: Depot) -> AuthenticatedUser:
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

    def test_create_project(self, depot):
        auth = self.create_test_user(depot)
        depot.project_stash.create_project(
            auth,
            "testie",
            "a test",
        )
        pr = depot.project_stash.retrieve_project(auth, "testie")
        assert pr.name == "testie"
        assert pr.baseline is not None
        assert pr.description == "a test"
        assert pr.creator == auth.user_id

        hist = depot.history_stash.retrieve_history(auth, pr.name, "main")
        hist_ver = depot.history_stash.retrieve_history_step(
            auth, pr.name, hist.name, len(hist.steps) - 1
        )
        assert hist_ver.history_name == hist.name
        baseline_ver = depot.artifact_stash.retrieve_version(
            auth, pr.name, hist_ver.baseline_id, hist_ver.baseline_version_id
        )
        b_cont = depot.storage.get(baseline_ver.content_id)
        baseline = depot.agents["baseline"].decode_from_bytes(b_cont)
        assert 1 == len(baseline.entries)
        assert baseline.get(baseline.root_dir) is not None
        root_dir_ver = depot.artifact_stash.retrieve_version(
            auth, pr.name, baseline.root_dir, baseline.get(baseline.root_dir)
        )
        dcont = depot.storage.get(root_dir_ver.content_id)
        dir = depot.agents["directory"].decode_from_bytes(dcont)
        assert len(dir.entries) == 0

    def test_create_text_in_directory(self, depot) -> None:
        auth = self.create_test_user(depot)
        depot.project_stash.create_project(auth, "testie", "a test")
        pr = depot.project_stash.retrieve_project(auth, "testie")
        hist = depot.history_stash.retrieve_history(auth, pr.name, "main")
        hist_ver = depot.history_stash.retrieve_history_step(
            auth, pr.name, hist.name, 0
        )
        baseline_ver = depot.artifact_stash.retrieve_version(
            auth, pr.name, hist_ver.baseline_id, hist_ver.baseline_version_id
        )
        baseline = depot.agents["baseline"].decode_from_bytes(
            depot.storage.get(baseline_ver.content_id)
        )
        root_dir_ver = depot.artifact_stash.retrieve_version(
            auth, pr.name, baseline.root_dir, baseline.get(baseline.root_dir)
        )
        dir = depot.agents["directory"].decode_from_bytes(
            depot.storage.get(root_dir_ver.content_id)
        )
        some_text = TextContent(["aaa\n", "bbb\n", "ccc\n", "ddd\n"])
        text_cid = depot.storage.put(depot.agents["text"].encode_to_bytes(some_text))
        (text_art, text_ver) = depot.artifact_stash.create_artifact(
            auth, "text", pr.name, text_cid, {"order": "lexical"}
        )
        new_dir = dir.copy()
        new_dir.add_binding("txt.txt", text_ver.artifact_id)
        baseline.add(text_ver.artifact_id, text_ver.id)
        dir_cid = depot.storage.put(depot.agents["directory"].encode_to_bytes(new_dir))
        new_dir_version = depot.artifact_stash.create_version(
            auth,
            pr.name,
            root_dir_ver.artifact_id,
            "directory",
            dir_cid,
            [root_dir_ver.id],
            {},
        )
        baseline.change(new_dir_version.artifact_id, new_dir_version.id)
        new_baseline_cid = depot.storage.put(
            depot.agents["baseline"].encode_to_bytes(baseline)
        )
        new_baseline_ver = depot.artifact_stash.create_version(
            auth,
            pr.name,
            baseline_ver.artifact_id,
            "baseline",
            new_baseline_cid,
            [baseline_ver.id],
            {},
        )
        baseline_ver_from_storage = depot.artifact_stash.retrieve_version(
            auth, pr.name, hist_ver.baseline_id, new_baseline_ver.id
        )
        baseline_from_storage = depot.agents["baseline"].decode_from_bytes(
            depot.storage.get(baseline_ver_from_storage.content_id)
        )
        dir_version_id = baseline_from_storage.get(baseline_from_storage.root_dir)
        assert dir_version_id is not None
        dir_version_from_storage = depot.artifact_stash.retrieve_version(
            auth, pr.name, baseline_from_storage.root_dir, dir_version_id
        )
        dir_contents = depot.agents["directory"].decode_from_bytes(
            depot.storage.get(dir_version_from_storage.content_id)
        )
        text_id = dir_contents.get_binding("txt.txt")
        assert text_id is not None
        assert text_art.id == text_id
        assert text_ver.id == baseline_from_storage.get(text_id)
        txt_from_storage = depot.artifact_stash.retrieve_version(
            auth, pr.name, text_id, baseline_from_storage.get(text_id)
        )
        assert "lexical" == txt_from_storage.metadata["order"]

    def test_create_history_version(self, depot: Depot) -> None:
        auth = self.create_test_user(depot)
        depot.project_stash.create_project(auth, "testie", "a test")
        pr = depot.project_stash.retrieve_project(auth, "testie")
        hist = depot.history_stash.retrieve_history(auth, pr.name, "main")
        hist_ver = depot.history_stash.retrieve_history_step(
            auth, pr.name, hist.name, 0
        )
        baseline_ver = depot.artifact_stash.retrieve_version(
            auth, pr.name, hist_ver.baseline_id, hist_ver.baseline_version_id
        )
        baseline = depot.agents["baseline"].decode_from_bytes(
            depot.storage.get(baseline_ver.content_id)
        )
        root_dir_ver = depot.artifact_stash.retrieve_version(
            auth, pr.name, baseline.root_dir, baseline.get(baseline.root_dir)
        )
        dir = depot.agents["directory"].decode_from_bytes(
            depot.storage.get(root_dir_ver.content_id)
        )

        change = depot.change_stash.create_change(
            auth=auth,
            project_name=pr.name,
            history="main",
            change_name="test-change",
            basis=ProjectVersionSpecifier.make_history(pr.name, "main", 0),
            description="a test",
        )

        some_text = TextContent(["aaa\n", "bbb\n", "ccc\n", "ddd\n"])
        (text_art, text_ver) = depot.artifact_stash.create_artifact(
            auth,
            pr.name,
            "text",
            depot.storage.put(depot.agents["text"].encode_to_bytes(some_text)),
            {"language": "english"},
        )

        new_dir = dir.copy()
        new_dir.add_binding("txt.txt", text_ver.artifact_id)
        baseline.add(text_ver.artifact_id, text_ver.id)
        new_dir_version = depot.artifact_stash.create_version(
            auth=auth,
            project=pr.name,
            art_type="directory",
            content_id=depot.storage.put(
                depot.agents["directory"].encode_to_bytes(new_dir)
            ),
            parents=[root_dir_ver.id],
            art_id=root_dir_ver.artifact_id,
            metadata={},
        )
        baseline.change(new_dir_version.artifact_id, new_dir_version.id)
        new_baseline_ver = depot.artifact_stash.create_version(
            auth=auth,
            project=pr.name,
            art_id=baseline_ver.artifact_id,
            art_type="baseline",
            parents=[baseline_ver.id],
            content_id=depot.storage.put(
                depot.agents["baseline"].encode_to_bytes(baseline)
            ),
            metadata={},
        )

        depot.change_stash.create_save_point(
            auth=auth,
            project=pr.name,
            history="main",
            change_name=change.name,
            changed_artifacts=[text_art.id, root_dir_ver.artifact_id],
            description="a test change",
            basis=ProjectVersionSpecifier.make_baseline(
                pr.name, "main", new_baseline_ver.id
            ),
            baseline_version=new_baseline_ver.id,
        )

        depot.change_stash.update_change_status(
            auth, pr.name, "main", "test-change", ChangeStatus.Closed
        )

        depot.history_stash.add_history_step(
            auth=auth,
            project=pr.name,
            history="main",
            change=change.id,
            baseline_version=new_baseline_ver,
            description="desc",
        )

        depot.history_stash.retrieve_history_step(auth, pr.name, "main", 1)

        baseline_ver_from_storage = depot.artifact_stash.retrieve_version(
            auth, pr.name, new_baseline_ver.artifact_id, new_baseline_ver.id
        )

        rbaseline_from_storage = depot.agents["baseline"].decode_from_bytes(
            depot.storage.get(baseline_ver_from_storage.content_id)
        )

        dir_version_id = rbaseline_from_storage.get(rbaseline_from_storage.root_dir)
        dir_version_from_storage = depot.artifact_stash.retrieve_version(
            auth, pr.name, rbaseline_from_storage.root_dir, dir_version_id
        )
        dir_contents: Directory = depot.agents["directory"].decode_from_bytes(
            depot.storage.get(dir_version_from_storage.content_id)
        )
        text_id = dir_contents.get_binding("txt.txt")
        assert text_id is not None

        assert text_art.id == text_id
        assert text_ver.id == rbaseline_from_storage.get(text_id)

        txt_from_storage = depot.artifact_stash.retrieve_version(
            auth, pr.name, text_id, rbaseline_from_storage.get(text_id)
        )
        assert "english" == txt_from_storage.metadata["language"]
        txt_content = depot.agents["text"].decode_from_bytes(
            depot.storage.get(txt_from_storage.content_id)
        )
        assert "aaa\nbbb\nccc\nddd\n" == "".join(txt_content.lines)

    def test_create_history(self, depot: Depot) -> None:
        auth = self.create_test_user(depot)
        depot.project_stash.create_project(auth, "testie", "a test")
        pr = depot.project_stash.retrieve_project(auth, "testie")
        hist = depot.history_stash.retrieve_history(auth, pr.name, "main")
        hist_ver = depot.history_stash.retrieve_history_step(
            auth, pr.name, hist.name, 0
        )
        baseline_ver = depot.artifact_stash.retrieve_version(
            auth, pr.name, hist_ver.baseline_id, hist_ver.baseline_version_id
        )
        baseline = depot.agents["baseline"].decode_from_bytes(
            depot.storage.get(baseline_ver.content_id)
        )
        root_dir_ver = depot.artifact_stash.retrieve_version(
            auth, pr.name, baseline.root_dir, baseline.get(baseline.root_dir)
        )
        dir: Directory = depot.agents["directory"].decode_from_bytes(
            depot.storage.get(root_dir_ver.content_id)
        )

        change = depot.change_stash.create_change(
            auth,
            pr.name,
            "main",
            "test-change",
            ProjectVersionSpecifier.make_history(pr.name, "main", 0),
            "a test",
        )

        some_text = TextContent(["aaa\n", "bbb\n", "ccc\n", "ddd\n"])
        text_cid = depot.storage.put(depot.agents["text"].encode_to_bytes(some_text))

        ttup = depot.artifact_stash.create_artifact(
            auth=auth,
            art_type="text",
            project=pr.name,
            metadata={"language": "english", "order": "lexical"},
            initial_content_id=text_cid,
        )
        text_art: Artifact = ttup[0]
        text_ver: ArtifactVersion = ttup[1]
        new_dir = dir.copy()
        new_dir.add_binding("txt.txt", text_ver.artifact_id)
        baseline.add(text_ver.artifact_id, text_ver.id)
        new_dir_cid = depot.storage.put(
            depot.agents["directory"].encode_to_bytes(new_dir)
        )
        new_dir_version = depot.artifact_stash.create_version(
            auth=auth,
            project=pr.name,
            art_type=depot.agents["directory"].artifact_type(),
            art_id=root_dir_ver.artifact_id,
            parents=[root_dir_ver.id],
            content_id=new_dir_cid,
            metadata={},
        )
        baseline.change(new_dir_version.artifact_id, new_dir_version.id)
        new_baseline_cid = depot.storage.put(
            depot.agents["baseline"].encode_to_bytes(baseline)
        )
        new_baseline_ver = depot.artifact_stash.create_version(
            auth=auth,
            project=pr.name,
            art_id=baseline_ver.artifact_id,
            art_type=depot.agents["baseline"].artifact_type(),
            parents=[baseline_ver.id],
            content_id=new_baseline_cid,
            metadata={},
        )

        depot.change_stash.create_save_point(
            auth=auth,
            project=pr.name,
            change_name=change.name,
            changed_artifacts=[new_dir_version.artifact_id, text_art.id],
            baseline_version=new_baseline_ver.id,
            description="a test change",
            history="main",
            basis=ProjectVersionSpecifier.make_baseline(
                pr.name, "main", new_baseline_ver.id
            ),
        )
        depot.change_stash.update_change_status(
            auth=auth,
            project=pr.name,
            change_name=change.name,
            history="main",
            status=ChangeStatus.Closed,
        )
        depot.history_stash.add_history_step(
            auth=auth,
            project=pr.name,
            history="main",
            change=change.id,
            baseline_version=new_baseline_ver,
            description="desc",
        )

        depot.history_stash.create_history(
            auth=auth,
            project=pr.name,
            name="alternate",
            description="testing a branched history",
            from_history="main",
            at_step=0,
        )

        depot.history_stash.retrieve_history_step(
            auth=auth, project=pr.name, history_name="alternate", number=0
        )

        baseline_ver_from_storage = depot.artifact_stash.retrieve_version(
            auth, pr.name, new_baseline_ver.artifact_id, new_baseline_ver.id
        )
        baseline_from_storage = depot.agents["baseline"].decode_from_bytes(
            depot.storage.get(baseline_ver_from_storage.content_id)
        )

        dir_version_id = baseline_from_storage.get(baseline_from_storage.root_dir)
        dir_version_from_storage = depot.artifact_stash.retrieve_version(
            auth, pr.name, baseline_from_storage.root_dir, dir_version_id
        )
        dir_contents = depot.agents["directory"].decode_from_bytes(
            depot.storage.get(dir_version_from_storage.content_id)
        )
        text_id = dir_contents.get_binding("txt.txt")
        assert text_art.id == text_id
        assert text_ver.id == baseline_from_storage.get(text_id)

        txt_from_storage = depot.artifact_stash.retrieve_version(
            auth, pr.name, text_id, baseline_from_storage.get(text_id)
        )
        assert "lexical" == txt_from_storage.metadata["order"]
