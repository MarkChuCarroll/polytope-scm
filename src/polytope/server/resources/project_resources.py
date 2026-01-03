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

import falcon
from falcon import Request, Response

from polytope.common.api.requests import ChangeListResponse, HistoryCreateRequest, HistoryListResponse, ProjectCreateRequest, ProjectListResponse, SavesListResponse
from polytope.common.error import PtException
from polytope.common.stashable.change import ChangeStatus
from polytope.common.stashable.project import Project
from polytope.common.stashable import JDict
from polytope.common.stashable.user import AuthenticatedUser
from polytope.server.depot import Depot
from polytope.server.resources.with_auth import with_auth


class PtResource:
    """
    Base class for all Polytope Depot resources
    """

    def __init__(self, depot: Depot) -> None:
        self.depot = depot


class ProjectsResource(PtResource):
    """
    Resource for /projects
    Handles get (list projects) and post (create project)
    """

    @with_auth
    def on_get(self, auth: AuthenticatedUser, req: Request, resp: Response) -> None:
        projects = self.depot.project_stash.list_projects(auth)
        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
        resp.media = ProjectListResponse(projects=projects).to_dict()

    @with_auth
    def on_post(self, auth: AuthenticatedUser, req: Request, resp: Response) -> None:
        req_body: ProjectCreateRequest = ProjectCreateRequest.from_dict(req.get_media())
        new_project = self.depot.project_stash.create_project(
            auth=auth,
            project_name=req_body.name,
            description=req_body.description,
        )
        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
        resp.media = new_project.to_dict()


class ProjectResource(PtResource):
    """
    Resource for /projects/{project_name}
    Handles get (retrieve project)
    """

    @with_auth
    def on_get(self, auth: AuthenticatedUser, req: Request, resp: Response, project_name: str) -> None:
        project = self.depot.project_stash.retrieve_project(
            auth, project_name
        )
        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
        resp.media = project.to_dict()


class ProjectHistoriesResource(PtResource):
    """
    Resource for /projects/{project}/histories
    Supports get (list histories) and post (create history)
    """

    @with_auth
    def on_get(self, auth: AuthenticatedUser, req: Request, resp: Response, project: str) -> None:
        histories = self.depot.history_stash.list_histories(
            auth, project
        )
        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
        resp.media = HistoryListResponse(
            project=project,
            histories=histories).to_dict()

    @with_auth
    def on_post(self, auth: AuthenticatedUser, req: Request, resp: Response, project: str) -> None:
        req_body = HistoryCreateRequest.from_dict(req.get_media())
        new_history = self.depot.history_stash.create_history(
            auth=auth,
            project=project,
            name=req_body.name,
            description=req_body.description,
            from_history=req_body.parent_history,
            at_step=req_body.step
        )
        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
        resp.media = new_history.to_dict()


class ProjectHistoryResource(PtResource):
    """
    Resource for /projects/{project}/histories/{history}
    Supports get (retrieve history)
    """

    @with_auth
    def on_get(self, auth: AuthenticatedUser, req: Request, resp: Response, project: str, history: str) -> None:
        history_obj = self.depot.history_stash.retrieve_history(
            auth, project, history
        )
        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
        resp.media = history_obj.to_dict()


class ProjectHistoryStepsResource(PtResource):
    """
    Resource for /projects/{project}/histories/{history}/steps
    Supports get (list history steps)
    """

    @with_auth
    def on_get(self, auth: AuthenticatedUser, req: Request, resp: Response, project: str, history: str) -> None:
        steps = self.depot.history_stash.list_history_steps(
            auth, project, history
        )
        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
        resp.media = {
            "type": "HistoryStepListResponse",
            "project": project,
            "history": history,
            "steps": [str(step) for step in steps],
        }


class ProjectHistoryStepResource(PtResource):
    """
    Resource for /projects/{project}/histories/{history}/steps/{step}
    Supports get (retrieve history step)
    """

    @with_auth
    def on_get(self, auth: AuthenticatedUser, req: Request, resp: Response, project: str, history: str, step: str) -> None:
        step_obj = self.depot.history_stash.retrieve_history_step(
            auth, project, history, int(step)
        )
        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
        resp.media = step_obj.to_dict()


class ProjectHistoryChangesResource(PtResource):
    """
    /projects/{project}/histories/{history}/changes
    Supports get.
    """

    @with_auth
    def on_get(self, auth: AuthenticatedUser, req: Request, resp: Response, project: str, history: str) -> None:
        changes = self.depot.change_stash.list_changes(auth, project, history, ChangeStatus.Open)
        resp.media = ChangeListResponse(changes=changes).to_dict()
        resp.content_type = "application/json"
        resp.status = falcon.HTTP_OK


class ProjectHistoryChangeResource(PtResource):
    """
    /projects/{project}/histories/{history}/changes/{change_name}
    Supports get.
    """

    @with_auth
    def on_get(self, auth: AuthenticatedUser, req: Request, resp: Response,
               project: str, history: str, change_name: str) -> None:
        ch = self.depot.change_stash.retrieve_change_by_name(auth, project, history, change_name)
        resp.media = ch.to_dict()
        resp.content_type = "application/json"
        resp.status = falcon.HTTP_OK


class ChangeSavesResource(PtResource):

    @with_auth
    def on_get(self, auth: AuthenticatedUser, req: Request, resp: Response,
               project: str, history: str, change: str) -> None:
        saves = self.depot.change_stash.list_save_points(auth, project, history, change)
        resp.media = SavesListResponse(saves).to_dict()
        resp.content_type = "application/json"
        resp.status = falcon.HTTP_OK
