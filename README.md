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

This will generate a set of new patches that reflect the changes made to the game code. Then put them in the repository.
