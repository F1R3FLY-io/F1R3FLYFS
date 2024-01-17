package ru.serce.jnrfuse.examples;

import casper.CasperMessage.DeployDataProto;
import casper.ServiceErrorOuterClass.ServiceError;
import casper.v1.DeployServiceV1.DeployResponse;
import casper.v1.DeployServiceGrpc.DeployServiceBlockingStub;
import casper.v1.DeployServiceGrpc;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.io.UnsupportedEncodingException;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;


import java.security.Security;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.bouncycastle.util.encoders.Base64;


// These imports should be moved to the top of the file, just after the package declaration.


import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.types.dev_t;
import jnr.ffi.types.gid_t;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import jnr.ffi.types.uid_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;

import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import java.security.SecureRandom;

import com.google.gson.Gson;
import org.web3j.crypto.ECKeyPair;
import org.bouncycastle.asn1.*;

import static jnr.ffi.Platform.OS.WINDOWS;

// Removed imports that are causing compilation errors

public class MemoryFS extends FuseStubFS {

    private static final Gson gson = new Gson();
    private static final byte[] key = "1234567890123456".getBytes(); // 16-byte key for AES
    private static final byte[] iv = new byte[16]; // 16-byte IV for AES (should be initialized securely)

    static {
        // Securely initialize the IV (for example purposes, using random bytes)
        new SecureRandom().nextBytes(iv);
    }

