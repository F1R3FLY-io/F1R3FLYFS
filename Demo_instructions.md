# A list of the Repos Used for Demo and Project

* [git@github.com:leithaus/f1r3fly.git](https://github.com/leithaus/f1r3fly/tree/working)

* [git@github.com:ComposeDAO/rchain-docker-cluster.git](https://github.com/ComposeDAO/rchain-docker-cluster/)

* [git@github.com:ComposeDAO/jnr-fuse](https://github.com/ComposeDAO/jnr-fuse)

* [git@github.com:ComposeDAO/rchain.xmpl.git](https://github.com/ComposeDAO/rchain.xmpl)

## Instructions for Building and Running the Demo

1. Use the `rchain-docker-cluster` repo to start a shard (`docker compuse -f ./shard.yml up`) on Intel only hardware currently
2. Use the `jnr-fuse` repo to integrate the MacFuse system with triggered events to deploy Rholang to the cluster
> 
```
gradle build # may not be necessary
gradle shadowTar # builds libs in class path
java -cp build/libs/jnr-fuse-0.5.8-SNAPSHOT-shadow.jar ru.serce.jnrfuse.examples.MemoryFS # executes Rholang template code against Fuse events
```
3. Navigate the file system by `cd /tmp/mntm`. Create a directory and file using an editor and not the interaction on chain as well as the deployed Rholang contracts. The information is being relayed to the shard as you create the files.
4. Run `docker compose -f ./shard.yml down` to stop the shard.

## Current Work to use enable use on Apple Silicon

* The `f1r3fly` `working` branch builds a docker container via the `sbt clean compile "project node" assembly node/docker:publishLocal` command.
* Make sure you have your Docker Desktop running so the image can be locally published.
* Navigate to the `rchain-docker-cluster` repo, and checkout the `working_branch_docPub` branch. Then `rm -fr data/*` to remove vestigial data that may interfere with the new storage format used by the chain.
* Run `docker compose -f ./shard.yml up` and note the console output. Once the nodes see peers you can interact in the same way as step 2 above.
* Note: the `wallets.txt` and `bonds.txt` on the `working_branch_docPub` of the `rchain-docker-cluster` repo do not match the modern format used by the F1r3fly build. The version that works on Intel uses older Ethereum addresses instead of Rev and has a slightly different format. We're working through getting this working on Apple silicon. 

