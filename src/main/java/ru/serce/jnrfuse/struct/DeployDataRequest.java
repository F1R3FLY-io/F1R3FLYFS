package ru.serce.jnrfuse.struct;

 public class DeployDataRequest {
     private DeployData data;
     private String deployer;
     private String signature;
     private String sigAlgorithm;

    public DeployDataRequest() {
    }
    public DeployData getData() {
        return data;
    }
    public void setData(DeployData data) {
        this.data = data;
    }
    public String getDeployer() {
        return deployer;
    }
    public void setDeployer(String deployer) {
        this.deployer = deployer;
    }
    public String getSignature() {
        return signature;
    }
    public void setSignature(String signature) {
        this.signature = signature;
    }
    public String getSigAlgorithm() {
        return sigAlgorithm;
    }
    public void setSigAlgorithm(String sigAlgorithm) {
        this.sigAlgorithm = sigAlgorithm;
    }
 }