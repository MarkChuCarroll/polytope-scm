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

from argparse import ArgumentParser
from typing import List
from wsgiref.simple_server import make_server

import falcon
from falcon_auth import FalconAuthMiddleware, TokenAuthBackend

from polytope.server.depot.config import Config, load_config_from_file
from polytope.server.depot.depot import Depot
from polytope.server.resources.login import Authenticator
from polytope.server.resources.project_resources import (
    ChangeSavesResource,
    ProjectHistoriesResource,
    ProjectHistoryChangeResource,
    ProjectHistoryChangesResource,
    ProjectHistoryResource,
    ProjectHistoryStepResource,
    ProjectHistoryStepsResource,
    ProjectResource,
    ProjectsResource,
)
from polytope.server.resources.user_resources import UserResource, UsersResource
from polytope.server.resources.util import LoginResource
from polytope.server.resources.workspace_resources import (
    WorkspaceResource,
    WorkspacesResource,
)


class Server:
    def __init__(self, config: Config) -> None:
        self.depot = Depot(config)
        self.auth_backend = TokenAuthBackend(
            Authenticator(self.depot.user_stash).user_loader,
            auth_header_prefix="Token",
        )

        self.auth_middleware: FalconAuthMiddleware = FalconAuthMiddleware(
            self.auth_backend, exempt_routes=["/login"]
        )

        self.app = falcon.App(middleware=[self.auth_middleware])
        self.app.req_options.default_media_type = "application/json"
        self.app.add_route("/login", LoginResource(self.depot))
        self.app.add_route("/users", UsersResource(self.depot))
        self.app.add_route("/users/user_id", UserResource(self.depot))
        self.app.add_route("/projects/", ProjectsResource(self.depot))
        self.app.add_route("/projects/{project}", ProjectResource(self.depot))
        self.app.add_route(
            "/projects/{project}/histories", ProjectHistoriesResource(self.depot)
        )
        self.app.add_route(
            "/projects/{project}/histories/{history}",
            ProjectHistoryResource(self.depot),
        )
        self.app.add_route(
            "/projects/{project}/histories/{history}/steps",
            ProjectHistoryStepsResource(self.depot),
        )
        self.app.add_route(
            "/projects/{project}/histories/{history}/steps/{step}",
            ProjectHistoryStepResource(self.depot),
        )
        self.app.add_route(
            "/projects/{project}/histories/{history}/changes",
            ProjectHistoryChangesResource(self.depot),
        )
        self.app.add_route(
            "/projects/{project}/histories/{history}/changes/{change}",
            ProjectHistoryChangeResource(self.depot),
        )
        self.app.add_route(
            "/projects/{project}/histories/{history}/changes/{change}/saves",
            ChangeSavesResource(self.depot),
        )

        self.app.add_route(
            "/projects/{project}/workspaces", WorkspacesResource(self.depot)
        )
        self.app.add_route(
            "/projects/{project}/ws_by_name/{workspace}", WorkspaceResource(self.depot)
        )
        self.app.add_route(
            "/projects/{project}/workspaces/{wsid}/action",
            WorkspaceActionResource(self.depot),
        )


def main(argv: List[str]) -> None:
    parser = ArgumentParser("polytope-server")
    parser.add_argument(
        "--config",
        type=str,
        required=True,
        help="Path to the Polytope server configuration file",
    )
    args = parser.parse_args(argv)
    config = load_config_from_file(args.config)
    server = Server(config)
    with make_server("", config["server"]["port"], server.app) as httpd:
        print(f"Serving on port {config['server']['port']}...")
        httpd.serve_forever()


if __name__ == "__main__":
    import sys

    main(sys.argv[1:])
