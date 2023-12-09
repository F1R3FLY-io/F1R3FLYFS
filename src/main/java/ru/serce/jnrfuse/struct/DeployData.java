package ru.serce.jnrfuse.struct;

 public class DeployData {
     private long timestamp;
     private String term;
     private long phloLimit;
     private long phloPrice;
     private long validAfterBlockNumber;
     private String shardId;

    public DeployData() {
    }
    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    public String getTerm() {
        return term;
    }
    public void setTerm(String term) {
        this.term = term;
    }
    public long getPhloLimit() {
        return phloLimit;
    }
    public void setPhloLimit(long phloLimit) {
        this.phloLimit = phloLimit;
    }
    public long getPhloPrice() {
        return phloPrice;
    }
    public void setPhloPrice(long phloPrice) {
        this.phloPrice = phloPrice;
    }
    public long getValidAfterBlockNumber() {
        return validAfterBlockNumber;
    }
    public void setValidAfterBlockNumber(long validAfterBlockNumber) {
        this.validAfterBlockNumber = validAfterBlockNumber;
    }
    public String getShardId() {
        return shardId;
    }
    public void setShardId(String shardId) {
        this.shardId = shardId;
    }
 }