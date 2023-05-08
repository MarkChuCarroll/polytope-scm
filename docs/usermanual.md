# Polytope User Manual

(This is, currently, very future-looking. Only a small subset of
this will be implemented in the initial version, but this is the
goal.)

## The Basic Model

Polytope take an opinionated approach to version control. It's built
specifically to support hierarchical workflows. What does that mean
in practice?

- Users work on projects.
- Within projects, there's a primary version of the project in a history
  called "main". As the project evolves, main will continually change
  to represent the current version of the project.
- There can be other histories, which are derived from a _basis_ in
  some other history.
  - histories form a tree: main is the root; more histories can branch
    from main; and still more histories can branch from those, and so on.
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
  primary flow directions follow the stream hierarchy. (So the most
  common flow is: change is made in a history; that gets sent to the
  stream's parent, and so on until it reaches the root.) Sibling histories
  _can_ sync changes between them, but the normal (and so most fully
  supported by the system) way for histories to share changes will
  be for one to send changes to its parent history, and then the
  sibling will accept those changes from their common ancestor.

## The Command Line

The primary (initial) interface for working with Polytope will
be a command line tool. At first, it's going to be very manual -
you'll need to deliberately tell the systems about things like
renaming files. (Eventually, I hope to be able to provide a FUSE based
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

- project: the top-level construct. Everything is done relative
  to a project.
- history: a long-lived named sequence of project versions.
- change: a short-lived space where a user works on changes
  that will eventually be delivered into a history.
- file
- user

### Verbs for project

#### Project Create

> pt project create _projectname_

_create_ creates a new project.

#### Project List Streams

> pt project list-streams

#### Set the selected project

> pt project select _projectname_

Sets the project for the current workspace.

#### Show the selected project

> pt project show

### Verbs for Streams

#### Create Stream

> pt stream create _stream-name_ _[--from=\_stream@version_]\_

(If the "from" parameter is omitted, then it defaults to main@current.)

#### Show Stream History

> pt stream show-history _streamname_

#### Set the selected (default) stream for this workspace

> pt stream select _stream-name_

The _selected_ stream is the stream that you'll be creating your changesets from.

#### Show the selected stream for this workspace.

> pt stream show

### Verbs for Changesets

#### Create a changeset

> pt changeset create _name_ _[--from=stream-name@version]_ _[--description="text"]_

Create a changeset. By default, the changeset will be created from the latest version
of the selected stream.

#### Save the current state of a changeset.

> pt changeset save _[--description=...]_

#### Update a changeset to include pending changes from its parent

> pt changeset update

#### Merge changes from another changeset into this one

> pt changeset merge _changeset_id_

#### Deliver changes to the parent stream

> pt changeset deliver

### Verbs for files

Eventually, most of these commands shouldn't be needed; we can automate them via
either fuse, or some kind of filesystem watcher.

#### Ignore a file

This command tells polytope to ignore the file. It won't attempt to track
changes to it, or include it in changesets.

> pt file ignore _paths_

#### Rename a file

> pt file rename _oldpath_ _newpath_

#### Delete a file

> pt file remove _path_

(This isn't strictly necessary; if you just delete a file, we'll notice,
and mark it deleted.)

#### Add a file

> pt file add _path_

(There'll be a configuration option around this; if you want, we can automatically
add any new files that you don't specifically flag with "ignore".)

#### Show file history

> pt file show-history _path_...

#### Show a files identifier

> pt file id _path_...

Shows the internal identifier for the file and its current version.

#### Show file changes.

> pt file diff _path_ \_[--relative-to=version_spec]

The version spec here can be one of:

- `stream@version` (the version from the specified stream)
- `-number` (a version _count_ levels back in the version history of the file.)
- `$versionid` a specific version ID.

### Commands for administering users.

#### Add a new user

> pt user add _username_ [--permissions=perm1,perm2,...]

#### Grant new permissions to a user

> pt user grant _username_ [--permissions=perm1,perm2,...]

#### Revoke permissions from a user

> pt user revoke _username_ [--permissions=perm1,perm2,...]

#### List users

> pt user list [--permissions]
