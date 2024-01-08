package ru.serce.jnrfuse.examples;

import casper.CasperMessage.DeployDataProto;
import casper.ServiceErrorOuterClass.ServiceError;
import casper.v1.DeployServiceV1.DeployResponse;
import casper.v1.DeployServiceV1;
import casper.v1.DeployServiceGrpc.DeployServiceBlockingStub;
import casper.v1.DeployServiceGrpc;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.google.protobuf.ByteString;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jcajce.provider.digest.Blake2b.Blake2b256;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;


import java.security.Security;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECParameterSpec;
import java.security.interfaces.ECPrivateKey;
import java.math.BigInteger;
import java.security.spec.X509EncodedKeySpec;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
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
import ru.serce.jnrfuse.struct.DeployData;
import ru.serce.jnrfuse.struct.DeployDataRequest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Signature;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.encoders.HexEncoder;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;
import org.bouncycastle.crypto.ec.ECPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.asn1.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
//import java.nio.charset.StandardCharsets;

import static jnr.ffi.Platform.OS.WINDOWS;

// Removed imports that are causing compilation errors

public class MemoryFS extends FuseStubFS {

    private static final String RCHAIN_NODE_URL = "localhost"; // Replace with actual RChain node URL
    private static final int RCHAIN_NODE_PORT = 40401; // Replace with actual RChain node port

    public void sendRholangCode(String path, String data) throws IOException {

        //System.out.println("sendRholangCode: " + data.toString());
            String fName = getLastComponent(path);
            String fPath = System.getProperty("user.home") + "/f1r3fly/rholang/examples/"+ fName +".rho"; //change to path..probably not for demo as long as storage works can reuse files?
            saveStringToFile(fPath, getRhoTemplate(data));

            sendHttpPost(data);
        
            //old rholang send code...now using http hopefully
        // try{
        //     //System.out.println("sendRholangCode: " + data.toString());
        //     String fName = getLastComponent(path);
        //     String fPath = System.getProperty("user.home") + "/f1r3fly/rholang/examples/"+ fName +".rho"; //change to path..probably not for demo as long as storage works can reuse files?
        //     saveStringToFile(fPath, getRhoTemplate(data));

        //UPDATE THIS TO POINT TO CURRENT WORKING OUTPUT BIN FILE
        //     String binaryPath = System.getProperty("user.home") + "/f1r3fly/node/target/universal/stage/bin/rnode";
        //     ProcessBuilder processBuilder = new ProcessBuilder(binaryPath, "eval", fPath);
        //     //ProcessBuilder processBuilder = new ProcessBuilder(binaryPath, "repl", "{}");

        //     File binaryDirectory = new File(binaryPath).getParentFile();
        //     processBuilder.directory(binaryDirectory);
        //     Process process = processBuilder.start();

        //     // Read the output stream
        //     try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        //         String line;
        //         while ((line = outputReader.readLine()) != null) {
        //             System.out.println(line);
        //         }
        //     }

        //     // Read the error stream
        //     try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
        //         String line;
        //         while ((line = errorReader.readLine()) != null) {
        //             System.err.println(line);
        //         }
        //     }

        //     int exitCode = process.waitFor();
        //     System.out.println("Process exited with code: " + exitCode);
            
        //     } catch (Exception e) {
        //         System.out.println("error: " + e.getStackTrace());
        //     }
     }

     public void getFileName() {

     }

     /*

     //from caspermessage.scala ...this is the data type that is passed in on API http side
      final case class DeployData(
    term: String,
    timestamp: Long,
    phloPrice: Long,
    phloLimit: Long,
    validAfterBlockNumber: Long,
    shardId: String
) {
  def totalPhloCharge = phloLimit * phloPrice
}
      */

