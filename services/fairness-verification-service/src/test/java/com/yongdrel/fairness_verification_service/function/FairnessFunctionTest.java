package com.yongdrel.fairness_verification_service.function;
import org.junit.jupiter.api.*;import java.util.*;import java.util.function.Function;import static org.junit.jupiter.api.Assertions.*;
class FairnessFunctionTest{
 private final FairnessFunction f=new FairnessFunction();private final UUID d=UUID.randomUUID(),e=UUID.randomUUID();private final List<String> tickets=List.of("A","B","C");
 private FairnessFunction.DrawEvidence evidence(List<String>winners){String seed="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";return new FairnessFunction.DrawEvidence(d,e,FairnessFunction.sha256(seed),seed,"SHA-256(seed || counter[8-byte big-endian])",FairnessFunction.sha256(String.join("\n",tickets)),tickets,"SHA256_COUNTER_FISHER_YATES_V1",winners);}
 @Test void validEvidenceVerifies(){var r=f.verifyLotteryDraw().apply(evidence(List.of("B")));assertEquals("VERIFIED",r.status());}
 @Test void tamperedSeedFails(){var x=evidence(List.of("A"));var r=f.verify(new FairnessFunction.DrawEvidence(d,e,x.seedCommitment(),"tampered",x.randomnessEvidence(),x.ticketSnapshotHash(),tickets,x.algorithmVersion(),x.winnerIds()));assertEquals("FAILED",r.status());}
 @Test void modifiedTicketsFailHash(){var x=evidence(List.of("A"));var r=f.verify(new FairnessFunction.DrawEvidence(d,e,x.seedCommitment(),x.revealedSeed(),x.randomnessEvidence(),x.ticketSnapshotHash(),List.of("A","B"),x.algorithmVersion(),x.winnerIds()));assertEquals("FAILED",r.status());}
 @Test void incorrectWinnerFailsEligibility(){var r=f.verify(evidence(List.of("Z")));assertEquals("FAILED",r.status());}
 @Test void deterministicReplayRejectsIncorrectWinner(){assertEquals("FAILED",f.verify(evidence(List.of("A"))).status());}
}
