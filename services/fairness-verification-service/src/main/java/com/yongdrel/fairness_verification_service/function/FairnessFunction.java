package com.yongdrel.fairness_verification_service.function;
import org.springframework.context.annotation.Bean;import org.springframework.stereotype.Component;import java.nio.charset.StandardCharsets;import java.security.*;import java.nio.ByteBuffer;import java.util.*;import java.util.function.Function;
@Component public class FairnessFunction{
 private static final String PUBLISHED_ALGORITHM="SHA256_COUNTER_FISHER_YATES_V1";
 private static final String RANDOMNESS_EVIDENCE="SHA-256(seed || counter[8-byte big-endian])";
 @Bean public Function<DrawEvidence,VerificationResult> verifyLotteryDraw(){return this::verify;}
 VerificationResult verify(DrawEvidence e){
  List<String> reasons=new ArrayList<>();if(e==null||e.drawId()==null||e.eventId()==null||blank(e.seedCommitment())||blank(e.revealedSeed())||blank(e.ticketSnapshotHash())||e.ticketIds()==null||e.winnerIds()==null||blank(e.algorithmVersion()))return new VerificationResult("INSUFFICIENT_EVIDENCE",List.of("Required draw evidence is missing"));
  if(!sha256(e.revealedSeed()).equalsIgnoreCase(e.seedCommitment()))reasons.add("Seed commitment does not match revealed seed");
  if(!sha256(String.join("\n",e.ticketIds())).equalsIgnoreCase(e.ticketSnapshotHash()))reasons.add("Ticket snapshot hash does not match ticket list");
  if(new HashSet<>(e.ticketIds()).size()!=e.ticketIds().size())reasons.add("Eligible ticket snapshot contains duplicates");
  if(!isSorted(e.ticketIds()))reasons.add("Eligible ticket snapshot is not canonically sorted");
  if(new HashSet<>(e.winnerIds()).size()!=e.winnerIds().size())reasons.add("Published winners contain duplicates");
  if(!e.ticketIds().containsAll(e.winnerIds()))reasons.add("Published winner is not eligible");
  if(!PUBLISHED_ALGORITHM.equals(e.algorithmVersion()))return new VerificationResult("INSUFFICIENT_EVIDENCE",List.of("Unsupported algorithm version"));
  if(!RANDOMNESS_EVIDENCE.equals(e.randomnessEvidence()))reasons.add("Randomness evidence does not describe the published algorithm");
  if(!e.revealedSeed().matches("[0-9a-fA-F]{64}"))reasons.add("Revealed seed is not 32 bytes encoded as hexadecimal");
  if(!reasons.isEmpty())return new VerificationResult("FAILED",List.copyOf(reasons));
  if(!replay(e.ticketIds(),e.winnerIds(),e.revealedSeed()))reasons.add("Published winners do not match deterministic replay");
  return reasons.isEmpty()?new VerificationResult("VERIFIED",List.of()):new VerificationResult("FAILED",List.copyOf(reasons));
 }
 private static boolean isSorted(List<String> ids){for(int i=1;i<ids.size();i++)if(ids.get(i-1).compareTo(ids.get(i))>0)return false;return true;}
 private static boolean replay(List<String> ids,List<String> winners,String seedHex){
  List<String> shuffled=new ArrayList<>(ids);byte[] seed=HexFormat.of().parseHex(seedHex);long counter=0;
  for(int i=0;i<winners.size();i++){int bound=shuffled.size()-i;int selected=i+nextInt(seed,counter++,bound);Collections.swap(shuffled,i,selected);if(!shuffled.get(i).equals(winners.get(i)))return false;}return true;
 }
 private static int nextInt(byte[] seed,long counter,int bound){long limit=(1L<<32)-((1L<<32)%bound);while(true){byte[] b=digest(seed,counter++);long value=ByteBuffer.wrap(b).getInt()&0xffffffffL;if(value<limit)return(int)(value%bound);}}
 private static byte[] digest(byte[] seed,long counter){try{MessageDigest d=MessageDigest.getInstance("SHA-256");d.update(seed);d.update(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());return d.digest();}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
 private static boolean blank(String s){return s==null||s.isBlank();}static String sha256(String s){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(b);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 public record DrawEvidence(UUID drawId,UUID eventId,String seedCommitment,String revealedSeed,String randomnessEvidence,String ticketSnapshotHash,List<String> ticketIds,String algorithmVersion,List<String> winnerIds){}
 public record VerificationResult(String status,List<String> reasons){}
}