     public void sendGRPC() {
        String jsonPayload = "{\"deployer\":\"BP/AFleaaAUNZV1V304J8EYFFkVD4lfI5t8QNh5gaKUzZYjps1XqhZxatChaXvDv32K8KLgDIM6Z4muxYHs62T0= ,\"term\":\"{4}\",\"sig\":\"MEQCIAISqeBbReZ4aAi5b+/fc5H7lzQYlP2uz2hATwxwFieDAiAJQ7QBNIlRnp6Eb8tqIjCEN4uUI Roi9hJGh0TI0kOg==\",\"sigAlgorithm\":\"secp256k1\",\"phloPrice\":\"500\",\"phloLimit\":\"1000\",\"shardId :\"root\"}";
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

     }

     public static DeployDataProto signDeploy(ECKey privKey, DeployDataProto deploy) {
        // Create a projection of only the fields used to validate the signature
        DeployDataProto.Builder projectionBuilder = DeployDataProto.newBuilder()
            .setTerm(deploy.getTerm())
            .setTimestamp(deploy.getTimestamp())
            .setPhloPrice(deploy.getPhloPrice())
            .setPhloLimit(deploy.getPhloLimit())
            .setValidAfterBlockNumber(deploy.getValidAfterBlockNumber())
            .setShardId(deploy.getShardId());

        // Serialize the projection
        byte[] serialized = projectionBuilder.build().toByteArray();

        // Get the public key bytes
        byte[] deployer = privKey.getPubKey();

        // Hash the serialized projection
        byte[] digest = Sha256Hash.hash(serialized);

        // Sign the hash
        ECKey.ECDSASignature signature = privKey.sign(Sha256Hash.wrap(digest));

        // Set the signature and deployer fields in the projection
        projectionBuilder.setSigAlgorithm("secp256k1")
            .setSig(ByteString.copyFrom(signature.encodeToDER()))
            .setDeployer(ByteString.copyFrom(deployer));

        // Build the final DeployDataProto with the signature
        return projectionBuilder.build();
    }

