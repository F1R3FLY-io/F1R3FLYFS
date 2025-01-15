//package io.f1r3fly.fs.examples;
//
//import fr.acinq.secp256k1.Hex;
//import io.f1r3fly.fs.examples.storage.grcp.client.F1r3flyApi;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.Random;
//
//public class OneTimeTest {
//
//    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);
//
//    public static void main(String[] args) throws Exception {
//        byte[] key = Hex.decode("a8cf01d889cc6ef3119ecbd57301036a52c41ae6e44964e098cb2aefa4598954");
//        F1r3flyApi f1r3flyApiClient = new F1r3flyApi(key, "localhost", 40402);
//
////        LOGGER.debug("file " + f1r3flyApiClient.findDataByName("f1r3flyfs-1259323347/test.txt.encrypted"));
//
//        // random string
//        String prefix = "f1r3flyfs-" + new Random().nextInt();
////
//        String rholang1 =
//            "new gptAnswer, audio, dalle3Answer,\n" +
//                "    gpt3(`rho:ai:gpt3`),\n" +
//                "    gpt4(`rho:ai:gpt4`),\n" +
//                "    dalle3(`rho:ai:dalle3`),\n" +
//                "    textToAudio(`rho:ai:textToAudio`),\n" +
//                "    dumpFile(`rho:ai:dumpFile`),  // temporary\n" +
//                "    stdout(`rho:io:stdout`) in {\n" +
//                "\n" +
//                "  gpt3!(\"Describe an appearance of human-like robot: \", *gptAnswer) |\n" +
//                "  for(@answer <- gptAnswer) {\n" +
//                "    stdout!([\"GTP3 created a prompt\", answer]) |\n" +
//                "\n" +
//                "    dalle3!(answer, *dalle3Answer) |\n" +
//                "    for(@dalle3Answer <- dalle3Answer) {\n" +
//                "      stdout!([\"Dall-e-3 created an image\", dalle3Answer])\n" +
//                "    }\n" +
//                "  } |\n" +
//                "\n" +
//                "  textToAudio!(\"Hello, I am a robot. Rholang give me a voice!\", *audio) |\n" +
//                "\n" +
//                "  for(@bytes <- audio) {\n" +
//                "    dumpFile!(\"text-to-audio.mp3\", bytes)\n" +
//                "  }\n" +
//                "}\n";
////
//        String block1 = f1r3flyApiClient.deploy(rholang1, true, "rholang");
////        List<RhoTypes.Par> pars1 = f1r3flyApiClient.getDataAtBlockByName(block1, prefix + "1");
//
////        LOGGER.info("Block {}, data {}", block1, pars1);
////        String rholang2 =
////            "for (@v <- @\"" +prefix +"1\") { @\"" +prefix +"2\"!(2) }";
////
////        String block2 = f1r3flyApiClient.deploy(rholang2, false, "rholang");
////        List<RhoTypes.Par> pars2 = f1r3flyApiClient.getDataAtBlockByName(block1, prefix + "1");
////
////        List<RhoTypes.Par> pars3 = f1r3flyApiClient.findDataByName(prefix+"1");
////
////        LOGGER.info("data {}", pars3);
////
////        String rholang3 =
////            "@\"" +prefix +"1\"!(3)";
////
////        String block3 = f1r3flyApiClient.deploy(rholang3, false, "rholang");
////
////        List<RhoTypes.Par> pars4 = f1r3flyApiClient.findDataByName(prefix+"1");
////        LOGGER.info("data {}", pars4);
//
//
//    }
//}