    private byte[] encryptData(byte[] data) {
        try {
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));
            cipher.init(true, new ParametersWithIV(new KeyParameter(key), iv));
            byte[] output = new byte[cipher.getOutputSize(data.length)];
            int outputLength = cipher.processBytes(data, 0, data.length, output, 0);
            outputLength += cipher.doFinal(output, outputLength);
            byte[] result = new byte[outputLength];
            System.arraycopy(output, 0, result, 0, outputLength);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting data", e);
        }
    }

    private byte[] decryptData(byte[] encryptedData) {
        try {
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));
            cipher.init(false, new ParametersWithIV(new KeyParameter(key), iv));
            byte[] output = new byte[cipher.getOutputSize(encryptedData.length)];
            int outputLength = cipher.processBytes(encryptedData, 0, encryptedData.length, output, 0);
            outputLength += cipher.doFinal(output, outputLength);
            byte[] result = new byte[outputLength];
            System.arraycopy(output, 0, result, 0, outputLength);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting data", e);
        }
    }

    private static String executeCommand(String[] command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);

        StringBuilder output = new StringBuilder();
        Process process;
        try {
            process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            StringBuilder errorOutput = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Command exited with code " + exitCode + ": " + errorOutput.toString());
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return e.getMessage();
        }

        //just do a grpc call directly...this is stupid

        return output.toString();
    }

    public void sendGRPC(String jsonPayload) {
        try {
            //String jsonPayload ="{\"deployer\":\"BP/AFleaaAUNZV1V304J8EYFFkVD4lfI5t8QNh5gaKUzZYjps1XqhZxatChaXvDv32K8KLgDIM6Z4muxYHs62T0=\",\"term\":\"{4}\",\"sig\":\"MEQCIAISqeBbReZ4aAi5b+/fc5H7lzQYlP2uz2hATwxwFieDAiAJQ7QBNIlRnp6Eb8tqIjCEN4uUIXERoi9hJGh0TI0kOg==\",\"sigAlgorithm\":\"secp256k1\",\"phloPrice\":\"500\",\"phloLimit\":\"1000\",\"shardId\":\"root\"}";
            //String jsonPayload ="{\"deployer\":\"BP/AFleaaAUNZV1V304J8EYFFkVD4lfI5t8QNh5gaKUzZYjps1XqhZxatChaXvDv32K8KLgDIM6Z4muxYHs62T0=\",\"term\":\"{4}\",\"sig\":\"MEUCIAISqeBbReZ4aAi5b+/fc5H7lzQYlP2uz2hATwxwFieDAiEA9rxL/st2rmFhe5A0ld3PeoMjSMU+Nv4MXq32GIOpHQc=\",\"sigAlgorithm\":\"secp256k1\",\"phloPrice\":\"500\",\"phloLimit\":\"1000\",\"shardId\":\"root\"}";
            String[] command = {
                "grpcurl",
                "-plaintext",
                "-d", jsonPayload,
                "--import-path", "/Users/btcmac/f1r3fly/node/target/protobuf_external",
                "--import-path", "/Users/btcmac/f1r3fly/models/src/main/protobuf",
                "--proto", "DeployServiceV1.proto",
                "127.0.0.1:40401",
                "casper.v1.DeployService.doDeploy"
            };
            String output = executeCommand(command);
            System.out.println(output);
        } catch (Exception e) {
            System.out.println("sendGRPC error: " + e.getStackTrace());
        }
    }

    public String getRhoTest(String data) {
        //grospic value in test wallet is this
        String rhoToSend = "new return(`rho:rchain:deployId`) in {\n  return!((42, true, \"Hello from blockchain!\"))\n}";
        return rhoToSend;
    }

    public String getRhoTemplate(String data) {
        String rhoToSend = "new helloworld, stdout(`rho:io:stdout`) in {\n" + //
                "    contract helloworld( world ) = {\n" + //
                "        for( @msg <- world ) {\n" + //
                "            stdout!(msg)\n" + //
                "        }\n" + //
                "    } |\n" + //
                "    new world, world2 in {\n" + //
                "        helloworld!(*world) |\n" + //
                "        world!(\"Hello World\") |\n" + //
                "        helloworld!(*world2) |\n" + //
                "        world2!(\"$DATA$\")\n" + //
                "    }\n" + //
                "}";

        rhoToSend = rhoToSend.replace("$DATA$", data);
        return rhoToSend;
    }
    
    public String loadFileAsString(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    public String getInsertRho() {
        return "new simpleInsertTest, simpleInsertTestReturnID,\n" +
        "ri(`rho:registry:insertArbitrary`),\n" +
        "stdout(`rho:io:stdout`),\n" +
        "stdoutAck(`rho:io:stdoutAck`) in {\n" +
        "simpleInsertTest!(*simpleInsertTestReturnID) |\n" +
        "contract simpleInsertTest(registryIdentifier) = {\n" +
        "    stdout!(\"REGISTRY_SIMPLE_INSERT_TEST: create arbitrary process X to store in the registry\") |\n" +
        "    new X, Y, innerAck in {\n" +
        "        stdoutAck!(*X, *innerAck) |\n" +
        "        for(_ <- innerAck){\n" +
        "            stdout!(\"REGISTRY_SIMPLE_INSERT_TEST: adding X to the registry and getting back a new identifier\") |\n" +
        "            ri!(*X, *Y) |\n" +
        "            for(@uri <- Y) {\n" +
        "                stdout!(\"@uri <- Y hit\") |\n" +
        "                stdout!(\"REGISTRY_SIMPLE_INSERT_TEST: got an identifier for X from the registry\") |\n" +
        "                stdout!(uri) |\n" +
        "                registryIdentifier!(uri)\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "}\n" +
        "}";
    }

    public String getLookupRho() {
        return "new simpleLookupTest, ack,\n" +
        "rl(`rho:registry:lookup`),\n" +
        "stdout(`rho:io:stdout`),\n" +
        "stdoutAck(`rho:io:stdoutAck`) in {\n" +
        
        "for(@idFromTest1 <- simpleLookupTest) {\n" +
        "    simpleLookupTest!(idFromTest1, *ack)\n" +
        "} |\n" +
        
        "contract simpleLookupTest(@uri, result) = {\n" +
        "    stdout!(\"uri= \" ++ uri) |\n" +
        "    stdout!(\"REGISTRY_SIMPLE_LOOKUP_TEST: looking up X in the registry using identifier\") |\n" +
        "    new lookupResponse in {\n" +
        "        rl!(uri, *lookupResponse) |\n" +
        "        for(@val <- lookupResponse) {\n" +
        "            stdout!(\"ajdksfhasdkfhjkasdfhkjasdfdskjh\") |\n" +
        "            stdout!(\"REGISTRY_SIMPLE_LOOKUP_TEST: got X from the registry using identifier\") |\n" +
        "            stdoutAck!(val, *result)\n" +
        "        }\n" +
        "    }\n" +
        "}\n" +
        "}";
    }


    private class MemoryDirectory extends MemoryPath {
        private List<MemoryPath> contents = new ArrayList<>();

        private MemoryDirectory(String name) {
            super(name);
            System.out.println("MemoryDirectory: " + name);
        }

        private MemoryDirectory(String name, MemoryDirectory parent) {
            super(name, parent);
            System.out.println("MemoryDirectory: " + parent + " > " + name);
        }

        public synchronized void add(MemoryPath p) {
            System.out.println("add MemoryPath: " + p.name);
            contents.add(p);
            p.parent = this;
        }

        private synchronized void deleteChild(MemoryPath child) {
            System.out.println("deleteChild MemoryPath: " + child);
            contents.remove(child);
        }

        @Override
        protected MemoryPath find(String path) {
            //System.out.println("find MemoryPath: " + path);
            if (super.find(path) != null) {
                return super.find(path);
            }
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            synchronized (this) {
                if (!path.contains("/")) {
                    for (MemoryPath p : contents) {
                        if (p.name.equals(path)) {
                            return p;
                        }
                    }
                    return null;
                }
                String nextName = path.substring(0, path.indexOf("/"));
                String rest = path.substring(path.indexOf("/"));
                for (MemoryPath p : contents) {
                    if (p.name.equals(nextName)) {
                        return p.find(rest);
                    }
                }
            }
            return null;
        }

        @Override
        protected void getattr(FileStat stat) {
            stat.st_mode.set(FileStat.S_IFDIR | 0777);
            stat.st_uid.set(getContext().uid.get());
            stat.st_gid.set(getContext().gid.get());
        }

        private synchronized void mkdir(String lastComponent) {
            contents.add(new MemoryDirectory(lastComponent, this));
        }

        public synchronized void mkfile(String lastComponent) {
            System.out.println("mkfile: " + lastComponent);
            contents.add(new MemoryFile(lastComponent, this));
        }

        public synchronized void read(Pointer buf, FuseFillDir filler) {
            for (MemoryPath p : contents) {
                filler.apply(buf, p.name, null, 0);
            }
        }
    }

    private class MemoryFile extends MemoryPath {
        private ByteBuffer contents = ByteBuffer.allocate(0);

        private MemoryFile(String name) {
            super(name);
        }

        private MemoryFile(String name, MemoryDirectory parent) {
            super(name, parent);
        }

        public MemoryFile(String name, String text) {
            super(name);
            try {
                byte[] contentBytes = text.getBytes("UTF-8");
                contents = ByteBuffer.wrap(contentBytes);
            } catch (UnsupportedEncodingException e) {
                System.out.println("UnsupportedEncodingException e: " + e.toString());
                // Not going to happen
            }
        }

        @Override
        protected void getattr(FileStat stat) {
            stat.st_mode.set(FileStat.S_IFREG | 0777);
            stat.st_size.set(contents.capacity());
            stat.st_uid.set(getContext().uid.get());
            stat.st_gid.set(getContext().gid.get());
        }

        private int read(Pointer buffer, long size, long offset) {
            int bytesToRead = (int) Math.min(contents.capacity() - offset, size);
            byte[] bytesRead = new byte[bytesToRead];
            synchronized (this) {
                contents.position((int) offset);
                contents.get(bytesRead, 0, bytesToRead);
                buffer.put(0, bytesRead, 0, bytesToRead);
                contents.position(0); // Rewind
            }
            return bytesToRead;
        }

        private synchronized void truncate(long size) {
            if (size < contents.capacity()) {
                // Need to create a new, smaller buffer
                ByteBuffer newContents = ByteBuffer.allocate((int) size);
                byte[] bytesRead = new byte[(int) size];
                contents.get(bytesRead);
                newContents.put(bytesRead);
                contents = newContents;
            }
        }

        private int write(Pointer buffer, long bufSize, long writeOffset) {
            int maxWriteIndex = (int) (writeOffset + bufSize);
            byte[] bytesToWrite = new byte[(int) bufSize];
            synchronized (this) {
                if (maxWriteIndex > contents.capacity()) {
                    // Need to create a new, larger buffer
                    ByteBuffer newContents = ByteBuffer.allocate(maxWriteIndex);
                    newContents.put(contents);
                    contents = newContents;
                }
                buffer.get(0, bytesToWrite, 0, (int) bufSize);
                contents.position((int) writeOffset);
                contents.put(bytesToWrite);
                contents.position(0); // Rewind
            }
            return (int) bufSize;
        }
    }

    private abstract class MemoryPath {
        protected String name;
        private MemoryDirectory parent;

        private MemoryPath(String name) {
            this(name, null);
        }

        private MemoryPath(String name, MemoryDirectory parent) {
            this.name = name;
            this.parent = parent;
        }

        private synchronized void delete() {
            if (parent != null) {
                parent.deleteChild(this);
                parent = null;
            }
        }

        protected MemoryPath find(String path) {
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.equals(name) || path.isEmpty()) {
                return this;
            }
            return null;
        }

        protected abstract void getattr(FileStat stat);

        private void rename(String newName) {
            while (newName.startsWith("/")) {
                newName = newName.substring(1);
            }
            name = newName;
        }
    }

    //notes for tomorrow meeting:
    //java string creation manually due to proto3 errors
    //still using grpcurl due to errors from proto3
    //timestamp always 0 or invalid signature
    //._ workaround is needed for writes on creation
    //need to figure out how to handle each event being processed (write/read/getattr/etc)

    public String sendProto(String dataString) {
        String privateKeyHex = "5f668a7ee96d944a4494cc947e4005e172d7ab3461ee5538f1f2a45a835e9657";
        BigInteger priv = new BigInteger(privateKeyHex, 16);
        ECKeyPair keyPair = ECKeyPair.create(priv);
        BigInteger privateKey = keyPair.getPrivateKey();
        BigInteger publicKey = keyPair.getPublicKey();
        Security.addProvider(new BouncyCastleProvider());
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        ECDomainParameters domain = new ECDomainParameters(spec.getCurve(), spec.getG(), spec.getN(), spec.getH());
        ECPrivateKeyParameters privateKeyParams = new ECPrivateKeyParameters(priv, domain);
        // Use HMacDSAKCalculator for deterministic ECDSA
        HMacDSAKCalculator kCalculator = new HMacDSAKCalculator(new SHA256Digest());
        ECDSASigner signer = new ECDSASigner(kCalculator);
        signer.init(true, privateKeyParams);

        System.out.println("passed in key= " + privateKeyHex);
        System.out.println("privateKeyVal= " + privateKey.toString(16));
        System.out.println("publicKey= " + publicKey.toString(16));

        String rholangCode = getRhoTemplate(dataString);
        //rholangCode = getInsertRho();
        //rholangCode = getLookupRho();
        // try {
        //     rholangCode = loadFileAsString("/Users/btcmac/f1r3fly/rholang/examples/tut-registry.rho");
        //     System.out.println("FILE LOADED with code:\n" +rholangCode);
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }
        
        DeployDataProto.Builder builder = DeployDataProto.newBuilder();
        DeployDataProto deployData = 
         builder.                                                                                                                                             
            setTerm(rholangCode).
            //setTimestamp(0L). //MUST BE 0 or invalid signature is given
            setPhloPrice(500L).
            setPhloLimit(1000L).                                                                                                                                                          
            setValidAfterBlockNumber(0L).
            setShardId("root").
         build();
        byte [] serializedData = deployData.toByteArray();
        //System.out.println("scala output: ByteVector(17 bytes, 0x12037b347d38f40340e8075a04726f6f74)");
        System.out.println("serializedData = " + printByteArray(serializedData));


        // Hash the serialized data using Blake2b
        Blake2bDigest blake2bDigest = new Blake2bDigest(256);
        blake2bDigest.update(serializedData, 0, serializedData.length);
        byte[] hashedData = new byte[blake2bDigest.getDigestSize()];
        blake2bDigest.doFinal(hashedData, 0);
        //String hashedDataHex = String.format("%02x", new BigInteger(1, hashedData));
        //System.out.println("scala output: ByteVector(32 bytes, 0x16124dcc2e8d61f6826833b73cd3ae184fcbf0e8a79d0e14a207a4be87272b29)");
        //System.out.println("java output: Hashed data (hex): " + hashedDataHex);

        // Sign the hash
        BigInteger[] signature = signer.generateSignature(hashedData);
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new ASN1Integer(signature[0]));
        v.add(new ASN1Integer(signature[1]));
        DERSequence derSequence = new DERSequence(v);
        byte[] derSignature;
        try {
            derSignature = derSequence.getEncoded(ASN1Encoding.DER);
        } catch (IOException e) {
            // Handle the exception according to your needs
            throw new RuntimeException("Failed to encode DER signature", e);
        }
        String sigStr = Base64.toBase64String(derSignature);
        //System.out.println("Signature: " + sigStr);

        //from scala: 
        //val deployer = privKey.publicKey.decompressed
        //deployer = ByteString.copyFrom(deployer.decompressedBytes.toArray)
        //"deployer":"BP/AFleaaAUNZV1V304J8EYFFkVD4lfI5t8QNh5gaKUzZYjps1XqhZxatChaXvDv32K8KLgDIM6Z4muxYHs62T0="
        //sig            = ByteString.copyFrom(signed.bytes.toArray),
        //"sig":    "MEQCIAISqeBbReZ4aAi5b+/fc5H7lzQYlP2uz2hATwxwFieDAiAJQ7QBNIlRnp6Eb8tqIjCEN4uUIXERoi9hJGh0TI0kOg=="

        //convert the proto to java...need to do it this way because of the proto3 option errors i was getting
        Map<String, String> values = new HashMap<>();
        values.put("deployer", "BP/AFleaaAUNZV1V304J8EYFFkVD4lfI5t8QNh5gaKUzZYjps1XqhZxatChaXvDv32K8KLgDIM6Z4muxYHs62T0=");
        values.put("term", deployData.getTerm());
        values.put("sig", sigStr);
        values.put("sigAlgorithm", "secp256k1");
        values.put("phloPrice", deployData.getPhloPrice()+"");
        values.put("phloLimit", deployData.getPhloLimit()+"");
        values.put("shardId", "root");
         String jsonString = gson.toJson(values);
         //System.out.println(jsonString);
         sendGRPC(jsonString);

        return sigStr;
    }

    //         /*
