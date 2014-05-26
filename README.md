# Activitor Tutorial Generator

This library and SBT plugin is used to generate Activator tutorials.

The idea is that the tutorial is a script containing a mixture of HTML, edit scripts and shell commands.  The tutorial can be verified by "running" the tutorial, this will apply the edit scripts to the projects source code and execute the shell commands as they appear in the tutorial.  By doing this, it can be ensured that all the code snippets in a tutorial compile and make sense.

The final tutorial can then be generated, which removes the shell command directives, and takes the edit scripts and formats them in HTML pre blocks suitable for inclusion in an Activator tutorial.

## Tutorial format

### Edit scripts

The format for specifying the start and end of an edit script is the same as that used by noweb (a literate programming tool).  The start of the edit script is indicated by a line containing the file name to edit inside double angle brackets, followed by the equals sign.  The end of the edit script is indicated by a single `@` sign on a line by itself:

```
<<app/backend/UserMetaData.java>>=
18a
        if (msg instanceof GetUser) {
            GetUser getUser = (GetUser) msg;
            Tuple<LatLng, Double> user = users.get(getUser.getId());
            if (user != null) {
                sender().tell(new User(getUser.getId(), user._2), self());
            } else {
                sender().tell(new User(getUser.getId(), 0), self());
            }
        }
.
@
```

The edit script itself supports a subset of edit commands:

* **append**: Append after the given line number.  For example, `27a` will append the given text after line 27.
* **insert**: Insert before the given line number.  For example, `12i` will insert the given text before line 12.
* **delete**: Delete the specified lines.  For example, `31,33d` will delete lines 31 to 33.
* **change**: Change the specified lines.  For example, `52,54c` will replace lines 52 to 54 with the given text.

For the commands that insert text into the file, the end of the text is indicated by a single fullstop.

Multiple commands can be included in a single edit script.

### Shell commands

A shell command is indicated by a line that starts with `@@`.  Everything after that is the shell command.  Typically this will be a command to compile or test the project, to verify that a particular edit script or set of edit scripts made the expected changes.  For example:

```
@@ sbt compile
```

## Installing the plugin

Typically, you would not want the plugin installed in an activator template, since the end user does not need it.  So the best place to install it is globally.  This can be done by adding the followin to `~/.sbt/0.13/plugins/build.sbt`:

```scala
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

"com.typesafe.sbt" %% "sbt-activator-tutorial-generator" % "1.0.1"
```

## Running the tutorial

The tutorial can be run by running `activatorRunTutorial`.

After making changes to the tutorial, sometimes a step deep into the tutorial may fail.  Fixing can be tedious, and requires running the tutorial several times over, while the early validation steps in the tutorial may be slow and completely unnecessary. Hence a an option is provided from the run command, that allows you to specify which command number if should start validating from, starting from 0.  So if you run:

    [reactive-maps] $ activatorRunTutorial 3

It will skip the first 3 commands in the tutorial (still applying all edit scripts though).

## Generating the tutorial

The tutorial can be generated by running `activatorGenerateTutorial`.

## Generating a git repo for the tutorial

You can also generate a git repository that contains all the steps of the tutorial (each edit script) as individual git commits.  This can help when modifying or developing the tutorial, as it gives you a project that you can start from, and allows you to use git to compare and/or look back in history at the project.

To generate the git repo, select an empty directory somewhere on your filesystem, and run the `activatorGenerateGitRepo` command with it:

    [reactive-maps] $ activatorGenerateGitRepo /tmp/reactive-maps