     public void sendHttpPost(String code) throws IOException {
        //  try {
        //      // Generate a new RSA key pair (or use an existing one)
        //      KeyPair keyPair = generateKeyPair();

        //      // Serialize the deploy data (this is a placeholder for actual serialization logic)
        //      String serializedDeployData = "Serialized deploy data";

        //      // Sign the serialized deploy data
        //      String signature = signData(serializedDeployData, keyPair.getPrivate());

        //      // Create the JSON payload with the signed deploy data (this is a placeholder for actual payload creation)
        //      String jsonPayload = "{ \"deployData\": \"" + serializedDeployData + "\", \"signature\": \"" + signature + "\" }";

        //      // Rest of the sendHttpPost method...
        //  } catch (Exception e) {
        //      e.printStackTrace();
        //  }
        HttpClient client = HttpClient.newHttpClient();

        //sending simplest code now just to test
        String termVal = getRhoTest(code);

        //eventually general idea is that you could call a template like this and it would deploy
        //String termVal = getRhoTemplate(code);

        //ANTON's would have a shardID of sandbox_1 --GREG make note if testing this functionality
        //current people using 'working' built has the value as root
        //otherwise this json should match up with grospic's wallet but it rejects it
        //with this error:
        /* 
         * 
         * Response status code: 400
Response body: "Invalid message body: Could not decode JSON: {\n  \"term\" : \"{4}\",\n  \"phloLimit\" : \"1\",\n  \"phloPrice\" : \"1\",\n  \"validAfterBlockNumber\" : \"1\",\n  \"timestamp\" : \"1702112802454\",\n  \"shardId\" : \"root\"\n}..."
         * 
        */
        //need to fix this call to be able to send it over...dont know whats wrong
        //String json = "{\"term\":\"" + termVal + "\", \"phloLimit\":\"" + 1 + "\", \"phloPrice\":\"" + 1 + "\", \"validAfterBlockNumber\":\"" + 1 + "\", \"timestamp\":\"" + System.currentTimeMillis() + "\", \"shardId\":\"" + "root" + "\"}";
        
        String json = createDeployDataRequest(termVal);

        System.out.println("json being sent: " + json.toString());

        //trying to figure out how to pair it with grospic wallet on rnode-client-js
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + 40403 + "/api/deploy"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        //this works...default API status check
        // HttpRequest request = HttpRequest.newBuilder()
        //         .uri(URI.create("http://localhost:" + 40403 + "/status"))
        //         .header("Content-Type", "application/json")
        //         .GET()
        //         .build();

        System.out.println("request: " + request.toString());
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
            System.out.println("Response status code: " + response.statusCode());
            System.out.println("Response body: " + response.body());
        } catch (ConnectException e) {
            System.err.println("Failed to connect to the server. Please check if the server is running and accessible.");
            // Log the error message for debugging purposes
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An error occurred while sending the HTTP request.");
            // Log the error message for debugging purposes
            e.printStackTrace();
        }
    }

    public String createDeployDataRequest(String term) throws IOException
    {

        //from rnode-openapi.json in f1r3fly repo
        /*
         * 
         "DeployRequest": {
      "type": "object",
      "required": ["data", "deployer", "sigAlgorithm", "signature"],
      "properties": {
        "data": {
          "$ref": "#/definitions/DeployData"
        },
        "deployer": {
          "type": "string",
          "format": "public key"
        },
        "signature": {
          "type": "string",
          "format": "signature"
        },
        "sigAlgorithm": {
          "type": "string",
          "enum": ["secp256k1", "secp256k1:eth"]
        }
      },
      "example": {
        "data": {
          "term": "new world in {\n  world!(\"Hello!\")\n}\n",
          "timestamp": 1600740233668,
          "phloPrice": 1,
          "phloLimit": 250000,
          "validAfterBlockNumber": 420299
        },
        "sigAlgorithm": "secp256k1:eth",
        "signature": "1f80ccdc2517d842e67b913f656357b3f7a54a3f7c993f6df98063417d0c680f72a666f2e8a6cb38d0591740adcbded9ad7449d26b6def78a0113e77124f96d41b",
        "deployer": "043c9c39d032925384f25413d553e91c261555384589329595e9c6956055719b54839704948477e4f0d4743cfdf1635bd497fa44995cea1c5c75971cf779da11b0"
      },
      "description": "DeployRequest"
    },
         * 
         */

         /*
          
        example from grospic wallet:
        Private key	84d1e11a81a7f1fd5a18f869573ae54123756aec5696ffb5e3f465e4ae01adf5
        Public key	04ec4d865367d9a4ce49db154b48823db817fd5574571053c006df74ff95fbf95784f89e3fefaf757a7106c4d23595db742df07a4edfdaa66a15ccfa24db8f092e
        ETH	052fd8959e1103c0903a5bededabd90bb6c49e06
        REV	11112r5BTTrR89JG64yS4LKJo6RpAxWDUc8eMDK1LBMNmLQV2v8zs8 

        anton's setup:
        BOOTSTRAP
        PRIVATE_KEY=34d969f43affa8e5c47900e6db475cb8ddd8520170ee73b2207c54014006ff2b
        PUB_KEY=04c5dfd5ab6ea61de1de4c307454fd95dbeb5399fd1a79ab67e2ed3436f153615ede974205b863bbe7b0dadfb6b308ea3307560ea2c41b774b9907fcad72e52c9b
        ETH_ADDRESS=07e36b04ed27e95fda8662358bddd95452872023
        VALIDATOR1
        PRIVATE_KEY=016120657a8f96c8ee5c50b138c70c66a2b1366f81ea41ae66065e51174e158e
        PUB_KEY=042b02e3069f5aaa09fc856d16abbf43a8f3cd45f8fa8889e4a2744ffd14f418a398945ec5ea08603c3726e794e9b936c3d45894fdb9f2df5591bdaea6607e6b0a
        ETH_ADDRESS=4349f17f7af650e819d84832e340795c8aa532a0
        VALIDATOR2
        PRIVATE_KEY=304b2893981c36122a687c1fd534628d6f1d4e9dd8f44569039ea762dae2d3e7
        PUB_KEY=04a98a4c7fceb7caec0bd5c1774e5307aad7f4c4a14ec6472cea4b1d262d08bfec683e0a15d5f78c5040405be3b469889b059e2986d55b239077be0d49aec8a85b
        ETH_ADDRESS=8c2013c7c7d227d18321852199b05990b4c21510
        */

        String pubKey = "04c5dfd5ab6ea61de1de4c307454fd95dbeb5399fd1a79ab67e2ed3436f153615ede974205b863bbe7b0dadfb6b308ea3307560ea2c41b774b9907fcad72e52c9b";
        String sigVal = "";
        Gson gson = new Gson();

        // Create the DeployData object
        DeployData deployData = new DeployData();
        deployData.setTerm(term);
        deployData.setTimestamp(System.currentTimeMillis());
        deployData.setPhloPrice(1);
        deployData.setPhloLimit(1);
        deployData.setValidAfterBlockNumber(1);
        deployData.setShardId("root");

        String privKeyHex = "34d969f43affa8e5c47900e6db475cb8ddd8520170ee73b2207c54014006ff2b";
        JsonObject serializedDeployData = gson.toJsonTree(deployData).getAsJsonObject();
        System.out.println("calling signJsonWithSecp256k1wBlake");
        //sigVal = signJsonWithSecp256k1wBlake(serializedDeployData, privKeyHex);

        String jsonPayload = "{ \"deployData\": \"" + serializedDeployData + "\", \"signature\": \"" + sigVal + "\" }";
        System.out.println("jsonPayload: \n" + jsonPayload);

        // Create the DeployRequest object
        DeployDataRequest deployDataRequest = new DeployDataRequest();
        deployDataRequest.setData(deployData);
        deployDataRequest.setDeployer(pubKey);
        deployDataRequest.setSignature(sigVal);
        //if i use secp256k it gives me an error "Signature algorithm not supported."
        //so both secp256k1:eth and secp256k1 options are valid just like grospic wallet
        //it says the error is "Invalid signature." so the actual signing must be off
        deployDataRequest.setSigAlgorithm("secp256k1");

        // Convert the DeployRequest object to JSON using Gson
        return gson.toJson(deployDataRequest);
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

        public Map<String, String> processWalletsFile(String walletsFilePath) throws IOException {
            Map<String, String> wallets = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(walletsFilePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        String publicKey = parts[0].trim();
                        String balance = parts[1].trim();
                        wallets.put(publicKey, balance);
                    }
                }
            }
            return wallets;
        }

        public Map<String, String> processBondsFile(String bondsFilePath) throws IOException {
            Map<String, String> bonds = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(bondsFilePath))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        String publicKey = parts[0].trim();
                        String bondAmount = parts[1].trim();
                        bonds.put(publicKey, bondAmount);
                    }
                }
            }
            return bonds;
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


    // public static ECPrivateKey generatePrivateKey(String privateKeyHex) throws Exception {
    //     BigInteger privKeyInt = new BigInteger(privateKeyHex, 16);
    //     ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
    //     ECParameterSpec ecSpec = new ECParameterSpec(spec.getCurve(), spec.getG(), spec.getN(),spec.getH());
    //     ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(privKeyInt, ecSpec);
    //     KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");
    //     return (ECPrivateKey) keyFactory.generatePrivate(privateKeySpec);
    // }

    public static String signProto2(String privateKeyHex) {
        BigInteger priv = new BigInteger(privateKeyHex, 16);
        //BigInteger pubKey = Sign.publicKeyFromPrivate(priv);
        //ECKeyPair keyPair = new ECKeyPair(priv, pubKey);
        ECKeyPair keyPair = ECKeyPair.create(priv);
        BigInteger privateKey = keyPair.getPrivateKey();
        BigInteger publicKey = keyPair.getPublicKey();
        //ECPrivateKey ecpk = new ECPrivateKey(priv);
        //BigInteger privateKey1 = ecpk.getKey();

        // Security.addProvider(new BouncyCastleProvider());
        // ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        // ECDomainParameters domain = new ECDomainParameters(spec.getCurve(), spec.getG(), spec.getN(), spec.getH());
        // ECPrivateKeyParameters privateKeyParams = new ECPrivateKeyParameters(priv, domain);
        // ECDSASigner signer = new ECDSASigner();
        // signer.init(true, privateKeyParams);

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
        // System.out.println("privateKey1= " + privateKey1);
        // System.out.println("publicKey1= " + ecpk.getPublicKey().toString());
        
        DeployDataProto.Builder builder = DeployDataProto.newBuilder();
        DeployDataProto deployData = 
         builder.                                                                                                                                             
            setTerm("{4}").
            setTimestamp(0L).
            setPhloPrice(500L).
            setPhloLimit(1000L).                                                                                                                                                          
            setValidAfterBlockNumber(0L).
            setShardId("root").
         build();
        byte [] serializedData = deployData.toByteArray();
        StringBuilder serializedStr = new StringBuilder();
        for (byte b : serializedData) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                serializedStr.append('0');
            }
            serializedStr.append(hex);
        }
        System.out.println("scala output: ByteVector(17 bytes, 0x12037b347d38f40340e8075a04726f6f74)");
        System.out.println("serializedData = " + serializedStr.toString());


        // Hash the serialized data using Blake2b
        Blake2bDigest blake2bDigest = new Blake2bDigest(256);
        blake2bDigest.update(serializedData, 0, serializedData.length);
        byte[] hashedData = new byte[blake2bDigest.getDigestSize()];
        blake2bDigest.doFinal(hashedData, 0);
        String hashedDataHex = String.format("%02x", new BigInteger(1, hashedData));
        System.out.println("scala output: ByteVector(32 bytes, 0x16124dcc2e8d61f6826833b73cd3ae184fcbf0e8a79d0e14a207a4be87272b29)");
        System.out.println("java output: Hashed data (hex): " + hashedDataHex);
        //up to this point looks correct

                //scala has this
        //val deployer = privKey.publicKey.decompressed
        //deployer = ByteString.copyFrom(deployer.decompressedBytes.toArray)
        //outputs this: 
        //"deployer":"BP/AFleaaAUNZV1V304J8EYFFkVD4lfI5t8QNh5gaKUzZYjps1XqhZxatChaXvDv32K8KLgDIM6Z4muxYHs62T0="
        


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
        //from scala: 
        //sig            = ByteString.copyFrom(signed.bytes.toArray),
        //"sig":    "MEQCIAISqeBbReZ4aAi5b+/fc5H7lzQYlP2uz2hATwxwFieDAiAJQ7QBNIlRnp6Eb8tqIjCEN4uUIXERoi9hJGh0TI0kOg=="
        //Signature: MEUCIAISqeBbReZ4aAi5b+/fc5H7lzQYlP2uz2hATwxwFieDAiEA9rxL/st2rmFhe5A0ld3PeoMjSMU+Nv4MXq32GIOpHQc=

        System.out.println("Signature: " + sigStr);
        return sigStr;
    }
    public static String signProto(String privateKeyHex) {
        // Decode the private key from Satoshis Base58 variant. If 51 characters long then it's from Bitcoins
        // dumpprivkey command and includes a version byte and checksum, or if 52 characters long then it has 
        // compressed pub key. Otherwise assume it's a raw key.

        /*
         * 
         * 
         * sig            = ByteString.copyFrom(signed.bytes.toArray),
         * "sig":"MEQCIAISqeBbReZ4aAi5b+/fc5H7lzQYlP2uz2hATwxwFieDAiAJQ7QBNIlRnp6Eb8tqIjCEN4uUIXERoi9hJGh0TI0kOg=="
    deployer       = ByteString.copyFrom(deployer.decompressedBytes.toArray)
    "deployer":"BP/AFleaaAUNZV1V304J8EYFFkVD4lfI5t8QNh5gaKUzZYjps1XqhZxatChaXvDv32K8KLgDIM6Z4muxYHs62T0="


         * 
         * val serialized   = ByteVector(projection.toByteArray)
  println(serialized)
                       1712037b347d38f40340e8075a04726f6f74
  ByteVector(17 bytes, 0x12037b347d38f40340e8075a04726f6f74)
  val deployer     = privKey.publicKey.decompressed
  println(deployer)
  ECPublicKey(03ffc016579a68050d655d55df4e09f04605164543e257c8e6df10361e6068a533)
  val digest       = ByteVector(Blake2b256.hash(serialized.toArray))
  println(digest)
  ByteVector(32 bytes, 0x16124dcc2e8d61f6826833b73cd3ae184fcbf0e8a79d0e14a207a4be87272b29)
  val signed       = privKey.sign(digest)
  println(signed)
  ECDigitalSignature(304402200212a9e05b45e6786808b96fefdf7391fb97341894fdaecf68404f0c7016278302200943b4013489519e9e846fcb6a223084378b94217111a22f612468744c8d243a)
         * 
         * 


         * 
         * 
         * 
         * 
         */

//         @ signDeployJSON("5f668a7ee96d944a4494cc947e4005e172d7ab3461ee5538f1f2a45a835e9657",res7)
// {"deployer":"BP/AFleaaAUNZV1V304J8EYFFkVD4lfI5t8QNh5gaKUzZYjps1XqhZxatChaXvDv32K8KLgDIM6Z4muxYHs62T0=","term":"{4}","sig":"MEQCIAISqeBbReZ4aAi5b+/fc5H7lzQYlP2uz2hATwxwFieDAiAJQ7QBNIlRnp6Eb8tqIjCEN4uUIXERoi9hJGh0TI0kOg==","sigAlgorithm":"secp256k1","phloPrice":"500","phloLimit":"1000","shardId":"root"}
//MEQCIBBP+ugdI9Rxi4UqqQHf2YnG+JRUX7gkBxDk67Ffl5etAiAxcFUmlc9WYhhn3/lpb0RRiXbuHeF9/BAr2ESw/zYPew==

//Private key	5f668a7ee96d944a4494cc947e4005e172d7ab3461ee5538f1f2a45a835e9657
//Public key	04ffc016579a68050d655d55df4e09f04605164543e257c8e6df10361e6068a5336588e9b355ea859c5ab4285a5ef0efdf62bc28b80320ce99e26bb1607b3ad93d
//ETH	fac7dde9d0fa1df6355bd1382fe75ba0c50e8840

        BigInteger priv = new BigInteger(privateKeyHex, 16);
        //BigInteger pubKey = Sign.publicKeyFromPrivate(priv);
        //ECKeyPair keyPair = new ECKeyPair(priv, pubKey);
        System.out.println("privateKeyHex= " + privateKeyHex);
        ECKeyPair keyPair = ECKeyPair.create(priv);
        BigInteger pubKey = keyPair.getPublicKey();

        System.out.println("priv= " + keyPair.getPrivateKey().toString(16));
        System.out.println("pubKey= " + pubKey.toString(16));
        System.out.println(pubKey.toByteArray().length);
        DeployDataProto.Builder builder = DeployDataProto.newBuilder();
        DeployDataProto deployData = 
         builder.                                                                                                                                             
            setTerm("{4}").
            setTimestamp(0L).
            setPhloPrice(500L).
            setPhloLimit(1000L).                                                                                                                                                          
            setValidAfterBlockNumber(0L).
            setShardId("root").
         build();
        byte [] serializedData = deployData.toByteArray();
        byte [] hashedData = new byte[32];
        
        Blake2bDigest hasher = new Blake2bDigest(256);
        hasher.update(serializedData, 0, serializedData.length);
        int hashLength = hasher.doFinal(hashedData, 0);
        Sign.SignatureData signature = Sign.signMessage(hashedData, keyPair, true);
        String sigStr = signature.getV().toString();
        //String sigStr = String.format("%02x", new BigInteger(1, signature.getV()));
        String hashedDataHex = String.format("%02x", new BigInteger(1, hashedData));
        System.out.println("sigStr = " + sigStr);
        System.out.println("hashedDataHex = " + hashedDataHex);
        return sigStr;

        /*
         * 
         * 
         * def signDeploy(privKey: ECPrivateKey, deploy: DeployDataProto): DeployDataProto = {
  // Take a projection of only the fields used to validate the signature
  val projection   = DeployDataProto(
    term                  = deploy.term,
    timestamp             = deploy.timestamp,
    phloPrice             = deploy.phloPrice,
    phloLimit             = deploy.phloLimit,
    validAfterBlockNumber = deploy.validAfterBlockNumber,
    shardId               = deploy.shardId 
  )
  val serialized   = ByteVector(projection.toByteArray)
  val deployer     = privKey.publicKey.decompressed
  val digest       = ByteVector(Blake2b256.hash(serialized.toArray))
  val signed       = privKey.sign(digest)
  projection.copy(
    sigAlgorithm   = "secp256k1",
    sig            = ByteString.copyFrom(signed.bytes.toArray),
    deployer       = ByteString.copyFrom(deployer.decompressedBytes.toArray)
  )
}
         * 
         * 
        */
    
        
        // Blake2bDigest hasher = new Blake2bDigest(256);
        // //todo come back here
        // hasher.update(serializedData, 0, serializedData.length);
        // // byte[] hashed = blake2bHex(deploySerialized);
        // int hashLength = hasher.doFinal(hashedData, 0);
        // //byte [] hashedHex = Hex.encode(hashedData);

        // // const sigArray = key.sign(hashed, {canonical: true}).toDER('array')
        // Sign.SignatureData signature = Sign.signMessage(hashedData, keyPair, true);

        // ASN1Integer r = new ASN1Integer(signature.getR());
        // ASN1Integer s = new ASN1Integer(signature.getS());
        // byte [] der = new DERSequence(new ASN1Integer []{r, s}).getEncoded();

        //return new String(Hex.encode(der));
    }

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

    private static String executeCommand(String[] command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        StringBuilder output = new StringBuilder();
        Process process;
        try {
            process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Command exited with code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return e.getMessage();
        }

        //just do a grpc call directly...this is stupid

        return output.toString();
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

        //sendDeploy();
        //sendHttpPost("AAAAAAAAAAAAAAAAAAAAAAAAH");
        //signProto("5f668a7ee96d944a4494cc947e4005e172d7ab3461ee5538f1f2a45a835e9657");
        signProto2("5f668a7ee96d944a4494cc947e4005e172d7ab3461ee5538f1f2a45a835e9657");
        //performGrpcDeployCall("AAAAAAAAAAAAAAAAAAAAAAAAH");
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        if (getPath(path) != null) {
            return -ErrorCodes.EEXIST();
        }
        MemoryPath parent = getParentPath(path);
        System.out.println("create() called with arguments: path = " + path + ", mode = " + mode + ", fi = " + fi);
        System.out.println("parent: " + parent.toString());
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
        String dataString = new String(data, StandardCharsets.UTF_8);
        System.out.println(dataString);
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
        if (path.contains("abc.txt")) {
            //sendDeploy();
            // try {
            //     sendRholangCode(path,dataString);
            // } catch (IOException ioe) {
            //     System.err.println("sendRholangCode failed catastrophically with an IOException.");
            // }
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

     public void saveStringToFile(String filePath, String content) {
         try (PrintWriter out = new PrintWriter(filePath)) {
             out.println(content);
         } catch (FileNotFoundException e) {
             e.printStackTrace();
         }
     }
}
