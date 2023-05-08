# Basics of SCM in Polytope

The basic data:

1. **Artifact**: each versioned thing stored by polytope is called an artifact.
   Each artifact is stored as a read-only entity in the database. The artifact
   records its _type_, its _identifier_, and a collection of key/value _properties_.
   These are all set when the artifact is created. Artifacts include everything that's
   part of the state of the system: files, directories, and version mappings.
2. **ArtifactVersion**: each artifact has a collection of _versions_. Like the
   artifact type, each version has a unique _version identifier_ and a list of properties.
   In addition, an artifact version has a list of _ancestors_ (other versions of the same
   artifact that it's derived from), and a _content_ object. For each type of
   artifact, there's a subclass of `ArtifactVersion` which specifies a content type.
3. **Content**: each type of artifact has a blob represented by a string which
   is an encoded form of its contents. The agent for the artifact type knows how to
   translate between the blob form and the proper data structure.
4. **Change**: a group of artifact modifications that are grouped together into an atomic
   unit. A change is roughly equivalent to a development branch in git: when a user
   starts working on a task, they create a change for the task. They work in the task,
   saving checkpoints (called ChangeSteps), until it's done, at which point they
   _deliver_ the resulting completed change.
5. **SavePoint**: a save point is a collection of artifact modifications that are recorded as an atomic
   unit. Each modification consists of an artifact ID, and a state change. A state change basically
   consists of a before and after state: the before state could be nothing (this is a new artifact being
   added to the system), or a version identifier; and the after state can be a version identifier (a new version
   of the artifact if it was modified) or nothing (if the artifact was deleted.)
6. **History**: a history is a named sequence of history versions. It's roughly equivalent to
   a persistent branch in git - except that it's much more common (and better supported) to have
   many histories in a system like polytope.
7. **HistoryVersion**: a complete image of a project, containing
   a list of all artifacts that are part of the project at the time it was created, and a mapping of each artifact to an artifact version.

## Change Management

Polytope is built around the idea that changes don't happen at random;
they're a part of a structured process.

1. **History**: a history is a _named_ series of versions of a project.
   It's similar to a long-lived important branch in git.
2. **Change** a change is a single piece of work that should be treated atomically.
   A change contains a sequence of change steps. The steps in a changeset represent a sequence
   of states in the process of building the complete changeset. You can think of a changeset as being something like a development branch in a system like git.

## Representing Projects

A software project is, to polytope, a collection of versioned artifacts. Every
repository includes a few basic built-in artifact types for those versioned artifacts:

1. **Directory**: a directory consists of a list of mappings from _names_ to artifact identifiers.
2. **Baseline**: a baseline map consists of a list of mappings from _artifact identifiers_ to
   _version identifiers_ and an artifact identifier which represents the _root directory_
   of the version map. The root directory must be a directory artifact.
3. **Text**: a text artifact is a blob of readable UTF-8 text representing a text file.

A project is defined by a baseline. Each version of a baseline
specifies which artifact versions are a part of that version, and through the
root directory of the baseline, it specifies where those artifact versions
should be placed in a filesystem.

When a new project is created, polytope creates a directory artifact
and a baseline artifact, an empty directory version, and finally
a baseline version containing the empty directory version.

Once the base artifacts are created, the system creates a history with the same
name as the project, and adds the initial baseline version as the first version
of the project's history.

## Workspaces

## Changes

A workspace starts the process of making a change by creating a
_working version_ of a file. Only working versions can be modified.
