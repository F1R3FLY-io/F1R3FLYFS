# Running local singleton RNode

1. Prepare `~/.rnode/genesis/wallet.txt` file with a key

```
11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w,50000000000000000
```

2. Prepare `~/.rnode/genesis/bonds.txt`:

```
0494eb76a9afa326ecd805592f1553f88ccc1f7992daf88703cc5af1d2894af50b97d077830706dbb447b2d5d8456b1fe4d4bafe4853d544b45ac6ce9ce0684bba 4
```

3. Run node with exeeded limit for gRPC message `--api-grpc-max-recv-message-size 1073741824`. `f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85` is machted for the above wallet.txt. We can skip step 1 and 2, and use the custom `validator-private-key`
   Before 'run node' we should build jar under f1r3fly
   ```sbt 'compile ;project node ;assembly'```

```sh
java -Djna.library.path=./rspace++/target/debug/ --add-opens java.base/sun.security.util=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED -jar node/target/scala-2.12/rnode-assembly-0.0.0-unknown.jar run -s --no-upnp --allow-private-addresses --synchrony-constraint-threshold=0.0 \
  --validator-private-key f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85 \
  --api-grpc-max-recv-message-size 1073741824
```

where f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85 - key from 'cat fileName.sk file' under local ~/.rnode/genesis folder

4. Wait on log like ```Listening for traffic on rnode://cfae6a0c885d734908f8c756fb0519d2df7fbcec@178.150.31.10?protocol=40400&discovery=40404```

# Run F1r3flyFS app

1. Got to `F1r3flyFS` folder
2. Build f1r3flyFS into Jar

```sh
./gradlew shadowJar -x test
```

3. Run F1r3flyFS app with a key (`a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954` is an exmaple key). This key has to be related to Wallet (row in wallet.txt at Node)

```sh
# creating ~/demo-f1r3flyfs folder and mounting it to F1r3flyFS
java -jar ./build/libs/f1r3flyfs-0.5.7-shadow.jar ~/demo-f1r3flyfs -sk a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954 -ck ~/cipher.key -sh localhost -sp 40402 -ch localhost -cp 51111 
```

4. Find the mount id of the first F1r3flyFS app. For example, it is `f1r3flyfs-1926576453` from the output below.

```sh
mount -v
f1r3flyfs-1926576453 on /Users/andriistefaniv/demo-f1r3flyfs (macfuse, nodev, nosuid, synchronous, mounted by andriistefaniv) # this is an example of output of `mount -v`
```

5. Run second F1r3flyFS app with the same key (`a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954`) from another location.

```sh
java -jar ./build/libs/f1r3flyfs-0.5.7-shadow.jar ~/demo-f1r3flyfs2 -sk a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954 -ck ~/cipher.key -sh localhost -sp 40402 -ch localhost -cp 51112 -mn f1r3flyfs-1926576453
```

# Demo

Creating a tiny file inside ~/demo-f1r3flyfs folder

```sh
echo "abc" > ~/demo-f1r3flyfs/demo.txt
ls -lh ~/demo-f1r3flyfs/demo.txt
cat ~/demo-f1r3flyfs/demo.txt
```

Copy 1M file inside ~/demo-f1r3flyfs folder

```sh
# generating binary file with 1M size
dd if=/dev/zero of=large_data.txt  bs=1m  count=1

cp large_data.txt ~/demo-f1r3flyfs/ # OR rsync -av --progress large_data.txt ./demo-f1r3flyfs # for more logs
# wait for some time

# make sure that file is copied
ls -lh ~/demo-f1r3flyfs/large_data.txt
```

Wait (from 10s or 1 minute) and read from second mount point

```sh
cat ~/demo-f1r3flyfs2/demo.txt
ls -lh ~/demo-f1r3flyfs2/
```

Delete the file via second client (it is two-way sync)

```sh
rm ~/demo-f1r3flyfs2/demo.txt
ls -lh ~/demo-f1r3flyfs2/
```

Wait again and check the file was deleted at the first mount

```sh
ls -lh ~/demo-f1r3flyfs/
```

**All operations can be performed via UI client (for example Finder from macOS) as well.**

# Cleanup

Stop all processes and remove all files

```sh
# Stop node and F1r3flyFS apps
# or kill if stuck
ps aux | grep java | grep -v grep | awk '{print $2}' | xargs kill -9

# clean Node state:
rm -rf ~/.rnode/casperbuffer/ ~/.rnode/dagstorage/ ~/.rnode/deploystorage/ ~/.rnode/blockstorage/ ~/.rnode/rnode.log ~/.rnode/rspace++/ ~/.rnode/node.certificate.pem ~/.rnode/node.key.pem

# Unmount ~/demo-f1r3flyfs if f1r3flyFS crashed
sudo diskutil umount force ~/demo-f1r3flyfs
sudo diskutil umount force ~/demo-f1r3flyfs2

# delete ~/demo-f1r3flyfs and ~/demo-f1r3flyfs2 before running the next demo
rm -rf ~/demo-f1r3flyfs
rm -rf ~/demo-f1r3flyfs2

# remove large_data.txt
rm -f large_data.txt
```