//          * 
//          * 
//          * sig            = ByteString.copyFrom(signed.bytes.toArray),
//          * "sig":"MEQCIAISqeBbReZ4aAi5b+/fc5H7lzQYlP2uz2hATwxwFieDAiAJQ7QBNIlRnp6Eb8tqIjCEN4uUIXERoi9hJGh0TI0kOg=="
//     deployer       = ByteString.copyFrom(deployer.decompressedBytes.toArray)
//     "deployer":"BP/AFleaaAUNZV1V304J8EYFFkVD4lfI5t8QNh5gaKUzZYjps1XqhZxatChaXvDv32K8KLgDIM6Z4muxYHs62T0="


//          * 
//          * val serialized   = ByteVector(projection.toByteArray)
//   println(serialized)
//                        1712037b347d38f40340e8075a04726f6f74
//   ByteVector(17 bytes, 0x12037b347d38f40340e8075a04726f6f74)
//   val deployer     = privKey.publicKey.decompressed
//   println(deployer)
//   ECPublicKey(03ffc016579a68050d655d55df4e09f04605164543e257c8e6df10361e6068a533)
//   val digest       = ByteVector(Blake2b256.hash(serialized.toArray))
//   println(digest)
//   ByteVector(32 bytes, 0x16124dcc2e8d61f6826833b73cd3ae184fcbf0e8a79d0e14a207a4be87272b29)
//   val signed       = privKey.sign(digest)
//   println(signed)
//   ECDigitalSignature(304402200212a9e05b45e6786808b96fefdf7391fb97341894fdaecf68404f0c7016278302200943b4013489519e9e846fcb6a223084378b94217111a22f612468744c8d243a)
//          * 
//          * 


