MinecraftPlus
===

How to do:
- The game code (client, server) **must be compatible with Eclipse JDT** because its core is used in Srg2Source

## Distributions


The project consists of three sub-projects in which the game code is stored. Each of the sub-projects corresponds to one distribution and allows you to build the appropriate part of the game, e.g. client or server.

### Joined

The most important part (because the changes introduced as part of the project are made in it) is the JOINED distro. The other two distributions are based on it.

The changes made to the code after pushing to the repository can be synchronized to the appropriate distribution using the 'repositoryClientSync' and 'repositoryServerSync' tasks.

### Client and Server

In the designs of these distributions, you MAY NOT introduce any extra code! That's what the JOINED distro is for.

After successfully synchronizing with the JOINED distro, run the 'projectClientMakePatches' or 'projectServerMakePatches' task.

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
