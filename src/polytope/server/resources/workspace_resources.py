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

from typing import List

import falcon

from polytope.common.api.workspace import (
    WorkspaceCreateRequest,
    WorkspaceListResponse,
    WsAbandonReq,
    WsAddFileReq,
    WsAddFileResp,
    WsCheckUpToDateResp,
    WsConflictResp,
    WsCreateChangeReq,
    WsDeleteFileReq,
    WsDeliverReq,
    WsFileContents,
    WsGetManyReq,
    WsGetManyResp,
    WsIntegrateReq,
    WsMoveFileReq,
    WsOpenChangeReq,
    WsOpenHistoryReq,
    WsPathListResp,
    WsSaveReq,
)
from polytope.common.stashable import JDict
from polytope.common.stashable.ids import Id
from polytope.common.stashable.user import AuthenticatedUser
from polytope.common.stashable.workspace import Workspace
from polytope.server.resources.project_resources import PtResource
from polytope.server.resources.with_auth import with_auth


class WorkspacesResource(PtResource):
    """
    Resource for /projects/{project}/workspaces
    Supports get (list workspaces) and post (create workspace)
    """

    @with_auth
    def on_get(
        self,
        auth: AuthenticatedUser,
        req: falcon.Request,
        resp: falcon.Response,
        project: str,
    ) -> None:
        workspaces = self.depot.workspace_stash.list_workspaces(auth, project, None)
        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
        resp.media = WorkspaceListResponse(workspaces=workspaces).to_dict()

    @with_auth
    def on_post(
        self,
        auth: AuthenticatedUser,
        req: falcon.Request,
        resp: falcon.Response,
        project: str,
    ) -> None:
        req_body = WorkspaceCreateRequest.from_dict(req.get_media())
        workspace_name = req_body.name
        base_history = req_body.history
        description = req_body.description
        new_workspace = self.depot.workspace_stash.create_workspace(
            auth=auth,
            project=project,
            name=workspace_name,
            history=base_history,
            description=description,
        )
        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
        resp.media = new_workspace.to_dict()


class WorkspaceResource(PtResource):
    """
    Resource for /projects/{project}/workspaces/{workspace}
    Supports get (retrieve workspace) and delete (delete workspace)
    """

    @with_auth
    def on_get(
        self,
        auth: AuthenticatedUser,
        req: falcon.Request,
        resp: falcon.Response,
        project: str,
        workspace: str,
    ) -> None:
        ws = self.depot.workspace_stash.retrieve_workspace(auth, project, workspace)
        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
        resp.media = ws.to_dict()

    @with_auth
    def on_delete(
        self,
        auth: AuthenticatedUser,
        req: falcon.Request,
        resp: falcon.Response,
        project: str,
        ws_id: str,
    ) -> None:
        self.depot.workspace_stash.delete_workspace(
            auth, project, Id.from_string(ws_id)
        )
        resp.status = falcon.HTTP_200