//          * 
//          * 
//          * 
//          * 
//          */

// //         @ signDeployJSON("5f668a7ee96d944a4494cc947e4005e172d7ab3461ee5538f1f2a45a835e9657",res7)
// // {"deployer":"BP/AFleaaAUNZV1V304J8EYFFkVD4lfI5t8QNh5gaKUzZYjps1XqhZxatChaXvDv32K8KLgDIM6Z4muxYHs62T0=","term":"{4}","sig":"MEQCIAISqeBbReZ4aAi5b+/fc5H7lzQYlP2uz2hATwxwFieDAiAJQ7QBNIlRnp6Eb8tqIjCEN4uUIXERoi9hJGh0TI0kOg==","sigAlgorithm":"secp256k1","phloPrice":"500","phloLimit":"1000","shardId":"root"}
// //MEQCIBBP+ugdI9Rxi4UqqQHf2YnG+JRUX7gkBxDk67Ffl5etAiAxcFUmlc9WYhhn3/lpb0RRiXbuHeF9/BAr2ESw/zYPew==

// //Private key	5f668a7ee96d944a4494cc947e4005e172d7ab3461ee5538f1f2a45a835e9657
// //Public key	04ffc016579a68050d655d55df4e09f04605164543e257c8e6df10361e6068a5336588e9b355ea859c5ab4285a5ef0efdf62bc28b80320ce99e26bb1607b3ad93d
// //ETH	fac7dde9d0fa1df6355bd1382fe75ba0c50e8840


    //NOT WORKING DUE TO PROTO3 AUTO GENERATED CODE ERRORS
    //HOWEVER THIS IS WHAT WE SHOULD BE USING TO ACTUALLY SEND GRPC CALLS
    public static void sendDeploy() {
        // Create a channel to the server
        System.out.println("sendDeploy");
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 40401)
                .usePlaintext()
                .build();

        System.out.println("channel = " + channel);
        // Create a blocking stub on the channel
        DeployServiceBlockingStub blockingStub = DeployServiceGrpc.newBlockingStub(channel);
        System.out.println("blockingStub = " + blockingStub);

        try {
            DeployDataProto.Builder builder = DeployDataProto.newBuilder();
            System.out.println("builder = " + builder);
            DeployDataProto deployData = 
            builder.                                                                                                                                             
                setTerm("{4}").
                setTimestamp(0L).
                setPhloPrice(500L).
                setPhloLimit(1000L).                                                                                                                                                          
                setValidAfterBlockNumber(0L).
                setShardId("root").
            build();

            System.out.println("deployData = " + deployData);

            // Make the call using the stub
            DeployResponse response = blockingStub.doDeploy(deployData);
            System.out.println("response = " + response);

            // Check if the response has an error
            if (response.hasError()) {
                // Handle the error case
                ServiceError error = response.getError();
                System.err.println("Deploy failed: " + error);
            } else if (response.hasResult()) {
                // Process the successful response
                String result = response.getResult();
                System.out.println("Deploy succeeded with result: " + result);
            } else {
                // Handle the case where neither error nor result is set
                System.err.println("Deploy response did not contain error or result.");
            }
        } catch (Exception e) {
            System.err.println("RPC failed: " + e.getMessage());
            e.printStackTrace();
        }

        // Shutdown the channel
        channel.shutdown();
    }

    public static void main(String[] args) throws IOException {
        MemoryFS memfs = new MemoryFS();
        try {
            String path;
            switch (Platform.getNativePlatform().getOS()) {
                case WINDOWS:
                    path = "J:\\";
                    break;
                default:
                    path = "/tmp/mntm";
            }
            
            memfs.mount(Paths.get(path), true, false);
        } finally {
            memfs.umount();
        }
    }

    private MemoryDirectory rootDirectory = new MemoryDirectory("mountedFromJava");

    public MemoryFS() throws IOException {
        // Sprinkle some files around
        rootDirectory.add(new MemoryFile("Sample file.txt", "Hello there, feel free to look around.\n"));
        rootDirectory.add(new MemoryDirectory("Sample directory"));
        MemoryDirectory dirWithFiles = new MemoryDirectory("Directory with files");
        rootDirectory.add(dirWithFiles);
        dirWithFiles.add(new MemoryFile("hello.txt", "This is some sample text.\n"));
        dirWithFiles.add(new MemoryFile("hello again.txt", "This another file with text in it! Oh my!\n"));
        MemoryDirectory nestedDirectory = new MemoryDirectory("Sample nested directory");
        dirWithFiles.add(nestedDirectory);
        nestedDirectory.add(new MemoryFile("So deep.txt", "Man, I'm like, so deep in this here file structure.\n"));
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        if (getPath(path) != null) {
            return -ErrorCodes.EEXIST();
        }
        MemoryPath parent = getParentPath(path);
        //System.out.println("create() called with arguments: path = " + path + ", mode = " + mode + ", fi = " + fi);
        //System.out.println("parent: " + parent.toString());
        if (parent instanceof MemoryDirectory) {
            System.out.println("is instance");
            ((MemoryDirectory) parent).mkfile(getLastComponent(path));
            return 0;
        }
        System.out.println("ERROR: " + ErrorCodes.ENOENT());
        return -ErrorCodes.ENOENT();
    }


    @Override
    public int getattr(String path, FileStat stat) {
        MemoryPath p = getPath(path);
        if (p != null) {
            System.out.println("getattr() called with arguments: path = " + path);// + ", stat = " + stat);
            p.getattr(stat);
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }

    public String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ""; // No extension found
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    private String getLastComponent(String path) {
        while (path.substring(path.length() - 1).equals("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.substring(path.lastIndexOf("/") + 1);
    }

    private MemoryPath getParentPath(String path) {
        return rootDirectory.find(path.substring(0, path.lastIndexOf("/")));
    }

    private MemoryPath getPath(String path) {
        return rootDirectory.find(path);
    }


    @Override
    public int mkdir(String path, @mode_t long mode) {
        System.out.println("mkdir() called with arguments: path = " + path + ", mode = " + mode);
        if (getPath(path) != null) {
            return -ErrorCodes.EEXIST();
        }
        MemoryPath parent = getParentPath(path);
        if (parent instanceof MemoryDirectory) {
            ((MemoryDirectory) parent).mkdir(getLastComponent(path));
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }


    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryFile)) {
            return -ErrorCodes.EISDIR();
        }
        System.out.println("read() called with arguments: path = " + path);// + ", buf = " + buf + ", size = " + size + ", offset = " + offset + ", fi = " + fi);
        return ((MemoryFile) p).read(buf, size, offset);
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryDirectory)) {
            return -ErrorCodes.ENOTDIR();
        }
        filter.apply(buf, ".", null, 0);
        filter.apply(buf, "..", null, 0);
        System.out.println("readdir() called with arguments: path = " + path);// + ", buf = " + buf + ", filter = " + filter + ", offset = " + offset + ", fi = " + fi);
        ((MemoryDirectory) p).read(buf, filter);
        return 0;
    }


    @Override
    public int statfs(String path, Statvfs stbuf) {
        if (Platform.getNativePlatform().getOS() == WINDOWS) {
            // statfs needs to be implemented on Windows in order to allow for copying
            // data from other devices because winfsp calculates the volume size based
            // on the statvfs call.
            // see https://github.com/billziss-gh/winfsp/blob/14e6b402fe3360fdebcc78868de8df27622b565f/src/dll/fuse/fuse_intf.c#L654
            if ("/".equals(path)) {
                stbuf.f_blocks.set(1024 * 1024); // total data blocks in file system
                stbuf.f_frsize.set(1024);        // fs block size
                stbuf.f_bfree.set(1024 * 1024);  // free blocks in fs
            }
        }
        //called all the time...too much spam on printouts
        //System.out.println("statfs() called with arguments: path = " + path);// + ", stbuf = " + stbuf);
        //can now drag and drop in Finder UI with these options set
        stbuf.f_blocks.set(1024 * 1024 * 2);    // total data blocks in file system
        stbuf.f_bsize.set(1024);                // file system block size
        stbuf.f_frsize.set(1024);               // fundamental fs block size
        stbuf.f_bfree.set(1024 * 1024 * 2);     // free blocks in fs
        stbuf.f_bavail.set(1024 * 1024 * 2);    // free blocks available to unprivileged user
        return super.statfs(path, stbuf);
    }

    @Override
    public int rename(String path, String newName) {
        System.out.println("rename() called with arguments: path = " + path + ", newName = " + newName);
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        MemoryPath newParent = getParentPath(newName);
        if (newParent == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(newParent instanceof MemoryDirectory)) {
            return -ErrorCodes.ENOTDIR();
        }
        p.delete();
        p.rename(newName.substring(newName.lastIndexOf("/")));
        ((MemoryDirectory) newParent).add(p);
        return 0;
    }

    @Override
    public int rmdir(String path) {
        System.out.println("rmdir() called with argument: path = " + path);
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryDirectory)) {
            return -ErrorCodes.ENOTDIR();
        }
        p.delete();
        return 0;
    }

    @Override
    public int truncate(String path, long offset) {
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryFile)) {
            return -ErrorCodes.EISDIR();
        }
        System.out.println("truncate() called with arguments: path = " + path + ", offset = " + offset);
        ((MemoryFile) p).truncate(offset);
        return 0;
    }

    @Override
    public int unlink(String path) {
        System.out.println("unlink() called with argument: path = " + path);
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        p.delete();
        return 0;
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        System.out.println("open() called with arguments: path = " + path);// + ", fi = " + fi);
        return 0;
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryFile)) {
            return -ErrorCodes.EISDIR();
        }

        System.out.println("write() called with arguments: path = " + path + /* ", buf = " + buf + */ ", size = " + size);// + ", offset = " + offset + ", fi = " + fi);
        byte[] data = new byte[(int)size];
        buf.get(0, data, 0, data.length);
        //data can now be passed into rho
        String dataString = "";
        dataString = new String(data, StandardCharsets.UTF_8);

        //problems: 
        //get multiple writes() per file...2 different names
        //._filename.txt and filename.txt for example
        //even filename.txt named correctly gets 2 events
        //i can read the file data from the buf value
        //in theory i could send the raw bytes onto the rchain node
        //need a contract that can take a directory string for the path and the filename
        //the contract should take the directory+filename string and store the data there
        //when trying to open a file it should do the reverse and retrieve the data
        //all those events will require more rholang hits to get directories and pass data
        //still dont know if i can even have the ui work with it
        //might be able to demo one laptop creating and saving a file
        //the other laptop running the same software connected to the node could pull it down
        //drag and drop i only get the ._filename one due to the disk full error from timestamp
        String fName = getLastComponent(path);
        System.out.println("fName = " + fName);
        if (!fName.contains("._")) {

            String fileType = getFileExtension(fName);
            System.out.println("fileType = " + fileType);

            if(fileType.equals("rho")) {
                dataString = new String(data, StandardCharsets.UTF_8);

            } else if(fileType.equals("metta")) {
                dataString = new String(data, StandardCharsets.UTF_8);
                //convert to rho?
            } else if (fileType.equals("encrypted")) {
                dataString = new String(data, StandardCharsets.UTF_8);
                System.out.println("not encrypted dataString = " + printByteArray(data));
                byte[] encryptedData = encryptData(data);
                System.out.println("encrypted dataString = " + printByteArray(encryptedData));
                try {
                    dataString = new String(encryptedData, "UTF-8");
                    System.out.println("encrypted dataString 2 = " + printByteArray(encryptedData));
                } catch (UnsupportedEncodingException e) {
                    System.out.println("unable to encrypt data: " + e.getStackTrace());
                }
                byte[] decryptedData = decryptData(encryptedData);
                System.out.println("decryptedData dataString = " + printByteArray(decryptedData));
            } else {
                dataString = new String(data, StandardCharsets.UTF_8);
            }

            System.out.println("dataString = " + dataString);
            // if (dataString.endsWith("\n")) {
            //     dataString = dataString.substring(0, dataString.length() - 1);
            // }
            sendProto(dataString);
        }

        System.out.println("WRITING: " + size);
        return ((MemoryFile) p).write(buf, size, offset);
    }

    @Override
     public int readlink(String path, Pointer buf, @size_t long size) {
         System.out.println("readlink() called with path: " + path);
         return -ErrorCodes.ENOSYS();
     }

     @Override
     public int symlink(String oldpath, String newpath) {
         System.out.println("symlink() called with oldpath: " + oldpath + ", newpath: " + newpath);
         return -ErrorCodes.ENOSYS();
     }

     @Override
     public int link(String oldpath, String newpath) {
         System.out.println("link() called with oldpath: " + oldpath + ", newpath: " + newpath);
         return -ErrorCodes.ENOSYS();
     }

     @Override
     public int mknod(String path, @mode_t long mode, @dev_t long rdev) {
         System.out.println("mknod() called with path: " + path);
         return -ErrorCodes.ENOSYS();
     }

     @Override
     public int chmod(String path, @mode_t long mode) {
         System.out.println("chmod() called with path: " + path);
         return -ErrorCodes.ENOSYS();
     }

     @Override
     public int chown(String path, @uid_t long uid, @gid_t long gid) {
         System.out.println("chown() called with path: " + path);
         return -ErrorCodes.ENOSYS();
     }

     @Override
     public int bmap(String path, @size_t long blocksize, long idx) {
         System.out.println("bmap() called with path: " + path);
         return -ErrorCodes.ENOSYS();
     }

     public String getFilePath(String fName) {
        return System.getProperty("user.home") + "/f1r3fly/rholang/examples/"+fName+".rho";
     }

     public String printByteArray(byte[] byteArray) {
        StringBuilder serializedStr = new StringBuilder();
        for (byte b : byteArray) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                serializedStr.append('0');
            }
            serializedStr.append(hex);
        }
        return serializedStr.toString();
     }

     public void saveStringToFile(String filePath, String content) {
         try (PrintWriter out = new PrintWriter(filePath)) {
             out.println(content);
         } catch (FileNotFoundException e) {
             e.printStackTrace();
         }
     }
    
}
