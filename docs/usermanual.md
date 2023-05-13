# Polytope User Manual

(This is, currently, very future-looking. Only a small subset of
this will be implemented in the initial version, but this is the
goal.)

## The Basic Model

Polytope take an opinionated approach to version control. It's built
specifically to support hierarchical workflows. What does that mean
in practice?

- Users work on projects.
- Changes to projects are organized into histories. A history is a
  linear sequence of versions of the project. Each step in the history
  is a change.
- There can be many histories in a project. Each one has an independent
  sequence of changes.
- In normal operation, users will work in one history.
- In the most common mode of operation, there's one history which is
  the target of development, called _main_.
- Within projects, there's a primary version of the project in a
  history called "main". As the project evolves, main will continually
  change to represent the current version of the project.
- There can be other histories, which are derived from a _basis_ in
  some other history.
  - histories form a tree: main is the root; more histories can branch
    from main; and still more histories can branch from those, and so
    on.
- Users work by making changes to a history. They don't change a
  history directly; they create a _change_, where they work on their
  changes to a history. When they're done, they can _deliver_ their
  change to their history.
  - The history can change while they're working; users can update
    their change by _accepting_ changes from their parent history.
  - While working on the change, users can save intermediate states.
    Each of these states is called a _savepoint_. The change is the
    sum of all of the savepoints.
- Changes can flow between histories, but it's expected that the
  primary flow directions follow the history hierarchy. (So the most
  common flow is: change is made in a history; that gets sent to the
  stream's parent, and so on until it reaches the root.) Sibling
  histories _can_ sync changes between them, but the normal (and so
  most fully supported by the system) way for histories to share
  changes will be for one to send changes to its parent history, and
  then the sibling will accept those changes from their common
  ancestor.

## The Command Line

The primary (initial) interface for working with Polytope will be a
command line tool. At first, it's going to be very manual - you'll
need to deliberately tell the systems about things like renaming
files. (Eventually, I hope to be able to provide a FUSE based
interface, so that things like that can be done automatically.)

The command-line tool creates a local copy of the project called
a _workspace_. Commands operate using the current content of the
workspace.

The command line is build around a noun/verb model. Each command
has the structure:

> pt _object-type_ _verb_ --parameter=value... subject...

For example, to list all histories within a project:

> pt history --project=my-project

Or to rename a file in the workspace:

> pt file rename foo/bar/old foo/bar/new

The supported nouns are:

- Project
- History
- Change
- Workspace (ws) 
- User
- File


### Verbs for project

#### Create a new project

> pt project create _projectname_

_create_ creates a new project.

#### List projects

> pt project list

List the projects available on the server.

#### Show a specific project

> pt project get _project_name_

### Verbs for History

#### List histories

> pt history list --project=_project_name_

#### Create a new history

> pt history create --project=_project_name_ --from-history=_source_history [ --from-history-version=_index_] _new_history_name

#### View the versions in a history

> pt history versions --project=_project_name_ --history=_history_name_

#### Select a history for work in a workspace

> pt history select _history_name_ 

**Note**: This command can only be run in a workspace.

### Verbs for Change

#### Create a new change

> pt change create --project=_project_name_ [--history=_history_name_] _new_change_name_

If the command is run in a workspace, and the parent history isn't
supplied, then it will default to the current history open in the workspace. 
If it's not run in a workspace, then it's an to omit the project and history.


#### Open an existing change in a workspace

**Note**: This command can only be run in a workspace.

> p change open --name=change_name [--history=_history_name_]

If the history name is omitted, then the current history open in the
workspace will be used.

#### List changes in a history

> pt changes list [--project=_project_name_] [--history=_histore_name] --status=(OPEN|ALL)

If the command is run in a workspace, and the project and/or parent
history isn't supplied, then it will default to the current project/history
open in the workspace.  If it's not run in a workspace, then it's an
error to ommit

#### View a change

> pt change get [--project=_project_name_] [--history=_history_name] --change=change_name


#### List the save points within a change

> pt change saves [--project=_project_name_] [--history=_history_name] --change=change_name



#### Deliver a change to its history

**Note**: This command can only be run inside of a workspace. It will use the project, history, and change open in the current workspace.

> pt change deliver

#### Integrate a change from a different history

**Note**: This command can only be run inside of a workspace.

> pt change integrate --history=_history_name --change=_change_name_

### Workspace Commands

#### List Workspaces in a project

> pt ws list --project=_project_ [--history=_history_]

#### View information about a workspace

> pt ws get --project=_project_ --ws=_ws_name_

#### Create a new workspace

> pt ws create --project=_project_ --ws=_new_ws_name [--location=path]

Creates a new workspace, with a client in the local filesystem
at the specified path. The workspace will initially be opened in
the current version of the _main_ history.

#### Open an existing workspace in the client

> pt ws open --project=_project_ --ws=_ws_ --location=path

Creates a new client for a workspace in the local filesystem.

#### Save the changes in a workspace as a changestep

> pt ws save --description="A description of the change" (--resolve=conflict-id)*

save the current state of the workspace in a savepoint, resolving any 
conflicts listed.

#### Update the workspace to include new changes in a history

> pt ws update 

#### Delete a workspace

> pt ws delete --project=_project --ws=_workspace_name_

Note that this doesn't just delete a local copy of a workspace (you
can do that by just `rm`ing it in the shell; this deletes the workspace
from the server, so that it can never be opened in another client.


#### Abandon changes in a workspace

> pt ws abandon --resean="desrciption of reason for abandoning" [--savepoint=_sp]

This abandons the currently unsaved changes in the workspace,
reverting to the state at the specified savepoint (or, if the saevpoint
isn't provided, to the most recent savepoint in the workspace's open change.)


### Verbs on File

#### Add a new file

> pt file add path...

#### Move/rename a file

> pt file mv old-path... new-path

#### Delete a file

> pt file rm path...

#### List the managed files in a workspace

> pt file list 


