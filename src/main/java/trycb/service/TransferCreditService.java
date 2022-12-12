package trycb.service;

public interface TransferCreditService {
    void transferCredit(String sourceUser, String targetUser, int creditsToTransfer);
    void transferCredit(String targetUser, int creditsToTransfer);
}
