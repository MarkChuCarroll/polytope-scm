# Polytope: Version Control Done Right for the Modern Programmer

## Motivation

<img style="float: right" src="docs/240px-Tesseract-1K.gif" title="tesseract" alt="A 4 dimensional cube rotating in 3 dimensions."/> 

Once upon a time, I worked at IBM research, where I lead a
project called <em>Stellation</em>. Stellation grew out of my
experience working on IBM's amazing C++ compiler, internally known
as Montana. Montana was developed by a distributed team, with members
in New York, Toronto, and Raleigh. 

Working on Montana, I got to watch a team of brilliant, experienced
engineers building a massive system - and I got to see the kinds of
problems that they had, working as a distributed team.


Stellation was an ambitious project that set out to change the way that we work
as distributed teams. At the heart of it was a new version control system that
was designed to be easier to use, and to support a range of features that made
it easier for a team to collaboratively develop a system. Due to political
pressures, we ended up abandoning that project. But ever since then, I've
regretted that I didn't find a way to continue that work.
 
The problem that I wanted to work on was: the way that we 
do version control in modern software development is still
primitive, difficult, and error-prone. At best, the SCM systems
we use today are based around ideas from the 1980s. 
(Literally: Perforce, which is one of the most successful
version control/software configuration management products out there still uses RCS as its underlying
VC engine; RCS was released in 1982.)

Even Git, which is the current favored tool for most engineers
is remarkably primitive. A few examples:
* What happens if you rename a file? Git doesn't track
  file histories, so it needs to guess at file renames, and
  it often gets them wrong. (This is particularly frustrating
  because there's a command to tell git that you're renaming
  a file - but all it does is run a "git rm" of the old filename,
  and a "git add" of the new. You tell it you're renaming;
  it discards that information, and then tries to infer it later!)
* Repeated merges. Git doesn't retain merge information
  about your branches - again, it discards valuable information,
  and leaves it to the user to try to figure out how to resolve
  the merges.
* The merge/rebase distinction. There's no reason to force users
  to understand this. In proper SCM systems, there are two slightly
  different forms of merge: upward merge (merge from a child branch
  into a parent), and downward merge (merge from a parent into a child).
  "git rebase" is a sloppy implementation of downward merge; "git merge"
  is a reasonable implementation of upward merge. You shouldn't need
  to tell git which merge to use.
  
So this project of mine is trying to bring the ideas of Stellation back from the
dead. This isn't my first try at this: I worked on a version of this for around
three years at Spotify during my hack days. But sadly, I was laid off by Spotify
before I finished the process of open-sourcing the system. So all of that work
is now sitting, unused and unloved, in Spotify's GHE archive.

The original Stellation was open-source, and the code for it is available in the
archive at eclipse.org. But this isn't going to use any of that code: 
I've grown as a programming in the intervening 15 years, and the software development
landscape has changed dramatically. So I'm starting from scratch, using modern
technologies.

## Core Idea

Polytope is built around a few key ideas:

### Focus on users, not implementations
   
Most version control systems provide their function in a way based
on how they're implemented. They don't think too much about what the
user wants to do, and how to make that easy: they think about what
needs to happen behind the scenes to make it all work, and leave all
of that exposed to their users. 

Let's look at Git as an example. To use git, you've got to understand
a lot of basic concepts: branches, commits, trees, merges, rebases,
and hashes, among others. But if you look at how users work with git,
they almost always follow a simple pattern:

* Start working on a change based on the current master.
* Incorporate changes from master into their work-in-progress change.
* Send the change for code review.
* Deliver their change to master.

Each of those steps could be written in terms of how git is implemented. The
user starts by pulling from their upstream to get an up-to-date clone of their
project, then they create a feature branch to start working on a change. When
their upstream gets changed, they pull the changes and rebase their branch. 
They're doing the same things as I said in my description - but instead of
working in terms of their mindset and the things that they're doing, they're
forced to work in terms of the primitives used to implement git. That gives
them a lot of power to do exactly what they want in rare cases, but for the
common use-case, it makes things more complicated and confusing.

Branches, labels, merges and rebases are the low-level primitives
of the SCM world. Those primitives are, most of the time, used to implement a couple
of common structures: hierarchical development, feature
development, code review, automated testing, etc.

The goal of Stellation was to present version control to users in terms of the
actual constructions that they need. In a large project, an engineer works as
part of a team on some part of the system - so they have a team space. Each
engineer in the team works on a feature or bug, and when they're done, they
share their change with their team. It goes through code review, and then
becomes part of the team's shared space. The team periodically releases from
their team space to the full system. Before the full systems base image is
modified, it needs to be tested by a continuous build system. If it passes, then
it becomes part of the system base image.