class WorkspaceActionResource(PtResource):
    """
    Resource for /project/{project}/workspace/{wsid}/action
        Handles Post.
    """

    def on_create_change(
        self,
        auth: AuthenticatedUser,
        project: str,
        ws_id: Id[Workspace],
        body: WsCreateChangeReq,
    ) -> JDict:
        result = self.depot.workspace_stash.create_change(
            auth=auth,
            project=project,
            wsid=ws_id,
            history=body.history,
            change_name=body.change_name,
            description=body.description,
        )
        return result.to_dict()

    def on_add_file(
        self,
        auth: AuthenticatedUser,
        project: str,
        ws_id: Id[Workspace],
        req: WsAddFileReq,
    ) -> JDict:
        new_file_id = self.depot.workspace_stash.add_file(
            auth=auth,
            project=project,
            wsid=ws_id,
            path=req.path,
            artifact_type=req.artifact_type,
            content=req.content,
        )
        return WsAddFileResp(new_file_id).to_dict()

    def on_up_to_date(
        self, auth: AuthenticatedUser, project: str, ws_id: Id[Workspace]
    ) -> JDict:
        utd = self.depot.workspace_stash.up_to_date(auth, project, ws_id)
        return WsCheckUpToDateResp(utd).to_dict()

    def on_delete_file(
        self,
        auth: AuthenticatedUser,
        project: str,
        ws_id: Id[Workspace],
        req: WsDeleteFileReq,
    ) -> JDict:
        self.depot.workspace_stash.delete_file(auth, project, ws_id, req.path)
        return self.depot.workspace_stash.retrieve_workspace_by_id(
            auth, project, ws_id
        ).to_dict()

    def on_open_history(
        self,
        auth: AuthenticatedUser,
        project: str,
        ws_id: Id[Workspace],
        req: WsOpenHistoryReq,
    ) -> JDict:
        # TODO: add support for the history step.
        ws = self.depot.workspace_stash.open_history(auth, project, ws_id, req.history)
        return ws.to_dict()

    def on_open_change(
        self,
        auth: AuthenticatedUser,
        project: str,
        ws_id: Id[Workspace],
        req: WsOpenChangeReq,
    ) -> JDict:
        # TODO: add support for the save point index.
        return self.depot.workspace_stash.open_change(
            auth, project, ws_id, req.history, req.change
        ).to_dict()

    def on_move_file(
        self,
        auth: AuthenticatedUser,
        project: str,
        ws_id: Id[Workspace],
        req: WsMoveFileReq,
    ) -> JDict:
        self.depot.workspace_stash.move_file(
            auth, project, ws_id, req.path_before, req.path_after
        )
        ws = self.depot.workspace_stash.retrieve_workspace_by_id(auth, project, ws_id)
        return ws.to_dict()

    def on_get_many(
        self,
        auth: AuthenticatedUser,
        project: str,
        ws_id: Id[Workspace],
        req: WsGetManyReq,
    ) -> JDict:
        contents: List[WsFileContents] = []
        for path in req.paths:
            contents.append(
                self.depot.workspace_stash.get_file_contents(auth, project, ws_id, path)
            )
        return WsGetManyResp(files=contents).to_dict()

    def on_deliver(
        self,
        auth: AuthenticatedUser,
        project: str,
        ws_id: Id[Workspace],
        req: WsDeliverReq,
    ) -> JDict:
        self.depot.workspace_stash.deliver(auth, project, ws_id, req.description)
        return self.depot.workspace_stash.retrieve_workspace_by_id(
            auth, project, ws_id
        ).to_dict()

    def on_save(
        self,
        auth: AuthenticatedUser,
        project: str,
        ws_id: Id[Workspace],
        req: WsSaveReq,
    ) -> JDict:
        return self.depot.workspace_stash.save(
            auth,
            project,
            ws_id,
            description=req.description,
            resolved=req.resolved_conflicts,
        ).to_dict()

    def on_integrate(
        self,
        auth: AuthenticatedUser,
        project: str,
        ws_id: Id[Workspace],
        req: WsIntegrateReq,
    ) -> JDict:
        conflicts = self.depot.workspace_stash.integrate_change(
            auth, project, ws_id, req.source_history, req.change_name
        )
        ws = self.depot.workspace_stash.retrieve_workspace_by_id(auth, project, ws_id)
        return WsConflictResp(ws, conflicts).to_dict()

    def on_update(
        self, auth: AuthenticatedUser, project: str, ws_id: Id[Workspace]
    ) -> JDict:
        conflicts = self.depot.workspace_stash.update(auth, project, ws_id)
        ws = self.depot.workspace_stash.retrieve_workspace_by_id(auth, project, ws_id)
        return WsConflictResp(ws, conflicts).to_dict()

    def on_abandon(
        self,
        auth: AuthenticatedUser,
        project: str,
        ws_id: Id[Workspace],
        req: WsAbandonReq,
    ) -> JDict:
        self.depot.workspace_stash.abandon_changes(auth, project, ws_id, req.reason)
        return self.depot.workspace_stash.retrieve_workspace_by_id(
            auth, project, ws_id
        ).to_dict()

    def on_get_paths(
        self, auth: AuthenticatedUser, project: str, ws_id: Id[Workspace]
    ) -> JDict:
        paths = self.depot.workspace_stash.list_paths(auth, project, ws_id)
        return WsPathListResp(paths=paths).to_dict()

    @with_auth
    def on_post(
        self,
        auth: AuthenticatedUser,
        req: falcon.Request,
        resp: falcon.Response,
        project: str,
        wsid: str,
    ) -> None:
        body = req.media
        ws_id: Id[Workspace] = Id.from_string(wsid)
        match body["type"]:
            case "WsCreateChangeReq":
                resp.media = self.on_create_change(
                    auth, project, ws_id, WsCreateChangeReq.from_dict(body)
                )
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK

            case "WsAddFileReq":
                resp.media = self.on_add_file(
                    auth, project, ws_id, WsAddFileReq.from_dict(body)
                )
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK
            case "WsDeleteFileReq":
                resp.media = self.on_delete_file(
                    auth, project, ws_id, WsDeleteFileReq.from_dict(body)
                )
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK
            case "WsCheckUpToDateReq":
                resp.media = self.on_up_to_date(auth, project, ws_id)
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK
            case "WsOpenHistoryReq":
                resp.media = self.on_open_history(
                    auth, project, ws_id, WsOpenHistoryReq.from_dict(req.media)
                )
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK
            case "WsOpenChangeReq":
                resp.media = self.on_open_change(
                    auth, project, ws_id, WsOpenChangeReq.from_dict(req.media)
                )
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK
            case "WsMoveFileReq":
                resp.media = self.on_move_file(
                    auth, project, ws_id, WsMoveFileReq.from_dict(req.media)
                )
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK
            case "WsGetManyReq":
                resp.media = self.on_get_many(
                    auth, project, ws_id, WsGetManyReq.from_dict(req.media)
                )
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK
            case "WsSaveReq":
                resp.media = self.on_save(
                    auth, project, ws_id, WsSaveReq.from_dict(req.media)
                )
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK
            case "WsDeliverReq":
                resp.media = self.on_deliver(
                    auth, project, ws_id, WsDeliverReq.from_dict(req.media)
                )
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK
            case "WsIntegrateReq":
                resp.media = self.on_integrate(
                    auth, project, ws_id, WsIntegrateReq.from_dict(req.media)
                )
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK
            case "WsUpdateReq":
                resp.media = self.on_update(auth, project, ws_id)
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK
            case "WsAbandonReq":
                resp.media = self.on_abandon(
                    auth, project, ws_id, WsAbandonReq.from_dict(req.media)
                )
            case "WsPathListReq":
                resp.media = self.on_get_paths(auth, project, ws_id)
                resp.content_type = "application/json"
                resp.status = falcon.HTTP_OK


class WsPathResource(PtResource):
    """
    Resource for /projects/{project}/workspaces/{wsid}/path/{path:path}
    Supports get (retrieve contents of the file at a path),
        put (update contents of a file at a path.)
    """

    @with_auth
    def on_get(
        self,
        auth: AuthenticatedUser,
        req: falcon.Request,
        resp: falcon.Response,
        project: str,
        wsid: str,
        path: str,
    ) -> None:
        ws_id: Id[Workspace] = Id.from_string(wsid)
        resp.media = self.depot.workspace_stash.get_file_contents(
            auth, project, ws_id, path
        ).to_dict()

        resp.status = falcon.HTTP_200
        resp.content_type = "application/json"
