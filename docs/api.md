
# The Polytope API

## Users

### Retrieve a list of users.
```
GET /users&pattern=str
```

- Retrieves a list of all users of the system whose
  username matches a regex pattern.
- pattern is optional; if omitted, returns all users.

### POST /users/

- Create a new user.
- Body is a CreateUserRequest.
- Return body is a User record.

### GET /users/{user_id}

### PUT /users/{user_id} (body:ModifyUserRequest)

## Projects

### GET /projects/&pattern=regex

Get a list of projects whose name match the pattern.

### Create a new project
```
POST /projects

<CreateProjectReq>
```
  

### GET /project/{project_name}

### PUT /project/{project_name}

## histories

### GET /project/{project_name}/histories

### POST /project/{project_name}/histories

### GET /project/{project_name}/histories/{history_name}

### PUT /project/{project_name}/histories/{history_name}y

### GET /project/{project_name}/histories/{history_name}/steps

### GET /project/{project_name}/histories/{history_name}/steps/{index}

### GET /project/{project_name}/histories/{history_name}/changes

### GET /project/{project_name}/histories/{history_name}/changes/{change_name}

## Workspaces

### `GET /project/{project_name}/workspaces?query"

- `query` is one of:
  - "owner=str"
  - "pattern=str"

### `GET /project/{project_name}/workspaces/by-name/{workspace-name}`

   `GET /project{project_name}/workspaces/by-id/{ws-id}`

### `PUT $ws-prefix` (body: WsModifyRequest)

### `POST /project/{project_name}/workspaces/` (body: WorkspaceCreateRequest)

### GET /project/{project_name}/workspaces/by-id/{ws-id}/...path

### PUT /project/{project_name}/workspaces/by-id/{ws-id}/...path (body: Update)

### `POST $ws-prefix/action`

Generic endpoint for all workspace operations that don't
really correspond to any normal HTTP method. The body of
this is a request object which includes a "type"  field.
The operation performed is determined by the type of the
request.

- Type "WsCreateChangeReq": create a new change, and populate the
  workspace with the initial savepoint of that new change. Returns
  the new state of the workspace.

- Type "WsUpDateDateReq": check if the workspace is
  up-to-date with its change/history. Returns a
  `WsCheckUpToDateResp` containing a boolean value.

- Type "WsOpenHistoryReq"

- Type "WsOpenChangeReq"

- Type "WsAddFileReq"

- Type "WsMoveFileReq"

- Type "WsDeleteFileReq"

- Type "WsDeleteFileReq"