That's how a version control system should be built. Users don't
care about how it's implemented: they care about how they use it.


### Full history tracking

Polytope tracks all changes, recording full history, including:

* File level changes.
* Directory changes: Directories can be versioned just like
  files. Adding a file to a directory is a change to the directory,
  just like adding a line to a file is a change to the file.
* Version maps changes: directories map names to files, but they
   don't say anything about versions. The mapping of file to version
  is managed by a separate object, called a baseline. A lot
  of benefits come from this: for example, if Joe edits
  "project/main.c", and Jane renames "project/main.c" to "project/app.c",
  there's no merge conflict: when the changes get merged into the team 
  space, they'll see the rename as part of the process of merging the 
  directory versions; and the file content change as part of the 
  process of merging the file.
* Merge histories. Every merge history is recorded and remembered; 
  and those histories are used to determine how to perform merge changes.
  Repeated merges don't cause problems, like repeatedly merging branches
  in git - they just work, because Polytope remembers the merges,
  and uses that information.

In Git, users need to think about things like rebase vs merge. The only
reason for that is because git doesn't track history
very well. It tracks sequences of commits, where each commit is a full
image of the system. There's no history of a file, or a directory; that needs
to be inferred by walking the commit chain. Lacking that history, git needs
the user to use their knowledge of the code to dec

We can do better.

### File type awareness

Version control systems treat files abstractly as a list of lines,
without considering anything about the structure of the file contents.
Diffs and merges have to be done without understanding the things
that they're diffing or merging. 

Back when tools like SCCS and RCS were implemented, this made
sense. But it really doesn't anymore. In the modern world,
we can have plugin components (like the VSCode language servers)
that can tell us what the structure of a code file is. Using
that, we can use the language-specific structure of a versioned
file to do a better job computing differences and merges. 

### Finer-grained versioning

This won't be done for a while; I'm going to do basic file level
versioning first. But at some point:  if you're able to use language
syntactic structure to identify meaningful elements smaller than a file, 
you can treat a "file" as a collection of smaller fragments, and perform
versioning on those fragments. That gives you better history, better 
diffs and merges, and opens up the ability to view code fragments 
in different ways!

### Simple Storage

Version control systems have typically spent a lot of effort on
_delta compression_. Delta compression reduces the amount of disk space
needed by only storing the difference between a file version and its
immediate predecessor.  For systems with long development histories, delta compression
reduces the size of the full-repository history by a factor of 10 compared to storing
full-text of all versions; or a factor of 2-3 compared to storing LZW 
compressed text of all versions.

Back when disks were much smaller and more expensive, cutting the disk space
needed to store your version history was a big deal. But now? Disks are cheap.
If I want to run a server in the cloud, using AWS or GCP, I can get gigabytes of
storage for pennies per month.

Delta compression just doesn't make sense anymore. Instead of spending time
and effort building a custom delta compressed storage system, we should be
working on providing users with features that make their job easier. So
in Polytope, we'll just use simple storage without delta compression.

## Why Polytope?

When I first got started in version control, I picked the name "Stellation" for two
reasons.

1. it sounded like "constellation" - like a constellation of engineers working together
on a project;
2. a Stellation, in geometry, is an extended version of a regular polygon.
  If you take a two-dimensional polygon, and extend its sides until they meet
  in a point, you've got a stellation. For example, the five-pointed star
  is a stellation of a regular pentagon. That idea felt connected to the way
  that we were taking the basic ideas of version control, and extending them
  in new directions.
    
But Polytope is _not_ Stellation. It's a new project. It's built on
many of same ideas and motivations that led me to start Stellation. 
But it's a new system: I'm not re-using any code from the old Stellation
system; I haven't looked at that code in a decade!

The _name_ Stellation still belongs to either IBM or the Eclipse
Foundation, and I don't want to step on their toes. So I wanted a name 
that was related to Stellation in some way, but that also captured the notion that I'm
extending this in different directions that the original Stellation. 

A polytope is an extension of the concept of a polygon into more than
two dimensions. Every stellation is a polytope; not every polytope is a stellation. 
That seems to fit, and it sounds cool.

<em>(Note: the tesseract image above is a public domain image from 
the wikimedia commons. The original URL is 
https://commons.wikimedia.org/wiki/File:Glass_tesseract_animation.gif.)
</em>
