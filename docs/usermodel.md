# Polytope's Model for Users

(This is a rough work-in-progress note listing scenarios.)

## Starting a system

Polytope's basic model is built around the following workflow:

1. User creates a _project_.
2. User creates a _history_ to represent the state of their project.
3. User starts a _change_ against the current version of a stream.
4. User does work, modifying the files in the change.
5. User saves intermediate states as changesteps
6. User _delivers_ the change into a history, creating a new version of the history.


## Normal Development cycle

1. User creates a workspace on a project, starting from the head of
  a history.
2. User starts a change in the workspace.
3. User saves changes as steps.
4. User _updates_ their workspace, recieving any changes that have been added
  to their base history.
5. User delivers changes from their workspace to the history.

## Alternate History Workflow

1. User sends a create-history request to the server specifying a version map
  as a starting point. (A version map should be identifiable using either
  its UUID, a project history version, or ???)
2. User starts working on new history as in the normal development cycle.


## Cherrypicking

1. User creates a workspace and populates it with the current head of a history.
2. User runs "merge" of the _change_.
3. Once testing is done, user delivers the merged change to their history.
