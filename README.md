MinecraftPlus
===

This repository contains a specially crafted toolkit to modify the original version of Minecraft in a way that allows multiple contributors to collaborate.

You can learn more about the project and its assumptions on [minecraftplus.org](https://minecraftplus.org).


## How to start

Clone this repository and run:

```
./gradlew buildAll
```

This task will download all necessary files (tools, game, etc.) and decompile game client and server. Decompiled source code will be stored in appropriate folder in `/projects` directory.


## Distributions

The project consists of four sub-projects in which the game code is stored. Each of the sub-projects corresponds to one distribution and allows you to build the appropriate part of the game, e.g. client or server.

### Joined

The most important part (because the changes introduced as part of the project are made in it) is the `JOINED` distro. The other two distributions are based on it.

The changes made to the code after pushing to the repository can be synchronized to the appropriate distribution using the `repositoryClientSync` and `repositoryServerSync` tasks.

### Client and Server

In the designs of these distributions, you MAY NOT introduce any extra code! That's what the `JOINED` distro is for.

After successfully synchronizing with the `JOINED` distro, run the `projectClientMakePatches` or `projectServerMakePatches` task.

This will generate a set of new patches that reflect the changes made to the game code. Then generated patches can be put into the repository.

### Resources

Resources project store all resource files used in game, simultaneously keeping them in division into `internal` (stored in game JAR file) and `remote` (indexed, downloadable assets).


## Running

Running game client or server implementation from source code in project is automated by gradle tasks in group `mcplus run`.  These can be listed by command:

```
./gradlew tasks --group="mcplus run"

------------------------------------------------------------
Tasks runnable from root project 'minecraftplus'
------------------------------------------------------------

Mcplus run tasks
----------------
runClient - Run client client application
runJoinedClient - Run joined client application
runJoinedServer - Run joined server application
runServer - Run server server application
```

Using these tasks is useful especially in running client side - which needs to pass needed arguments from version manifest (normally prepared by game launcher).
